package com.lqb9.qqwatchmod;

import java.util.Map;

/** CPU reads bracket an immutable trace counter capture; only their lower bound can prove missing time. */
final class CoreTimeWindow {
    static final class Frame {
        final Map<String, Long> before, after;
        final long runtimeNs;
        Frame(Map<String, Long> before, Map<String, Long> after, long runtimeNs) {
            this.before = new java.util.HashMap<String, Long>(before);
            this.after = new java.util.HashMap<String, Long>(after); this.runtimeNs = runtimeNs;
        }
    }
    static final class Result {
        final boolean comparable, rollback, mismatch;
        final long lowerTicks, upperTicks, lowerNs, upperNs, runtimeNs, allowedNs;
        Result(boolean comparable, boolean rollback, long lower, long upper, long runtime, long hz) {
            this.comparable = comparable; this.rollback = rollback; lowerTicks = lower; upperTicks = upper;
            lowerNs = comparable ? lower * 1000000000L / hz : -1;
            upperNs = comparable ? upper * 1000000000L / hz : -1;
            runtimeNs = runtime; allowedNs = (long)(runtime * 1.25 + 80000000L);
            mismatch = comparable && lowerNs > runtime * 1.25 + 80000000L;
        }
    }
    static Result compare(Frame previous, Frame current, long hz) {
        if (hz <= 0) throw new IllegalArgumentException("clock ticks");
        long runtime = previous == null ? current.runtimeNs : current.runtimeNs - previous.runtimeNs;
        boolean same = previous != null && current.before.keySet().equals(current.after.keySet())
                && current.before.keySet().equals(previous.before.keySet())
                && current.before.keySet().equals(previous.after.keySet());
        long lower = 0, upper = 0; boolean rollback = runtime < 0;
        if (same) for (String identity : current.before.keySet()) {
            long nowBefore = current.before.get(identity), nowAfter = current.after.get(identity);
            long oldBefore = previous.before.get(identity), oldAfter = previous.after.get(identity);
            if (nowAfter < nowBefore || oldAfter < oldBefore || nowBefore < oldAfter) rollback = true;
            lower += Math.max(0, nowBefore - oldAfter);
            upper += Math.max(0, nowAfter - oldBefore);
        }
        return new Result(same, rollback, lower, upper, runtime, hz);
    }
}
