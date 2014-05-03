package org.lance.lib.bitmap.common;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;

import org.lance.lib.bitmap.core.BitmapCache;
import org.lance.lib.bitmap.core.BitmapDisplayConfig;
import org.lance.lib.bitmap.core.BitmapLoader;
import org.lance.lib.bitmap.core.BitmapProcessor;
import org.lance.lib.bitmap.core.BitmapWorker;
import org.lance.lib.bitmap.util.CacheUtils;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;

/**
 * 位图加载器
 * @author lance
 *
 */
public class CommonBitmapLoader implements BitmapLoader {
	private static final String TAG = "CommonBitmapLoader";

	protected BitmapCache mCache;
	private File mHttpCacheDir;
	private final Object mHttpDirLock = new Object();
	private boolean mHttpDirStarting = true;

	private static final int HTTP_CACHE_SIZE = 10 * 1024 * 1024; // 10MB
	private static final int IO_BUFFER_SIZE = 8 * 1024;
	private static final String HTTP_CACHE_DIR = "http_temp";
	private static final int MESSAGE_INIT_HTTP_DIR = 0;

	public CommonBitmapLoader(Context context){
		if(Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())){
			mHttpCacheDir = CacheUtils.getDiskCacheDir(context, HTTP_CACHE_DIR);
		}else{
			mHttpCacheDir = context.getDir(HTTP_CACHE_DIR, Context.MODE_PRIVATE);
		}
	}

	public CommonBitmapLoader(Context context, BitmapCache cache){
		mHttpCacheDir = CacheUtils.getDiskCacheDir(context, HTTP_CACHE_DIR);
		mCache = cache;
		new CacheAsyncTask().execute(MESSAGE_INIT_HTTP_DIR);
	}

	public void setCache(BitmapCache cache){
		mCache = cache;
		new CacheAsyncTask().execute(MESSAGE_INIT_HTTP_DIR);
	}

	private void initHttpDir() {
		synchronized (mHttpDirLock) {
			System.out.println("BitmapLoader->initHttpDir:HttpDir-exists:"+mHttpCacheDir.exists());
			if (!mHttpCacheDir.exists()) {
				System.out.println("BitmapLoader->initHttpDir:HttpDir-mkdirs:" + mHttpCacheDir.mkdirs());
			}else{
				for(File file : mHttpCacheDir.listFiles()){
					file.delete();
				}
			}
			mHttpDirStarting = false;
			mHttpDirLock.notifyAll();
		}
	}

	@Override
	public Bitmap load(Object object, WeakReference<BitmapWorker.Progress> progressRef,
					   BitmapProcessor processor, BitmapDisplayConfig displayConfig, Resources resources) {
		Bitmap bitmap = null;
		InputStream inputStream = null;
		File temp = null;
		inputStream = getFromDisk(object);
		if(inputStream==null){
			String url = object.toString();
			String key = CacheUtils.hashKeyForDisk(url);
			synchronized (mHttpDirLock) {
				while (mHttpDirStarting) {
					try {
						mHttpDirLock.wait();
					} catch (InterruptedException e) {}
				}
			}
			System.out.println("BitmapLoader->load:HttpDir-exists:"+mHttpCacheDir.exists());
			if (!mHttpCacheDir.exists()) {
				mHttpCacheDir.mkdirs();
			}
			//如果下载url相同,会发生问题
			temp = new File(mHttpCacheDir, key);
			inputStream = getFromNetToFile(url, temp, progressRef);
		}else{
			final BitmapWorker.Progress progress = progressRef.get();
			if(progress!=null){
				progress.setProgress(100, 100);
			}
		}
		if(inputStream instanceof FileInputStream){
			try {
				FileDescriptor fd = ((FileInputStream) inputStream).getFD();
				bitmap = processor.process(fd, displayConfig, mCache);
			} catch (IOException e) {
				e.printStackTrace();
			}finally {
				try {
					inputStream.close();
				} catch (IOException e) { }
			}
		}
		if(temp!=null&&temp.exists()){
			temp.delete();
		}
		return bitmap;
	}

	private InputStream getFromDisk(Object data){
		return mCache.getFromDisk(data);
	}

	private InputStream getFromNetToFile(String url, File file, WeakReference<BitmapWorker.Progress> progressRef){
		FileInputStream inputStream = null;
		boolean success = downloadToFile(url, file, progressRef.get());
		if(success){
			try {
				inputStream = new FileInputStream(file);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return inputStream;
	}

	private boolean downloadToFile(String urlString, File file, BitmapWorker.Progress progress) {
		CacheUtils.disableConnectionReuseIfNecessary();
		HttpURLConnection urlConnection = null;
		BufferedOutputStream out = null;
		BufferedInputStream in = null;
		try {
			final URL url = new URL(urlString);
			urlConnection = (HttpURLConnection) url.openConnection();
			in = new BufferedInputStream(urlConnection.getInputStream(), IO_BUFFER_SIZE);
			int fileLen = urlConnection.getContentLength();
			out = new BufferedOutputStream(new FileOutputStream(file), IO_BUFFER_SIZE);
			int count = 0;
			int b;
			while ((b = in.read()) != -1) {
				out.write(b);
				count++;
				if(progress!=null)
					progress.setProgress(fileLen, count);		//使用WeakReference降低性能,强引用的话,不能保证及时回收
			}
			return true;
		} catch (final IOException e) {
			Log.e(TAG, "Error in downloadBitmap - " + e);
		} finally {
			if (urlConnection != null) {
				urlConnection.disconnect();
			}
			try {
				if (out != null) {
					out.close();
				}
				if (in != null) {
					in.close();
				}
			} catch (final IOException e) {}
		}
		return false;
	}

	protected class CacheAsyncTask extends AsyncTask<Object, Void, Void> {

		@Override
		protected Void doInBackground(Object... params) {
			switch ((Integer)params[0]) {
				case MESSAGE_INIT_HTTP_DIR:
					initHttpDir();
					break;
			}
			return null;
		}
	}
}
