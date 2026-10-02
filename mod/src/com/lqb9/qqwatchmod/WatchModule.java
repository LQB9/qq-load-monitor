package com.lqb9.qqwatchmod;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam;
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam;

/** API 102 entry only; process runtime owns hooks, sampling and shared services. */
public class WatchModule extends XposedModule {
    private WatchRuntime runtime;
    @Override public void onModuleLoaded(ModuleLoadedParam param) {
        try {log(android.util.Log.INFO,ModuleInfo.TAG,ModuleInfo.TAG+" loaded in "+param.getProcessName());}
        catch(Throwable ignored){}
    }
    @Override public synchronized void onPackageReady(PackageReadyParam param) {
        if(!ModuleInfo.QQ_PACKAGE.equals(param.getPackageName()) || runtime!=null)return;
        try {runtime=new WatchRuntime(this);runtime.boot();}
        catch(Throwable failed) {
            try {log(android.util.Log.ERROR,ModuleInfo.TAG,ModuleInfo.TAG+" packageReady ERR "+failed);}catch(Throwable ignored){}
            if(runtime!=null)try {runtime.close();}catch(Throwable unavailable){runtime.say(ModuleInfo.TAG+" teardown ERR "+unavailable);}
        }
    }
}
