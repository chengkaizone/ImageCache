package org.lance.lib.bitmap.core;

import java.lang.ref.WeakReference;

import android.content.res.Resources;
import android.graphics.Bitmap;

/**
 * 位图加载器---加载方式
 * @author lance
 *
 */
public interface BitmapLoader {
	Bitmap load(Object object, WeakReference<BitmapWorker.Progress> progress,
			BitmapProcessor processor, BitmapDisplayConfig displayConfig, Resources resources);

}
