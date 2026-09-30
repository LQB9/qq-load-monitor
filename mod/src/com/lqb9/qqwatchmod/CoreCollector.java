package com.lqb9.qqwatchmod;

import android.os.SystemClock;
import android.system.Os;
import android.system.OsConstants;
import android.util.AtomicFile;
import java.io.*;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Root app_process entry. Own trace instance, QQ UID filtering, cumulative actual runtime. */
public final class CoreCollector {
    static final String DATA = "watchdog.core", STOP = "watchdog.core.stop";
    private final int uid, onlyPid;
    private final File directory;
    private File instance;
    private final Object lock = new Object();
    private final Map<Integer, Tracked> tracked = new HashMap<Integer, Tracked>();
    private final Map<Integer, Long> rejected = new HashMap<Integer, Long>();
    private final long[] totals = new long[8];
    private long allRuntime, generation, sequence, lastLoss, warmUntil;
    private volatile boolean stopping;
    private volatile String readerError = "";
    private volatile int foreground = -1;
    private InputStream pipe;
    private String baseSession = UUID.randomUUID().toString();
    private String previousIds = "";
    private static final class Tracked {
        final int pid, tid; final long pidStart, start; final String name;
        final long[] cores = new long[8]; long lastSeen;
        Tracked(int pid, long pidStart, int tid, long start, String name, long seen) {
            this.pid = pid; this.pidStart = pidStart; this.tid = tid; this.start = start; this.name = name; lastSeen = seen;
        }
        String key() { return pid + ":" + pidStart + ":" + tid + ":" + start; }
    }
    public static void main(String[] args) {
        if (args.length < 2) { System.err.println("Usage: CoreCollector uid directory [onlyPid]"); return; }
        CoreCollector collector = new CoreCollector(Integer.parseInt(args[0]), new File(args[1]), args.length > 2 ? Integer.parseInt(args[2]) : 0);
        try { collector.run(); }
        catch (Throwable failure) {
            try { collector.publish(false, "精确采集失败：" + failure.getClass().getSimpleName() + " " + failure.getMessage()); }
            catch (Throwable ignored) {}
            System.err.println("CORE_FAILED " + failure);
        } finally { collector.close(); }
    }
    CoreCollector(int uid, File directory, int onlyPid) { this.uid = uid; this.directory = directory; this.onlyPid = onlyPid; }

    private void run() throws Exception {
        if (android.os.Process.myUid() != 0) throw new IOException("精确采集需要 root 授权");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("无法创建采集目录");
        try (RandomAccessFile owner = new RandomAccessFile(new File(directory, "watchdog.core.lock"), "rw");
             FileLock lease = owner.getChannel().tryLock()) {
            if (lease == null) { System.out.println("CORE_ALREADY_RUNNING"); return; }
            new File(directory, STOP).delete();
            if (!"1".equals(read(new File("/proc/sys/kernel/sched_schedstats")).trim()))
                throw new IOException("内核调度统计未启用");
            File trace = new File("/sys/kernel/tracing");
            if (!new File(trace, "instances").isDirectory()) trace = new File("/sys/kernel/debug/tracing");
            String prefix = "qqwatch-" + uid + "-" + Integer.toHexString(directory.getAbsolutePath().hashCode()) + "-";
            File[] stale = new File(trace, "instances").listFiles();
            if (stale != null) for (File old : stale) if (old.getName().startsWith(prefix)) cleanup(old);
            instance = new File(trace, "instances/" + prefix + android.os.Process.myPid());
            if (!instance.mkdir()) throw new IOException("无法创建独立调度采集实例");
            write(new File(instance, "tracing_on"), "0");
            write(new File(instance, "buffer_size_kb"), "256");
            write(new File(instance, "trace_clock"), "mono");
            write(new File(instance, "options/event-fork"), "1");
            write(new File(instance, "events/sched/sched_stat_runtime/enable"), "1");
            warmUntil = SystemClock.elapsedRealtime() + 2500;
            Thread reader = new Thread(() -> readEvents(), "qqcore-events"); reader.setDaemon(true); reader.start();
            Thread focus = new Thread(() -> readForeground(), "qqcore-focus"); focus.setDaemon(true); focus.start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> close()));
            Map<String, Long> previousCpu = null; long previousRuntime = 0;
            long hz = Os.sysconf(OsConstants._SC_CLK_TCK);
            System.out.println("CORE_READY pid=" + android.os.Process.myPid());
            while (!new File(directory, STOP).exists() && readerError.isEmpty()) {
                long began = SystemClock.elapsedRealtime();
                Map<String, Long> cpu = discover();
                long runtime; synchronized (lock) { runtime = allRuntime; }
                boolean complete = true;
                boolean discontinuity = false;
                if (previousCpu != null && cpu.keySet().equals(previousCpu.keySet())) {
                    long ticks = 0;
                    for (String identity : cpu.keySet()) {
                        long diff = cpu.get(identity) - previousCpu.get(identity);
                        if (diff < 0) complete = false; else ticks += diff;
                    }
                    long measured = ticks * 1000000000L / hz;
                    if (measured > (runtime - previousRuntime) * 1.25 + 80000000L) complete = false;
                } else { warmUntil = Math.max(warmUntil, began + 1200); discontinuity = true; }
                long loss = losses();
                if (loss > lastLoss) { complete = false; warmUntil = began + 2500; }
                // A slow consumer may miss an intermediate invalid snapshot. The
                // session generation must still break its next difference window.
                if (!complete || discontinuity) synchronized (lock) { generation++; }
                lastLoss = loss;
                previousCpu = cpu; previousRuntime = runtime;
                boolean valid = complete && began >= warmUntil;
                publish(valid, valid ? "调度采集运行中" : complete ? "调度采集预热" : "调度事件不完整，暂停处理");
                SystemClock.sleep(Math.max(10, 1000 - (SystemClock.elapsedRealtime() - began)));
            }
            publish(false, readerError.isEmpty() ? "精确采集已停止" : "精确采集失败：" + readerError);
        }
    }

    private Map<String, Long> discover() throws Exception {
        Map<String, Long> cpu = new HashMap<String, Long>(); StringBuilder ids = new StringBuilder();
        String[] processes = new File("/proc").list(); if (processes == null) throw new IOException("无法读取进程列表");
        long now = SystemClock.elapsedRealtime();
        for (String entry : processes) {
            int pid; try { pid = Integer.parseInt(entry); } catch (NumberFormatException ignored) { continue; }
            if (onlyPid != 0 && pid != onlyPid) continue;
            File proc = new File("/proc/" + pid);
            try {
                if (Os.stat(proc.getPath()).st_uid != uid) continue;
                QqCpuTracker.Reading p = QqCpuTracker.Reading.parse(pid, "QQ", read(new File(proc, "stat")));
                cpu.put(pid + ":" + p.startTicks, p.cpuTicks);
                String[] tasks = new File(proc, "task").list(); if (tasks == null) throw new IOException("线程列表不可读");
                for (String task : tasks) {
                    int tid = Integer.parseInt(task);
                    try {
                        ThreadCpuTracker.Reading t = ThreadCpuTracker.Reading.parse(pid, tid, "QQ", read(new File(proc, "task/" + task + "/stat")));
                        synchronized (lock) {
                            Tracked old = tracked.get(tid);
                            if (old == null || old.start != t.startTicks || old.pidStart != p.startTicks)
                                tracked.put(tid, new Tracked(pid, p.startTicks, tid, t.startTicks, t.name, now));
                            else old.lastSeen = now;
                        }
                        ids.append(tid).append(' ');
                    } catch (IOException gone) { if (new File(proc, "task/" + task).exists()) throw gone; }
                }
            } catch (android.system.ErrnoException gone) { if (proc.exists()) throw gone; }
              catch (IOException gone) { if (proc.exists()) throw gone; }
        }
        String filter = ids.toString();
        if (!filter.equals(previousIds)) {
            // Replacing a live filter does not stop recording. Newly forked tasks are
            // added by event-fork; refresh also discovers independent QQ processes.
            if (filter.isEmpty()) write(new File(instance, "tracing_on"), "0");
            write(new File(instance, "set_event_pid"), filter);
            if (!filter.isEmpty()) write(new File(instance, "tracing_on"), "1");
            previousIds = filter;
        }
        synchronized (lock) {
            Iterator<Tracked> it = tracked.values().iterator();
            while (it.hasNext()) if (now - it.next().lastSeen > 60000) it.remove();
            if (tracked.size() > 4096) throw new IOException("线程采集超过容量");
            if (rejected.size() > 4096) rejected.clear();
        }
        return cpu;
    }

    private void readEvents() {
        try {
            pipe = new FileInputStream(new File(instance, "trace_pipe"));
            BufferedReader reader = new BufferedReader(new InputStreamReader(pipe, StandardCharsets.UTF_8), 65536);
            String line;
            while (!stopping && (line = reader.readLine()) != null) {
                RuntimeLine event = RuntimeLine.parse(line); if (event == null) continue;
                synchronized (lock) {
                    Tracked thread = tracked.get(event.tid);
                    long now = SystemClock.elapsedRealtime();
                    if (thread == null) {
                        Long denied = rejected.get(event.tid); if (denied != null && now - denied < 2000) continue;
                        try {
                            File proc = new File("/proc/" + event.tid);
                            if (Os.stat(proc.getPath()).st_uid != uid) { rejected.put(event.tid, now); continue; }
                            String status = read(new File(proc, "status")); int at = status.indexOf("Tgid:");
                            int pid = Integer.parseInt(status.substring(at + 5, status.indexOf('\n', at)).trim());
                            if (onlyPid != 0 && pid != onlyPid) continue;
                            QqCpuTracker.Reading p = QqCpuTracker.Reading.parse(pid, "QQ", read(new File("/proc/" + pid + "/stat")));
                            ThreadCpuTracker.Reading t = ThreadCpuTracker.Reading.parse(pid, event.tid, "QQ", read(new File(proc, "stat")));
                            thread = new Tracked(pid, p.startTicks, event.tid, t.startTicks, t.name, now); tracked.put(event.tid, thread);
                        } catch (Exception gone) { continue; }
                    }
                    thread.lastSeen = now; allRuntime += event.runtime;
                    if (event.core < 8) { totals[event.core] += event.runtime; thread.cores[event.core] += event.runtime; }
                }
            }
        } catch (Exception failure) { if (!stopping) readerError = failure.toString(); }
    }

    private long losses() throws IOException {
        long loss = 0; File[] cpus = new File(instance, "per_cpu").listFiles();
        if (cpus == null) throw new IOException("无法检查采集丢失");
        for (File cpu : cpus) for (String line : read(new File(cpu, "stats")).split("\n")) {
            int colon = line.indexOf(':'); if (colon < 0) continue;
            String label = line.substring(0, colon).trim();
            if ("overrun".equals(label) || "commit overrun".equals(label) || "dropped events".equals(label))
                loss += Long.parseLong(line.substring(colon + 1).trim());
        }
        return loss;
    }
    private void publish(boolean valid, String note) throws IOException {
        List<CoreSnapshot.Counter> threads = new ArrayList<CoreSnapshot.Counter>(); CoreSnapshot snapshot;
        synchronized (lock) {
            for (Tracked t : tracked.values()) threads.add(new CoreSnapshot.Counter(t.pid, t.pidStart, t.tid, t.start, t.name, t.cores));
            snapshot = new CoreSnapshot(baseSession + "-" + generation, uid, ++sequence, SystemClock.elapsedRealtime(),
                    System.nanoTime(), valid, foreground, note, totals, threads);
        }
        byte[] bytes = snapshot.encode().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > CoreSnapshot.MAX_BYTES) throw new IOException("采集数据超过容量");
        AtomicFile atomic = new AtomicFile(new File(directory, DATA)); FileOutputStream out = null;
        try { out = atomic.startWrite(); out.write(bytes); atomic.finishWrite(out); }
        catch (IOException error) { if (out != null) atomic.failWrite(out); throw error; }
    }
    private void readForeground() {
        while (!stopping) {
            Process command = null;
            try {
                command = new ProcessBuilder("/system/bin/dumpsys", "activity", "activities").redirectErrorStream(true).start();
                final Process running = command;
                Thread timeout = new Thread(() -> { SystemClock.sleep(1500); if (running.isAlive()) running.destroy(); });
                timeout.setDaemon(true); timeout.start();
                BufferedReader input = new BufferedReader(new InputStreamReader(command.getInputStream()));
                int state = -1, count = 0; String line;
                while ((line = input.readLine()) != null && ++count <= 30000) {
                    if (line.contains("topResumedActivity=") || line.toLowerCase(Locale.ROOT).contains("resumedactivity:")) {
                        if (line.contains("ActivityRecord{")) state = line.contains("com.tencent.mobileqq/") ? 1 : state == 1 ? 1 : 0;
                    }
                }
                foreground = command.waitFor() == 0 ? state : -1;
            } catch (Exception failure) { foreground = -1; }
            finally { if (command != null) command.destroy(); }
            SystemClock.sleep(2000);
        }
    }
    private synchronized void close() {
        if (stopping) return; stopping = true;
        if (instance != null) {
            try { write(new File(instance, "tracing_on"), "0"); write(new File(instance, "events/sched/sched_stat_runtime/enable"), "0"); }
            catch (Exception ignored) {}
        }
        try { if (pipe != null) pipe.close(); } catch (Exception ignored) {}
        if (instance != null) instance.delete();
    }
    private static void cleanup(File owned) {
        if (!owned.getName().startsWith("qqwatch-")) return;
        try { write(new File(owned, "tracing_on"), "0"); write(new File(owned, "events/sched/sched_stat_runtime/enable"), "0"); }
        catch (Exception ignored) {}
        owned.delete();
    }
    static String read(File file) throws IOException {
        try (FileInputStream input = new FileInputStream(file); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int n;
            while ((n = input.read(buffer)) != -1) { if (out.size() + n > CoreSnapshot.MAX_BYTES) throw new IOException("文件过大"); out.write(buffer, 0, n); }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
    private static void write(File file, String value) throws IOException {
        try (FileOutputStream out = new FileOutputStream(file)) { out.write(value.getBytes(StandardCharsets.UTF_8)); }
    }
}
