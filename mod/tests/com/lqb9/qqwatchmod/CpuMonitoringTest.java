package com.lqb9.qqwatchmod;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Regression cases for observed CPU data; uses the JDK, no Android device or dependencies. */
public final class CpuMonitoringTest {
    private static int checks;

    private static void check(boolean condition, String name) {
        checks++;
        if (!condition) throw new AssertionError(name);
    }

    private static void close(double actual, double expected, String name) {
        check(Math.abs(actual - expected) < 0.001, name + ": " + actual);
    }

    private static QqCpuTracker.Reading process(int pid, long start, long ticks) {
        return new QqCpuTracker.Reading(pid, "QQ:" + pid, start, ticks);
    }

    private static ThreadCpuTracker.Reading thread(int pid, int tid, long start, long ticks) {
        return new ThreadCpuTracker.Reading(pid, tid, "QQ", "worker:" + tid, start, ticks, 2);
    }

    private static String stat(String comm, long user, long system, long start, int core) {
        String[] f = new String[40];
        Arrays.fill(f, "0");
        f[0] = "R";
        f[11] = String.valueOf(user);
        f[12] = String.valueOf(system);
        f[19] = String.valueOf(start);
        f[36] = String.valueOf(core);
        return "100 (" + comm + ") " + String.join(" ", f);
    }

    private static void parsing() {
        String text = stat("worker (a) ) b", 100, 20, 900, 3);
        QqCpuTracker.Reading p = QqCpuTracker.Reading.parse(100, "QQ", text);
        check(p.cpuTicks == 120 && p.startTicks == 900, "process stat field offsets");
        ThreadCpuTracker.Reading t = ThreadCpuTracker.Reading.parse(100, 101, "QQ", text);
        check(t.cpuTicks == 120 && t.startTicks == 900 && t.lastCore == 3, "thread stat field offsets");
        check("worker (a) ) b".equals(t.name), "thread name parentheses and spaces");
        boolean threw = false;
        try { ThreadCpuTracker.Reading.parse(1, 2, "QQ", "1 (bad) R 0"); }
        catch (IllegalArgumentException expected) { threw = true; }
        check(threw, "short stat rejected");
    }

    private static void aggregate() {
        QqCpuTracker tracker = new QqCpuTracker();
        List<QqCpuTracker.Reading> initial = Arrays.asList(process(1, 10, 10000), process(2, 20, 20000));
        close(tracker.sample(initial, 1000, 4, 100, true).cpu, -1, "first frame is unknown");
        List<QqCpuTracker.Reading> next = Arrays.asList(process(1, 10, 10100), process(2, 20, 20200));
        QqCpuTracker.Snapshot sum = tracker.sample(next, 2000, 4, 100, true);
        close(sum.cpu, 300, "QQ total is 300%, not 75% or 100%");
        close(sum.processes.get(0).cpu, 200, "multithreaded process may exceed 100%");
        close(sum.processes.get(1).cpu, 100, "one-core process contribution");
        close(tracker.sample(Arrays.asList(process(1, 10, 10200), process(2, 999, 100000)),
                3000, 4, 100, true).cpu, -1, "PID reuse cannot be a CPU spike");
        close(tracker.sample(Arrays.asList(process(1, 10, 10300), process(2, 999, 100100)),
                4000, 4, 100, true).cpu, 200, "sampling recovers after PID reuse");
        close(tracker.sample(Arrays.asList(process(1, 10, 10400)), 5000, 4, 100, true).cpu,
                -1, "process exit resets aggregate interval");
        close(tracker.sample(Arrays.asList(process(1, 10, 10450)), 6000, 4, 100, true).cpu,
                50, "remaining process CPU remains valid");
        close(tracker.sample(Arrays.asList(process(1, 10, 10500)), 7000, 4, 100, false).cpu,
                -1, "partial sampling cannot produce a trustworthy total");
        close(tracker.sample(Arrays.asList(process(1, 10, 99999)), 8000, 4, 100, true).cpu,
                -1, "no stale CPU reused after a read error");
        close(tracker.sample(Arrays.asList(process(1, 10, 1)), 9000, 4, 100, true).cpu,
                -1, "counter rollback rejected");
        close(tracker.sample(Arrays.asList(process(1, 10, 101), process(3, 30, 900000)),
                10000, 4, 100, true).cpu, -1, "new process lifetime CPU not mistaken for interval CPU");
    }

    private static void threads() {
        ThreadCpuTracker tracker = new ThreadCpuTracker();
        tracker.sample(Arrays.asList(thread(1, 10, 10, 1000), thread(1, 11, 20, 2000)), 1000, 100, 0);
        ThreadCpuTracker.Snapshot hot = tracker.sample(Arrays.asList(
                thread(1, 10, 10, 1075), thread(1, 11, 20, 2025)), 2000, 100, 0);
        close(hot.threads.get(0).cpu, 75, "thread CPU measures time rather than thread count");
        check(hot.threads.get(0).reading.tid == 10, "hot threads sorted by measured CPU");
        ThreadCpuTracker.Snapshot reused = tracker.sample(Arrays.asList(
                thread(1, 10, 999, 900000), thread(2, 11, 20, 900000)), 3000, 100, 1);
        close(reused.threads.get(0).cpu, -1, "TID reuse starts fresh");
        close(reused.threads.get(1).cpu, -1, "same TID in another process starts fresh");
        check(reused.errors == 1, "thread read coverage exposed");
        List<ThreadCpuTracker.Reading> many = new ArrayList<ThreadCpuTracker.Reading>();
        for (int i = 0; i < 40; i++) many.add(thread(1, 100 + i, i, i));
        ThreadCpuTracker.Snapshot bounded = tracker.sample(many, 4000, 100, 0);
        check(bounded.scanned == 40 && bounded.threads.size() == 12, "top list bounded, scanned count retained");
    }

    private static void frequency() {
        final java.util.Map<String, String> files = new java.util.HashMap<String, String>();
        String cpu0 = "/sys/devices/system/cpu/cpu0";
        String cpu1 = "/sys/devices/system/cpu/cpu1";
        files.put(cpu0 + "/cpufreq/cpuinfo_cur_freq", "1200000");
        files.put(cpu0 + "/cpufreq/scaling_cur_freq", "1100000");
        files.put(cpu1 + "/cpufreq/scaling_cur_freq", "1100000");
        files.put("/sys/devices/system/cpu/cpu2/online", "0");
        List<CoreFrequency.Core> cores = CoreFrequency.read(4, new CoreFrequency.Reader() {
            public String read(String path) { return files.containsKey(path) ? files.get(path) : ""; }
        });
        check(cores.size() == 4, "frequency coverage retains every configured core");
        check(cores.get(0).khz == 1200000 && cores.get(0).hardwareReading, "hardware frequency preferred");
        check(cores.get(1).khz == 1100000 && !cores.get(1).hardwareReading, "driver fallback marked distinctly");
        check(!cores.get(2).online, "offline core marked");
        check(cores.get(3).khz == -1, "unreadable frequency stays unknown rather than zero");
    }

    private static void decisions() {
        CpuLoadMonitor.Settings immediate = new CpuLoadMonitor.Settings(true, 200, 0, 1, "restart");
        check("record".equals(immediate.action), "stale restart config cannot enable process killing");
        CpuLoadMonitor monitor = new CpuLoadMonitor();
        check(!monitor.sample(-1, 0, immediate, 0).trigger, "initial frame cannot trigger");
        check(!monitor.sample(200, 1000, immediate, 0).trigger, "equal threshold does not trigger");
        check(monitor.sample(201, 2000, immediate, 0).trigger, "strictly above threshold prompts immediately");
        check(!monitor.sample(300, 3000, immediate, 0).trigger, "continuous load prompts once");
        check(!monitor.sample(199, 4000, immediate, 0).trigger, "load below threshold resets episode");
        check(monitor.sample(250, 5000, immediate, 0).trigger, "new high episode prompts again");

        CpuLoadMonitor.Settings sustained = new CpuLoadMonitor.Settings(true, 200, 3, 1, "record");
        monitor.reset();
        monitor.sample(-1, 0, sustained, 0);
        check(!monitor.sample(250, 1000, sustained, 0).trigger, "sustained interval one");
        check(!monitor.sample(250, 2000, sustained, 0).trigger, "sustained interval two");
        check(monitor.sample(250, 3000, sustained, 0).trigger, "sustained duration reached");
        check(!monitor.sample(Double.NaN, 4000, sustained, 0).trigger, "invalid CPU cannot trigger");
        check(!monitor.sample(250, 5000, sustained, 0).trigger, "invalid frame clears accumulated time");
        check("gap".equals(monitor.sample(250, 15000, sustained, 0).state), "freeze gap breaks sustained load");
        check(!monitor.sample(250, 16000, sustained, 0).trigger, "recovery from freeze starts new streak");
        CpuLoadMonitor.Settings changed = new CpuLoadMonitor.Settings(true, 201, 0, 1, "record");
        check("changed".equals(monitor.sample(250, 17000, changed, 0).state), "config change does not reuse old interval");
        CpuLoadMonitor.Settings off = new CpuLoadMonitor.Settings(false, 201, 0, 1, "record");
        check("off".equals(monitor.sample(999, 18000, off, 0).state), "disabled monitoring cannot prompt");
        check(!monitor.sample(999, 19000, changed, 0).trigger, "enable transition skips disabled interval");
        check(!monitor.sample(999, 18000, changed, 0).trigger, "clock rollback rejected");
        check(!monitor.sample(Double.POSITIVE_INFINITY, 20000, changed, 0).trigger, "infinite CPU rejected");
    }

    private static void handlingDuration() {
        CpuLoadMonitor.Settings five = new CpuLoadMonitor.Settings(true, 200, 5, 1, "stop_task");
        CpuLoadMonitor monitor = new CpuLoadMonitor();
        monitor.sample(-1, 0, five, 0);
        for (int sec=1; sec<5; sec++)
            check(!monitor.sample(250, sec*1000L, five, 0).handlingReady(), "no cancellation before second " + sec);
        CpuLoadMonitor.Sample reached = monitor.sample(250, 5000, five, 0);
        check(reached.handlingReady() && reached.trigger, "cancel becomes eligible at exactly five seconds");
        CpuLoadMonitor.Sample later = monitor.sample(250, 6000, five, 0);
        check(later.handlingReady() && !later.trigger, "later samples may retry without repeating alert");
        CpuLoadMonitor.Sample normal = monitor.sample(200, 7000, five, 0);
        check(!normal.handlingReady() && normal.highMs==0, "equal threshold clears handling duration");
        check(!monitor.sample(250, 8000, five, 0).handlingReady(), "next spike does not reuse old duration");
        CpuLoadMonitor.Sample invalid = monitor.sample(-1, 9000, five, 0);
        check(!invalid.handlingReady() && invalid.highMs==0, "invalid sample clears handling duration");
        check(!monitor.sample(250, 10000, five, 0).handlingReady(), "valid sample after invalid begins a new streak");
        CpuLoadMonitor.Sample gap = monitor.sample(250, 20000, five, 0);
        check(!gap.handlingReady() && gap.highMs==0, "sleep gap cannot complete handling duration");
        CpuLoadMonitor.Settings immediate = new CpuLoadMonitor.Settings(true,200,0,1,"stop_task");
        check(!monitor.sample(250,21000,immediate,0).handlingReady(), "duration change discards interval with old config");
        check(monitor.sample(250,22000,immediate,0).handlingReady(), "zero seconds handles the next valid high interval");
        CpuLoadMonitor.Settings off = new CpuLoadMonitor.Settings(false,200,0,1,"stop_task");
        check(!monitor.sample(999,23000,off,0).handlingReady(), "disabled handling is never eligible");
        CpuLoadMonitor.Settings record = new CpuLoadMonitor.Settings(true,200,0,1,"record");
        monitor.sample(250,24000,record,0);
        check(!monitor.sample(250,25000,record,0).handlingReady(), "record action cannot cancel");
        CpuLoadMonitor.Settings twoStep = new CpuLoadMonitor.Settings(true,200,5,2,"stop_task");
        monitor.reset(); monitor.sample(-1,0,twoStep,0);
        check(!monitor.sample(250,2000,twoStep,0).handlingReady(), "two-second interval first sample waits");
        check(!monitor.sample(250,4000,twoStep,0).handlingReady(), "four seconds waits for five-second duration");
        check(monitor.sample(250,6000,twoStep,0).handlingReady(), "first valid sample beyond duration handles");
        check(new CpuLoadMonitor.Settings(true,200,-5,1,"stop_task").durationSeconds==0,"negative duration clamps to zero");
        check(new CpuLoadMonitor.Settings(true,200,4000,1,"stop_task").durationSeconds==3600,"duration upper limit is one hour");
    }

    private static void history() {
        LoadHistory history = new LoadHistory();
        history.add(1000, 300, 1); history.add(2000, 200, 1);
        close(history.snapshot().get(0).cpu, 300, "history preserves load above 100%");
        check(history.snapshot().get(1).connected, "normal consecutive samples connect");
        history.add(2000, 900, 1); history.add(1500, 900, 1);
        check(history.snapshot().size() == 2, "duplicate or old UI refreshes do not add fake samples");
        history.add(3000, Double.NaN, 1); history.add(4000, 100, 1);
        check(history.snapshot().get(2).cpu < 0 && !history.snapshot().get(3).connected,
                "missing data is a gap, not zero or a connecting line");
        history.add(20000, 100, 1);
        check(!history.snapshot().get(4).connected, "long freeze leaves a visible gap");
        List<LoadHistory.Point> saved = history.snapshot();
        for (int i = 1; i <= 100; i++) history.add(20000 + i * 1000, 20, 1);
        check(history.snapshot().size() == 60 && saved.size() == 5, "bounded history and stable UI snapshot");
        check(history.snapshot().get(0).elapsedMs == 61000, "history retains newest measured timestamps");
    }

    public static void main(String[] args) {
        parsing(); aggregate(); threads(); frequency(); decisions(); handlingDuration(); history();
        System.out.println("PASS " + checks + " CPU monitoring regression checks");
    }
}
