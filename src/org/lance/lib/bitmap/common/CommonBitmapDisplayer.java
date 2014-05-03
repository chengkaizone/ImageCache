package org.lance.lib.bitmap.common;

import org.lance.lib.bitmap.core.BitmapDisplayer;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

/**
 * λͼ��ʾ��
 * @author lance
 *
 */
public class CommonBitmapDisplayer implements BitmapDisplayer {


	@Override
	public void show(ImageView imageView, Drawable drawable) {
		imageView.setImageDrawable(drawable);
	}

	@Override
	public void show(ImageView imageView, Bitmap bitmap) {
		imageView.setImageBitmap(bitmap);
	}

}
