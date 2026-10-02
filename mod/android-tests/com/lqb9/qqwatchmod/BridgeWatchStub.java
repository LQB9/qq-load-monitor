package com.lqb9.qqwatchmod;

/** Test-only settings source. Production WatchSettings always reads the existing QQ files. */
final class WatchSettings {
    static volatile CpuLoadMonitor.Settings settings=new CpuLoadMonitor.Settings(true,200,0,1,"stop_task",15);
    static CpuLoadMonitor.Settings read(){return settings;}
    static boolean on(){return settings.enabled;}
    static boolean save(CpuLoadMonitor.Settings value){settings=value;return true;}
    static boolean setEnabled(boolean enabled){return true;}
    static String legacyLeftover(){return null;}
    static int legacyCount(){return 0;}
    static int legacyPurge(){return 0;}
}
