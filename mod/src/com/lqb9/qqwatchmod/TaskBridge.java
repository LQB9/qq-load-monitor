package com.lqb9.qqwatchmod;

import android.app.Application;
import android.content.*;
import android.os.*;
import java.io.File;
import java.util.*;
import java.util.concurrent.*;

/** QQ UID-only control and confirmation; no native kill signal. */
final class TaskBridge {
    static final TaskRegistry tasks = new TaskRegistry();
    static final ProcessingHistory history = new ProcessingHistory();
    private static final String CANCEL = "com.lqb9.qqwatchmod.CANCEL_TASK_V2", RESULT = "com.lqb9.qqwatchmod.TASK_RESULT_V2";
    private static volatile Application app;
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static final Map<String, Long> attempted = new LinkedHashMap<String, Long>();
    private static final Map<String, Long> pending = new LinkedHashMap<String, Long>();
    private static final ExecutorService controls = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<Runnable>(16), r -> { Thread t = new Thread(r, "qqwatch-control"); t.setDaemon(true); return t; });
    static synchronized void attach(Application application) {
        if (app != null) return;
        app = application;
        if (Build.VERSION.SDK_INT < 33) return;
        IntentFilter filter = new IntentFilter(); filter.addAction(CANCEL); filter.addAction(RESULT);
        application.registerReceiver(new BroadcastReceiver() {
            public void onReceive(Context c, Intent i) {
                if (RESULT.equals(i.getAction())) {
                    if (WatchModule.isMain) acceptResult(i);
                } else if (CANCEL.equals(i.getAction()) && i.getIntExtra("pid", 0) == android.os.Process.myPid()) {
                    try { controls.execute(() -> {
                        try { handle(i); } catch (Throwable failed) { reply(i.getStringExtra("id"), "处理失败", failed.getClass().getSimpleName()); }
                    }); } catch (RejectedExecutionException busy) { reply(i.getStringExtra("id"), "未处理", "控制队列已满"); }
                }
            }
        }, filter, Context.RECEIVER_NOT_EXPORTED);
    }
    static boolean identity(CoreSnapshot.Counter t) {
        try {
            QqCpuTracker.Reading p = QqCpuTracker.Reading.parse(t.pid, "QQ", CoreCollector.read(new File("/proc/" + t.pid + "/stat")));
            ThreadCpuTracker.Reading thread = ThreadCpuTracker.Reading.parse(t.pid, t.tid, "QQ",
                    CoreCollector.read(new File("/proc/" + t.pid + "/task/" + t.tid + "/stat")));
            return p.startTicks == t.pidStart && thread.startTicks == t.tidStart
                    && android.system.Os.stat("/proc/" + t.pid).st_uid == android.os.Process.myUid();
        } catch (Exception gone) { return false; }
    }
    static synchronized void consider(CoreTracker.Result data, CpuLoadMonitor.Settings settings) {
        long now = SystemClock.elapsedRealtime();
        Iterator<Map.Entry<String, Long>> expired = pending.entrySet().iterator();
        while (expired.hasNext()) {
            Map.Entry<String, Long> request = expired.next();
            if (now - request.getValue() > 8000) {
                ProcessingHistory.Row row = history.find(request.getKey());
                if (row != null) history.update(row.id, "结果未知", "未收到目标进程的停止确认；未记为成功");
                expired.remove();
            }
        }
        if (data == null || data.cpu <= settings.threshold || !settings.enabled || !"stop_task".equals(settings.action)
                || data.snapshot == null || data.threads.isEmpty()) return;
        CoreTracker.Detail hot = data.threads.get(0);
        for (CoreTracker.Detail candidate : data.threads) {
            if (candidate.thread.tid != candidate.thread.pid && !protectedName(candidate.thread.name)) { hot = candidate; break; }
        }
        CoreSnapshot.Counter t = hot.thread;
        Long last = attempted.get(t.key());
        if (last != null && now - last < 10000) return;
        attempted.put(t.key(), now);
        while (attempted.size() > 200) attempted.remove(attempted.keySet().iterator().next());
        String id = UUID.randomUUID().toString();
        String reason = data.snapshot.foreground == 1 ? "QQ 前台超限" : data.snapshot.foreground == 0 ? "QQ 后台超限" : "QQ 前后台未知";
        String unavailable = t.tid == t.pid || protectedName(t.name) ? "主线程或系统线程受到保护，未处理"
                : data.snapshot.foreground < 0 ? "前后台未确认，暂停处理"
                : app == null || Build.VERSION.SDK_INT < 33 ? "目标控制通道不可用"
                : !identity(t) ? "目标线程已退出或身份变化" : "";
        history.add(new ProcessingHistory.Row(id, System.currentTimeMillis(), t, settings.coreMask, data.cpu, hot.cpu,
                reason, unavailable.isEmpty() ? "等待处理" : "未处理", unavailable));
        if (!unavailable.isEmpty()) return;
        pending.put(id, now);
        Intent request = new Intent(CANCEL).setPackage(app.getPackageName());
        request.putExtra("id", id).putExtra("pid", t.pid).putExtra("pidStart", t.pidStart).putExtra("tid", t.tid)
                .putExtra("tidStart", t.tidStart).putExtra("name", t.name).putExtra("window", data.windowStartNs)
                .putExtra("sampleNs", data.snapshot.monoNs).putExtra("mask", settings.coreMask).putExtra("threshold", settings.threshold);
        app.sendBroadcast(request);
    }
    private static boolean protectedName(String name) {
        return name.startsWith("qqwatch-") || name.equals("RenderThread") || name.toLowerCase(Locale.ROOT).startsWith("binder:")
                || name.contains("Signal Catcher") || name.contains("Jit thread") || name.contains("HeapTaskDaemon")
                || name.contains("Finalizer") || name.contains("ReferenceQueue");
    }
    private static void handle(Intent request) {
        String id = request.getStringExtra("id");
        if (id == null || id.length() > 80) return;
        CpuLoadMonitor.Settings settings = WatchModule.readSettings();
        if (!settings.enabled || settings.coreMask != request.getIntExtra("mask", 0)
                || settings.threshold != request.getIntExtra("threshold", 0) || !"stop_task".equals(settings.action)) {
            reply(id, "未处理", "配置已变化或监控已关闭"); return;
        }
        long age = System.nanoTime() - request.getLongExtra("sampleNs", 0);
        if (age < 0 || age > 2500000000L) { reply(id, "未处理", "采样已过期"); return; }
        CoreSnapshot.Counter t = new CoreSnapshot.Counter(request.getIntExtra("pid", 0), request.getLongExtra("pidStart", 0),
                request.getIntExtra("tid", 0), request.getLongExtra("tidStart", 0), request.getStringExtra("name"), new long[8]);
        if (t.tid == t.pid || protectedName(t.name) || !identity(t)) { reply(id, "未处理", "目标身份已变化或属于受保护线程"); return; }
        TaskRegistry.Entry task = tasks.current(t.tid, request.getLongExtra("window", 0));
        if (task == null && tasks.current(t.tid, Long.MAX_VALUE) != null) { reply(id, "任务已变化", "当前任务晚于采样窗口开始，等待新样本定位"); return; }
        if (task == null) { reply(id, "无法处理", "未定位到覆盖该采样窗口的 Java 任务；原生线程需要专用停止接口"); return; }
        String result = tasks.request(task);
        if (!result.startsWith("已请求")) { reply(id, "未处理", result); return; }
        reply(id, "待确认", result + "；取消标记不等于任务结束");
        main.postDelayed(() -> verify(id, t, task, SystemClock.elapsedRealtime() + 5000), 100);
    }
    private static void verify(String id, CoreSnapshot.Counter t, TaskRegistry.Entry task, long deadline) {
        if (task.ended) { reply(id, "任务已结束", "已观察到原任务返回；线程池工作线程可继续保留"); return; }
        if (!identity(t)) {
            reply(id, "目标已退出", "线程或进程身份已变化；无法单凭此结果确认取消原因"); return;
        }
        if (SystemClock.elapsedRealtime() >= deadline) { reply(id, "仍在运行", "任务没有响应取消或中断；未强制结束 QQ 线程/进程"); return; }
        main.postDelayed(() -> verify(id, t, task, deadline), 200);
    }
    private static void reply(String id, String state, String detail) {
        Application context = app; if (context == null) return;
        try { context.sendBroadcast(new Intent(RESULT).setPackage(context.getPackageName())
                .putExtra("id", id).putExtra("pid", android.os.Process.myPid()).putExtra("state", state).putExtra("detail", detail)); }
        catch (Throwable unavailable) {}
    }
    private static synchronized void acceptResult(Intent i) {
        String id = i.getStringExtra("id"); ProcessingHistory.Row row = history.find(id);
        if (row == null || row.pid != i.getIntExtra("pid", 0) || !pending.containsKey(id)) return;
        String state = i.getStringExtra("state"), detail = i.getStringExtra("detail");
        if (state == null || detail == null || detail.length() > 1024) return;
        history.update(id, state, detail); if (!state.equals("待确认")) pending.remove(id);
        if (state.equals("任务已结束") || state.equals("任务已变化"))
            attempted.remove(row.pid + ":" + row.pidStart + ":" + row.tid + ":" + row.tidStart);
    }
}
