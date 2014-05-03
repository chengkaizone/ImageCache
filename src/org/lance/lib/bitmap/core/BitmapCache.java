package org.lance.lib.bitmap.core;

import java.io.InputStream;

import android.graphics.Bitmap;

/**
 * 位图缓存接口---等待开发者自己实现
 * @author lance
 *
 */
public interface BitmapCache {
	/** 将位图添加到内存 */
	public void addToMemory(Object data, Bitmap bitmap);
	/** 从内存中获取位图 */
	public Bitmap getFromMemory(Object data);
	/** 添加位图到磁盘 */
	public void addToDisk(Object data, Bitmap bitmap);
	/** 从磁盘中获取输入流 */
	public InputStream getFromDisk(Object data);
	/** 关闭回调 */
	public void postClose(Callback callback);
	/** 清理回调 */
	public void postClear(Callback callback);
	/** 清空内存 */
	public void clearMemoryCache();
	/** 获取缓存大小 */
	public long getCacheSize();

	/** 回调类 */
	public static interface Callback{
		void onFinished(int operationId);
	}
}
