package com.lqb9.qqwatchmod;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface.Chain;
import io.github.libxposed.api.XposedInterface.Hooker;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam;
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam;

/**
 * QQ 看门狗 v1.15：主进程汇总 QQ 同 UID 所有进程的 CPU 消耗，并保留滚动日志。
 * CPU% = 所有 QQ 进程 CPU 时间增量 / 单调时间增量 × 100；单核满载=100%。
 * 汇总负载、线程热点和当前频率，只记录并提示，不结束进程。
 * watchdog.on 沿用总开关，watchdog.load 原子保存整组负载设置。
 * 老的线程基线/K/scope 不再参与判定；没有新配置时沿用旧 interval。
 * 长按 + 优先 QQ 增强，两个面板继续通过 Runnable View tag 互跳。
 */
public class WatchModule extends XposedModule {

    static final String TAG = "[QQWATCH]";
    static final String VERSION = "1.15";
    static final String SW = "/sdcard/Android/data/com.tencent.mobileqq/files";

    static final String F_ON = "watchdog.on";
    static final String F_LOAD = "watchdog.load";
    static final String F_IV = "watchdog.iv";

    static final int DEF_CPU = 200;
    static final int DEF_DURATION = 0;
    static final int DEF_IV = 1;
    static final long GUARD_MS = 0L;

    // ---------- 运行时状态（面板读，守护线程写） ----------
    static volatile String selfName = "?";
    static volatile boolean isMain = false;
    static final class LoadStatus {
        final CpuLoadMonitor.Sample sample;
        final QqCpuTracker.Snapshot processes;
        final ThreadCpuTracker.Snapshot threads;
        final java.util.List<CoreFrequency.Core> frequencies;
        final java.util.List<LoadHistory.Point> history;
        LoadStatus(CpuLoadMonitor.Sample sample, QqCpuTracker.Snapshot processes,
                   ThreadCpuTracker.Snapshot threads, java.util.List<CoreFrequency.Core> frequencies) {
            this.sample = sample;
            this.processes = processes;
            this.threads = threads;
            this.frequencies = frequencies;
            this.history = loadHistory.snapshot();
        }
    }
    static volatile LoadStatus latest;
    static final LoadHistory loadHistory = new LoadHistory();
    static volatile long startedAt = 0;
    static volatile int alertCount = 0;
    static volatile String[] events = new String[0];

    private static final java.util.concurrent.atomic.AtomicBoolean watchStarted =
            new java.util.concurrent.atomic.AtomicBoolean();
    private static final java.util.concurrent.atomic.AtomicBoolean summonHooked =
            new java.util.concurrent.atomic.AtomicBoolean();
    private static final Object sampleWake = new Object();

    static void requestSample() {
        synchronized (sampleWake) { sampleWake.notifyAll(); }
    }

    private static void waitForSample(int seconds) throws InterruptedException {
        synchronized (sampleWake) { sampleWake.wait(seconds * 1000L); }
    }

    // ==================== 入口 ====================

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        say(TAG + " loaded in " + param.getProcessName());
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        try {
            selfName = procName();
            isMain = "com.tencent.mobileqq".equals(selfName);
            if (isMain) WatchLog.start(new File(SW, "watchdog-logs"), "QQ 看门狗 v" + VERSION
                    + " pid=" + android.os.Process.myPid() + " process=" + selfName
                    + " Android=" + android.os.Build.VERSION.RELEASE + " SDK=" + android.os.Build.VERSION.SDK_INT
                    + " device=" + android.os.Build.MANUFACTURER + "/" + android.os.Build.MODEL
                    + " 单核满载=100% action=record");
            say(TAG + " packageReady " + selfName);
            if (isMain) {
                hookSummon();
                startWatchdog();
            }
        } catch (Throwable t) {
            say(TAG + " packageReady ERR " + t);
        }
    }

    /** API 102 的 log 是 log(int, String, String)，没有单参重载；探针老代码里的 say() 其实是反射，
     *  在本版根本没生效 —— 这里老老实实调三参那个。 */
    private void say(String s) {
        if (isMain) WatchLog.record("INFO", s);
        try {
            log(android.util.Log.INFO, TAG, s);
        } catch (Throwable ignored) {}
    }

    // ==================== 看门狗 ====================

    private void startWatchdog() {
        if (!watchStarted.compareAndSet(false, true)) return;
        startedAt = android.os.SystemClock.elapsedRealtime();
        Thread t = new Thread(new Runnable() {
            public void run() {
                CpuLoadMonitor monitor = new CpuLoadMonitor();
                QqCpuTracker tracker = new QqCpuTracker();
                ThreadCpuTracker threadTracker = new ThreadCpuTracker();
                long lastErrorMs = -60000L;
                long lastLogSampleMs = -1L;
                CpuLoadMonitor.Settings loggedSettings = null;
                while (true) {
                    try {
                        CpuLoadMonitor.Settings settings = readSettings();
                        if (!settings.sameAs(loggedSettings)) {
                            loggedSettings = settings;
                            WatchLog.record("SETTINGS", settingsText(settings));
                        }
                        QqCpuTracker.Snapshot processes = collectCpu(tracker);
                        ThreadCpuTracker.Snapshot threads = collectThreads(threadTracker, processes);
                        java.util.List<CoreFrequency.Core> frequencies = CoreFrequency.read(processes.cores);
                        CpuLoadMonitor.Sample sample = monitor.sample(processes.cpu,
                                processes.elapsedMs, settings, startedAt + GUARD_MS);
                        loadHistory.add(sample.elapsedMs, sample.cpu, settings.intervalSeconds);
                        latest = new LoadStatus(sample, processes, threads, frequencies);
                        if (lastLogSampleMs < 0 || sample.elapsedMs - lastLogSampleMs >= 5000L) {
                            lastLogSampleMs = sample.elapsedMs;
                            WatchLog.record("SAMPLE", snapshotText(latest));
                        }
                        if (sample.trigger) {
                            // 决定执行前再核对完整配置，关闭/改阈值后不使用旧判定。
                            CpuLoadMonitor.Settings current = readSettings();
                            if (settings.sameAs(current)) {
                                String what = "QQ 总 CPU 高负载 cpu=" + round1(sample.cpu)
                                        + "% threshold=" + settings.threshold + "% 持续="
                                        + sample.highMs / 1000 + "秒 进程=" + processes.processes.size()
                                        + " 核心=" + processes.cores;
                                alertCount++;
                                push(what);
                                say(TAG + " " + what);
                                int recorded = 0;
                                for (ThreadCpuTracker.Detail thread : threads.threads) {
                                    if (thread.cpu <= 0 || recorded >= 3) break;
                                    String hot = "热点 pid=" + thread.reading.pid + " tid=" + thread.reading.tid
                                            + " " + thread.reading.name + " CPU=" + round1(thread.cpu)
                                            + "% 最后核心=" + thread.reading.lastCore;
                                    push(hot);
                                    say(TAG + " " + hot);
                                    recorded++;
                                }
                                showLoadAlert(what);
                            } else {
                                monitor.reset();
                            }
                        }
                        waitForSample(settings.enabled || WatchPanel.isVisible()
                                ? settings.intervalSeconds : Math.max(10, settings.intervalSeconds));
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        return;
                    } catch (Throwable t) {
                        monitor.reset();
                        tracker.reset();
                        threadTracker.reset();
                        CpuLoadMonitor.Settings settings = readSettings();
                        long now = android.os.SystemClock.elapsedRealtime();
                        loadHistory.add(now, -1, settings.intervalSeconds);
                        latest = new LoadStatus(
                                new CpuLoadMonitor.Sample(settings, now, -1, 0, false, "invalid"),
                                new QqCpuTracker.Snapshot(-1, 0, now,
                                        new java.util.ArrayList<QqCpuTracker.Detail>(), "CPU 采样失败"),
                                new ThreadCpuTracker.Snapshot(
                                        new java.util.ArrayList<ThreadCpuTracker.Detail>(), 0, 1),
                                java.util.Collections.<CoreFrequency.Core>emptyList());
                        if (now - lastErrorMs >= 60000L) {
                            lastErrorMs = now;
                            push("CPU 采样失败：" + t.getClass().getSimpleName());
                            say(TAG + " sample ERR " + t);
                        }
                        try {
                            waitForSample(Math.max(1, settings.intervalSeconds));
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                }
            }
        });
        t.setName("qqwatch-dog");
        t.setDaemon(true);
        t.start();
    }

    private static ThreadCpuTracker.Snapshot collectThreads(ThreadCpuTracker tracker,
                                                            QqCpuTracker.Snapshot processes) throws Exception {
        java.util.List<ThreadCpuTracker.Reading> readings =
                new java.util.ArrayList<ThreadCpuTracker.Reading>();
        int errors = 0;
        for (QqCpuTracker.Detail process : processes.processes) {
            String taskDir = "/proc/" + process.reading.pid + "/task";
            String[] tids = new File(taskDir).list();
            if (tids == null) { errors++; continue; }
            for (String entry : tids) {
                int tid;
                try { tid = Integer.parseInt(entry); }
                catch (NumberFormatException ignored) { continue; }
                String dir = taskDir + "/" + tid;
                try {
                    readings.add(ThreadCpuTracker.Reading.parse(process.reading.pid, tid,
                            process.reading.name, readProc(dir + "/stat", false)));
                } catch (Exception error) {
                    if (new File(dir).exists()) errors++;
                }
            }
        }
        return tracker.sample(readings, android.os.SystemClock.elapsedRealtime(),
                android.system.Os.sysconf(android.system.OsConstants._SC_CLK_TCK), errors);
    }

    /** 只读 QQ 自己 UID 的 /proc；任何仍存活的目标进程读失败，整轮不做超限判定。 */
    private QqCpuTracker.Snapshot collectCpu(QqCpuTracker tracker) throws Exception {
        long hz = android.system.Os.sysconf(android.system.OsConstants._SC_CLK_TCK);
        int cores = (int) android.system.Os.sysconf(android.system.OsConstants._SC_NPROCESSORS_CONF);
        String[] entries = new File("/proc").list();
        if (entries == null) throw new java.io.IOException("Cannot list /proc");
        java.util.List<QqCpuTracker.Reading> readings = new java.util.ArrayList<QqCpuTracker.Reading>();
        boolean complete = true;
        boolean haveMain = false;
        for (String entry : entries) {
            int pid;
            try { pid = Integer.parseInt(entry); } catch (NumberFormatException ignored) { continue; }
            String dir = "/proc/" + pid;
            try {
                if (android.system.Os.stat(dir).st_uid != android.os.Process.myUid()) continue;
            } catch (android.system.ErrnoException ignored) {
                continue;
            }
            try {
                String name = readProc(dir + "/cmdline", true);
                if (name.length() == 0) continue; // 已退出的僵尸进程不占 CPU。
                QqCpuTracker.Reading reading = QqCpuTracker.Reading.parse(
                        pid, name, readProc(dir + "/stat", false));
                readings.add(reading);
                if (pid == android.os.Process.myPid()) haveMain = true;
            } catch (Exception error) {
                if (new File(dir).exists()) complete = false;
            }
        }
        return tracker.sample(readings, android.os.SystemClock.elapsedRealtime(),
                cores, hz, complete && haveMain);
    }

    private static String readProc(String path, boolean cmdline) throws Exception {
        byte[] bytes = new byte[4096];
        int n;
        try (FileInputStream input = new FileInputStream(path)) { n = input.read(bytes); }
        if (n < 1) return "";
        if (cmdline) { int i = 0; while (i < n && bytes[i] != 0) i++; n = i; }
        return new String(bytes, 0, n, "UTF-8").trim();
    }

    private static void showLoadAlert(final String message) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
            public void run() {
                try {
                    android.content.Context context = lastCtx.get();
                    if (context == null) {
                        Method app = Class.forName("android.app.ActivityThread")
                                .getDeclaredMethod("currentApplication");
                        context = (android.content.Context) app.invoke(null);
                    }
                    if (context != null) android.widget.Toast.makeText(context.getApplicationContext(),
                            message, android.widget.Toast.LENGTH_LONG).show();
                } catch (Throwable ignored) {}
            }
        });
    }

    /** /proc/self/cmdline 的第一段 = 进程名（含 :MSF 之类后缀） */
    static String procName() {
        try {
            byte[] b = new byte[256];
            FileInputStream in = new FileInputStream("/proc/self/cmdline");
            int n = in.read(b);
            in.close();
            if (n <= 0) return "?";
            int e = 0;
            while (e < n && b[e] != 0) e++;
            return new String(b, 0, e, "UTF-8");
        } catch (Throwable t) {
            return "?";
        }
    }

    /** 新配置是一个原子文件；首次升级保留旧 interval，线程/范围设置不读。 */
    static CpuLoadMonitor.Settings readSettings() {
        java.util.Properties p = new java.util.Properties();
        String text = cfg(F_LOAD);
        try {
            p.load(new java.io.StringReader(text));
        } catch (Exception ignored) {
            return new CpuLoadMonitor.Settings(false, DEF_CPU, DEF_DURATION, DEF_IV, "record");
        }
        boolean fresh = text.length() == 0;
        return new CpuLoadMonitor.Settings(on(),
                settingInt(p, "cpu", DEF_CPU), settingInt(p, "duration", DEF_DURATION),
                settingInt(p, "interval", fresh ? cfgInt(F_IV, DEF_IV) : DEF_IV),
                p.getProperty("action", "record"));
    }

    private static int settingInt(java.util.Properties p, String key, int fallback) {
        try { return Integer.parseInt(p.getProperty(key, String.valueOf(fallback)).trim()); }
        catch (Exception ignored) { return fallback; }
    }

    static boolean saveSettings(CpuLoadMonitor.Settings settings) {
        String data = "cpu=" + settings.threshold + "\nduration=" + settings.durationSeconds
                + "\ninterval=" + settings.intervalSeconds
                + "\naction=" + settings.action + "\n";
        return cfgSet(F_LOAD, data);
    }

    // ==================== 配置文件 ====================

    static boolean on() {
        try {
            return new File(SW + "/" + F_ON).exists();
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * QQ 增强 v1.48 起把看门狗整块摘掉了（挪到本模块）。
     * 那边留下的 killleak.* 文件现在没人读了 —— 面板要能提示用户清理。
     */
    static final String[] LEGACY = {"killleak.on", "killleak.scope", "killleak.base", "killleak.k",
            "killleak.limit"};

    /** 返回第一个还存在的遗留文件名，没有就返回 null */
    static String legacyLeftover() {
        try {
            for (int i = 0; i < LEGACY.length; i++) {
                if (new File(SW + "/" + LEGACY[i]).exists()) return LEGACY[i];
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    static int legacyCount() {
        int n = 0;
        try {
            for (int i = 0; i < LEGACY.length; i++) {
                if (new File(SW + "/" + LEGACY[i]).exists()) n++;
            }
        } catch (Throwable ignored) {
        }
        return n;
    }

    /** 删掉 killleak.* 遗留文件，返回实际删掉的数量（只删白名单里的这 5 个名字） */
    static int legacyPurge() {
        int n = 0;
        for (int i = 0; i < LEGACY.length; i++) {
            try {
                File f = new File(SW + "/" + LEGACY[i]);
                if (f.exists() && f.delete()) n++;
            } catch (Throwable ignored) {
            }
        }
        return n;
    }

    static String cfg(String name) {
        try {
            File f = new File(SW + "/" + name);
            if (!f.exists()) return "";
            byte[] b = new byte[256];
            int n;
            try (FileInputStream in = new FileInputStream(f)) { n = in.read(b); }
            return n <= 0 ? "" : new String(b, 0, n, "UTF-8").trim();
        } catch (Throwable t) {
            return "";
        }
    }

    static int cfgInt(String name, int def) {
        try {
            String s = cfg(name);
            if (s.length() == 0) return def;
            return Integer.parseInt(s);
        } catch (Throwable t) {
            return def;
        }
    }

    static boolean cfgSet(String name, String v) {
        android.util.AtomicFile atomic = null;
        FileOutputStream out = null;
        try {
            File f = new File(SW + "/" + name);
            if (v == null) {
                return !f.exists() || f.delete();
            }
            File dir = f.getParentFile();
            if (!dir.isDirectory() && !dir.mkdirs()) return false;
            atomic = new android.util.AtomicFile(f);
            out = atomic.startWrite();
            out.write(v.getBytes("UTF-8"));
            atomic.finishWrite(out);
            return v.trim().equals(cfg(name));
        } catch (Throwable t) {
            if (atomic != null && out != null) atomic.failWrite(out);
            push("配置保存失败：" + name);
            return false;
        }
    }

    static String round1(double d) {
        return String.valueOf(Math.round(d * 10) / 10.0d);
    }

    private static String settingsText(CpuLoadMonitor.Settings settings) {
        return "监控=" + (settings.enabled ? "开启" : "关闭") + " 阈值=" + settings.threshold
                + "% 持续=" + settings.durationSeconds + "秒 采样=" + settings.intervalSeconds + "秒 action=record";
    }

    private static String loadText(double cpu) { return cpu < 0 ? "不可用" : round1(cpu) + "%"; }

    static String snapshotText(LoadStatus data) {
        if (data == null) return "等待采样";
        StringBuilder text = new StringBuilder("elapsedMs=").append(data.sample.elapsedMs)
                .append(" QQ总CPU=").append(loadText(data.sample.cpu)).append(" state=").append(data.sample.state)
                .append(" 超限持续Ms=").append(data.sample.highMs).append(" 进程数=").append(data.processes.processes.size())
                .append(" 扫描线程数=").append(data.threads.scanned).append(" 线程读取错误=").append(data.threads.errors)
                .append(" note=").append(data.processes.note);
        for (QqCpuTracker.Detail process : data.processes.processes)
            text.append(" | 进程 pid=").append(process.reading.pid).append(' ').append(process.reading.name)
                    .append(" CPU=").append(loadText(process.cpu));
        for (ThreadCpuTracker.Detail thread : data.threads.threads)
            text.append(" | 线程 pid=").append(thread.reading.pid).append(" tid=").append(thread.reading.tid)
                    .append(' ').append(thread.reading.name).append(" CPU=").append(loadText(thread.cpu))
                    .append(" 最后核心=").append(thread.reading.lastCore);
        for (CoreFrequency.Core core : data.frequencies)
            text.append(" | 核心").append(core.id).append(" 频率=")
                    .append(!core.online ? "离线" : core.khz <= 0 ? "不可读" : core.khz / 1000 + "MHz")
                    .append(core.hardwareReading ? "(硬件)" : "(驱动请求)");
        return text.toString();
    }

    static String exportHeader() {
        LoadStatus data = latest;
        long ageMs = data == null ? -1 : Math.max(0, android.os.SystemClock.elapsedRealtime() - data.sample.elapsedMs);
        return "QQ 看门狗日志 v" + VERSION + "\n导出时间：" + new java.text.SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss Z", java.util.Locale.ROOT).format(new java.util.Date())
                + "\n设备：" + android.os.Build.MANUFACTURER + "/" + android.os.Build.MODEL
                + " Android " + android.os.Build.VERSION.RELEASE + " SDK " + android.os.Build.VERSION.SDK_INT
                + "\n当前进程：" + selfName + " PID=" + android.os.Process.myPid()
                + "\n设置：" + settingsText(readSettings()) + "\n当前样本年龄Ms：" + ageMs
                + "\n当前快照：" + snapshotText(data)
                + "\n\n口径：单核100%；汇总QQ同UID进程。最后核心不是完整驻留，频率不是核心占用率。"
                + "\n日志约每5秒记录一次快照（不快于采样），事件即时入队；最多4份×512KiB，重启保留。"
                + "\n仅记录监控信息，不记录聊天内容。以下历史按记录顺序排列，超出上限的旧记录已轮换。\n\n";
    }

    static synchronized void push(String s) {
        WatchLog.record("EVENT", s);
        String[] old = events;
        int n = Math.min(old.length + 1, 20);
        String[] neo = new String[n];
        neo[0] = time() + "  " + s;
        for (int i = 1; i < n; i++) neo[i] = old[i - 1];
        events = neo;
    }

    static String time() {
        try {
            return new java.text.SimpleDateFormat("HH:mm:ss")
                    .format(new java.util.Date());
        } catch (Throwable t) {
            return "--:--:--";
        }
    }

    // ==================== 面板呼出（长按 QQ 右上角「+」） ====================

    private static volatile java.lang.ref.WeakReference<android.view.View> armed =
            new java.lang.ref.WeakReference<android.view.View>(null);
    /** QQ 增强（或别的模块）挂上的长按监听，我们包一层，别把它吃掉 */
    private static volatile android.view.View.OnLongClickListener prevLongClick;

    /**
     * v1.11：两个模块的"会合点" = 同一个 View 上的 tag。
     *   - key 必须是资源 id 形态（View.setTag(int,Object) 要求 key >= 0x02000000）
     *   - 值的类型只能用 **boot classpath 的 java.lang.Runnable**：两个模块各是各的 ClassLoader，
     *     自定义接口跨模块 cast 会 ClassCastException，Runnable/View 这些框架类才是同一个。
     * 谁放了谁的 opener，对方就能直接调起来 —— 比"抢长按监听"稳得多。
     */
    static final int TAG_OPEN_WD = 0x7f0f0002;    // 我（看门狗）放：打开看门狗面板
    static final int TAG_OPEN_ENH = 0x7f0f0001;   // QQ 增强放：打开增强面板

    /** 面板里那个"打开 QQ 增强面板"按钮用的：从 + 的 tag 里取增强模块的 opener */
    static Runnable enhOpener() {
        try {
            android.view.View v = armed.get();
            if (v == null) return null;
            Object o = v.getTag(TAG_OPEN_ENH);
            return (o instanceof Runnable) ? (Runnable) o : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** 我自己的 opener（放在 tag 上给 QQ 增强跳转用） */
    private Runnable wdOpener() {
        return new Runnable() {
            public void run() {
                try {
                    WatchPanel.show(lastCtx.get());
                } catch (Throwable t) {
                    say(TAG + " opener ERR " + t);
                }
            }
        };
    }

    /** 最近一次 onResume 拿到的 Activity（面板和 opener 都要 Context） */
    private static volatile java.lang.ref.WeakReference<android.content.Context> lastCtx =
            new java.lang.ref.WeakReference<android.content.Context>(null);
    private static volatile boolean backedOff;   // 已经让给 QQ 增强，日志只打一次


    /** 我自己的监听（单例）—— 每次 onResume 靠它判断"现在挂的是不是我的" */
    private static final android.view.View.OnLongClickListener MY_LONG_CLICK =
            new android.view.View.OnLongClickListener() {
                public boolean onLongClick(android.view.View view) {
                    try {
                        WatchPanel.show(view.getContext());
                    } catch (Throwable t) {
                        // 面板炸了也不能把长按吞掉，交回给原来的监听
                        callPrevListener(view);
                    }
                    return true;    // 吃掉，别让 QQ 也处理
                }
            };

    private void hookSummon() {
        if (!summonHooked.compareAndSet(false, true)) return;
        final String id = "WATCH:Activity#onResume";
        try {
            Class<?> act = Class.forName("android.app.Activity");
            Method m = act.getDeclaredMethod("onResume");
            m.setAccessible(true);
            hook(m).setId(id).intercept(new Hooker() {
                public Object intercept(Chain chain) throws Throwable {
                    Object r = chain.proceed();
                    try {
                        Object self = chain.getThisObject();
                        if (self instanceof android.app.Activity) {
                            android.app.Activity a = (android.app.Activity) self;
                            if (a.getClass().getName().indexOf("SplashActivity") >= 0) {
                                arm(a, 0);
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                    return r;
                }
            });
            say(TAG + " summon hook ok");
        } catch (Throwable t) {
            summonHooked.set(false);
            say(TAG + " summon hook FAIL " + t);
        }
    }

    /**
     * 每次 onResume 连查几轮。
     *
     * ⚠️ 为什么不能只查一次：实测 2026-09-30 —— 13:18:55.790 我们挂上了（prevListener=false），
     * QQ 增强在同一次 onResume 的稍后时刻（它自己也 postDelayed 150ms）又挂了一次，
     * `setOnLongClickListener` 是覆盖语义，我们直接被顶掉，长按出来的是它的面板。
     * 所以这里一路盯到 2.5 秒：谁最后挂谁赢，我们查到它挂上之后再接管。
     */
    private static final long[] RETRY_MS = {150L, 350L, 700L, 1500L, 2500L};

    private void arm(final android.app.Activity a, final int attempt) {
        if (attempt >= RETRY_MS.length) return;
        try {
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
                public void run() {
                    try {
                        android.view.View v = findPlus(a);
                        if (v != null) {
                            lastCtx = new java.lang.ref.WeakReference<android.content.Context>(a);
                            // 我自己的 opener 挂到 tag 上（给 QQ 增强的跳转行调）
                            try { v.setTag(TAG_OPEN_WD, wdOpener()); } catch (Throwable ignored) {}
                            Object enh = null;
                            try { enh = v.getTag(TAG_OPEN_ENH); } catch (Throwable ignored) {}
                            android.view.View.OnLongClickListener cur = readLongClick(v);
                            if (enh instanceof Runnable) {
                                // 装了 QQ 增强 → 长按归它（用户要求"优先 QQ 增强"），我们只留 tag
                                if (cur != MY_LONG_CLICK) {
                                    prevLongClick = (cur == null) ? prevLongClick : cur;
                                }
                                armed = new java.lang.ref.WeakReference<android.view.View>(v);
                                if (!backedOff) {
                                    backedOff = true;
                                    say(TAG + " long-press left to QQ 增强 (tag found)");
                                }
                            } else if (cur != MY_LONG_CLICK) {
                                armed = new java.lang.ref.WeakReference<android.view.View>(v);
                                // 别人的（QQ 增强的旧版）先存下来，面板里给按钮还回去
                                prevLongClick = (cur == null) ? prevLongClick : cur;
                                v.setOnLongClickListener(MY_LONG_CLICK);
                                say(TAG + " armed(try=" + attempt + ") on "
                                        + v.getClass().getSimpleName()
                                        + " prevListener=" + (prevLongClick != null));
                            } else {
                                armed = new java.lang.ref.WeakReference<android.view.View>(v);
                            }
                        }
                    } catch (Throwable t) {
                        say(TAG + " arm ERR " + t);
                    }
                    arm(a, attempt + 1);
                }
            }, RETRY_MS[attempt]);
        } catch (Throwable ignored) {
        }
    }

    static void callPrevListener(android.view.View v) {
        try {
            android.view.View.OnLongClickListener l = prevLongClick;
            if (l != null) l.onLongClick(v);
        } catch (Throwable ignored) {
        }
    }

    static boolean hasPrevListener() {
        return prevLongClick != null;
    }

    private static android.view.View.OnLongClickListener readLongClick(android.view.View v) {
        try {
            Field lf = android.view.View.class.getDeclaredField("mListenerInfo");
            lf.setAccessible(true);
            Object li = lf.get(v);
            if (li == null) return null;
            Field f2 = li.getClass().getDeclaredField("mOnLongClickListener");
            f2.setAccessible(true);
            return (android.view.View.OnLongClickListener) f2.get(li);
        } catch (Throwable t) {
            return null;
        }
    }

    private static android.view.View findPlus(android.app.Activity a) {
        try {
            int rid = a.getResources().getIdentifier("ba3", "id", "com.tencent.mobileqq");
            if (rid != 0) {
                android.view.View v = a.findViewById(rid);
                if (v != null) return v;
            }
        } catch (Throwable ignored) {
        }
        try {
            return walk(a.getWindow().getDecorView(), 0);
        } catch (Throwable t) {
            return null;
        }
    }

    private static android.view.View walk(android.view.View v, int depth) {
        if (v == null || depth > 24) return null;
        try {
            if (v instanceof android.widget.ImageView && v.isClickable()) {
                CharSequence cd = v.getContentDescription();
                String s = (cd == null) ? "" : cd.toString();
                if (s.indexOf("快捷入口") >= 0 || s.indexOf("更多") >= 0) {
                    int[] loc = new int[2];
                    v.getLocationOnScreen(loc);
                    if (loc[1] < 400) return v;     // 只看标题栏那一行
                }
            }
        } catch (Throwable ignored) {
        }
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                android.view.View r = walk(g.getChildAt(i), depth + 1);
                if (r != null) return r;
            }
        }
        return null;
    }
}
