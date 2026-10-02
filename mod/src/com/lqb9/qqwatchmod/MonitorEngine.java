package com.lqb9.qqwatchmod;

import java.io.*;
import java.lang.reflect.*;
import static com.lqb9.qqwatchmod.ModuleInfo.*;
import static com.lqb9.qqwatchmod.MonitorState.*;

/** One sampling worker per QQ main process; owns timing and coordinates immutable samples. */
final class MonitorEngine {
    private final java.util.concurrent.atomic.AtomicBoolean started=new java.util.concurrent.atomic.AtomicBoolean();
    private final Object sampleWake=new Object();
    private final java.util.function.Consumer<String> logger;
    private final java.util.function.BooleanSupplier panelVisible;
    private final java.util.function.Consumer<String> alerts;
    private volatile Thread worker;
    MonitorEngine(java.util.function.Consumer<String> logger,java.util.function.BooleanSupplier panelVisible,
                  java.util.function.Consumer<String> alerts) {
        this.logger=logger;this.panelVisible=panelVisible;this.alerts=alerts;
    }
    void requestSample() {synchronized(sampleWake){sampleWake.notifyAll();}}
    private void waitForSample(int seconds) throws InterruptedException {
        synchronized(sampleWake){sampleWake.wait(seconds*1000L);}
    }
    synchronized void stop() {Thread t=worker;if(t!=null)t.interrupt();requestSample();}
    boolean running() {Thread t=worker;return t!=null && t.isAlive();}
    synchronized void start() {
        if (!started.compareAndSet(false, true)) return;
        startedAt = android.os.SystemClock.elapsedRealtime();
        Thread t = new Thread(new Runnable() {
            public void run() {
                try { runLoop(); } finally { started.set(false); }
            }
            private void runLoop() {
                CpuLoadMonitor monitor = new CpuLoadMonitor();
                ThreadLoadMonitor singleMonitor=new ThreadLoadMonitor();
                ThreadLoadHistory threadHistory=new ThreadLoadHistory();
                QqCpuTracker tracker = new QqCpuTracker();
                ThreadCpuTracker threadTracker = new ThreadCpuTracker();
                CoreTracker coreTracker = new CoreTracker();
                CoreSamplingDiagnostics.Forwarder coreDiagnostics = new CoreSamplingDiagnostics.Forwarder();
                String lastCoreState = "";
                long lastErrorMs = -60000L;
                long lastLogSampleMs = -1L;
                CpuLoadMonitor.Settings loggedSettings = null;
                int historyMask = 0;
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        CpuLoadMonitor.Settings settings = WatchSettings.read();
                        if (historyMask != settings.coreMask) { loadHistory.clear(); historyMask = settings.coreMask; }
                        if (!settings.sameAs(loggedSettings)) {
                            loggedSettings = settings;
                            WatchLog.record("SETTINGS", MonitorReport.settingsText(settings));
                        }
                        QqCpuTracker.Snapshot processes = QqLoadSampler.collectCpu(tracker);
                        ThreadCpuTracker.Snapshot threads = QqLoadSampler.collectThreads(threadTracker, processes);
                        java.util.List<CoreFrequency.Core> frequencies = CoreFrequency.read(processes.cores);
                        CoreSnapshot coreSnapshot = null;
                        String coreReadError = "";
                        try { coreSnapshot = CoreSnapshot.decode(CoreCollector.read(new File(SW, CoreCollector.DATA))); }
                        catch (Exception unavailable) { coreReadError = unavailable.getClass().getSimpleName() + ": " + unavailable.getMessage(); }
                        if (coreSnapshot != null && coreSnapshot.uid == android.os.Process.myUid())
                            coreDiagnostics.forward(coreSnapshot.diagnostics, WatchLog::recordAt);
                        CoreTracker.Result core = coreTracker.sample(coreSnapshot, settings.coreMask,
                                android.os.Process.myUid(), processes.elapsedMs, settings.intervalSeconds);
                        if (core == null) {
                            TaskBridge.consider(null, settings, null);
                            waitForSample(settings.intervalSeconds); continue;
                        }
                        String coreState = core.cpu >= 0 ? "VALID" : core.note;
                        if (!coreState.equals(lastCoreState)) {
                            WatchLog.record("CORE_STATE", "elapsedMs=" + processes.elapsedMs + " previous=" + lastCoreState
                                    + " state=" + coreState + " note=" + core.note + " readError=" + coreReadError);
                            lastCoreState = coreState;
                        }
                        CpuLoadMonitor.Sample sample = monitor.sample(core.cpu,
                                coreSnapshot == null ? processes.elapsedMs : Math.min(processes.elapsedMs, coreSnapshot.elapsedMs), settings, startedAt + GUARD_MS);
                        loadHistory.add(sample.elapsedMs, sample.cpu, settings.intervalSeconds);
                        ThreadLoadMonitor.Result single=singleMonitor.sample(core,settings,processes.elapsedMs,startedAt+GUARD_MS);
                        threadHistory.add(core,settings,processes.elapsedMs);
                        latest = new LoadSnapshot(sample, processes, threads, frequencies, core,single,threadHistory.snapshot());
                        if (settings.sameAs(WatchSettings.read())) TaskBridge.consider(core, settings, sample,single);
                        if (lastLogSampleMs < 0 || sample.elapsedMs - lastLogSampleMs >= 5000L) {
                            lastLogSampleMs = sample.elapsedMs;
                            WatchLog.record("SAMPLE", MonitorReport.snapshotText(latest));
                            TaskBridge.logExecutions(core);
                        }
                        if (sample.trigger) {
                            // 决定执行前再核对完整配置，关闭/改阈值后不使用旧判定。
                            CpuLoadMonitor.Settings current = WatchSettings.read();
                            if (settings.sameAs(current)) {
                                String what = "QQ 所选核心 CPU 高负载 cpu=" + MonitorReport.round1(sample.cpu)
                                        + "% threshold=" + settings.threshold + "% 持续="
                                        + sample.highMs / 1000 + "秒 进程=" + processes.processes.size()
                                        + " 核心=" + CoreSnapshot.selection(settings.coreMask)
                                        + " QQ=" + (coreSnapshot == null || coreSnapshot.foreground < 0 ? "未知" : coreSnapshot.foreground == 1 ? "前台" : "后台");
                                alertCount++;
                                push(what);
                                logger.accept(TAG + " " + what);
                                int recorded = 0;
                                for (ThreadCpuTracker.Detail thread : threads.threads) {
                                    if (thread.cpu <= 0 || recorded >= 3) break;
                                    String hot = "热点 pid=" + thread.reading.pid + " tid=" + thread.reading.tid
                                            + " " + thread.reading.name + " CPU=" + MonitorReport.round1(thread.cpu)
                                            + "% 最后核心=" + thread.reading.lastCore;
                                    push(hot);
                                    logger.accept(TAG + " " + hot);
                                    recorded++;
                                }
                                alerts.accept(what);
                            } else {
                                monitor.reset();
                            }
                        }
                        waitForSample(settings.enabled || panelVisible.getAsBoolean()
                                ? settings.intervalSeconds : Math.max(10, settings.intervalSeconds));
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        return;
                    } catch (Throwable t) {
                        monitor.reset();
                        singleMonitor.reset();
                        threadHistory.clear();
                        tracker.reset();
                        threadTracker.reset();
                        coreTracker.reset();
                        CpuLoadMonitor.Settings settings = WatchSettings.read();
                        long now = android.os.SystemClock.elapsedRealtime();
                        loadHistory.add(now, -1, settings.intervalSeconds);
                        latest = new LoadSnapshot(
                                new CpuLoadMonitor.Sample(settings, now, -1, 0, false, "invalid"),
                                new QqCpuTracker.Snapshot(-1, 0, now,
                                        new java.util.ArrayList<QqCpuTracker.Detail>(), "CPU 采样失败"),
                                new ThreadCpuTracker.Snapshot(
                                        new java.util.ArrayList<ThreadCpuTracker.Detail>(), 0, 1),
                                java.util.Collections.<CoreFrequency.Core>emptyList());
                        if (now - lastErrorMs >= 60000L) {
                            lastErrorMs = now;
                            push("CPU 采样失败：" + t.getClass().getSimpleName());
                            logger.accept(TAG + " sample ERR " + t);
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
        worker=t;
        t.start();
    }
}

