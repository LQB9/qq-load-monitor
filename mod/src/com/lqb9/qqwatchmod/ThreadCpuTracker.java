package com.lqb9.qqwatchmod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Per-thread CPU time deltas. Last CPU is a location hint, not a residency measurement. */
final class ThreadCpuTracker {
    static final class Reading {
        final int pid;
        final int tid;
        final String processName;
        final String name;
        final long startTicks;
        final long cpuTicks;
        final int lastCore;

        Reading(int pid, int tid, String processName, String name,
                long startTicks, long cpuTicks, int lastCore) {
            this.pid = pid;
            this.tid = tid;
            this.processName = processName;
            this.name = name;
            this.startTicks = startTicks;
            this.cpuTicks = cpuTicks;
            this.lastCore = lastCore;
        }

        static Reading parse(int pid, int tid, String processName, String stat) {
            ProcStat parsed = ProcStat.parse(stat, true);
            return new Reading(pid, tid, processName, parsed.name, parsed.startTicks, parsed.cpuTicks, parsed.core);
        }
    }

    static final class Detail {
        final Reading reading;
        final double cpu;
        Detail(Reading reading, double cpu) { this.reading = reading; this.cpu = cpu; }
    }

    static final class Snapshot {
        final List<Detail> threads;
        final int scanned;
        final int errors;
        Snapshot(List<Detail> threads, int scanned, int errors) {
            this.threads = Collections.unmodifiableList(threads);
            this.scanned = scanned;
            this.errors = errors;
        }
    }

    private Map<String, Reading> previous = new HashMap<String, Reading>();
    private long previousMs = -1;

    void reset() { previous.clear(); previousMs = -1; }

    Snapshot sample(List<Reading> readings, long nowMs, long hz, int errors) {
        long dt = nowMs - previousMs;
        boolean valid = previousMs >= 0 && dt >= 500 && hz > 0;
        Map<String, Reading> current = new HashMap<String, Reading>();
        List<Detail> details = new ArrayList<Detail>();
        for (Reading reading : readings) {
            String key = reading.pid + ":" + reading.tid;
            Reading old = previous.get(key);
            double percent = -1;
            if (valid && old != null && old.startTicks == reading.startTicks
                    && reading.cpuTicks >= old.cpuTicks) {
                percent = Math.min(100d, (reading.cpuTicks - old.cpuTicks) * 100000.0d / hz / dt);
            }
            details.add(new Detail(reading, percent));
            current.put(key, reading);
        }
        previous = current;
        previousMs = nowMs;
        Collections.sort(details, new Comparator<Detail>() {
            public int compare(Detail a, Detail b) { return Double.compare(b.cpu, a.cpu); }
        });
        // 展示最忙的 12 个线程；完整计数用于显示覆盖率，不参与负载判定。
        return new Snapshot(new ArrayList<Detail>(details.subList(0, Math.min(12, details.size()))),
                readings.size(), errors);
    }
}
