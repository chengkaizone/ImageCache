package org.lance.lib.bitmap.common;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.SoftReference;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.lance.lib.BuildConfig;
import org.lance.lib.bitmap.cache.DiskLruCache;
import org.lance.lib.bitmap.core.BitmapCache;
import org.lance.lib.bitmap.core.BitmapCache.Callback;
import org.lance.lib.bitmap.util.CacheUtils;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.util.LruCache;

/**
 * 缓存实现类
 * 
 * @author lance
 * 
 */
@SuppressLint("NewApi")
public class CommonBitmapCache implements BitmapCache {

	private static final String TAG = "CommonBitmapCache";

	private Config mConfig;
	private LruCache<String, Bitmap> mMemoryCache;

	private final Object mDiskCacheLock = new Object();
	private DiskLruCache mDiskCache;
	private boolean mDiskCacheStarting = true;

	private Set<SoftReference<Bitmap>> mReusableBitmaps;

	// Compression settings when writing images to disk cache
	private static final Bitmap.CompressFormat DEFAULT_COMPRESS_FORMAT = Bitmap.CompressFormat.PNG;
	private static final int DEFAULT_COMPRESS_QUALITY = 70;
	private static final int DISK_CACHE_INDEX = 0;

	public static final int MESSAGE_CLEAR = 0;
	public static final int MESSAGE_INIT_DISK_CACHE = 1;
	public static final int MESSAGE_FLUSH = 2;
	public static final int MESSAGE_CLOSE = 3;

	public CommonBitmapCache(Config config) {
		init(config);
	}

	public static CommonBitmapCache getInstance(
			FragmentManager fragmentManager, Config config) {
		final RetainFragment mRetainFragment = findOrCreateRetainFragment(fragmentManager);
		CommonBitmapCache imageCache = (CommonBitmapCache) mRetainFragment
				.getObject();
		if (imageCache == null) {
			imageCache = new CommonBitmapCache(config);
			mRetainFragment.setObject(imageCache);
		}
		return imageCache;
	}

	private void init(Config config) {
		mConfig = config;
		if (CacheUtils.hasHoneycomb()) {
			mReusableBitmaps = Collections
					.synchronizedSet(new HashSet<SoftReference<Bitmap>>());
		}
		mMemoryCache = new LruCache<String, Bitmap>(mConfig.memCacheSize) {
			@Override
			protected void entryRemoved(boolean evicted, String key,
					Bitmap oldValue, Bitmap newValue) {
				// System.err.println("entryRemoved:"+RecyclingBitmapDrawable.class.isInstance(oldValue));
				mReusableBitmaps.add(new SoftReference<Bitmap>(oldValue));
			}

			@Override
			protected int sizeOf(String key, Bitmap value) {
				final int bitmapSize = CacheUtils.getBitmapSize(value) / 1024;
				return bitmapSize == 0 ? 1 : bitmapSize;
			}
		};
		new CacheAsyncTask(null).execute(MESSAGE_INIT_DISK_CACHE);
	}

	
	@Override
	public void addToMemory(Object data, Bitmap value) {
		if (data == null || value == null) {
			return;
		}
		if (mMemoryCache != null) {
			mMemoryCache.put(data.toString(), value);
		}
	}

	@Override
	public Bitmap getFromMemory(Object data) {
		Bitmap memValue = null;
		if (mMemoryCache != null) {
			memValue = mMemoryCache.get(data.toString());
		}
		return memValue;
	}

	@Override
	public void addToDisk(Object data, Bitmap value) {
		synchronized (mDiskCacheLock) {
			// Add to disk cache
			if (mDiskCache != null) {
				final String key = CacheUtils.hashKeyForDisk(data.toString());
				OutputStream out = null;
				try {
					DiskLruCache.Snapshot snapshot = mDiskCache.get(key);
					if (snapshot == null) {
						final DiskLruCache.Editor editor = mDiskCache.edit(key);
						if (editor != null) {
							out = editor.newOutputStream(DISK_CACHE_INDEX);
							value.compress(DEFAULT_COMPRESS_FORMAT,
									DEFAULT_COMPRESS_QUALITY, out);
							editor.commit();
							out.close();
						}
					} else {
						snapshot.getInputStream(DISK_CACHE_INDEX).close();
					}
				} catch (final IOException e) {
					Log.e(TAG, "addBitmapToCache - " + e);
				} catch (Exception e) {
					Log.e(TAG, "addBitmapToCache - " + e);
				} finally {
					try {
						if (out != null) {
							out.close();
						}
						mDiskCache.flush();
					} catch (IOException e) {
					}
				}
			}
		}
	}

	@Override
	public InputStream getFromDisk(Object data) {
		final String key = CacheUtils.hashKeyForDisk(data.toString());
		InputStream inputStream = null;
		synchronized (mDiskCacheLock) {
			while (mDiskCacheStarting) {
				try {
					mDiskCacheLock.wait();
				} catch (InterruptedException e) {
				}
			}
			if (mDiskCache != null) {
				try {
					final DiskLruCache.Snapshot snapshot = mDiskCache.get(key);
					if (snapshot != null) {
						inputStream = snapshot.getInputStream(DISK_CACHE_INDEX);
					}
				} catch (final IOException e) {
					Log.e(TAG, "getBitmapFromDiskCache - " + e);
				}
			}
			return inputStream;
		}
	}

	/**
	 * @param options
	 *            - BitmapFactory.Options with out* options populated
	 * @return Bitmap that case be used for inBitmap
	 */
	protected Bitmap getBitmapFromReusableSet(BitmapFactory.Options options) {
		Bitmap bitmap = null;

		if (mReusableBitmaps != null && !mReusableBitmaps.isEmpty()) {
			synchronized (mReusableBitmaps) {
				final Iterator<SoftReference<Bitmap>> iterator = mReusableBitmaps
						.iterator();
				Bitmap item;

				while (iterator.hasNext()) {
					item = iterator.next().get();

					if (null != item && item.isMutable()) {
						// Check to see it the item can be used for inBitmap
						if (canUseForInBitmap(item, options)) {
							bitmap = item;

							// Remove from reusable set so it can't be used
							// again
							iterator.remove();
							break;
						}
					} else {
						// Remove from the set if the reference has been
						// cleared.
						iterator.remove();
					}
				}
			}
		}

		return bitmap;
	}

	/**
	 * @param candidate
	 *            - Bitmap to check
	 * @param targetOptions
	 *            - Options that have the out* value populated
	 * @return true if <code>candidate</code> can be used for inBitmap re-use
	 *         with <code>targetOptions</code>
	 */
	// @TargetApi(Build.VERSION_CODES.KITKAT)
	private static boolean canUseForInBitmap(Bitmap candidate,
			BitmapFactory.Options targetOptions) {

		if (!CacheUtils.hasKitKat()) {
			// On earlier versions, the dimensions must match exactly and the
			// inSampleSize must be 1
			return candidate.getWidth() == targetOptions.outWidth
					&& candidate.getHeight() == targetOptions.outHeight
					&& targetOptions.inSampleSize == 1;
		}

		// From Android 4.4 (KitKat) onward we can re-use if the byte size of
		// the new bitmap
		// is smaller than the reusable bitmap candidate allocation byte count.
		int width = targetOptions.outWidth / targetOptions.inSampleSize;
		int height = targetOptions.outHeight / targetOptions.inSampleSize;
		int byteCount = width * height
				* getBytesPerPixel(candidate.getConfig());
		return byteCount <= candidate.getByteCount();
		// return byteCount <= candidate.getAllocationByteCount();
	}

	/**
	 * Return the byte usage per pixel of a bitmap based on its configuration.
	 * 
	 * @param config
	 *            The bitmap configuration.
	 * @return The byte usage per pixel.
	 */
	private static int getBytesPerPixel(Bitmap.Config config) {
		if (config == Bitmap.Config.ARGB_8888) {
			return 4;
		} else if (config == Bitmap.Config.RGB_565) {
			return 2;
		} else if (config == Bitmap.Config.ARGB_4444) {
			return 2;
		} else if (config == Bitmap.Config.ALPHA_8) {
			return 1;
		}
		return 1;
	}

	private void initDiskCache() {
		synchronized (mDiskCacheLock) {
			System.out.println("initDiskCache");
			if (mDiskCache == null || mDiskCache.isClosed()) {
				File diskCacheDir = mConfig.diskCacheDir;
				if (diskCacheDir != null) {
					if (!diskCacheDir.exists()) {
						diskCacheDir.mkdirs();
					}
					if (getUsableSpace(diskCacheDir) > mConfig.diskCacheSize) {
						try {
							mDiskCache = DiskLruCache.open(diskCacheDir, 1, 1,
									mConfig.diskCacheSize);
						} catch (final IOException e) {
							mConfig.diskCacheDir = null;
							Log.e(TAG, "initDiskCache - " + e);
						}
					}
				}
			}
			mDiskCacheStarting = false;
			mDiskCacheLock.notifyAll();
		}
	}

	private void clearCache() {
		if (mMemoryCache != null) {
			mMemoryCache.evictAll();
			if (BuildConfig.DEBUG) {
				Log.d(TAG, "Memory cache cleared");
			}
		}

		synchronized (mDiskCacheLock) {
			mDiskCacheStarting = true;
			if (mDiskCache != null && !mDiskCache.isClosed()) {
				try {
					mDiskCache.delete();
					if (BuildConfig.DEBUG) {
						Log.d(TAG, "Disk cache cleared");
					}
				} catch (IOException e) {
					Log.e(TAG, "postClearCache - " + e);
				}
				mDiskCache = null;
				initDiskCache();
			}
		}
	}

	public void clearMemoryCache() {
		if (mMemoryCache != null) {
			mMemoryCache.evictAll();
			if (BuildConfig.DEBUG) {
				Log.d(TAG, "Memory cache cleared");
			}
		}
	}

	private void flush() {
	}

	@Override
	public void postClose(Callback callback) {
		new CacheAsyncTask(callback).execute(MESSAGE_CLOSE);
	}

	@Override
	public void postClear(Callback callback) {
		new CacheAsyncTask(callback).execute(MESSAGE_CLEAR);
	}

	private void closeCache() {
		synchronized (mDiskCacheLock) {
			if (mDiskCache != null) {
				try {
					if (!mDiskCache.isClosed()) {
						mDiskCache.close();
						mDiskCache = null;
					}
				} catch (IOException e) {
					Log.e(TAG, "postClose - " + e);
				}
			}
		}
	}

	public long getCacheSize() {
		return mDiskCache.size();
	}

	protected class CacheAsyncTask extends AsyncTask<Object, Void, Integer> {

		private Callback callback;

		public CacheAsyncTask(Callback callback) {
			this.callback = callback;
		}

		@Override
		protected Integer doInBackground(Object... params) {
			final int operationId = (Integer) params[0];
			switch (operationId) {
			case MESSAGE_CLEAR:
				clearCache();
				break;
			case MESSAGE_INIT_DISK_CACHE:
				initDiskCache();
				break;
			case MESSAGE_FLUSH:
				flush();
				break;
			case MESSAGE_CLOSE:
				closeCache();
				break;
			}
			return operationId;
		}

		@Override
		protected void onPostExecute(Integer operationId) {
			if (callback != null) {
				callback.onFinished(operationId);
			}
		}
	}

	public static long getUsableSpace(File path) {
		return path.getUsableSpace();
	}

	public static class Config {
		public File diskCacheDir;
		private int memCacheSize;
		public int diskCacheSize;
		/** 设置内存的缓存比例 */
		public void setMemCacheSizePercent(Context context, float percent) {
			if (percent < 0.05f || percent > 0.8f) {
				throw new IllegalArgumentException(
						"setMemCacheSizePercent - percent must be "
								+ "between 0.05 and 0.8 (inclusive)");
			}
			// memCacheSize = Math.round(percent * getMemoryClass(context) *
			// 1024 * 1024);
			memCacheSize = Math.round(percent
					* Runtime.getRuntime().maxMemory() / 1024);
		}

		private static int getMemoryClass(Context context) {
			return ((ActivityManager) context
					.getSystemService(Context.ACTIVITY_SERVICE))
					.getMemoryClass();
		}

	}

	protected static RetainFragment findOrCreateRetainFragment(
			FragmentManager fm) {
		// Check to see if we have retained the worker fragment.
		RetainFragment mRetainFragment = (RetainFragment) fm
				.findFragmentByTag(TAG);

		// If not retained (or first time running), we need to create and add
		// it.
		if (mRetainFragment == null) {
			mRetainFragment = new RetainFragment();
			fm.beginTransaction().add(mRetainFragment, TAG)
					.commitAllowingStateLoss();
		}

		return mRetainFragment;
	}

	public static class RetainFragment extends Fragment {
		private Object mObject;

		public RetainFragment() {
		}

		@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			setRetainInstance(true);
		}

		public void setObject(Object object) {
			mObject = object;
		}

		public Object getObject() {
			return mObject;
		}
	}

}
