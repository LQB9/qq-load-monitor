package com.lqb9.qqwatchmod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Process identities and aggregate CPU deltas, independent of Android for regression tests. */
final class QqCpuTracker {
    static final class Reading {
        final int pid;
        final String name;
        final long startTicks;
        final long cpuTicks;

        Reading(int pid, String name, long startTicks, long cpuTicks) {
            this.pid = pid;
            this.name = name;
            this.startTicks = startTicks;
            this.cpuTicks = cpuTicks;
        }

        static Reading parse(int pid, String name, String stat) {
            int end = stat.lastIndexOf(')');
            if (end < 0) throw new IllegalArgumentException("Missing stat comm");
            String[] fields = stat.substring(end + 1).trim().split("\\s+");
            if (fields.length < 20) throw new IllegalArgumentException("Short proc stat");
            long ticks = Long.parseLong(fields[11]) + Long.parseLong(fields[12]);
            long start = Long.parseLong(fields[19]);
            if (ticks < 0 || start < 0) throw new IllegalArgumentException("Negative proc stat");
            return new Reading(pid, name, start, ticks);
        }
    }

    static final class Detail {
        final Reading reading;
        final double cpu;
        Detail(Reading reading, double cpu) { this.reading = reading; this.cpu = cpu; }
    }

    static final class Snapshot {
        final double cpu;
        final int cores;
        final long elapsedMs;
        final List<Detail> processes;
        final String note;
        Snapshot(double cpu, int cores, long elapsedMs, List<Detail> processes, String note) {
            this.cpu = cpu;
            this.cores = cores;
            this.elapsedMs = elapsedMs;
            this.processes = Collections.unmodifiableList(processes);
            this.note = note;
        }
    }

    private Map<Integer, Reading> previous = new HashMap<Integer, Reading>();
    private long previousMs = -1;
    private int previousCores;

    void reset() {
        previous.clear();
        previousMs = -1;
        previousCores = 0;
    }

    Snapshot sample(List<Reading> readings, long nowMs, int cores, long hz, boolean complete) {
        List<Detail> details = new ArrayList<Detail>();
        if (!complete || readings.isEmpty() || cores < 1 || hz < 1 || nowMs < 0) {
            for (Reading r : readings) details.add(new Detail(r, -1));
            reset();
            return new Snapshot(-1, cores, nowMs, details, "QQ 进程采样不完整，暂停负载判定");
        }
        long dt = nowMs - previousMs;
        boolean valid = previousMs >= 0 && dt >= 500 && previousCores == cores
                && previous.size() == readings.size();
        Map<Integer, Reading> current = new HashMap<Integer, Reading>();
        double deltaTicks = 0;
        for (Reading reading : readings) {
            Reading old = previous.get(reading.pid);
            long delta = old == null ? -1 : reading.cpuTicks - old.cpuTicks;
            boolean matches = old != null && old.startTicks == reading.startTicks && delta >= 0;
            if (!matches || current.containsKey(reading.pid)) valid = false;
            double percent = matches && dt >= 500
                    ? delta * 100000.0d / hz / dt : -1;
            details.add(new Detail(reading, percent));
            if (matches) deltaTicks += delta;
            current.put(reading.pid, reading);
        }
        previous = current;
        previousMs = nowMs;
        previousCores = cores;
        Collections.sort(details, new Comparator<Detail>() {
            public int compare(Detail a, Detail b) { return Double.compare(b.cpu, a.cpu); }
        });
        double cpu = valid ? Math.min(cores * 100d, deltaTicks * 100000.0d / hz / dt) : -1;
        return new Snapshot(cpu, cores, nowMs, details,
                valid ? "" : "首次采样或 QQ 进程发生变化，重新建立采样");
    }
}
