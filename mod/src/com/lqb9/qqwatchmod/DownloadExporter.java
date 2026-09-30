package com.lqb9.qqwatchmod;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

/** Publishes a complete text file in Downloads; failed exports leave no partial entry. */
final class DownloadExporter {
    interface Source { void writeTo(OutputStream output) throws IOException; }
    static final class Result {
        final Uri uri;
        final String path;
        Result(Uri uri, String path) { this.uri = uri; this.path = path; }
    }
    private static final String DIRECTORY = Environment.DIRECTORY_DOWNLOADS + "/QQWatchdog/";

    static Result export(Context context, Source source) throws IOException {
        String name = "QQWatchdog_" + new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.ROOT)
                .format(new Date()) + "_" + UUID.randomUUID().toString().substring(0, 8) + ".txt";
        if (Build.VERSION.SDK_INT < 29) return exportLegacy(context, source, name);
        ContentResolver resolver = context.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, DIRECTORY);
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);
        Uri uri = null;
        try {
            uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IOException("无法创建下载文件");
            try (OutputStream output = resolver.openOutputStream(uri, "w")) {
                if (output == null) throw new IOException("无法打开下载文件");
                source.writeTo(output);
            }
            ContentValues published = new ContentValues();
            published.put(MediaStore.MediaColumns.IS_PENDING, 0);
            if (resolver.update(uri, published, null, null) != 1) throw new IOException("下载文件发布失败");
            return new Result(uri, DIRECTORY + name);
        } catch (Exception failure) {
            if (uri != null) try { resolver.delete(uri, null, null); } catch (Exception ignored) {}
            throw new IOException("导出失败：" + failure.getMessage(), failure);
        }
    }

    private static Result exportLegacy(Context context, Source source, String name) throws IOException {
        if (context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != android.content.pm.PackageManager.PERMISSION_GRANTED)
            throw new IOException("Android 8/9 请先在系统设置中授予 QQ 存储权限");
        File directory = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS), "QQWatchdog");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("无法创建下载目录");
        File file = new File(directory, name);
        boolean created = false;
        try {
            if (!file.createNewFile()) throw new IOException("下载文件已存在");
            created = true;
            try (FileOutputStream output = new FileOutputStream(file)) {
                source.writeTo(output); output.getFD().sync();
            }
            android.media.MediaScannerConnection.scanFile(context, new String[]{file.getAbsolutePath()},
                    new String[]{"text/plain"}, null);
            return new Result(Uri.fromFile(file), DIRECTORY + name);
        } catch (Exception failure) {
            if (created && file.exists()) file.delete();
            throw new IOException("导出失败：" + failure.getMessage(), failure);
        }
    }
}
