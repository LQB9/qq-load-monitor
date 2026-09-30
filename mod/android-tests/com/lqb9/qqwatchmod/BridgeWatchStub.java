package com.lqb9.qqwatchmod;

/** Test-only model adapter. Never compiled by production build.ps1. */
final class WatchModule {
    static final boolean isMain = true;
    static CpuLoadMonitor.Settings readSettings() { return new CpuLoadMonitor.Settings(true,200,0,1,"stop_task",15); }
}
