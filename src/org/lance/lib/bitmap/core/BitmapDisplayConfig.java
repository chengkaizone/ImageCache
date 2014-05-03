package org.lance.lib.bitmap.core;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;

/**
 * ŒªÕºœ‘ æ≈‰÷√
 * @author lance
 *
 */
public class BitmapDisplayConfig {
	private int bitmapWidth;
	private int bitmapHeight;
	
	private BitmapDrawable loadingDrawable;
	private BitmapDrawable loadfailDrawable;

	private BitmapFactory.Options decodingOptions = new BitmapFactory.Options();

	public BitmapDisplayConfig(){
		decodingOptions.inPurgeable = true;
		decodingOptions.inInputShareable = true;
	}

	public int getBitmapWidth() {
		return bitmapWidth;
	}

	public BitmapDisplayConfig setBitmapWidth(int bitmapWidth) {
		this.bitmapWidth = bitmapWidth;
		return this;
	}

	public int getBitmapHeight() {
		return bitmapHeight;
	}

	public BitmapDisplayConfig setBitmapHeight(int bitmapHeight) {
		this.bitmapHeight = bitmapHeight;
		return this;
	}

	public BitmapDrawable getLoadingDrawable() {
		return loadingDrawable;
	}

	public BitmapDisplayConfig setLoadingDrawable(BitmapDrawable loadingDrawable) {
		this.loadingDrawable = loadingDrawable;
		return this;
	}

	public BitmapDrawable getLoadfailDrawable() {
		return loadfailDrawable;
	}

	public BitmapDisplayConfig setLoadfailDrawable(BitmapDrawable loadfailDrawable) {
		this.loadfailDrawable = loadfailDrawable;
		return this;
	}

	public BitmapDisplayConfig bitmapConfig(Bitmap.Config bitmapConfig){
		decodingOptions.inPreferredConfig = bitmapConfig;
		return this;
	}

	public BitmapFactory.Options getDecodingOptions(){
		return decodingOptions;
	}
}
