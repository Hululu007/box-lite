package com.github.tvbox.osc.bbox.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.ArrayList;
import java.util.List;

/**
 * @author pj567
 * @date :2021/1/5
 * @description:
 */
public class CustomWebReceiver extends BroadcastReceiver {
    public static String action = "android.content.movie.custom.web.Action";

    public static String REFRESH_SOURCE = "source";
    public static String REFRESH_LIVE = "live";
    public static String REFRESH_PARSE = "parse";

    public static List<Callback> callback = new ArrayList<>();

    public interface Callback {
        void onChange(String action, Object obj);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (action.equals(intent.getAction()) && intent.getExtras() != null) {
            Object refreshObj = null;
            // v2.0.0（A2）：action 键判空——此前缺键时 action.equals(...) 直接 NPE 崩溃（广播 DoS）
            String action = intent.getExtras().getString("action");
            if (action == null) return;
            if (action.equals(REFRESH_PARSE)) {
                /*String name = intent.getExtras().getString("name");
                String url = intent.getExtras().getString("url");*/
                return;
            } else if (action.equals(REFRESH_LIVE)) {
                return;
            } else {
                return;
            }
            /*if (callback != null) {
                for (Callback call : callback) {
                    call.onChange(action, refreshObj);
                }
            }*/
        }
    }
}