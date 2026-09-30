package com.lqb9.qqwatchmod;

/** QQ CPU load. One fully occupied CPU core equals 100%. No process-killing action. */
final class CpuLoadMonitor {
    static final class Settings {
        final boolean enabled;
        final int threshold;
        final int durationSeconds;
        final int intervalSeconds;
        final String action;
        final int coreMask;

        Settings(boolean enabled, int threshold, int durationSeconds, int intervalSeconds,
                 String action) {
            this(enabled, threshold, durationSeconds, intervalSeconds, action, 255);
        }
        Settings(boolean enabled, int threshold, int durationSeconds, int intervalSeconds,
                 String action, int coreMask) {
            this.enabled = enabled;
            this.threshold = clamp(threshold, 1, 10000);
            this.durationSeconds = clamp(durationSeconds, 0, 3600);
            this.intervalSeconds = clamp(intervalSeconds, 1, 3600);
            this.action = "stop_task".equals(action) ? "stop_task" : "record";
            this.coreMask = coreMask > 0 && coreMask <= 255 ? coreMask : 255;
        }

        boolean sameAs(Settings other) {
            return other != null && enabled == other.enabled && threshold == other.threshold
                    && durationSeconds == other.durationSeconds
                    && intervalSeconds == other.intervalSeconds
                    && action.equals(other.action) && coreMask == other.coreMask;
        }

        private static int clamp(int value, int low, int high) {
            return Math.max(low, Math.min(value, high));
        }
    }

    static final class Sample {
        final Settings settings;
        final long elapsedMs;
        final double cpu;
        final long highMs;
        final boolean trigger;
        final String state;

        Sample(Settings settings, long elapsedMs, double cpu, long highMs,
               boolean trigger, String state) {
            this.settings = settings;
            this.elapsedMs = elapsedMs;
            this.cpu = cpu;
            this.highMs = highMs;
            this.trigger = trigger;
            this.state = state;
        }
    }

    private long previousElapsedMs = -1;
    private Settings previousSettings;
    private long highMs;
    private boolean reported;

    void reset() {
        previousElapsedMs = -1;
        previousSettings = null;
        clearStreak();
    }

    private void clearStreak() {
        highMs = 0;
        reported = false;
    }

    Sample sample(double cpu, long nowMs, Settings settings, long guardEndMs) {
        long intervalStart = previousElapsedMs;
        boolean changed = !settings.sameAs(previousSettings);
        previousSettings = settings;
        if (nowMs < 0) {
            reset();
            return new Sample(settings, nowMs, -1, 0, false, "invalid");
        }
        previousElapsedMs = nowMs;
        if (changed) clearStreak();

        long dt = nowMs - intervalStart;
        boolean seeded = intervalStart >= 0;
        boolean valid = seeded && dt >= 500 && !Double.isNaN(cpu)
                && !Double.isInfinite(cpu) && cpu >= 0 && cpu <= 10000;
        // A frozen/sleeping process cannot establish uninterrupted high load across the gap.
        boolean gap = valid && dt > settings.intervalSeconds * 3000L;
        if (!valid || gap) cpu = -1;

        String state;
        if (!settings.enabled) state = "off";
        else if (nowMs < guardEndMs) state = "guard";
        else if (gap) state = "gap";
        else if (!valid) state = seeded ? "invalid" : "warming";
        else if (changed) state = "changed";
        else state = "ready";
        if (!"ready".equals(state)) {
            clearStreak();
            return new Sample(settings, nowMs, cpu, 0, false, state);
        }

        if (cpu <= settings.threshold) {
            clearStreak();
            return new Sample(settings, nowMs, cpu, 0, false, "normal");
        }
        highMs += Math.max(0, nowMs - Math.max(intervalStart, guardEndMs));
        boolean trigger = !reported && highMs >= settings.durationSeconds * 1000L;
        if (trigger) reported = true;
        return new Sample(settings, nowMs, cpu, highMs, trigger, reported ? "reported" : "high");
    }
}
