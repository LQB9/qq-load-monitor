package com.lqb9.qqwatchmod;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Bounded measured history. Unavailable samples and long pauses break the line. */
final class LoadHistory {
    static final int CAPACITY = 60;
    static final class Point {
        final long elapsedMs;
        final double cpu;
        final boolean connected;
        Point(long elapsedMs, double cpu, boolean connected) {
            this.elapsedMs = elapsedMs;
            this.cpu = cpu;
            this.connected = connected;
        }
    }

    private final ArrayDeque<Point> points = new ArrayDeque<Point>();
    synchronized void clear() { points.clear(); }

    synchronized void add(long elapsedMs, double cpu, int intervalSeconds) {
        Point last = points.peekLast();
        if (last != null && elapsedMs <= last.elapsedMs) return;
        if (Double.isNaN(cpu) || Double.isInfinite(cpu) || cpu < 0) cpu = -1;
        boolean connected = last != null && last.cpu >= 0 && cpu >= 0
                && elapsedMs - last.elapsedMs <= Math.max(3000L, intervalSeconds * 3000L);
        points.addLast(new Point(elapsedMs, cpu, connected));
        while (points.size() > CAPACITY) points.removeFirst();
    }

    synchronized List<Point> snapshot() {
        return Collections.unmodifiableList(new ArrayList<Point>(points));
    }
}
