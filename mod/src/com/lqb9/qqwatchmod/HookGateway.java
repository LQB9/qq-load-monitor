package com.lqb9.qqwatchmod;

import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

/** Owns real API 102 handles; equal methods share one hook. */
final class HookGateway implements AutoCloseable {
    private final XposedInterface engine;
    private final Map<Method,XposedInterface.HookHandle> installed=new LinkedHashMap<>();
    private boolean closed;
    HookGateway(XposedInterface engine) {this.engine=engine;}
    synchronized void install(Method method,String label,XposedInterface.Hooker hooker) {
        if(closed)throw new IllegalStateException("Hook gateway closed");
        if(installed.containsKey(method))return;
        XposedInterface.HookBuilder builder=engine.hook(method);
        if(label.startsWith("WATCH:"))builder.setId(label);
        XposedInterface.HookHandle handle=builder.intercept(hooker);
        if(handle==null)throw new IllegalStateException("Framework returned no hook handle");
        installed.put(method,handle);
    }
    synchronized int count() {return installed.size();}
    public synchronized void close() {
        closed=true;
        RuntimeException failure=null;
        for(java.util.Iterator<Map.Entry<Method,XposedInterface.HookHandle>> it=installed.entrySet().iterator();it.hasNext();) {
            try {it.next().getValue().unhook();it.remove();}
            catch(RuntimeException error) {failure=error;}
        }
        if(failure!=null)throw failure;
    }
}
