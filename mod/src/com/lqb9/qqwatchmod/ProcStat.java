package com.lqb9.qqwatchmod;

/** Reads the required /proc stat fields without a regex or an array of all fields. */
final class ProcStat {
    final String name;
    final long cpuTicks, startTicks;
    final int core;
    private ProcStat(String name, long cpuTicks, long startTicks, int core) {
        this.name = name; this.cpuTicks = cpuTicks; this.startTicks = startTicks; this.core = core;
    }
    static ProcStat parse(String text, boolean thread) {
        int left = text.indexOf('('), right = text.lastIndexOf(')');
        if (left < 0 || right <= left) throw new IllegalArgumentException("Missing stat comm");
        int pos = right + 1, last = thread ? 36 : 19, core = -1;
        long user = 0, system = 0, start = 0;
        for (int field = 0; field <= last; field++) {
            while (pos < text.length() && space(text.charAt(pos))) pos++;
            int begin = pos;
            while (pos < text.length() && !space(text.charAt(pos))) pos++;
            if (begin == pos) throw new IllegalArgumentException("Short stat");
            if (field == 11) user = nonnegative(text, begin, pos);
            else if (field == 12) system = nonnegative(text, begin, pos);
            else if (field == 19) start = nonnegative(text, begin, pos);
            else if (field == 36) {
                long value = nonnegative(text, begin, pos);
                if (value > Integer.MAX_VALUE) throw new IllegalArgumentException("Invalid stat core");
                core = (int) value;
            }
        }
        if (user > Long.MAX_VALUE - system) throw new IllegalArgumentException("Stat CPU overflow");
        return new ProcStat(thread ? text.substring(left + 1, right) : "", user + system, start, core);
    }
    private static boolean space(char c) { return c == ' ' || (c >= '\t' && c <= '\r'); }
    private static long nonnegative(String text, int begin, int end) {
        if (text.charAt(begin) == '+') begin++;
        if (begin == end) throw new IllegalArgumentException("Missing stat number");
        long value = 0;
        for (int i = begin; i < end; i++) {
            int digit = text.charAt(i) - '0';
            if (digit < 0 || digit > 9 || value > (Long.MAX_VALUE - digit) / 10)
                throw new IllegalArgumentException("Invalid stat number");
            value = value * 10 + digit;
        }
        return value;
    }
}
