package com.lqb9.qqwatchmod;

import java.util.Locale;

/** Pure action eligibility and target selection; no I/O or task mutation. */
final class LoadPolicy {
    static boolean ready(CoreTracker.Result data,CpuLoadMonitor.Settings settings,CpuLoadMonitor.Sample sample,int uid) {
        return detected(data,settings,sample,uid) && sample.handlingReady();
    }
    static boolean detected(CoreTracker.Result data,CpuLoadMonitor.Settings settings,CpuLoadMonitor.Sample sample,int uid) {
        return data!=null && data.cpu>settings.threshold && settings.enabled && settings.totalRule
                && sample!=null && sample.settings.sameAs(settings) && sample.detectedReady() && sample.cpu==data.cpu
                && data.snapshot!=null && data.snapshot.uid==uid && !data.threads.isEmpty();
    }
    static CoreTracker.Detail target(CoreTracker.Result data) {
        for(CoreTracker.Detail candidate:data.threads)
            if(candidate.thread.tid!=candidate.thread.pid && !protectedName(candidate.thread.name))return candidate;
        return data.threads.get(0);
    }
    static boolean protectedName(String name) {
        return name.startsWith("qqwatch-") || name.equals("RenderThread") || name.toLowerCase(Locale.ROOT).startsWith("binder:")
                || name.contains("Signal Catcher") || name.contains("Jit thread") || name.contains("HeapTaskDaemon")
                || name.contains("Finalizer") || name.contains("ReferenceQueue");
    }
    static String reason(CoreTracker.Result data,CpuLoadMonitor.Settings settings,CpuLoadMonitor.Sample sample) {
        String reason=data.snapshot.foreground==1?"QQ 前台超限":data.snapshot.foreground==0?"QQ 后台超限":"QQ 前后台未知";
        if(settings.durationSeconds>0)reason+=" · 持续"+sample.highMs/1000+"秒，要求"+settings.durationSeconds+"秒";
        return reason;
    }
    static String unavailable(CoreSnapshot.Counter t,int foreground,boolean channel) {
        return t.tid==t.pid || protectedName(t.name)?"主线程或系统线程受到保护，未处理"
                :foreground<0?"前后台未确认，暂停处理":!channel?"目标控制通道不可用":"";
    }
}
