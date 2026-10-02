package com.lqb9.qqwatchmod;

import java.io.*;
import java.lang.reflect.*;
import static com.lqb9.qqwatchmod.ModuleInfo.*;

/** Pure settings format and migration; thresholds and duration keep their previous semantics. */
final class SettingsCodec {
    static CpuLoadMonitor.Settings decode(String text, boolean enabled, int legacyInterval) {
        java.util.Properties p = new java.util.Properties();
        try {
            p.load(new java.io.StringReader(text));
        } catch (Exception ignored) {
            return new CpuLoadMonitor.Settings(false, DEF_CPU, DEF_DURATION, DEF_IV, "record");
        }
        boolean fresh = text.length() == 0;
        return new CpuLoadMonitor.Settings(enabled,
                settingInt(p, "cpu", DEF_CPU), settingInt(p, "duration", DEF_DURATION),
                settingInt(p, "interval", fresh ? legacyInterval : DEF_IV),
                "stop_task", settingInt(p, "cores", 255),Boolean.parseBoolean(p.getProperty("gifRule","true").trim()),
                Boolean.parseBoolean(p.getProperty("threadRule","true").trim()),settingInt(p,"threadCpu",80),
                settingInt(p,"threadDuration",10),Boolean.parseBoolean(p.getProperty("threadHandle","false").trim()),
                Boolean.parseBoolean(p.getProperty("totalRule","true").trim()),Boolean.parseBoolean(p.getProperty("totalHandle","true").trim()));
    }
    private static int settingInt(java.util.Properties p, String key, int fallback) {
        try { return Integer.parseInt(p.getProperty(key, String.valueOf(fallback)).trim()); }
        catch (Exception ignored) { return fallback; }
    }
    static String encode(CpuLoadMonitor.Settings settings) {
        String data = "cpu=" + settings.threshold + "\nduration=" + settings.durationSeconds
                + "\ninterval=" + settings.intervalSeconds
                + "\ncores=" + settings.coreMask
                + "\naction=" + settings.action + "\ngifRule="+settings.gifRule
                + "\nthreadRule="+settings.threadRule+"\nthreadCpu="+settings.threadThreshold
                + "\nthreadDuration="+settings.threadDuration+"\nthreadHandle="+settings.threadHandle
                + "\ntotalRule="+settings.totalRule+"\ntotalHandle="+settings.totalHandle+"\n";
        return data;
    }
}

