package com.github.tvbox.osc.bbox.service;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.Process;

import com.fongmi.nodejs.RuntimeEntry;
import com.github.catvod.Runtime;
import com.github.catvod.runtime.NodeRuntimeApi;

/**
 * Node.js 宿主服务（运行于 :nodejs 独立进程）。
 * 由插件侧 NodeClient 通过 NodeRuntimeApi.SERVICE_CLASS 显式 Intent 拉起。
 * 嵌入模式：com.fongmi.nodejs.* 已编进主 APK dex，直接实例化 RuntimeEntry，
 * 不走 FongMi 的 RuntimeModule.NODEJS.entry()（外部 runtime APK DexClassLoader 机制）。
 */
public final class NodeService extends Service {

    private final IBinder binder = new Binder();
    private boolean started;

    @Override
    public void onCreate() {
        super.onCreate();
        // :nodejs 进程内初始化 catvod Runtime（OkHttp/Path 等依赖 Context）
        Runtime.set(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        start(intent);
        return START_NOT_STICKY;
    }

    private void start(Intent intent) {
        if (intent != null && NodeRuntimeApi.SERVICE_ACTION.equals(intent.getAction()) && !started) {
            started = true;
            new RuntimeEntry().startProcess(this, intent);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        start(intent);
        return binder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Process.killProcess(Process.myPid());
    }
}