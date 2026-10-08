package com.lqb9.qqwatchmod;

import java.io.IOException;
import java.io.InputStream;

/** Parses ASCII numeric trace fields directly; thread names still come from UTF8 proc stat. */
final class RuntimeStream {
    interface Consumer { void batch(); void accept(int tid, int core, long runtime); }
    private static final byte[] EVENT = ascii(": sched_stat_runtime:"), PID = ascii(" pid="), RUNTIME = ascii(" runtime=");
    private static byte[] ascii(String value) { return value.getBytes(java.nio.charset.StandardCharsets.US_ASCII); }
    static void read(InputStream input, Consumer consumer) throws IOException {
        byte[] buffer = new byte[65536]; int pending = 0; boolean discard = false;
        Parser parser = new Parser();
        for (;;) {
            int count = input.read(buffer, pending, buffer.length - pending);
            if (count < 0) {
                if (pending > 0 && !discard && parser.parse(buffer, 0, pending)) {
                    consumer.batch(); consumer.accept(parser.tid, parser.core, parser.runtime);
                }
                return;
            }
            if (count == 0) continue;
            consumer.batch(); int used = pending + count, start = 0;
            for (int end = pending; end < used; end++) if (buffer[end] == '\n') {
                if (!discard && parser.parse(buffer, start, end)) consumer.accept(parser.tid, parser.core, parser.runtime);
                discard = false; start = end + 1;
            }
            pending = used - start;
            if (pending == buffer.length) { pending = 0; discard = true; }
            else if (pending > 0 && start > 0) System.arraycopy(buffer, start, buffer, 0, pending);
        }
    }
    private static final class Parser {
        int tid, core; long runtime;
        boolean parse(byte[] data, int start, int end) {
            int event = find(data, start, end, EVENT); if (event < 0) return false;
            int left = -1, right = -1;
            for (int i = start; i < event; i++) if (data[i] == '[') left = i;
            if (left < 0) return false;
            for (int i = left + 1; i < event; i++) if (data[i] == ']') { right = i; break; }
            int pid = find(data, event, end, PID), ns = find(data, event, end, RUNTIME);
            if (right < 0 || pid < 0 || ns < 0) return false;
            try {
                long c = number(data, left + 1, right, false), id = number(data, pid + PID.length, end, true), time = number(data, ns + RUNTIME.length, end, true);
                if (c > 127 || id <= 0 || id > Integer.MAX_VALUE || time > 1000000000L) return false;
                tid = (int)id; core = (int)c; runtime = time; return true;
            } catch (IllegalArgumentException invalid) { return false; }
        }
    }
    private static int find(byte[] data, int start, int end, byte[] word) {
        outer: for (int i = start; i <= end - word.length; i++) {
            if (data[i] != word[0]) continue;
            for (int n = 1; n < word.length; n++) if (data[i+n] != word[n]) continue outer;
            return i;
        }
        return -1;
    }
    private static long number(byte[] data, int start, int end, boolean suffix) {
        long value = 0; int digits = 0;
        for (int i = start; i < end; i++) {
            int d = data[i] - '0';
            if (d < 0 || d > 9) { if (suffix) break; throw new IllegalArgumentException("Invalid runtime core"); }
            if (value > (Long.MAX_VALUE - d) / 10) throw new IllegalArgumentException("Runtime overflow");
            value = value * 10 + d; digits++;
        }
        if (digits == 0) throw new IllegalArgumentException("Missing runtime number");
        return value;
    }
    private RuntimeStream() {}
}
