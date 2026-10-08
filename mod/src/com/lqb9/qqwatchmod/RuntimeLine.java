package com.lqb9.qqwatchmod;

/** Parses only sched_stat_runtime trace records; CPU is the event's actual CPU. */
final class RuntimeLine {
    final int tid, core;
    final long runtime;
    RuntimeLine(int tid, int core, long runtime) { this.tid = tid; this.core = core; this.runtime = runtime; }
    static RuntimeLine parse(String line) {
        int event = line.indexOf(": sched_stat_runtime:");
        if (event < 0) return null;
        try {
            int left = line.lastIndexOf('[', event), right = line.indexOf(']', left);
            int pid = line.indexOf(" pid=", event), runtime = line.indexOf(" runtime=", event);
            if (left < 0 || right < 0 || pid < 0 || runtime < 0) return null;
            long parsedCore = number(line, left + 1, right);
            if (parsedCore > 127) return null;
            int core = (int) parsedCore;
            long id = number(line, pid + 5), ns = number(line, runtime + 9);
            if (core < 0 || core > 127 || id <= 0 || id > Integer.MAX_VALUE || ns < 0 || ns > 1000000000L) return null;
            return new RuntimeLine((int) id, core, ns);
        } catch (RuntimeException ignored) { return null; }
    }
    private static long number(String line, int start) {
        int end = start; while (end < line.length() && Character.isDigit(line.charAt(end))) end++;
        return number(line, start, end);
    }
    private static long number(String line, int start, int end) {
        if (start == end) throw new NumberFormatException("Missing runtime number");
        long value = 0;
        for (int i = start; i < end; i++) {
            int digit = line.charAt(i) - '0';
            if (digit < 0 || digit > 9 || value > (Long.MAX_VALUE - digit) / 10)
                throw new NumberFormatException("Invalid runtime number");
            value = value * 10 + digit;
        }
        return value;
    }
}
