package com.lqb9.qqwatchmod;


/** Shared module identity and defaults; no Android or Xposed initialization. */
final class ModuleInfo {
    static final String TAG="[QQWATCH]", VERSION="1.32", QQ_PACKAGE="com.tencent.mobileqq";
    static final String SW="/sdcard/Android/data/com.tencent.mobileqq/files";
    static final String F_ON="watchdog.on", F_LOAD="watchdog.load", F_IV="watchdog.iv";
    static final int DEF_CPU=200, DEF_DURATION=0, DEF_IV=1;
    static final long GUARD_MS=0L;
    private ModuleInfo() {}
}
