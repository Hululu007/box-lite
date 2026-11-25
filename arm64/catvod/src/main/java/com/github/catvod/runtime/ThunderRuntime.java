package com.github.catvod.runtime;

import java.io.File;
import java.util.List;
import java.util.Objects;

public interface ThunderRuntime extends RuntimePlugin {

    Task parse(String url, File savePath);

    Task addThunderTask(String url, File savePath);

    Task addTorrentTask(File torrent, File savePath, int index);

    List<Media> getTorrentInfo(File torrent);

    String getLocalUrl(File file);

    void deleteTask(Task task);

    void stopTask(Task task);

    Status getTaskInfo(Task task);

    Status getBtSubTaskInfo(Task task, int index);

    void release();

    interface Task {

        File getSaveFile();

        String getFileName();

        String getRealUrl();
    }

    // 原为 Java 17 record，改为普通类（保持访问器命名与 equals 语义）
    final class Status {

        private final int taskStatus;
        private final String errorMessage;

        public Status(int taskStatus, String errorMessage) {
            this.taskStatus = taskStatus;
            this.errorMessage = errorMessage;
        }

        public int taskStatus() {
            return taskStatus;
        }

        public String errorMessage() {
            return errorMessage;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) return true;
            if (!(object instanceof Status other)) return false;
            return taskStatus == other.taskStatus && errorMessage.equals(other.errorMessage);
        }

        @Override
        public int hashCode() {
            return Objects.hash(taskStatus, errorMessage);
        }
    }

    final class Media {

        private final String fileName;
        private final String size;
        private final String playUrl;

        public Media(String fileName, String size, String playUrl) {
            this.fileName = fileName;
            this.size = size;
            this.playUrl = playUrl;
        }

        public String fileName() {
            return fileName;
        }

        public String size() {
            return size;
        }

        public String playUrl() {
            return playUrl;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) return true;
            if (!(object instanceof Media other)) return false;
            return fileName.equals(other.fileName) && size.equals(other.size) && playUrl.equals(other.playUrl);
        }

        @Override
        public int hashCode() {
            return Objects.hash(fileName, size, playUrl);
        }
    }
}
