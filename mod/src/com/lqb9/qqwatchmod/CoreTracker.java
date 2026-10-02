package com.lqb9.qqwatchmod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Differences selected real per-core counters. Partial/stale data never triggers handling. */
final class CoreTracker {
    static final class Detail {
        final CoreSnapshot.Counter thread;
        final double cpu;
        final double allCpu;
        Detail(CoreSnapshot.Counter thread, double cpu) { this(thread,cpu,cpu); }
        Detail(CoreSnapshot.Counter thread, double cpu, double allCpu) { this.thread = thread; this.cpu = cpu; this.allCpu=allCpu; }
    }
    static final class Result {
        final double cpu;
        final double[] cores;
        final CoreSnapshot snapshot;
        final long windowStartNs;
        final List<Detail> threads;
        final String note;
        Result(double cpu, double[] cores, CoreSnapshot snapshot, long start, List<Detail> threads, String note) {
            this.cpu = cpu; this.cores = cores; this.snapshot = snapshot; this.windowStartNs = start;
            this.threads = Collections.unmodifiableList(threads); this.note = note;
        }
    }
    private CoreSnapshot previous;
    private int previousMask;
    void reset() { previous = null; }
    Result sample(CoreSnapshot now, int mask, int expectedUid, long elapsedMs, int interval) {
        if (now == null || now.uid != expectedUid || !now.valid || mask <= 0 || mask > 255
                || now.elapsedMs > elapsedMs + 1000 || elapsedMs - now.elapsedMs > Math.max(3000L, interval * 3000L)) {
            String note = now == null ? "精确核心采集未启用，暂停处理" : now.uid != expectedUid ? "采集 UID 不匹配，暂停处理"
                    : !now.valid ? now.note : "精确采集数据过期或配置无效，暂停处理";
            reset(); return invalid(now, note);
        }
        CoreSnapshot old = previous; previous = now;
        boolean changed = previousMask != mask; previousMask = mask;
        if (old == null || changed || !old.session.equals(now.session)) return invalid(now, "建立核心采样基线");
        if (now.sequence == old.sequence) { previous = old; return null; }
        long dt = now.monoNs - old.monoNs;
        if (now.sequence < old.sequence || dt < 500000000L || dt > interval * 3000000000L
                || now.elapsedMs - old.elapsedMs > interval * 3000L) return invalid(now, "核心采样发生间断");
        double[] cores = new double[8]; double sum = 0;
        for (int core = 0; core < 8; core++) {
            long delta = now.cores[core] - old.cores[core];
            if (delta < 0 || delta > dt * 1.1) return invalid(now, "核心计数回退或不完整");
            cores[core] = delta * 100d / dt;
            if ((mask & (1 << core)) != 0) sum += cores[core];
        }
        Map<String, CoreSnapshot.Counter> baseline = new HashMap<String, CoreSnapshot.Counter>();
        for (CoreSnapshot.Counter thread : old.threads) baseline.put(thread.key(), thread);
        List<Detail> hot = new ArrayList<Detail>();
        for (CoreSnapshot.Counter thread : now.threads) {
            CoreSnapshot.Counter before = baseline.get(thread.key()); long delta = 0, allDelta=0;
            for (int core = 0; core < 8; core++) {
                long value = thread.cores[core] - (before == null ? 0 : before.cores[core]);
                if (value < 0 || value > dt * 1.1) return invalid(now, "线程计数回退或不完整");
                allDelta += value;
                if ((mask & (1 << core)) != 0) delta += value;
            }
            if (allDelta > dt * 1.1) return invalid(now, "线程计数不完整");
            if (delta > 0) hot.add(new Detail(thread, delta * 100d / dt, allDelta * 100d / dt));
        }
        Collections.sort(hot, (a, b) -> Double.compare(b.cpu, a.cpu));
        return new Result(sum, cores, now, old.monoNs, hot, "调度实际 CPU 时间 · 仅 QQ");
    }
    private Result invalid(CoreSnapshot data, String note) {
        double[] unknown = new double[8]; java.util.Arrays.fill(unknown, -1);
        return new Result(-1, unknown, data, 0, new ArrayList<Detail>(), note);
    }
}
