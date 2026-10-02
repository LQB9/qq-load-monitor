package com.lqb9.qqwatchmod;

import java.io.*;
import java.lang.reflect.*;
import static com.lqb9.qqwatchmod.ModuleInfo.*;
import io.github.libxposed.api.XposedInterface.Chain;
import io.github.libxposed.api.XposedInterface.Hooker;

/** QQ GIF-specific hook installation; failure leaves the object rule unavailable. */
final class GifHookAdapter {
    private final HookGateway gateway;
    private final java.util.function.Consumer<String> logger;
    private final java.util.Set<Class<?>> gifAdapters=new java.util.HashSet<Class<?>>();
    GifHookAdapter(HookGateway gateway,java.util.function.Consumer<String> logger) {this.gateway=gateway;this.logger=logger;}
    void prepare(Object render) {
        if(!render.getClass().getName().equals(GifTaskRule.RENDER))return;
        synchronized(gifAdapters) {if(!gifAdapters.add(render.getClass()))return;}
        try {
            GifHostAdapter adapter=TaskServices.gif.adapter(render);
            gateway.install(adapter.start,"GIF",new Hooker() {
                public Object intercept(Chain chain) throws Throwable {
                    if(TaskServices.gif.blockStart(chain.getThisObject()))return null;
                    return chain.proceed();
                }
            });
            gateway.install(adapter.recycle,"GIF",new Hooker() {
                public Object intercept(Chain chain) throws Throwable {
                    TaskServices.gif.recycled(chain.getThisObject());
                    return chain.proceed();
                }
            });
            adapter.hooksReady=true;
            logger.accept(TAG+" GIF专项接口=ready ownerFieldType="+adapter.owner.getType().getName()+" stop/start/isRunning/recycle verified; object-scoped");
        } catch(Throwable unavailable) {
            logger.accept(TAG+" GIF专项接口=unavailable "+unavailable.getClass().getSimpleName()+"；保持原任务处理并记录原因");
        }
    }
}

