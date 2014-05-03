package org.lance.lib.bitmap.core;

import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.ImageView;

/**
 * 
 * @author lance
 *
 */
public class BitmapWorker {
	private Resources mResources;
	private boolean mExitTasksEarly = false;
	private boolean mPauseWork = false;
	private final Object mPauseWorkLock = new Object();//ͬ����
	private ExecutorService mExecutor;
	private BitmapWorkerConfig mConfig;
	private BitmapCache mCache;

	public BitmapWorker(Context context, BitmapWorkerConfig config, BitmapCache cache) {
		mResources = context.getResources();
		mConfig = config;
		mCache = cache;
		init();
	}

	private void init() {
		mExecutor = Executors.newFixedThreadPool(mConfig.poolSize, new ThreadFactory() {
			@Override
			public Thread newThread(Runnable r) {
				Thread t = new Thread(r);
				// �����̵߳����ȼ������߳��Ⱥ�˳��ִ�У�����Խ�ߣ�����cpuִ�е�ʱ��Խ�ࣩ
				t.setPriority(Thread.NORM_PRIORITY - 1);
				return t;
			}
		});
	}

	public void loadImage(ImageView imageView, Object data, BitmapDisplayConfig displayConfig){
		this.loadImage(imageView, null, data, displayConfig);
	}

	public void loadImage(ImageView imageView, Progress progress, Object data, BitmapDisplayConfig displayConfig) {
		if (data == null || imageView == null) {
			return;
		}

		if (displayConfig == null)
			displayConfig = mConfig.defaultDisplayConfig;

		Bitmap bitmap = null;

		if (mCache != null) {
			bitmap = mCache.getFromMemory(data);
		}
		System.err.println("loadImage:"+bitmap);

		if (bitmap != null) {
			imageView.setImageBitmap(bitmap);
		} else if (cancelPotentialWork(data, imageView)) {
			final BitmapLoadAndDisplayTask task = new BitmapLoadAndDisplayTask(imageView, progress, displayConfig);
			final AsyncDrawable asyncDrawable = new AsyncDrawable(mResources, displayConfig.getLoadingDrawable(), task);
			imageView.setImageDrawable(asyncDrawable);
			task.executeOnExecutor(mExecutor, data);
		}
	}

	public void postCloseCache(BitmapCache.Callback callback){
		mCache.postClose(callback);
	}

	public void postClearCache(BitmapCache.Callback callback){
		mCache.postClear(callback);
	}

	public void clearMemoryCache(){
		mCache.clearMemoryCache();
	}

	public long getCacheSize(){
		return mCache.getCacheSize();
	}

	public static class AsyncDrawable extends BitmapDrawable {
		private final WeakReference<BitmapLoadAndDisplayTask> bitmapWorkerTaskReference;

		public AsyncDrawable(Resources res, Bitmap bitmap, BitmapLoadAndDisplayTask bitmapWorkerTask) {
			super(res, bitmap);
			bitmapWorkerTaskReference = new WeakReference<BitmapLoadAndDisplayTask>(bitmapWorkerTask);
		}

		public AsyncDrawable(Resources res, BitmapDrawable drawable, BitmapLoadAndDisplayTask bitmapLoadAndDisplayTask){
			this(res, drawable!=null?drawable.getBitmap():null, bitmapLoadAndDisplayTask);
		}

		public BitmapLoadAndDisplayTask getBitmapWorkerTask() {
			return bitmapWorkerTaskReference.get();
		}
	}
	
	/**
	 * λͼ��������  �������߳���ʾ
	 * @author lance
	 *
	 */
	@SuppressLint("NewApi")
	private class BitmapLoadAndDisplayTask extends AsyncTask<Object, Void, Bitmap> {
		private Object data;
		private final WeakReference<ImageView> imageViewReference;
		private final WeakReference<Progress> progressReference;
		private final BitmapDisplayConfig displayConfig;

		public BitmapLoadAndDisplayTask(ImageView imageView, Progress progress, BitmapDisplayConfig config) {
			imageViewReference = new WeakReference<ImageView>(imageView);
			progressReference = new WeakReference<Progress>(progress);
			displayConfig = config;
		}

		@Override //Object ��Ӧ���͵�һ������ String��Ӧ����������
		protected Bitmap doInBackground(Object... params) {
			data = params[0];
			Bitmap bitmap = null;

			synchronized (mPauseWorkLock) {
				while (mPauseWork && !isCancelled()) {
					try {
						mPauseWorkLock.wait();
					} catch (InterruptedException e) {
					}
				}
			}

			if (!isCancelled() && getAttachedImageView() != null && !mExitTasksEarly) {
				bitmap = mConfig.loader.load(data, progressReference, mConfig.processor, displayConfig, mResources);
			}

			if (bitmap != null) {
				mCache.addToMemory(data, bitmap);
				mCache.addToDisk(data, bitmap);
			}

			return bitmap;
		}

		@Override //Bitmap��doInbackground�����ķ���ֵ
		protected void onPostExecute(Bitmap bitmap) {
			if (isCancelled() || mExitTasksEarly) {
				bitmap = null;
			}

			// �ж��̺߳͵�ǰ��imageview�Ƿ���ƥ��
			final ImageView imageView = getAttachedImageView();
			if (bitmap != null && imageView != null) {
				mConfig.displayer.show(imageView, bitmap);
			} else if (bitmap == null && imageView != null) {
				mConfig.displayer.show(imageView, displayConfig.getLoadfailDrawable());
			}
		}

		@Override //ȡ��
		protected void onCancelled(Bitmap bitmap) {
			super.onCancelled(bitmap);
			synchronized (mPauseWorkLock) {
				mPauseWorkLock.notifyAll();
			}
		}

		//��ȡ��ǰ����ƥ���imageView,��ֹ��������������
		private ImageView getAttachedImageView() {
			final ImageView imageView = imageViewReference.get();
			final BitmapLoadAndDisplayTask bitmapWorkerTask = getBitmapTaskFromImageView(imageView);
			if (this == bitmapWorkerTask) {
				return imageView;
			}
			return null;
		}
	}

	private static boolean cancelPotentialWork(Object data, ImageView imageView) {
		final BitmapLoadAndDisplayTask bitmapWorkerTask = getBitmapTaskFromImageView(imageView);

		if (bitmapWorkerTask != null) {
			final Object bitmapData = bitmapWorkerTask.data;
			if (bitmapData == null || !bitmapData.equals(data)) {
				bitmapWorkerTask.cancel(true);
			} else {
				// ͬһ���߳��Ѿ���ִ��
				return false;
			}
		}
		return true;
	}

	private static BitmapLoadAndDisplayTask getBitmapTaskFromImageView(View imageView) {
		if (imageView != null) {
			Drawable drawable = null;
			drawable = ((ImageView) imageView).getDrawable();

			if (drawable instanceof AsyncDrawable) {
				final AsyncDrawable asyncDrawable = (AsyncDrawable) drawable;
				return asyncDrawable.getBitmapWorkerTask();
			}
		}
		return null;
	}

	public static class BitmapWorkerConfig {
		public BitmapDisplayer displayer;
		public BitmapLoader loader;
		public BitmapProcessor processor;
		public BitmapDisplayConfig defaultDisplayConfig;
		public int poolSize;

		public BitmapWorkerConfig(Resources resources) {
			defaultDisplayConfig = new BitmapDisplayConfig();
			//����ͼƬ����ʾ���ߴ磨Ϊ��Ļ�Ĵ�С,Ĭ��Ϊ��Ļ��ȵ�1/2��
			DisplayMetrics displayMetrics = resources.getDisplayMetrics();
			int defaultWidth = (int) Math.floor(displayMetrics.widthPixels / 2);
			defaultDisplayConfig.setBitmapHeight(defaultWidth);
			defaultDisplayConfig.setBitmapWidth(defaultWidth);

		}
	}

	public static interface Progress{
		void setProgress(int total, int current);
	}
}
