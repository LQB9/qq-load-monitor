package com.lqb9.qqwatchmod;

import java.io.*;
import java.lang.reflect.*;
import static com.lqb9.qqwatchmod.ModuleInfo.*;

/** Read-only QQ-UID process and thread collection; selected-core tracing remains in CoreCollector. */
final class QqLoadSampler {
    static ThreadCpuTracker.Snapshot collectThreads(ThreadCpuTracker tracker,
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
    static QqCpuTracker.Snapshot collectCpu(QqCpuTracker tracker) throws Exception {
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
}

