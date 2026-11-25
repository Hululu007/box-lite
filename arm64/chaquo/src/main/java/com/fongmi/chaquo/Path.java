package com.fongmi.chaquo;

import com.github.catvod.Runtime;

import java.io.File;

// Python 运行时的缓存根目录（spider 源码解包/生成目录都落在其下）
public final class Path {

    private Path() {
    }

    public static File py() {
        File dir = new File(Runtime.context().getFilesDir(), "python");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }
}
