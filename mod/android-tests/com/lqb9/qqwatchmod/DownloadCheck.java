package com.lqb9.qqwatchmod;

import android.app.Instrumentation;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Exercises the production exporter without opening windows or operating QQ. */
public final class DownloadCheck extends Instrumentation {
    private final List<Uri> cleanup = new ArrayList<Uri>();
    private int checks;
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    private void check(boolean condition, String message) {
        checks++; if (!condition) throw new AssertionError(message);
    }
    @Override public void onStart() {
        Bundle result = new Bundle(); Context context = getTargetContext();
        try {
            check(context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED, "test has no storage permission");
            final File directory = new File(context.getCacheDir(), "log-export-" + System.currentTimeMillis());
            final RollingLog log = new RollingLog(directory, 1024, 3);
            log.append(System.currentTimeMillis(), "EVENT", "演示测试：中文线程🙂 CPU=245.3%");
            DownloadExporter.Source source = new DownloadExporter.Source() {
                public void writeTo(java.io.OutputStream output) throws IOException {
                    output.write("TEST ONLY\n".getBytes(StandardCharsets.UTF_8)); log.writeSnapshot(output);
                }
            };
            DownloadExporter.Result first = DownloadExporter.export(context, source); cleanup.add(first.uri);
            check(first.path.startsWith("Download/QQWatchdog/") && first.path.endsWith(".txt"), "downloads path");
            check(read(context, first.uri).contains("演示测试：中文线程🙂 CPU=245.3%"), "UTF-8 exported content");
            try (Cursor cursor = context.getContentResolver().query(first.uri,
                    new String[]{MediaStore.MediaColumns.IS_PENDING, MediaStore.MediaColumns.MIME_TYPE,
                            MediaStore.MediaColumns.RELATIVE_PATH}, null, null, null)) {
                check(cursor != null && cursor.moveToFirst() && cursor.getInt(0) == 0
                        && "text/plain".equals(cursor.getString(1)) && "Download/QQWatchdog/".equals(cursor.getString(2)),
                        "file is published in Downloads as plain text");
            }
            DownloadExporter.Result second = DownloadExporter.export(context, source); cleanup.add(second.uri);
            check(!first.path.equals(second.path) && !first.uri.equals(second.uri), "repeat export never overwrites prior file");
            int before = ownedFiles(context); boolean failed = false;
            try {
                DownloadExporter.export(context, new DownloadExporter.Source() {
                    public void writeTo(java.io.OutputStream output) throws IOException {
                        output.write("partial".getBytes(StandardCharsets.UTF_8)); throw new IOException("injected disk error");
                    }
                });
            } catch (IOException expected) { failed = true; }
            check(failed && before == ownedFiles(context), "failed write removes pending and partial entries");
            ByteArrayOutputStream persisted = new ByteArrayOutputStream();
            new RollingLog(directory, 1024, 3).writeSnapshot(persisted);
            check(persisted.toString("UTF-8").contains("中文线程"), "on-device reopen preserves logs");

            WatchLog.start(new File(context.getCacheDir(), "async-log-" + System.currentTimeMillis()), "TEST SESSION");
            WatchLog.record("EVENT", "QUEUED EVENT 中文");
            final CountDownLatch done = new CountDownLatch(1); final String[] outcome = new String[2];
            check(WatchLog.export(context, "ASYNC HEADER\n", new WatchLog.Callback() {
                public void done(String path, String error) { outcome[0] = path; outcome[1] = error; done.countDown(); }
            }), "async export accepted");
            check(done.await(30, TimeUnit.SECONDS) && outcome[0] != null && outcome[1] == null, "async callback completes successfully");
            Uri asyncFile = find(context, outcome[0].substring(outcome[0].lastIndexOf('/') + 1)); cleanup.add(asyncFile);
            String async = read(context, asyncFile);
            check(async.startsWith("ASYNC HEADER") && async.contains("TEST SESSION")
                    && async.contains("QUEUED EVENT 中文") && async.contains("请求导出日志"), "queued records flushed before export");
            check(!WatchLog.isExporting() && WatchLog.status().contains("自动记录"), "async status recovers after export");
            result.putString("stream", "PASS " + checks + " Android download/log checks; no storage permission; no foreground window\n");
        } catch (Throwable failure) { result.putString("stream", "FAILED " + failure); }
        finally {
            for (Uri uri : cleanup) if (uri != null) context.getContentResolver().delete(uri, null, null);
        }
        finish(result.getString("stream").startsWith("PASS") ? -1 : 1, result);
    }
    private String read(Context context, Uri uri) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IOException("No input"); byte[] bytes = new byte[8192]; int count;
            while ((count = input.read(bytes)) != -1) output.write(bytes, 0, count);
        }
        return output.toString("UTF-8");
    }
    private int ownedFiles(Context context) {
        try (Cursor cursor = context.getContentResolver().query(MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.MediaColumns._ID}, MediaStore.MediaColumns.RELATIVE_PATH + "=?",
                new String[]{"Download/QQWatchdog/"}, null)) { return cursor == null ? -1 : cursor.getCount(); }
    }
    private Uri find(Context context, String name) throws IOException {
        try (Cursor cursor = context.getContentResolver().query(MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.MediaColumns._ID}, MediaStore.MediaColumns.DISPLAY_NAME + "=?",
                new String[]{name}, null)) {
            if (cursor == null || !cursor.moveToFirst()) throw new IOException("Export missing");
            return ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cursor.getLong(0));
        }
    }
}
