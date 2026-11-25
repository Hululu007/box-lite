package com.fongmi.nodejs;

import android.content.Context;
import android.content.Intent;

import com.github.catvod.Runtime;
import com.github.catvod.runtime.NodeRuntimeApi;

/**
 * NodeJS 运行时插件入口（由 catvod RuntimeModule 经 "com.fongmi.nodejs.RuntimeEntry" 反射装配）。
 *
 * <p>嵌入模式下 RuntimeModule.NODEJS.entry() 返回本类；create() 供宿主 ApiConfig/NodeRuntime
 * 构建 NodeClient，startProcess() 供 :nodejs 进程内启动 NodeService。是模块对外的唯一工厂。</p>
 */
public final class RuntimeEntry implements NodeRuntimeApi {

    @Override
    public Client create(Context context, Callback callback) {
        return new NodeClient(context, callback);
    }

    @Override
    public void check() throws Exception {
        new NodeClient(Runtime.context(), null).check();
    }

    @Override
    public void startProcess(Context context, Intent intent) {
        new NodeService(context).start(intent);
    }
}
