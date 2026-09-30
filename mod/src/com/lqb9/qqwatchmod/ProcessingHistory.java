package com.lqb9.qqwatchmod;

import java.util.*;

/** Independent in-memory status table. Never included in the export log. */
final class ProcessingHistory {
    static final class Row {
        final String id, name, reason; final long timeMs, pidStart, tidStart;
        final int pid, tid, mask; final double load, threadLoad; final String state, detail;
        Row(String id, long timeMs, CoreSnapshot.Counter t, int mask, double load, double threadLoad,
            String reason, String state, String detail) {
            this.id = id; this.timeMs = timeMs; this.name = t.name; this.pid = t.pid; this.tid = t.tid;
            this.pidStart = t.pidStart; this.tidStart = t.tidStart; this.mask = mask; this.load = load;
            this.threadLoad = threadLoad; this.reason = reason; this.state = state; this.detail = detail;
        }
        Row state(String state, String detail) {
            return new Row(id, timeMs, new CoreSnapshot.Counter(pid, pidStart, tid, tidStart, name, new long[8]),
                    mask, load, threadLoad, reason, state, detail);
        }
    }
    private final LinkedList<Row> rows = new LinkedList<Row>();
    private long revision;
    synchronized void add(Row row) { rows.addFirst(row); while (rows.size() > 100) rows.removeLast(); revision++; }
    synchronized void update(String id, String state, String detail) {
        for (int i = 0; i < rows.size(); i++) if (rows.get(i).id.equals(id)) {
            rows.set(i, rows.get(i).state(state, detail)); revision++; return;
        }
    }
    synchronized Row find(String id) { for (Row r : rows) if (r.id.equals(id)) return r; return null; }
    synchronized List<Row> snapshot() { return Collections.unmodifiableList(new ArrayList<Row>(rows)); }
    synchronized long revision() { return revision; }
}
