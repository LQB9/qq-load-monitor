package com.lqb9.qqwatchmod;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

/** Versioned cumulative scheduler counters, not last-CPU estimates. */
final class CoreSnapshot {
    static final int ALL = 255, MAX_BYTES = 512 * 1024;
    final String session, note;
    final int uid, foreground;
    final long sequence, elapsedMs, monoNs;
    final boolean valid;
    final long[] cores;
    final List<Counter> threads;
    static final class Counter {
        final int pid, tid;
        final long pidStart, tidStart;
        final String name;
        final long[] cores;
        Counter(int pid, long pidStart, int tid, long tidStart, String name, long[] cores) {
            this.pid = pid; this.pidStart = pidStart; this.tid = tid; this.tidStart = tidStart;
            this.name = name; this.cores = Arrays.copyOf(cores, 8);
        }
        String key() { return pid + ":" + pidStart + ":" + tid + ":" + tidStart; }
    }
    CoreSnapshot(String session, int uid, long sequence, long elapsedMs, long monoNs,
                 boolean valid, int foreground, String note, long[] cores, List<Counter> threads) {
        this.session = session; this.uid = uid; this.sequence = sequence; this.elapsedMs = elapsedMs;
        this.monoNs = monoNs; this.valid = valid; this.foreground = foreground; this.note = note;
        this.cores = Arrays.copyOf(cores, 8); this.threads = Collections.unmodifiableList(new ArrayList<Counter>(threads));
    }
    String encode() {
        StringBuilder out = new StringBuilder("QQCORE2 ").append(session).append(' ').append(uid).append(' ')
                .append(sequence).append(' ').append(elapsedMs).append(' ').append(monoNs).append(' ')
                .append(valid ? 1 : 0).append(' ').append(foreground).append(' ').append(encoded(note)).append('\n');
        out.append('C'); for (long value : cores) out.append(' ').append(value); out.append('\n');
        for (Counter thread : threads) {
            out.append("T ").append(thread.pid).append(' ').append(thread.pidStart).append(' ').append(thread.tid)
                    .append(' ').append(thread.tidStart).append(' ').append(encoded(thread.name));
            for (long value : thread.cores) out.append(' ').append(value); out.append('\n');
        }
        return out.toString();
    }
    static CoreSnapshot decode(String text) throws IOException {
        try {
            if (text.length() > MAX_BYTES || !text.endsWith("\n")) throw new IllegalArgumentException("incomplete snapshot");
            String[] lines = text.split("\n");
            if (lines.length < 2 || lines.length > 4098) throw new IllegalArgumentException("snapshot size");
            String[] h = lines[0].split(" ");
            if (h.length != 9 || !"QQCORE2".equals(h[0]) || !h[1].matches("[a-zA-Z0-9-]{1,80}")) throw new IllegalArgumentException("header");
            int uid = Integer.parseInt(h[2]), fg = Integer.parseInt(h[7]), state = Integer.parseInt(h[6]);
            long seq = nonnegative(h[3]), elapsed = nonnegative(h[4]), mono = nonnegative(h[5]);
            if (uid < 0 || fg < -1 || fg > 1 || state < 0 || state > 1) throw new IllegalArgumentException("header values");
            String[] c = lines[1].split(" ");
            if (c.length != 9 || !"C".equals(c[0])) throw new IllegalArgumentException("core counters");
            long[] cores = new long[8]; for (int i = 0; i < 8; i++) cores[i] = nonnegative(c[i + 1]);
            List<Counter> threads = new ArrayList<Counter>(); java.util.HashSet<String> identities = new java.util.HashSet<String>();
            for (int i = 2; i < lines.length; i++) {
                String[] t = lines[i].split(" ");
                if (t.length != 14 || !"T".equals(t[0])) throw new IllegalArgumentException("thread counters");
                int pid = Integer.parseInt(t[1]), tid = Integer.parseInt(t[3]);
                if (pid <= 0 || tid <= 0) throw new IllegalArgumentException("thread identity");
                long[] times = new long[8];
                for (int core = 0; core < 8; core++) times[core] = nonnegative(t[core + 6]);
                Counter counter = new Counter(pid, nonnegative(t[2]), tid, nonnegative(t[4]), decoded(t[5]), times);
                if (!identities.add(counter.key())) throw new IllegalArgumentException("duplicate thread");
                threads.add(counter);
            }
            return new CoreSnapshot(h[1], uid, seq, elapsed, mono, state == 1, fg, decoded(h[8]), cores, threads);
        } catch (RuntimeException error) { throw new IOException("核心采集数据无效：" + error.getMessage(), error); }
    }
    private static long nonnegative(String value) { long n = Long.parseLong(value); if (n < 0) throw new IllegalArgumentException("negative counter"); return n; }
    private static String encoded(String text) { return text.isEmpty() ? "-" : Base64.getUrlEncoder().withoutPadding().encodeToString(text.getBytes(StandardCharsets.UTF_8)); }
    private static String decoded(String text) { return "-".equals(text) ? "" : new String(Base64.getUrlDecoder().decode(text), StandardCharsets.UTF_8); }
    static String selection(int mask) {
        StringBuilder out = new StringBuilder();
        for (int core = 0; core < 8; core++) if ((mask & (1 << core)) != 0) out.append(out.length() == 0 ? "" : ",").append(core);
        return out.length() == 0 ? "未选择" : out.toString();
    }
}
