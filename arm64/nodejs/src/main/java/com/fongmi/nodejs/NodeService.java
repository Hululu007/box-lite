package com.fongmi.nodejs;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.os.Process;

import androidx.annotation.Keep;

import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;
import com.github.catvod.utils.RuntimeModule;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * :nodejs 独立进程宿主服务（无 AIDL，经 bindService + 静态广播语义拉起）。
 *
 * <p>进程模型：宿主 NodeClient.bindProcess() 携带 index/config/data 目录与 bridge 端口、token
 * bind 到本服务；start() 在独立线程调用 JNI startNodeWithArguments 启动 libnode 运行时，
 * bootstrap.js 随即监听随机端口并向 bridge 回报 serverStarted（地址+token）。
 * 任何退出路径（正常退出/启动失败/运行时缺失）都会通过 bridge 上报后 killProcess 自尽，
 * 保证 :nodejs 进程不留僵尸；宿主侧下次请求时由 recover() 重新拉起。</p>
 */
@Keep
public class NodeService extends ContextWrapper {

    public static final String ACTION_START = "com.fongmi.nodejs.START";
    public static final String EXTRA_INDEX = "index";
    public static final String EXTRA_CONFIG = "config";
    public static final String EXTRA_DATA = "data";
    public static final String EXTRA_BRIDGE_PORT = "bridgePort";
    public static final String EXTRA_TOKEN = "token";
    private static final String HEADER_TOKEN = "X-CatVod-Token";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final OkHttpClient CLIENT = OkHttp.client().newBuilder().proxy(Proxy.NO_PROXY).connectTimeout(2, TimeUnit.SECONDS).readTimeout(2, TimeUnit.SECONDS).writeTimeout(2, TimeUnit.SECONDS).build();

    private final AtomicBoolean started = new AtomicBoolean();

    public NodeService(Context context) {
        super(context);
    }

    private static native int startNodeWithArguments(String[] arguments);

    public void start(Intent intent) {
        if (intent == null || !ACTION_START.equals(intent.getAction()) || !started.compareAndSet(false, true)) return;
        String index = intent.getStringExtra(EXTRA_INDEX);
        String config = intent.getStringExtra(EXTRA_CONFIG);
        String data = intent.getStringExtra(EXTRA_DATA);
        int bridgePort = intent.getIntExtra(EXTRA_BRIDGE_PORT, 0);
        String token = intent.getStringExtra(EXTRA_TOKEN);
        new Thread(() -> start(index, config, data, bridgePort, token), "nodejs-runtime").start();
    }

    private void start(String index, String config, String data, int bridgePort, String token) {
        String message;
        RuntimeModule.MissingException missing = null;
        try {
            File indexFile = requirePrivateFile(index);
            File configFile = requirePrivateFile(config);
            File dataDir = requirePrivateDirectory(data);
            int code = startRuntime(indexFile, configFile, dataDir, bridgePort, token);
            message = "Node exited with code " + code;
        } catch (Throwable e) {
            missing = RuntimeModule.MissingException.find(e);
            message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        }
        reportError(bridgePort, token, message, missing);
        Process.killProcess(Process.myPid());
    }

    private int startRuntime(File indexFile, File configFile, File dataDir, int bridgePort, String token) throws IOException {
        File bootstrap = extractBootstrap();
        // 嵌入模式：libnode.so / libnodebridge.so 由安装器解压到 nativeLibraryDir，直接按名加载
        // 顺序：先 libnode 再 libnodebridge（nodebridge 依赖 node::Start）
        System.loadLibrary("node");
        System.loadLibrary("nodebridge");
        File library = new File(getApplicationInfo().nativeLibraryDir, "libnode.so");
        String[] arguments = {
                "node", bootstrap.getAbsolutePath(),
                indexFile.getAbsolutePath(), configFile.getAbsolutePath(),
                dataDir.getAbsolutePath(), String.valueOf(bridgePort), token,
                library.getAbsolutePath()
        };
        return startNodeWithArguments(arguments);
    }

    private File requirePrivateFile(String path) throws IOException {
        File file = requirePrivatePath(path);
        if (!file.isFile()) throw new IOException("Node bundle file is missing");
        return file;
    }

    private File requirePrivateDirectory(String path) throws IOException {
        File file = requirePrivatePath(path);
        if (!file.isDirectory() && !file.mkdirs() && !file.isDirectory()) throw new IOException("Unable to create Node data directory");
        return file;
    }

    private File requirePrivatePath(String path) throws IOException {
        if (path == null || path.isEmpty()) throw new IOException("Node path is empty");
        File root = new File(getFilesDir(), "nodejs").getCanonicalFile();
        File file = new File(path).getCanonicalFile();
        if (!file.getPath().startsWith(root.getPath() + File.separator)) throw new IOException("Node path is outside app storage");
        return file;
    }

    private File extractBootstrap() throws IOException {
        File target = new File(getFilesDir(), "nodejs/runtime/bootstrap-v1.js");
        // 嵌入模式：直接从 APK assets 读取 bootstrap.js（不再走 RuntimeModule.readAsset 的 runtime APK 提取）
        try (InputStream input = getAssets().open("nodejs/bootstrap.js")) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            Path.writeAtomically(target, output.toByteArray());
        }
        return target;
    }

    private void reportError(int port, String token, String message, RuntimeModule.MissingException missing) {
        if (port <= 0) return;
        try (Response ignored = CLIENT.newCall(errorRequest(port, token, message, missing)).execute()) {
        } catch (Exception ignored) {
        }
    }

    private Request errorRequest(int port, String token, String message, RuntimeModule.MissingException missing) throws JSONException {
        JSONObject options = new JSONObject().put("token", token).put("message", message);
        if (missing != null) options.put("missingModule", missing.module.id);
        JSONObject body = new JSONObject().put("action", "nodeError").put("opt", options);
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        return new Request.Builder().url("http://127.0.0.1:" + port + "/msg").header(HEADER_TOKEN, token).post(RequestBody.create(bytes, JSON)).build();
    }

}
