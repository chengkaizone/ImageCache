package org.lance.lib.bitmap.core;

import java.lang.ref.WeakReference;

import android.content.res.Resources;
import android.graphics.Bitmap;

/**
 * λͼ������---���ط�ʽ
 * @author lance
 *
 */
public interface BitmapLoader {
	Bitmap load(Object object, WeakReference<BitmapWorker.Progress> progress,
			BitmapProcessor processor, BitmapDisplayConfig displayConfig, Resources resources);

}
