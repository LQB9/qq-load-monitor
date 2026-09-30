package com.lqb9.qqwatchmod;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Frequency is separate from CPU utilization and is never used to fabricate occupancy. */
final class CoreFrequency {
    interface Reader { String read(String path); }
    static final class Core {
        final int id;
        final boolean online;
        final long khz;
        final boolean hardwareReading;
        Core(int id, boolean online, long khz, boolean hardwareReading) {
            this.id = id;
            this.online = online;
            this.khz = khz;
            this.hardwareReading = hardwareReading;
        }
    }

    static List<Core> read(int count) {
        return read(count, new Reader() {
            public String read(String path) { return text(path); }
        });
    }

    static List<Core> read(int count, Reader reader) {
        List<Core> result = new ArrayList<Core>();
        for (int core = 0; core < Math.min(count, 128); core++) {
            String dir = "/sys/devices/system/cpu/cpu" + core;
            String onlineText = reader.read(dir + "/online");
            boolean online = !"0".equals(onlineText);
            long khz = number(reader, dir + "/cpufreq/cpuinfo_cur_freq");
            boolean hardware = khz > 0;
            if (!hardware) khz = number(reader, dir + "/cpufreq/scaling_cur_freq");
            result.add(new Core(core, online, khz, hardware));
        }
        return Collections.unmodifiableList(result);
    }

    private static long number(Reader reader, String path) {
        try { return Long.parseLong(reader.read(path)); } catch (Exception ignored) { return -1; }
    }

    private static String text(String path) {
        try {
            byte[] bytes = new byte[128];
            int n;
            try (FileInputStream input = new FileInputStream(new File(path))) { n = input.read(bytes); }
            return n < 1 ? "" : new String(bytes, 0, n, "UTF-8").trim();
        } catch (Exception ignored) { return ""; }
    }
}
