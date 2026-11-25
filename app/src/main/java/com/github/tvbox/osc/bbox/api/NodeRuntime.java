package com.github.tvbox.osc.bbox.api;

import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.fongmi.nodejs.NodeClient;
import com.github.catvod.runtime.NodeRuntimeApi;
import com.github.catvod.utils.Json;
import com.github.tvbox.osc.bbox.base.App;
import com.google.gson.JsonObject;

import java.net.URLDecoder;
import java.util.Locale;

/**
 * Node.js 宿主运行时（box 简化版）。
 * 回调仅处理 toast（转 box Toast），其余返回 FAILURE；onPlayInfo 空实现。
 * 嵌入模式：直接实例化编进主 dex 的插件侧 NodeClient。
 */
public final class NodeRuntime implements NodeRuntimeApi.Callback {

    private static final String SUCCESS_RESPONSE = "{\"success\":true,\"code\":0}";
    private static final String FAILURE_RESPONSE = "{\"success\":false,\"code\":-1}";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private volatile NodeRuntimeApi.Client client;

    public static NodeRuntime get() {
        return Loader.INSTANCE;
    }

    // 判断配置源 URL 是否为 Node bundle 配置（index.js.md5 结尾）
    public static boolean isConfig(String url) {
        if (url == null || url.regionMatches(true, 0, "content://", 0, 10)) return false;
        String value = url.split("[?#]", 2)[0];
        try {
            // Java 8 兼容：decode(String,String) 抛受检 UnsupportedEncodingException，用 StandardCharsets 重载在 app 源级 8 下不可用
            value = URLDecoder.decode(value, "UTF-8");
        } catch (Exception ignored) {
        }
        return value.toLowerCase(Locale.ROOT).endsWith("/index.js.md5") || value.equalsIgnoreCase("index.js.md5");
    }

    public synchronized NodeRuntimeApi.Client client() {
        if (client == null) client = new NodeClient(App.getInstance(), this);
        return client;
    }

    public void cancelLoad() {
        if (client != null) client.cancelLoad();
    }

    public void clear() {
        if (client != null) client.clear();
    }

    @Override
    public void onPlayInfo(String flag, String id) {
        // box 简化：空实现
    }

    @Override
    public String onMessage(String action, JsonObject options) {
        try {
            if ("toast".equals(action)) {
                String message = Json.safeString(options, "message");
                if (!message.isEmpty()) MAIN.post(() -> Toast.makeText(App.getInstance(), message, Toast.LENGTH_SHORT).show());
                return SUCCESS_RESPONSE;
            }
            return FAILURE_RESPONSE;
        } catch (Exception e) {
            return FAILURE_RESPONSE;
        }
    }

    private static class Loader {
        private static final NodeRuntime INSTANCE = new NodeRuntime();
    }
}