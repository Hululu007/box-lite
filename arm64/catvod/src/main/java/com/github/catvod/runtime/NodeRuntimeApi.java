package com.github.catvod.runtime;

import android.content.Context;
import android.content.Intent;
import com.github.catvod.crawler.Spider;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.Objects;

public interface NodeRuntimeApi extends RuntimePlugin {

    // FongMi 原为 com.fongmi.android.tv.api.node.NodeService；box 宿主服务全名（受保护广播可用 service 类直启）
    String SERVICE_CLASS = "com.github.tvbox.osc.bbox.service.NodeService";
    String SERVICE_ACTION = "com.fongmi.nodejs.START";

    Client create(Context context, Callback callback);

    void startProcess(Context context, Intent intent);

    interface Client {

        LoadedConfig loadConfig(String source) throws Exception;

        void check() throws Exception;

        void clear();

        void accept(LoadedConfig loaded) throws IOException;

        void prune(LoadedConfig loaded, Iterable<String> sources, String currentSource) throws IOException;

        boolean fail(LoadedConfig loaded);

        void cancelLoad();

        Spider spider(String api);
    }

    // 原为 Java 17 record，改为普通类（保持访问器命名与 equals 语义）
    final class LoadedConfig {

        private final String json;
        private final long session;

        public LoadedConfig(String json, long session) {
            this.json = json;
            this.session = session;
        }

        public String json() {
            return json;
        }

        public long session() {
            return session;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) return true;
            if (!(object instanceof LoadedConfig other)) return false;
            return session == other.session && json.equals(other.json);
        }

        @Override
        public int hashCode() {
            return Objects.hash(json, session);
        }
    }

    interface Callback {

        String onMessage(String action, JsonObject options);

        void onPlayInfo(String flag, String id);
    }
}
