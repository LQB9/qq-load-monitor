package com.lqb9.qqwatchmod;

import io.github.libxposed.api.XposedInterface;
import java.io.File;
import java.io.FileInputStream;

/** Composition root; repeated package callbacks do not reinstall the process runtime. */
final class WatchRuntime implements AutoCloseable {
    private static volatile WatchRuntime active;
    private final XposedInterface engine;
    private final HookGateway gateway;
    private final TaskObservationHooks taskHooks;
    private final PanelEntryHooks panelHooks;
    private final MonitorEngine monitor;
    private boolean booted;
    WatchRuntime(XposedInterface engine) {
        this.engine=engine;gateway=new HookGateway(engine);
        taskHooks=new TaskObservationHooks(gateway,this::say);
        panelHooks=new PanelEntryHooks(gateway,this::say);
        monitor=new MonitorEngine(this::say,WatchPanel::isVisible,PanelEntryHooks::showLoadAlert);
    }
    synchronized void boot() {
        if(booted)return;
        MonitorState.selfName=processName();
        MonitorState.isMain=ModuleInfo.QQ_PACKAGE.equals(MonitorState.selfName);
        if(MonitorState.isMain)WatchLog.start(new File(ModuleInfo.SW,"watchdog-logs"),"QQ负载监控 v"+ModuleInfo.VERSION
                +" pid="+android.os.Process.myPid()+" process="+MonitorState.selfName
                +" Android="+android.os.Build.VERSION.RELEASE+" SDK="+android.os.Build.VERSION.SDK_INT
                +" device="+android.os.Build.MANUFACTURER+"/"+android.os.Build.MODEL+" 单核满载=100% action=stop_task");
        active=this;
        say(ModuleInfo.TAG+" packageReady "+MonitorState.selfName);
        taskHooks.install();
        if(MonitorState.isMain){panelHooks.install();monitor.start();}
        booted=true;
        say(ModuleInfo.TAG+" runtime ready hooks="+gateway.count()+" main="+MonitorState.isMain+" rules="+TaskServices.rules.names());
    }
    static void requestSample() {WatchRuntime runtime=active;if(runtime!=null)runtime.monitor.requestSample();}
    void say(String message) {
        if(MonitorState.isMain)WatchLog.record("INFO",message);
        try {engine.log(android.util.Log.INFO,ModuleInfo.TAG,message);}catch(Throwable ignored){}
    }
    public synchronized void close() {
        monitor.stop();
        if(TaskServices.gif.pausedCount()>0){say(ModuleInfo.TAG+" runtime close deferred: GIF pause leases remain");return;}
        panelHooks.close();TaskBridge.detach();gateway.close();
        if(active==this)active=null;
    }
    private static String processName() {
        try(FileInputStream input=new FileInputStream("/proc/self/cmdline")) {
            byte[] bytes=new byte[256];int n=input.read(bytes),end=0;
            while(end<n && bytes[end]!=0)end++;
            return n<=0?"?":new String(bytes,0,end,"UTF-8");
        }catch(Exception unavailable){return "?";}
    }
}
