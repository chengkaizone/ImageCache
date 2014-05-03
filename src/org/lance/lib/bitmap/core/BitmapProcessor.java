package org.lance.lib.bitmap.core;

import java.io.FileDescriptor;

import android.graphics.Bitmap;

/**
 * 如何处理图片
 * @author lance
 *
 */
public interface BitmapProcessor {
	/** 根据配置和缓存处理文件返回位图 */
	Bitmap process(FileDescriptor source, BitmapDisplayConfig config, BitmapCache cache);
}
