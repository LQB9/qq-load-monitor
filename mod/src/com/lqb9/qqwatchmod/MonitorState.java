package com.lqb9.qqwatchmod;


/** Process-local sample, bounded event history and presentation state. */
final class MonitorState {
    static volatile String selfName = "?";
    static volatile boolean isMain = false;
    static volatile LoadSnapshot latest;
    static final LoadHistory loadHistory = new LoadHistory();
    static volatile long startedAt = 0;
    static volatile int alertCount = 0;
    static volatile String[] events = new String[0];

    static synchronized void push(String s) {
        WatchLog.record("EVENT", s);
        String[] old = events;
        int n = Math.min(old.length + 1, 20);
        String[] neo = new String[n];
        neo[0] = time() + "  " + s;
        for (int i = 1; i < n; i++) neo[i] = old[i - 1];
        events = neo;
    }
    static String time() {
        try {
            return new java.text.SimpleDateFormat("HH:mm:ss")
                    .format(new java.util.Date());
        } catch (Throwable t) {
            return "--:--:--";
        }
    }
}

