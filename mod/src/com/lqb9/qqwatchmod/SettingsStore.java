package com.lqb9.qqwatchmod;

import java.io.*;
import java.lang.reflect.*;
import static com.lqb9.qqwatchmod.ModuleInfo.*;

/** Android atomic-file persistence, isolated from hooks, the sampling loop and UI. */
final class SettingsStore {
    private final File directory;
    private final java.util.function.Consumer<String> errors;
    SettingsStore(File directory) {this(directory,message -> {});}
    SettingsStore(File directory,java.util.function.Consumer<String> errors) {this.directory=directory;this.errors=errors;}
    CpuLoadMonitor.Settings read() {
        String text=cfg(F_LOAD);
        return SettingsCodec.decode(text,on(),text.length()==0?cfgInt(F_IV,DEF_IV):DEF_IV);
    }
    boolean save(CpuLoadMonitor.Settings settings) {return cfgSet(F_LOAD,SettingsCodec.encode(settings));}
    static final String[] LEGACY = {"killleak.on", "killleak.scope", "killleak.base", "killleak.k",
            "killleak.limit"};

    boolean on() {
        try {
            return new File(directory, F_ON).exists();
        } catch (Throwable t) {
            return false;
        }
    }
    String legacyLeftover() {
        try {
            for (int i = 0; i < LEGACY.length; i++) {
                if (new File(directory, LEGACY[i]).exists()) return LEGACY[i];
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
    int legacyCount() {
        int n = 0;
        try {
            for (int i = 0; i < LEGACY.length; i++) {
                if (new File(directory, LEGACY[i]).exists()) n++;
            }
        } catch (Throwable ignored) {
        }
        return n;
    }
    int legacyPurge() {
        int n = 0;
        for (int i = 0; i < LEGACY.length; i++) {
            try {
                File f = new File(directory, LEGACY[i]);
                if (f.exists() && f.delete()) n++;
            } catch (Throwable ignored) {
            }
        }
        return n;
    }
    String cfg(String name) {
        try {
            File f = new File(directory, name);
            if (!f.exists()) return "";
            byte[] b = new byte[256];
            int n;
            try (FileInputStream in = new FileInputStream(f)) { n = in.read(b); }
            return n <= 0 ? "" : new String(b, 0, n, "UTF-8").trim();
        } catch (Throwable t) {
            return "";
        }
    }
    int cfgInt(String name, int def) {
        try {
            String s = cfg(name);
            if (s.length() == 0) return def;
            return Integer.parseInt(s);
        } catch (Throwable t) {
            return def;
        }
    }
    boolean cfgSet(String name, String v) {
        android.util.AtomicFile atomic = null;
        FileOutputStream out = null;
        try {
            File f = new File(directory, name);
            if (v == null) {
                return !f.exists() || f.delete();
            }
            File dir = f.getParentFile();
            if (!dir.isDirectory() && !dir.mkdirs()) return false;
            atomic = new android.util.AtomicFile(f);
            out = atomic.startWrite();
            out.write(v.getBytes("UTF-8"));
            atomic.finishWrite(out);
            return v.trim().equals(cfg(name));
        } catch (Throwable t) {
            if (atomic != null && out != null) atomic.failWrite(out);
            errors.accept("配置保存失败：" + name);
            return false;
        }
    }
}

