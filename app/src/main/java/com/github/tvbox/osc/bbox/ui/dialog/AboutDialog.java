package com.github.tvbox.osc.bbox.ui.dialog;

import android.content.Context;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.bbox.R;

public class AboutDialog extends BaseDialog {

    public AboutDialog(@NonNull Context context) {
        super(context);
        setContentView(R.layout.dialog_about);
        TextView tvVersion = findViewById(R.id.tvVersion);
        if (tvVersion != null) {
            String v = "unknown";
            try { v = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName; } catch (Exception ignore) {}
            // v2.0.0：关于页展示版本号 + 架构标识——双包（32/64 位）同机共存时可直接分辨
            tvVersion.setText("星辰影音 v" + v + "（" + archLabel(context) + "）");
        }
    }

    /** 用安装包自身的 native 库目录判断架构（设备可能同时兼容双架构，SUPPORTED_ABIS 不可靠） */
    private static String archLabel(Context context) {
        try {
            String libDir = context.getApplicationInfo().nativeLibraryDir;
            if (libDir != null && libDir.contains("arm64")) return "64位";
        } catch (Throwable ignored) {
        }
        return "32位";
    }
}
