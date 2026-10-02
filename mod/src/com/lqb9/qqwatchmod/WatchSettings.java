package com.lqb9.qqwatchmod;

import java.io.File;

/** Process-local access to the existing QQ configuration files. */
final class WatchSettings {
    private static final SettingsStore store=new SettingsStore(new File(ModuleInfo.SW),MonitorState::push);
    static CpuLoadMonitor.Settings read() {return store.read();}
    static boolean save(CpuLoadMonitor.Settings settings) {return store.save(settings);}
    static boolean on() {return store.on();}
    static boolean setEnabled(boolean enabled) {return store.cfgSet(ModuleInfo.F_ON,enabled?"1":null);}
    static String legacyLeftover() {return store.legacyLeftover();}
    static int legacyCount() {return store.legacyCount();}
    static int legacyPurge() {return store.legacyPurge();}
}
