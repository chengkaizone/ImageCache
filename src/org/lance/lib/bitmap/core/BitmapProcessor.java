package org.lance.lib.bitmap.core;

import java.io.FileDescriptor;

import android.graphics.Bitmap;

/**
 * ��δ���ͼƬ
 * @author lance
 *
 */
public interface BitmapProcessor {
	/** �������úͻ��洦���ļ�����λͼ */
	Bitmap process(FileDescriptor source, BitmapDisplayConfig config, BitmapCache cache);
}
