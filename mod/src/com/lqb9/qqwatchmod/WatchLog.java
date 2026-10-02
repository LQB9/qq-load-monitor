package com.lqb9.qqwatchmod;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Main QQ process only. Disk writes and export never run on the UI or sampling thread. */
final class WatchLog {
    interface Callback { void done(String path, String error); }
    private static final ThreadPoolExecutor io = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<Runnable>(128), new ThreadFactory() {
                public Thread newThread(Runnable task) {
                    Thread thread = new Thread(task, "qqwatch-log"); thread.setDaemon(true); return thread;
                }
            });
    private static final AtomicBoolean exporting = new AtomicBoolean();
    private static final AtomicInteger dropped = new AtomicInteger();
    private static volatile RollingLog store;
    private static volatile String error = "";
    private static volatile boolean written;

    static synchronized void start(File directory, String session) {
        if (store != null) return;
        store = new RollingLog(directory, 512 * 1024, 4);
        record("SESSION", session);
    }

    static void record(final String category, final String message) {
        recordAt(System.currentTimeMillis(), category, message);
    }

    static void recordAt(final long time, final String category, final String message) {
        if (store == null) return;
        try {
            io.execute(new Runnable() {
                public void run() {
                    try {
                        writeDropped(); store.append(time, category, message); written = true; error = "";
                    } catch (Exception failure) { error = problemText(failure); }
                }
            });
        } catch (RejectedExecutionException full) { dropped.incrementAndGet(); }
    }

    private static void writeDropped() throws IOException {
        int count = dropped.getAndSet(0);
        if (count > 0) {
            try { store.append(System.currentTimeMillis(), "WARN", "日志队列满，跳过 " + count + " 条记录"); }
            catch (IOException failure) { dropped.addAndGet(count); throw failure; }
        }
    }

    static String status() {
        if (!error.isEmpty()) return "日志写入失败：" + error;
        if (!written) return "正在初始化日志…";
        return "自动记录 · 约每 5 秒一份负载快照 · 最多 2 MiB，滚动保留";
    }

    static boolean isExporting() { return exporting.get(); }

    static boolean export(Context context, final String header, final Callback callback) {
        if (!exporting.compareAndSet(false, true)) return false;
        final Context app = context.getApplicationContext() == null ? context : context.getApplicationContext();
        try {
            io.execute(new Runnable() {
                public void run() {
                    String path = null, failure = null;
                    try {
                        if (store == null) throw new IOException("日志尚未初始化");
                        writeDropped();
                        store.append(System.currentTimeMillis(), "EXPORT", "请求导出日志");
                        DownloadExporter.Result result = DownloadExporter.export(app, new DownloadExporter.Source() {
                            public void writeTo(java.io.OutputStream output) throws IOException {
                                output.write(header.getBytes(StandardCharsets.UTF_8)); store.writeSnapshot(output);
                            }
                        });
                        path = result.path;
                        try { store.append(System.currentTimeMillis(), "EXPORT", "已保存 " + path); }
                        catch (Exception logFailure) { error = problemText(logFailure); }
                    } catch (Exception problem) {
                        failure = problemText(problem);
                        try { if (store != null) store.append(System.currentTimeMillis(), "ERROR", failure); }
                        catch (Exception logFailure) { error = problemText(logFailure); }
                    }
                    finally { exporting.set(false); }
                    final String donePath = path, doneError = failure;
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        public void run() { callback.done(donePath, doneError); }
                    });
                }
            });
            return true;
        } catch (RejectedExecutionException full) { exporting.set(false); return false; }
    }

    private static String problemText(Exception problem) {
        return problem.getMessage() == null ? problem.getClass().getSimpleName() : problem.getMessage();
    }
}
