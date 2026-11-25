package com.github.catvod.crawler;

import android.util.Log;

import com.fongmi.chaquo.Loader;
import com.github.tvbox.osc.bbox.base.App;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Python(.py) 源加载器：源 API 以 .py 结尾时走 chaquopy 内嵌 Python 3.10 运行时。
 * 与 JsLoader 保持同一套 key 缓存 / recentKey 代理约定。
 */
public class PyLoader {
    private static final String TAG = "PyLoader";
    private static final ConcurrentHashMap<String, Spider> spiders = new ConcurrentHashMap<>();
    // 当前的 Python 爬虫 key（供 proxyInvoke 使用）
    private volatile String recentKey = "";
    private volatile Loader loader;

    public static void destroy() {
        for (Spider spider : spiders.values()) {
            spider.cancelByTag();
            spider.destroy();
        }
    }

    public static void stopAll() {
        for (Spider spider : spiders.values()) {
            spider.cancelByTag();
        }
    }

    public void clear() {
        spiders.clear();
    }

    public synchronized void setRecentKey(String key) {
        this.recentKey = key;
    }

    // 首次加载时自检依赖（ujson/pycryptodome/cryptography 可导入，CPython 可执行）
    private synchronized Loader requireLoader() {
        if (loader == null) {
            Loader.check();
            loader = new Loader();
        }
        return loader;
    }

    public Spider getSpider(String key, String api, String ext) {
        if (spiders.containsKey(key)) return spiders.get(key);
        recentKey = key;
        try {
            Log.i(TAG, "py spider loading: " + api);
            Loader pyLoader = requireLoader();
            Spider sp = pyLoader.spider(api, key, null, null);
            sp.init(App.getInstance(), ext);
            spiders.put(key, sp);
            return sp;
        } catch (Throwable th) {
            Log.e(TAG, "py spider load failed: " + api, th);
        }
        return new SpiderNull();
    }

    public Object[] proxyInvoke(Map<String, String> params) {
        try {
            Spider proxyFun = spiders.get(recentKey);
            if (proxyFun != null) return proxyFun.proxyLocal(params);
        } catch (Throwable ignored) {
        }
        return null;
    }
}
