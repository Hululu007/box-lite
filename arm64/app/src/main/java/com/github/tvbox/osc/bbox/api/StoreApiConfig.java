package com.github.tvbox.osc.bbox.api;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import com.github.tvbox.osc.bbox.base.App;
import com.github.tvbox.osc.bbox.constant.URL;
import com.github.tvbox.osc.bbox.util.HawkConfig;
import com.github.tvbox.osc.bbox.util.LOG;
import com.github.tvbox.osc.bbox.util.OkGoHelper;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.orhanobut.hawk.Hawk;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.ConnectionSpec;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class StoreApiConfig {
    private static volatile StoreApiConfig instance;

    // v1.1.5：OkGo 的回调经 OkGo.getDelivery()（主线程 Handler）分发；v1.1.4 迁移到 okhttp4 原生 enqueue 后
    // 回调落在 okhttp 线程池（无 Looper），Subscribe 回调里 Toast.makeText 抛 RuntimeException →
    // 回调链静默中断（无任何 Toast、异常不显示、数据不入库）。这里复刻 OkGo 主线程回调语义。
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    // v1.1.6：订阅专用干净 client。v1.1.5 复测仍无任何 Toast——唯一能“无回调”的路径是请求挂死，
    // 而 defaultClient 挂着 dns(dnsOverHttps)（DoH 内部 latch.await() 无超时等待，DoH 服务器在真机网络
    // 下不可达时可能长期阻塞 DNS）。该 client 走系统 DNS + callTimeout(30s) 强制必有回调 + 宽 TLS + 全信任。
    private static volatile OkHttpClient storeClient = null;

    private static OkHttpClient storeClient() {
        if (storeClient == null) {
            synchronized (StoreApiConfig.class) {
                if (storeClient == null) {
                    OkHttpClient.Builder b = new OkHttpClient.Builder()
                            .connectionSpecs(Arrays.asList(ConnectionSpec.COMPATIBLE_TLS, ConnectionSpec.CLEARTEXT))
                            .connectTimeout(15, TimeUnit.SECONDS)
                            .readTimeout(20, TimeUnit.SECONDS)
                            .writeTimeout(20, TimeUnit.SECONDS)
                            .callTimeout(30, TimeUnit.SECONDS)
                            .followRedirects(true)
                            .followSslRedirects(true)
                            .retryOnConnectionFailure(true);
                    // v2.0.0（A4）：与 OkGoHelper 保持一致——用户开启 SSL 严格校验后，订阅通道同样走
                    // 系统默认校验；默认保持宽 TLS 以兼容大量自签/HTTP 仓库（广泛兼容优先）。
                    if (!Hawk.get(HawkConfig.SSL_STRICT_VERIFY, false)) {
                    try {
                        final X509TrustManager trustAll = new X509TrustManager() {
                            @Override
                            public void checkClientTrusted(X509Certificate[] chain, String authType) {
                            }

                            @Override
                            public void checkServerTrusted(X509Certificate[] chain, String authType) {
                            }

                            @Override
                            public X509Certificate[] getAcceptedIssuers() {
                                return new X509Certificate[0];
                            }
                        };
                        SSLContext sslContext = SSLContext.getInstance("TLS");
                        sslContext.init(null, new X509TrustManager[]{trustAll}, new SecureRandom());
                        b.sslSocketFactory(sslContext.getSocketFactory(), trustAll);
                        b.hostnameVerifier((hostname, session) -> true);
                    } catch (Throwable t) {
                        LOG.e("StoreApi SSL init failed, fallback system trust: " + t);
                    }
                    }
                    storeClient = b.build();
                    logToFile("storeClient inited (system DNS, callTimeout=30s)");
                }
            }
        }
        return storeClient;
    }

    // v1.1.6：订阅全链路诊断日志，持久写到 Android/data/<包名>/files/store_debug.log
    // （电视文件管理器可直接打开；Toast 在 TV 上时间短、易被覆盖，不可靠，已两次复现“看不清/没看到”）
    private static void logToFile(String line) {
        try {
            App app = App.getInstance();
            if (app == null) return;
            File dir = app.getExternalFilesDir(null);
            if (dir == null) dir = app.getCacheDir();
            File f = new File(dir, "store_debug.log");
            // v2.0.0（B5）：诊断日志轮转，超 256KB 截断重写，防止无界增长
            if (f.exists() && f.length() > 256L * 1024) f.delete();
            FileWriter fw = new FileWriter(f, true);
            fw.write(new SimpleDateFormat("MM-dd HH:mm:ss").format(new Date()) + " " + line + "\n");
            fw.close();
        } catch (Throwable ignored) {
        }
    }

    public static StoreApiConfig get() {
        if (instance == null) {
            synchronized (StoreApiConfig.class) {
                if (instance == null) {
                    instance = new StoreApiConfig();
                }
            }
        }
        return instance;
    }

    public void doGet(String url, StoreApiConfigCallback callback) {
        LOG.i("request url : " + url);
        // v1.1.4：绕开 OkGo 3.0.4×okhttp4 兼容层，改用 okhttp4 原生通道。
        // v1.1.5：读 body 在 okhttp 线程完成，回调经 MAIN 切主线程（复刻 OkGo 回调语义）。
        // v1.1.6：改用订阅专用干净 client（系统 DNS、callTimeout 30s 强制回调）+ 全链路写 store_debug.log。
        // v1.1.7：空 URL 保护——STORE_API_MAP 里取不到地址时回退到 URL.DEFAULT_STORE_API_URL（空串），
        //         okhttp4 的 Request.Builder().url("") 会同步抛异常导致无回调无 Toast；这里直接报错，不发起请求。
        if (url == null || url.trim().isEmpty()) {
            logToFile("EMPTY_URL: 订阅未拿到有效仓库地址");
            LOG.e("StoreApi 订阅地址为空，无法请求");
            String msg = "订阅地址为空！请在 多源地址 中填写仓库地址后再订阅\n详情见 store_debug.log";
            MAIN.post(() -> callback.error(msg));
            return;
        }
        logToFile("GET " + url);
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "okhttp/3.15")
                .header("Accept", "text/html," + "application/xhtml+xml,application/xml;q=0.9,image/avif," + "image/webp,image/apng," + "*/*;q=0.8,application/signed-exchange;v=b3;" + "q=0.9")
                .build();
        storeClient().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                String detail = e.getClass().getName() + ": " + e.getMessage();
                LOG.e("StoreApi 请求失败: " + detail);
                logToFile("FAIL " + detail);
                Throwable cause = e.getCause();
                int depth = 0;
                while (cause != null && depth++ < 3) {
                    String c = "caused by " + cause.getClass().getName() + ": " + cause.getMessage();
                    LOG.e("StoreApi 请求失败 " + c);
                    logToFile("FAIL " + c);
                    cause = cause.getCause();
                }
                StackTraceElement[] st = e.getStackTrace();
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < Math.min(5, st.length); i++) sb.append("\n  at ").append(st[i].toString());
                logToFile("STACK" + sb);
                String msg = "请求失败，没有获取到数据！！\n" + e.getClass().getSimpleName() + ": " + e.getMessage() + "\n详情见 store_debug.log";
                MAIN.post(() -> callback.error(msg));
            }

            @Override
            public void onResponse(Call call, Response response) {
                // okhttp 线程内读完 body（try-with-resources 确保连接释放），再切主线程分发
                String body = null;
                String errMsg = null;
                try (Response r = response) {
                    if (r.isSuccessful()) {
                        body = r.body() != null ? r.body().string() : "";
                        logToFile("OK HTTP " + r.code() + " len=" + body.length() + " head=" + (body.length() > 60 ? body.substring(0, 60) : body).replace('\n', ' '));
                    } else {
                        LOG.e("StoreApi 请求失败: HTTP " + r.code());
                        logToFile("FAIL HTTP " + r.code());
                        errMsg = "请求失败，没有获取到数据！！\nHTTP " + r.code() + "\n详情见 store_debug.log";
                    }
                } catch (Throwable t) {
                    LOG.e("StoreApi 请求失败: " + t);
                    logToFile("FAIL body-read " + t.getClass().getName() + ": " + t.getMessage());
                    errMsg = "请求失败，没有获取到数据！！\n" + t.getClass().getSimpleName() + ": " + t.getMessage() + "\n详情见 store_debug.log";
                }
                final String result = body;
                final String error = errMsg;
                MAIN.post(() -> {
                    if (result != null) callback.success(result);
                    else if (error != null) callback.error(error);
                });
            }
        });
    }


    public void Subscribe(Context context) {

        Toast.makeText(context, "开始获取订阅！！！", Toast.LENGTH_LONG).show();

        // 获取多仓地址
        HashMap<String, String> storeMap = Hawk.get(HawkConfig.STORE_API_MAP, new HashMap<>());
        ArrayList<String> storeNameHistory = Hawk.get(HawkConfig.STORE_API_NAME_HISTORY, new ArrayList<>());

        /*
        if (storeMap.isEmpty()) {
            Toast.makeText(context, "仓库为空，使用备份仓库", Toast.LENGTH_LONG).show();
            String name = "自备份仓库";
            String sotreApi = Hawk.get(HawkConfig.DEFAULT_STORE_API, Hawk.get(HawkConfig.PROXY_URL, "https://raw.bunnylblbblbl.eu.org/") + URL.DEFAULT_STORE_API_URL);
            storeMap.put(name, sotreApi);
            storeNameHistory.add(name);
            Hawk.put(HawkConfig.STORE_API_NAME_HISTORY, storeNameHistory);
            Hawk.put(HawkConfig.STORE_API_NAME, name);
            Hawk.put(HawkConfig.STORE_API_MAP, storeMap);
            Hawk.put(HawkConfig.STORE_API, sotreApi);
        }
        */

        String storeUrl = storeMap.isEmpty() ? URL.DEFAULT_STORE_API_URL:storeMap.get(Hawk.get(HawkConfig.STORE_API_NAME, ""));
        // 1.0.14：map 非空但当前线路名不在 map 中时 get() 返回 null，原实现把 null 直接传给 OkGo
        if (storeUrl == null || storeUrl.isEmpty()) storeUrl = URL.DEFAULT_STORE_API_URL;

        LOG.i("订阅仓库地址：" + storeUrl);
        // v1.1.7 诊断：把订阅用到的 store 状态全量写日志，定位“地址为何为空”——
        // 复测后看 store_debug.log 里 SUBSCRIBE 行：mapKeys 是否有地址、name 是否匹配、storeUrl 解析结果
        logToFile("SUBSCRIBE name=" + Hawk.get(HawkConfig.STORE_API_NAME, "") + " mapKeys=" + storeMap.keySet() + " storeUrl=" + storeUrl);

        // 处理多仓获取多节点
        StoreApiConfig.get().doGet(storeUrl, new StoreApiConfigCallback() {
            @Override
            public void success(String sourceJson) {
                // 1.0.14：订阅地址可能返回非 JSON（网关错误页/空内容），原实现直接 Gson 解析，
                // 异常抛在 OkGo 回调线程会导致应用崩溃。整体兜底：异常只提示、不入库。
                try {
                    JsonObject json = new Gson().fromJson(sourceJson, JsonObject.class);
                    if (json == null) {
                        Toast.makeText(context, "订阅数据格式异常！", Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (null == json.get("urls")) {
                        // 仓库链接，先获取多源，再获取多配置
                        JsonObject infoJson = new Gson().fromJson(sourceJson, JsonObject.class);
                        JsonArray storeHouses = infoJson.get("storeHouse").getAsJsonArray();
                        JsonObject defStoreHouse = storeHouses.get(0).getAsJsonObject();

                        // 多仓线路作为历史添加
                        // 默认添加30条，多出的直接放弃
                        for (int i = 0; i < storeHouses.size(); i++) {

                            JsonObject storeHouse = infoJson.get("storeHouse").getAsJsonArray().get(i).getAsJsonObject();

                            String sourceName = storeHouse.get("sourceName").getAsString();
                            String sourceUrl = storeHouse.get("sourceUrl").getAsString();

                            if (!storeMap.containsKey(sourceName) && !storeMap.containsValue(sourceUrl)) {
                                // v2.0.0（B3）：name+url 双条件判重，避免同名线路被覆盖/历史重复
                                storeMap.put(sourceName, sourceUrl);
                                storeNameHistory.add(sourceName);
                            }
                            // if (history.size() > 30)
                            //     // history.remove(30);
                            //     break;
                            if (0 == i) {

                                String name = defStoreHouse.get("sourceName").getAsString();
                                String url = defStoreHouse.get("sourceUrl").getAsString();
                                Toast.makeText(context, name, Toast.LENGTH_LONG).show();

                                Hawk.put(HawkConfig.STORE_API, url);
                                Hawk.put(HawkConfig.STORE_API_NAME, name);

                                // 配置默认配置线路
                                StoreApiConfig.get().doGet(url, new StoreApiConfigCallback() {
                                    @Override
                                    public void success(String urlsJson) {
                                        String result = MutiUrl(urlsJson);
                                        Toast.makeText(context, result, Toast.LENGTH_LONG).show();
                                    }

                                    @Override
                                    public void error(String msg) {
                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
                                    }
                                });
                            }
                        }
                        // 已移除 30 条上限：完整保留多仓订阅返回的全部线路
                        Toast.makeText(context, "多仓订阅结束，到多源历史中切换！！！", Toast.LENGTH_LONG).show();
                    } else {
                        // 单源链接，直接请求
                        // 获取单源中的配置线路
                        String result = MutiUrl(sourceJson);
                        Toast.makeText(context, "单源链接，" + result, Toast.LENGTH_LONG).show();
                        String currentStoreName = Hawk.get(HawkConfig.STORE_API_NAME, "");
                        if (!currentStoreName.isEmpty() && !storeNameHistory.contains(currentStoreName)) {
                            storeNameHistory.add(0, currentStoreName);
                        }
                    }
                    Hawk.put(HawkConfig.STORE_API_MAP, storeMap);
                    Hawk.put(HawkConfig.STORE_API_NAME_HISTORY, storeNameHistory);
                } catch (Throwable e) {
                    LOG.e("订阅数据解析异常：" + e);
                    Toast.makeText(context, "订阅数据格式异常！", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void error(String msg) {
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    private String MutiUrl(String urlsJson) {
        // 1.0.14：该函数会被嵌套 doGet 回调再次调用（外层兜底覆盖不到），线路 JSON
        // 缺 urls/name/url 字段或非法时不再抛异常，统一返回失败文案。
        try {
            // 合并既有持久化的历史，避免每次订阅都覆盖已有线路（原实现用全新局部变量导致历史被清空）
            ArrayList<String> history = new ArrayList<>();
            HashMap<String, String> map = new HashMap<>();
            ArrayList<String> savedHistory = Hawk.get(HawkConfig.API_NAME_HISTORY, new ArrayList<>());
            HashMap<String, String> savedMap = Hawk.get(HawkConfig.API_MAP, new HashMap<>());
            if (savedHistory != null) history.addAll(savedHistory);
            if (savedMap != null) map.putAll(savedMap);

            String apiName = Hawk.get(HawkConfig.API_NAME, "");
            String apiUrl = Hawk.get(HawkConfig.API_URL, "");

            if (!apiName.isEmpty() && !history.contains(apiName)) {
                history.add(apiName);
                map.put(apiName, apiUrl);
            }

            JsonObject urlsObject = new Gson().fromJson(urlsJson, JsonObject.class);

            if (null == urlsObject || null == urlsObject.get("urls")) return "订阅出错，失败！！！";

            JsonArray urlsObjects = urlsObject.get("urls").getAsJsonArray();


            for (JsonElement element : urlsObjects) {
                JsonObject obj = element.getAsJsonObject();
                String name = obj.get("name").getAsString();
                String url = obj.get("url").getAsString();
                // v2.0.0（B3）：name+url 双条件判重，避免同名线路 map 覆盖 + 历史重复项
                if (!map.containsKey(name) && !map.containsValue(url)) {
                    history.add(name);
                    map.put(name, url);
                }
            }
            // 已移除 30 条上限：完整保留订阅返回的全部线路
            Hawk.put(HawkConfig.API_NAME_HISTORY, history);
            Hawk.put(HawkConfig.API_MAP, map);
            return "订阅结束，共 " + history.size() + " 条线路";
        } catch (Throwable e) {
            LOG.e("线路订阅解析异常：" + e);
            return "订阅出错，失败！！！";
        }
    }

    public interface StoreApiConfigCallback {
        void success(String json);

        void error(String msg);
    }
}
