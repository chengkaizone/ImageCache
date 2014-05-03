package org.lance.lib.bitmap.core;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

/**
 * ��ʾͼƬ�ķ�ʽ
 * @author lance
 *
 */
public interface BitmapDisplayer {

	void show(ImageView imageView, Bitmap bitmap);
	void show(ImageView imageView, Drawable drawable);
	
}
