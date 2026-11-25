package com.github.tvbox.osc.bbox.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.github.tvbox.osc.bbox.event.ServerEvent;
import com.github.tvbox.osc.bbox.ui.activity.SearchActivity;
import com.github.tvbox.osc.bbox.util.AppManager;

import org.greenrobot.eventbus.EventBus;

/**
 * @author pj567
 * @date :2021/1/5
 * @description:
 */
public class SearchReceiver extends BroadcastReceiver {
    public static String action = "android.content.movie.search.Action";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (action.equals(intent.getAction()) && intent.getExtras() != null) {
            // v2.0.0（A2）：title 键判空，残缺/恶意广播不再向下传 null
            String title = intent.getExtras().getString("title");
            if (title == null) return;
            if (AppManager.getInstance().getActivity(SearchActivity.class) != null) {
                AppManager.getInstance().backActivity(SearchActivity.class);
                EventBus.getDefault().post(new ServerEvent(ServerEvent.SERVER_SEARCH, title));
            } else {
                Intent newIntent = new Intent(context, SearchActivity.class);
                newIntent.putExtra("title", title);
                newIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                context.startActivity(newIntent);
            }
        }
    }
}