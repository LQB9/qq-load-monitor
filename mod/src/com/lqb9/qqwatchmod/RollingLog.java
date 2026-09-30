package com.lqb9.qqwatchmod;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Bounded UTF-8 records. All file access is serialized, including export snapshots. */
final class RollingLog {
    private final File directory;
    private final int maxBytes, fileCount;

    RollingLog(File directory, int maxBytes, int fileCount) {
        if (maxBytes < 256 || fileCount < 1 || fileCount > 8) throw new IllegalArgumentException();
        this.directory = directory;
        this.maxBytes = maxBytes;
        this.fileCount = fileCount;
    }

    synchronized void append(long timeMs, String category, String message) throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("无法创建日志目录");
        String prefix = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.ROOT)
                .format(new Date(timeMs)) + " [" + clean(category) + "] ";
        String content = clean(message);
        // A Unicode code point needs at most four UTF-8 bytes. Never split a surrogate pair.
        int limit = Math.max(1, (Math.min(maxBytes, 16384) - prefix.length() * 4 - 32) / 4);
        if (content.codePointCount(0, content.length()) > limit)
            content = content.substring(0, content.offsetByCodePoints(0, limit)) + " [truncated]";
        byte[] record = (prefix + content + "\n").getBytes(StandardCharsets.UTF_8);
        if (record.length > maxBytes) throw new IOException("日志记录过长");
        File active = file(0);
        if (active.length() + record.length > maxBytes) rotate();
        try (FileOutputStream output = new FileOutputStream(active, true)) { output.write(record); }
    }

    synchronized void writeSnapshot(OutputStream output) throws IOException {
        boolean found = false;
        byte[] buffer = new byte[8192];
        for (int i = fileCount - 1; i >= 0; i--) {
            File part = file(i);
            if (!part.isFile()) continue;
            found = true;
            try (FileInputStream input = new FileInputStream(part)) {
                int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            }
        }
        if (!found) throw new IOException("暂无可导出的日志，请稍后重试");
    }

    private File file(int index) { return new File(directory, "watchdog." + index + ".log"); }

    private void rotate() throws IOException {
        File oldest = file(fileCount - 1);
        if (oldest.exists() && !oldest.delete()) throw new IOException("日志轮换失败：无法删除旧日志");
        for (int i = fileCount - 2; i >= 0; i--) {
            File source = file(i);
            if (source.exists() && !source.renameTo(file(i + 1))) throw new IOException("日志轮换失败：无法移动日志");
        }
    }

    private static String clean(String text) {
        return text == null ? "" : text.replace("\\", "\\\\").replace("\r", "\\r")
                .replace("\n", "\\n").replace("\u0000", "");
    }
}
