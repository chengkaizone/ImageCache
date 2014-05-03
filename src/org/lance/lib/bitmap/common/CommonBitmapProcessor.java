package org.lance.lib.bitmap.common;

import java.io.FileDescriptor;

import org.lance.lib.bitmap.core.BitmapCache;
import org.lance.lib.bitmap.core.BitmapDisplayConfig;
import org.lance.lib.bitmap.core.BitmapProcessor;
import org.lance.lib.bitmap.util.CacheUtils;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;

/**
 * 位图处理器
 * @author lance
 *
 */
public class CommonBitmapProcessor implements BitmapProcessor {
	@Override
	public Bitmap process(FileDescriptor source, BitmapDisplayConfig config, BitmapCache cache) {
		Bitmap bitmap = decodeSampledBitmapFromDescriptor(source, config.getBitmapWidth(), config.getBitmapHeight(), config.getDecodingOptions(), (CommonBitmapCache)cache);
		return bitmap;
	}

	public static Bitmap decodeSampledBitmapFromDescriptor(
			FileDescriptor fileDescriptor, int reqWidth, int reqHeight, BitmapFactory.Options options, CommonBitmapCache cache) {
		BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
		decodeOptions.inPurgeable = options.inPurgeable;
		decodeOptions.inInputShareable = options.inInputShareable;
		decodeOptions.inPreferredConfig = options.inPreferredConfig;
		decodeOptions.inJustDecodeBounds = true;
		BitmapFactory.decodeFileDescriptor(fileDescriptor, null, decodeOptions);
		decodeOptions.inSampleSize = calculateInSampleSize(decodeOptions, reqWidth, reqHeight);
		//利用缓存中保存的Bitmap进行重载,减少了内存回收,再分配的消耗
		if (CacheUtils.hasHoneycomb()) {
			addInBitmapOptions(decodeOptions, cache);
		}

		decodeOptions.inJustDecodeBounds = false;
		return BitmapFactory.decodeFileDescriptor(fileDescriptor, null, decodeOptions);
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	private static void addInBitmapOptions(BitmapFactory.Options options, CommonBitmapCache cache) {
		// inBitmap only works with mutable bitmaps so force the decoder to
		// return mutable bitmaps.
		options.inMutable = true;

		if (cache != null) {
			// Try and find a bitmap to use for inBitmap
			Bitmap inBitmap = cache.getBitmapFromReusableSet(options);

			if (inBitmap != null) {
				options.inBitmap = inBitmap;
			}
		}
	}

	public static int calculateInSampleSize(int width, int height, int reqWidth, int reqHeight) {
		int inSampleSize = 1;
		if (reqWidth == 0 && reqHeight == 0) {
			return inSampleSize;
		}
		if (height > reqHeight || width > reqWidth) {
			if (width > height) {
				inSampleSize = Math.round((float) height / (float) reqHeight);
			} else {
				inSampleSize = Math.round((float) width / (float) reqWidth);
			}
			final float totalPixels = width * height;
			final float totalReqPixelsCap = reqWidth * reqHeight * 2;

			while (totalPixels / (inSampleSize * inSampleSize) > totalReqPixelsCap) {
				inSampleSize++;
			}
		}
		return inSampleSize;
	}

	public static int calculateInSampleSizeFromGoogle(int width, int height, int reqWidth, int reqHeight){
		int inSampleSize = 1;

		if (height > reqHeight || width > reqWidth) {

			final int halfHeight = height / 2;
			final int halfWidth = width / 2;

			// Calculate the largest inSampleSize value that is a power of 2 and keeps both
			// height and width larger than the requested height and width.
			while ((halfHeight / inSampleSize) > reqHeight
					&& (halfWidth / inSampleSize) > reqWidth) {
				inSampleSize *= 2;
			}

			// This offers some additional logic in case the image has a strange
			// aspect ratio. For example, a panorama may have a much larger
			// width than height. In these cases the total pixels might still
			// end up being too large to fit comfortably in memory, so we should
			// be more aggressive with sample down the image (=larger inSampleSize).

			long totalPixels = width * height / inSampleSize;

			// Anything more than 2x the requested pixels we'll sample down further
			final long totalReqPixelsCap = reqWidth * reqHeight * 2;

			while (totalPixels > totalReqPixelsCap) {
				inSampleSize *= 2;
				totalPixels /= 2;
			}
		}
		return inSampleSize;
	}

	public static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
		return calculateInSampleSize(options.outWidth, options.outHeight, reqWidth, reqHeight);
	}
}
