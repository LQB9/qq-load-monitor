package com.lqb9.qqwatchmod;

import android.app.Instrumentation;
import io.github.libxposed.api.XposedInterface;
import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

/** Real Android lifecycle/file APIs with a simulated hook backend; never hooks QQ or platform execution. */
final class RuntimeCheck {
    static void threadSettings(Instrumentation instrumentation)throws Exception {
        instrumentation.runOnMainSync(()->{
            try {
                int[] widths={360,300,260};float[] fonts={1f,1.3f,1.6f};
                for(int i=0;i<widths.length;i++){
                    android.content.res.Configuration config=new android.content.res.Configuration(instrumentation.getTargetContext().getResources().getConfiguration());config.fontScale=fonts[i];
                    android.content.Context context=instrumentation.getTargetContext().createConfigurationContext(config);
                    CpuLoadMonitor.Settings initial=new CpuLoadMonitor.Settings(true,285,3,2,"stop_task",255,true);
                    ThreadRuleSettingsView view=new ThreadRuleSettingsView(context,initial);
                    if(!view.ruleEnabled() || view.handlingEnabled() || view.threshold()!=80 || view.seconds()!=10)throw new AssertionError("thread UI defaults");
                    view.action.performClick();if(!view.handlingEnabled())throw new AssertionError("thread mode click");
                    view.toggle.performClick();if(view.ruleEnabled() || view.action.isEnabled())throw new AssertionError("thread disable click");
                    view.toggle.performClick();view.action.performClick();view.cpu.setText("85");view.duration.setText("15");view.normalize();
                    if(view.threshold()!=85 || view.seconds()!=15 || view.handlingEnabled())throw new AssertionError("thread input or mode restore");
                    int width=DashboardView.dp(context,widths[i]);view.measure(android.view.View.MeasureSpec.makeMeasureSpec(width,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(0,android.view.View.MeasureSpec.UNSPECIFIED));
                    view.layout(0,0,width,view.getMeasuredHeight());
                    android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(width,view.getMeasuredHeight(),android.graphics.Bitmap.Config.ARGB_8888);
                    android.graphics.Canvas canvas=new android.graphics.Canvas(bitmap);canvas.drawColor(android.graphics.Color.WHITE);view.draw(canvas);
                    File out=new File(instrumentation.getTargetContext().getFilesDir(),"thread-settings-"+widths[i]+".png");
                    try(java.io.FileOutputStream stream=new java.io.FileOutputStream(out)){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,stream);}bitmap.recycle();
                }
            }catch(Exception failure){throw new RuntimeException(failure);}
        });
    }
    private static final class HookBackend {
        int registrations,releases;
        Method failInstall,failRelease;
        boolean failedRelease;
        final Map<Method,Integer> counts=new HashMap<>();
        final Map<Method,XposedInterface.Hooker> hooks=new HashMap<>();
        final XposedInterface engine=(XposedInterface)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{XposedInterface.class},(p,m,args) -> {
            if(m.getName().equals("hook")) {
                final Method method=(Method)args[0];
                return Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{XposedInterface.HookBuilder.class},(builder,b,values) -> {
                    if(b.getName().startsWith("set"))return builder;
                    if(b.getName().equals("intercept")) {
                        if(method.equals(failInstall))throw new IllegalStateException("simulated hook failure");
                        registrations++;counts.put(method,counts.getOrDefault(method,0)+1);
                        hooks.put(method,(XposedInterface.Hooker)values[0]);
                        return Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{XposedInterface.HookHandle.class},(handle,h,v) -> {
                            if(h.getName().equals("unhook")) {
                                if(method.equals(failRelease) && !failedRelease){failedRelease=true;throw new IllegalStateException("simulated unhook failure");}
                                releases++;return null;
                            }
                            if(h.getName().equals("getExecutable"))return method;
                            if(h.getName().equals("getId"))return "fixture";
                            return null;
                        });
                    }
                    return null;
                });
            }
            if(m.getName().equals("log"))return null;
            if(m.getName().equals("getApiVersion"))return 102;
            if(m.getReturnType()==long.class)return 0L;
            if(m.getReturnType()==int.class)return 0;
            return null;
        });
    }
    static void executorHooks(Instrumentation instrumentation)throws Exception {
        HookBackend backend=new HookBackend();HookGateway gateway=new HookGateway(backend.engine);
        instrumentation.runOnMainSync(() -> new TaskObservationHooks(gateway,message -> {}).install());
        Method before=java.util.concurrent.ThreadPoolExecutor.class.getDeclaredMethod("beforeExecute",Thread.class,Runnable.class);
        Method after=java.util.concurrent.ThreadPoolExecutor.class.getDeclaredMethod("afterExecute",Runnable.class,Throwable.class);
        java.util.concurrent.CountDownLatch ended=new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<Throwable> failure=new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<String> ownership=new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.ThreadPoolExecutor pool=new java.util.concurrent.ThreadPoolExecutor(1,1,1,java.util.concurrent.TimeUnit.SECONDS,new java.util.concurrent.LinkedBlockingQueue<Runnable>()) {
            protected void beforeExecute(Thread thread,Runnable task){
                try {invoke(backend,before,this,new Object[]{thread,task});}catch(Throwable error){failure.set(error);}
            }
            protected void afterExecute(Runnable task,Throwable error){
                try {invoke(backend,after,this,new Object[]{task,error});}catch(Throwable failed){failure.set(failed);}finally {ended.countDown();}
            }
        };
        try {
            java.util.concurrent.Future<?> future=pool.submit(() -> {
                int tid=android.os.Process.myTid();TaskRegistry.Entry task=TaskServices.tasks.current(tid,Long.MAX_VALUE);
                if(task==null || task.thread!=Thread.currentThread())throw new AssertionError("actual beforeExecute task not captured");
                String text=TaskServices.executors.execution(tid,Thread.currentThread(),task.task);
                if(!text.contains("execution-confirmed") || !TaskServices.executors.describe(tid,Thread.currentThread()).contains("workerState=active"))throw new AssertionError(text);
                ownership.set(text);
                TaskExecutions.Scope scope=TaskServices.executions.begin(task.task,tid,Thread.currentThread(),System.nanoTime(),android.os.Debug.threadCpuTimeNanos(),"actual-pool-fixture");
                if(!scope.executor.equals(text))throw new AssertionError("execution report lost actual pool");
                TaskServices.executions.end(scope,System.nanoTime(),android.os.Debug.threadCpuTimeNanos());
            });
            future.get(5,java.util.concurrent.TimeUnit.SECONDS);
            if(!ended.await(5,java.util.concurrent.TimeUnit.SECONDS) || failure.get()!=null || ownership.get()==null)throw new AssertionError("real pool hook lifecycle failed",failure.get());
            WatchLog.record("INFO","实际线程池夹具归属="+ownership.get()+"；QQ未参与");
        } finally {pool.shutdown();pool.awaitTermination(5,java.util.concurrent.TimeUnit.SECONDS);instrumentation.runOnMainSync(TaskBridge::detach);gateway.close();}
    }
    private static Object invoke(HookBackend backend,Method method,Object receiver,Object[] arguments)throws Throwable {
        XposedInterface.Chain chain=(XposedInterface.Chain)Proxy.newProxyInstance(RuntimeCheck.class.getClassLoader(),new Class<?>[]{XposedInterface.Chain.class},(proxy,m,args) -> {
            if(m.getName().equals("getThisObject"))return receiver;
            if(m.getName().equals("getArg"))return arguments[(Integer)args[0]];
            if(m.getName().equals("getArgs"))return arguments;
            if(m.getName().equals("getExecutable"))return method;
            if(m.getName().equals("proceed"))return null;
            return null;
        });
        return backend.hooks.get(method).intercept(chain);
    }
    static void qq925Api(Instrumentation instrumentation)throws Exception {
        File apk=new File(instrumentation.getTargetContext().getFilesDir(),"qq925-reference.apk");
        if(!apk.isFile() || apk.canWrite())throw new AssertionError("real QQ reference APK missing or mutable");
        ClassLoader parent=RuntimeCheck.class.getClassLoader();
        dalvik.system.DexClassLoader loader=new dalvik.system.DexClassLoader(apk.getAbsolutePath(),instrumentation.getTargetContext().getCodeCacheDir().getAbsolutePath(),null,parent) {
            protected synchronized Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException {
                if(!name.startsWith("com.tencent.libra.extension.gif."))return super.loadClass(name,resolve);
                Class<?> type=findLoadedClass(name);if(type==null)type=findClass(name);if(resolve)resolveClass(type);return type;
            }
        };
        Class<?> render=Class.forName(GifTaskRule.RENDER,false,loader);
        GifHostAdapter adapter=new GifHostAdapter(render);
        if(render.getClassLoader()!=loader || adapter.stop.getDeclaringClass().getClassLoader()!=loader
                || !adapter.owner.getDeclaringClass().getName().equals("com.tencent.libra.extension.gif.SafeRunnable")
                || !render.getMethod("run").getDeclaringClass().getName().equals("com.tencent.libra.extension.gif.SafeRunnable"))throw new AssertionError("real QQ925 adapter resolved fixture or wrong interfaces");
        WatchLog.record("INFO","QQ9.2.25真实APK反射匹配：SafeRunnable owner / run 与 GifDrawable start/stop/isRunning/recycle；未初始化QQ对象，未调用原生解码");
    }
    static void qqReferenceApi(Instrumentation instrumentation)throws Exception {
        File apk=new File(instrumentation.getTargetContext().getFilesDir(),"qq-reference.apk");
        if(!apk.isFile() || apk.canWrite())throw new AssertionError("reference missing or writable");
        android.content.pm.PackageInfo info=instrumentation.getTargetContext().getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(),0);
        if(info==null || !ModuleInfo.QQ_PACKAGE.equals(info.packageName))throw new AssertionError("not a QQ archive");
        long code=android.os.Build.VERSION.SDK_INT>=28?info.getLongVersionCode():info.versionCode;
        String profile=QqTaskAdapter.configure(info.packageName,info.versionName,code);
        if(!profile.contains("五版接口已核对"))throw new AssertionError("exact version not in profile: "+profile);
        dalvik.system.DexClassLoader loader=new dalvik.system.DexClassLoader(apk.getAbsolutePath(),instrumentation.getTargetContext().getCodeCacheDir().getAbsolutePath(),null,RuntimeCheck.class.getClassLoader()) {
            protected synchronized Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException {
                if(!name.startsWith("com.tencent.") && !name.startsWith("mqq."))return super.loadClass(name,resolve);
                Class<?> type=findLoadedClass(name);if(type==null)type=findClass(name);if(resolve)resolveClass(type);return type;
            }
        };
        Class<?> render=Class.forName(GifTaskRule.RENDER,false,loader),job=Class.forName(QqTaskAdapter.JOB,false,loader);
        GifHostAdapter gif=new GifHostAdapter(render);QqTaskAdapter.JobContract binding=new QqTaskAdapter.JobContract(job);
        if(render.getClassLoader()!=loader || gif.stop.getDeclaringClass().getClassLoader()!=loader || job.getClassLoader()!=loader
                || binding.body.getDeclaringClass()!=job || !binding.body.getName().equals("mJob") || job.getSuperclass()!=java.lang.ref.WeakReference.class)
            throw new AssertionError("reference resolved fixture or incorrect contracts");
        WatchLog.record("INFO","真实APK "+info.versionName+" vc="+code+" GIF公共接口与Job业务字段反射核验通过；未初始化QQ对象/JNI，未替换已安装QQ");
    }
    static void jobHooks(Instrumentation instrumentation)throws Exception {
        HookBackend backend=new HookBackend();HookGateway gateway=new HookGateway(backend.engine);
        instrumentation.runOnMainSync(()->new TaskObservationHooks(gateway,message->{}).install());
        QqTaskAdapter.configure(ModuleInfo.QQ_PACKAGE,"9.3.65",16240);
        Method before=java.util.concurrent.ThreadPoolExecutor.class.getDeclaredMethod("beforeExecute",Thread.class,Runnable.class);
        Method after=java.util.concurrent.ThreadPoolExecutor.class.getDeclaredMethod("afterExecute",Runnable.class,Throwable.class);
        Method run=java.util.concurrent.FutureTask.class.getDeclaredMethod("run");
        java.util.concurrent.CountDownLatch active=new java.util.concurrent.CountDownLatch(1),returned=new java.util.concurrent.CountDownLatch(1),next=new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<TaskRegistry.Entry> actual=new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<Throwable> failure=new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.FutureTask<Void> future=new java.util.concurrent.FutureTask<Void>(()->{
            TaskRegistry.Entry entry=TaskServices.tasks.current(android.os.Process.myTid(),Long.MAX_VALUE);actual.set(entry);active.countDown();
            try {new java.util.concurrent.CountDownLatch(1).await();}catch(InterruptedException accepted){}return null;
        });
        com.tencent.mobileqq.app.Job job=new com.tencent.mobileqq.app.Job(future);
        job.runObserver=business->{
            XposedInterface.Chain chain=(XposedInterface.Chain)Proxy.newProxyInstance(RuntimeCheck.class.getClassLoader(),new Class<?>[]{XposedInterface.Chain.class},(p,m,args)->{
                if(m.getName().equals("getThisObject"))return future;
                if(m.getName().equals("getExecutable"))return run;
                if(m.getName().equals("proceed")){future.run();return null;}return null;
            });
            try{backend.hooks.get(run).intercept(chain);}catch(Throwable error){failure.set(error);}finally{returned.countDown();}
        };
        java.util.concurrent.ThreadPoolExecutor pool=new java.util.concurrent.ThreadPoolExecutor(1,1,1,java.util.concurrent.TimeUnit.SECONDS,new java.util.concurrent.LinkedBlockingQueue<Runnable>()) {
            protected void beforeExecute(Thread thread,Runnable task){try{invoke(backend,before,this,new Object[]{thread,task});}catch(Throwable error){failure.set(error);}}
            protected void afterExecute(Runnable task,Throwable error){try{invoke(backend,after,this,new Object[]{task,error});}catch(Throwable failed){failure.set(failed);}}
        };
        try {
            pool.execute(job);if(!active.await(5,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("Job inner Future not active",failure.get());
            TaskRegistry.Entry entry=actual.get();
            if(entry==null || entry.task!=future || entry.parent==null || entry.parent.task!=job)throw new AssertionError("production hook missed real inner business");
            CpuLoadMonitor.Settings settings=new CpuLoadMonitor.Settings(true,200,0,1,"stop_task",255,false);
            HandlingDecision tooNew=TaskServices.rules.apply(new HandlingRequest("job-old-window",entry.tid,entry.startNs-1,System.nanoTime(),90,90,settings));
            if(!tooNew.code.equals("TASK_WINDOW_MISMATCH") || future.isCancelled())throw new AssertionError("outer time transferred to inner");
            com.tencent.mobileqq.app.Job.$redirector_=new com.tencent.mobileqq.qfix.redirect.IPatchRedirector(){};
            if(!TaskServices.tasks.request(entry).contains("热补丁") || future.isCancelled())throw new AssertionError("hotfix guard bypassed");
            com.tencent.mobileqq.app.Job.$redirector_=null;
            HandlingDecision stop=TaskServices.rules.apply(new HandlingRequest("job-exact",entry.tid,entry.startNs+1,System.nanoTime(),90,90,settings,null,null));
            if(!stop.state.equals("待确认") || !future.isCancelled() || stop.task!=entry)throw new AssertionError("real inner Future not cancelled");
            if(!returned.await(5,java.util.concurrent.TimeUnit.SECONDS) || !entry.ended)throw new AssertionError("cancel marker falsely treated as return");
            pool.execute(next::countDown);if(!next.await(5,java.util.concurrent.TimeUnit.SECONDS) || pool.isShutdown() || failure.get()!=null)throw new AssertionError("worker/pool failed after one business stop",failure.get());
            WatchLog.record("INFO","QQ Job隔离夹具：实际生产Future入口挂钩、旧窗口拒绝、热补丁拒绝、单业务取消并返回、原线程池继续执行下一任务通过；负载未造假为真实QQ故障");
        }finally{com.tencent.mobileqq.app.Job.$redirector_=null;future.cancel(true);pool.shutdown();pool.awaitTermination(5,java.util.concurrent.TimeUnit.SECONDS);QqTaskAdapter.configure("","unknown",0);instrumentation.runOnMainSync(TaskBridge::detach);gateway.close();}
    }
    static void gatewayAndRuntime(Instrumentation instrumentation)throws Exception {
        HookBackend backend=new HookBackend();HookGateway gateway=new HookGateway(backend.engine);
        Method one=String.class.getMethod("length"),two=String.class.getMethod("isEmpty");
        XposedInterface.Hooker pass=chain -> chain.proceed();
        gateway.install(one,"fixture",pass);gateway.install(String.class.getMethod("length"),"fixture",pass);
        if(backend.registrations!=1 || gateway.count()!=1)throw new AssertionError("equal-method hooks duplicated");
        backend.failInstall=two;
        try {gateway.install(two,"fixture",pass);throw new AssertionError("failure hidden");}catch(IllegalStateException expected){}
        if(gateway.count()!=1)throw new AssertionError("failed hook falsely owned");
        backend.failInstall=null;gateway.install(two,"fixture",pass);
        backend.failRelease=one;
        try {gateway.close();throw new AssertionError("unhook failure hidden");}catch(IllegalStateException expected){}
        if(gateway.count()!=1 || backend.releases!=1)throw new AssertionError("other hook release stopped or failed hook forgotten");
        gateway.close();gateway.close();
        if(gateway.count()!=0 || backend.releases!=2)throw new AssertionError("release retry/idempotence failed");
        try {gateway.install(one,"fixture",pass);throw new AssertionError("closed registry accepted new hook");}catch(IllegalStateException expected){}
        HookBackend runtimeHooks=new HookBackend();WatchRuntime runtime=new WatchRuntime(runtimeHooks.engine);
        instrumentation.runOnMainSync(runtime::boot);
        int before=runtimeHooks.registrations;
        instrumentation.runOnMainSync(runtime::boot);
        if(before==0 || runtimeHooks.registrations!=before || MonitorState.isMain)throw new AssertionError("runtime did not deduplicate or treated fixture as QQ main");
        for(Thread thread:Thread.getAllStackTraces().keySet())if(thread.getName().equals("qqwatch-dog"))throw new AssertionError("non-QQ/main process started sampler");
        instrumentation.runOnMainSync(runtime::close);
        if(runtimeHooks.releases!=runtimeHooks.registrations)throw new AssertionError("runtime did not release owned hook handles");
    }
    static void settings(Instrumentation instrumentation)throws Exception {
        File folder=new File(instrumentation.getTargetContext().getFilesDir(),"architecture-config");
        SettingsStore store=new SettingsStore(folder);
        if(!store.cfgSet(ModuleInfo.F_ON,null) || !store.cfgSet(ModuleInfo.F_LOAD,null))throw new AssertionError("fixture reset failed");
        if(!store.cfgSet(ModuleInfo.F_IV,"7") || store.read().intervalSeconds!=7 || store.on())throw new AssertionError("fresh migration/enable mismatch");
        if(!store.cfgSet(ModuleInfo.F_ON,"1"))throw new AssertionError("enable write failed");
        CpuLoadMonitor.Settings value=new CpuLoadMonitor.Settings(true,285,3,2,"stop_task",255,false);
        if(!store.save(value) || !new SettingsStore(folder).read().sameAs(value))throw new AssertionError("actual atomic settings save/reopen failed");
        if(new File(folder,ModuleInfo.F_LOAD+".new").exists() || new File(folder,ModuleInfo.F_LOAD+".bak").exists())throw new AssertionError("atomic write left an intermediate file");
        if(!store.cfgSet(ModuleInfo.F_LOAD,"cpu=285\nduration=3\ninterval=2\ncores=255\naction=stop_task") || !store.read().gifRule)throw new AssertionError("pre-GIF settings default lost");
        if(!store.cfgSet(ModuleInfo.F_ON,null) || store.read().enabled)throw new AssertionError("toggle file not respected");
        for(String name:SettingsStore.LEGACY)if(!store.cfgSet(name,"1"))throw new AssertionError("legacy fixture write failed");
        if(store.legacyCount()!=5 || store.legacyPurge()!=5 || store.legacyLeftover()!=null)throw new AssertionError("legacy whitelist cleanup changed");
        if(store.cfg(ModuleInfo.F_LOAD).isEmpty())throw new AssertionError("legacy cleanup touched live settings");
    }
}
