package org.lance.lib.bitmap.common;

import org.lance.lib.bitmap.core.BitmapDisplayConfig;
import org.lance.lib.bitmap.core.BitmapWorker;
import org.lance.lib.bitmap.util.CacheUtils;

import android.app.FragmentManager;
import android.content.Context;

/**
 * ������
 * @author lance
 *
 */
public class CommonBitmapWorkerFactory {
	//����Ŀ¼
	private static final String DISK_CACHE_DIR = "Common";
	//30M�Ĵ��̻���ռ�
	private static final int DISK_CACHE_SIZE = 30 * 1024 * 1024;
	//�ش�С
	private static final int POOL_SIZE = 2;
	/**
	 * ����BitmapWorkerʵ��
	 * @param context
	 * @param fragmentManager
	 * @param displayConfig
	 * @return
	 */
	public static BitmapWorker createBitmapWorker(Context context, FragmentManager fragmentManager,
			BitmapDisplayConfig displayConfig) {
		CommonBitmapCache.Config cacheConfig = new CommonBitmapCache.Config();
		cacheConfig.diskCacheDir = CacheUtils.getDiskCacheDir(context, DISK_CACHE_DIR);
		cacheConfig.diskCacheSize = DISK_CACHE_SIZE;
		cacheConfig.setMemCacheSizePercent(context, 0.5f);
		CommonBitmapCache cache = CommonBitmapCache.getInstance(fragmentManager, cacheConfig);
		BitmapWorker.BitmapWorkerConfig config = new BitmapWorker.BitmapWorkerConfig(context.getResources());
		config.processor = new CommonBitmapProcessor();
		config.loader = new CommonBitmapLoader(context, cache);
		config.poolSize = POOL_SIZE;
		config.displayer = new CommonBitmapDisplayer();
		config.defaultDisplayConfig = displayConfig;
		return createBitmapWorker(context, cache, config);
	}

	/**
	 * ����BitmapWorkerʵ��
	 * @param context
	 * @param cache
	 * @param config
	 * @return
	 */
	public static BitmapWorker createBitmapWorker(Context context, CommonBitmapCache cache, BitmapWorker.BitmapWorkerConfig config) {
		BitmapWorker worker = new BitmapWorker(context, config, cache);
		return worker;
	}
}
