package org.lance.lib.bitmap.core;

import java.io.InputStream;

import android.graphics.Bitmap;

/**
 * λͼ����ӿ�---�ȴ��������Լ�ʵ��
 * @author lance
 *
 */
public interface BitmapCache {
	/** ��λͼ��ӵ��ڴ� */
	public void addToMemory(Object data, Bitmap bitmap);
	/** ���ڴ��л�ȡλͼ */
	public Bitmap getFromMemory(Object data);
	/** ���λͼ������ */
	public void addToDisk(Object data, Bitmap bitmap);
	/** �Ӵ����л�ȡ������ */
	public InputStream getFromDisk(Object data);
	/** �رջص� */
	public void postClose(Callback callback);
	/** ����ص� */
	public void postClear(Callback callback);
	/** ����ڴ� */
	public void clearMemoryCache();
	/** ��ȡ�����С */
	public long getCacheSize();

	/** �ص��� */
	public static interface Callback{
		void onFinished(int operationId);
	}
}
