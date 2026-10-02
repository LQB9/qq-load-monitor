package com.lqb9.qqwatchmod;

import java.io.*;
import java.lang.reflect.*;
import static com.lqb9.qqwatchmod.ModuleInfo.*;
import io.github.libxposed.api.XposedInterface.Chain;
import io.github.libxposed.api.XposedInterface.Hooker;

/** Executor/Handler execution observation and bounded dynamic business hooks. */
final class TaskObservationHooks {
    private final HookGateway gateway;
    private final java.util.function.Consumer<String> logger;
    private final GifHookAdapter gifAdapter;
    private boolean installed;
    TaskObservationHooks(HookGateway gateway,java.util.function.Consumer<String> logger) {
        this.gateway=gateway;this.logger=logger;gifAdapter=new GifHookAdapter(gateway,logger);
    }
    void install() {
        if (installed) return;
        installed=true;
        final ThreadLocal<TaskRegistry.Entry> poolTask = new ThreadLocal<TaskRegistry.Entry>();
        final ThreadLocal<ExecutorRegistry.Binding> poolBinding = new ThreadLocal<ExecutorRegistry.Binding>();
        String owners = "";
        // Lifetime ownership supplies an exact Java-thread/TID map for diagnostics
        // even between executor tasks. It is never used as a cancellable task.
        for (String source : new String[]{"Thread.run", "Looper.loop"}) {
            try {
                Method entry = source.equals("Thread.run") ? Thread.class.getDeclaredMethod("run")
                        : android.os.Looper.class.getDeclaredMethod("loop");
                gateway.install(entry,"TASK",new Hooker() {
                    public Object intercept(Chain chain) throws Throwable {
                        TaskServices.tasks.diagnostics.observe(android.os.Process.myTid(), Thread.currentThread(), source);
                        return chain.proceed();
                    }
                });
                owners += source + "=ready ";
            } catch (Throwable unavailable) { owners += source + "=" + unavailable.getClass().getSimpleName() + " "; }
        }
        try {
            gateway.install(android.app.Application.class.getDeclaredMethod("attach", android.content.Context.class),"TASK",new Hooker() {
                public Object intercept(Chain chain) throws Throwable {
                    Object result = chain.proceed();
                    try { TaskBridge.attach((android.app.Application) chain.getThisObject()); } catch (Throwable failure) { logger.accept(TAG + " control attach " + failure); }
                    return result;
                }
            });
            try {
                Class<?> activityThread = Class.forName("android.app.ActivityThread");
                android.app.Application app = (android.app.Application) activityThread.getDeclaredMethod("currentApplication").invoke(null);
                if (app != null) TaskBridge.attach(app);
            } catch (Throwable ignored) {}
            for (String method : new String[]{"run", "runAndReset"}) {
                gateway.install(java.util.concurrent.FutureTask.class.getDeclaredMethod(method),"TASK",new Hooker() {
                    public Object intercept(Chain chain) throws Throwable {
                        prepareExecution(chain.getThisObject());
                        TaskRegistry.Entry task = TaskServices.tasks.beginFuture(android.os.Process.myTid(), Thread.currentThread(), chain.getThisObject(), System.nanoTime(), "FutureTask." + method);
                        TaskExecutions.Scope execution = beginExecution(chain.getThisObject(), "FutureTask." + method);
                        try { return chain.proceed(); } finally { endExecution(execution); TaskServices.tasks.end(task); }
                    }
                });
            }
            gateway.install(java.util.concurrent.ThreadPoolExecutor.class.getDeclaredMethod("beforeExecute", Thread.class, Runnable.class),"TASK",new Hooker() {
                public Object intercept(Chain chain) throws Throwable {
                    Object result = chain.proceed();
                    try {if(chain.getArg(0)==Thread.currentThread())poolBinding.set(TaskServices.executors.begin(chain.getThisObject(),android.os.Process.myTid(),Thread.currentThread(),chain.getArg(1)));}
                    catch(Throwable ignored){}
                    prepareExecution(chain.getArg(1));
                    poolTask.set(TaskServices.tasks.begin(android.os.Process.myTid(), Thread.currentThread(), chain.getArg(1), System.nanoTime(), "ThreadPoolExecutor.beforeExecute"));
                    return result;
                }
            });
            gateway.install(java.util.concurrent.ThreadPoolExecutor.class.getDeclaredMethod("afterExecute", Runnable.class, Throwable.class),"TASK",new Hooker() {
                public Object intercept(Chain chain) throws Throwable {
                    try { return chain.proceed(); } finally {
                        TaskServices.tasks.end(poolTask.get());poolTask.remove();
                        try {TaskServices.executors.end(poolBinding.get());}catch(Throwable ignored){}finally {poolBinding.remove();}
                    }
                }
            });
            TaskDiagnostics.hookState = "FutureTask+ThreadPoolExecutor=ready " + owners;
            logger.accept(TAG + " task hooks ready · " + TaskDiagnostics.hookState);
        } catch (Throwable error) {
            TaskDiagnostics.hookState = "task-hooks=partial-or-unavailable:" + error.getClass().getSimpleName() + " " + owners;
            logger.accept(TAG + " task hooks unavailable " + error);
        }
        String executionHooks = "";
        try {
            gateway.install(Class.forName("java.util.concurrent.ScheduledThreadPoolExecutor$ScheduledFutureTask").getDeclaredMethod("run"),"TASK",new Hooker() {
                public Object intercept(Chain chain) throws Throwable {
                    prepareExecution(chain.getThisObject());
                    TaskExecutions.Scope execution = beginExecution(chain.getThisObject(), "ScheduledFutureTask.run");
                    try { return chain.proceed(); } finally { endExecution(execution); }
                }
            });
            executionHooks += "ScheduledFutureTask.run=ready ";
        } catch (Throwable failed) { executionHooks += "ScheduledFutureTask.run=" + failed.getClass().getSimpleName() + " "; }
        try {
            gateway.install(java.util.concurrent.ThreadPoolExecutor.class.getDeclaredMethod("execute", Runnable.class),"TASK",new Hooker() {
                public Object intercept(Chain chain) throws Throwable {
                    prepareExecution(chain.getArg(0));Object result=chain.proceed();
                    try {TaskServices.executors.submitted(chain.getThisObject(),chain.getArg(0),"execute");}catch(Throwable ignored){}
                    return result;
                }
            });
            executionHooks += "execute-admission=ready";
        } catch (Throwable failed) { executionHooks += "execute-admission=" + failed.getClass().getSimpleName(); }
        for (String name : new String[]{"scheduleAtFixedRate", "scheduleWithFixedDelay", "schedule", "scheduleCallable"}) {
            try {
                final String source = name;
                Method admission = name.equals("scheduleCallable") ? java.util.concurrent.ScheduledThreadPoolExecutor.class.getDeclaredMethod("schedule",java.util.concurrent.Callable.class,long.class,java.util.concurrent.TimeUnit.class)
                        : name.equals("schedule") ? java.util.concurrent.ScheduledThreadPoolExecutor.class.getDeclaredMethod(name,Runnable.class,long.class,java.util.concurrent.TimeUnit.class)
                        : java.util.concurrent.ScheduledThreadPoolExecutor.class.getDeclaredMethod(name,Runnable.class,long.class,long.class,java.util.concurrent.TimeUnit.class);
                gateway.install(admission,"TASK",new Hooker() {
                    public Object intercept(Chain chain) throws Throwable {
                        Object result = chain.proceed();
                        try {
                            long period = source.startsWith("scheduleAt") || source.equals("scheduleWithFixedDelay")
                                    ? ((java.util.concurrent.TimeUnit)chain.getArg(3)).toNanos((Long)chain.getArg(2)) : 0;
                            if (source.equals("scheduleWithFixedDelay")) period = -period;
                            TaskServices.executors.submitted(chain.getThisObject(),result,source);
                            TaskServices.executions.scheduled(result,period); prepareExecution(result);
                        } catch (Throwable ignored) {}
                        return result;
                    }
                });
                executionHooks += " " + name + "=ready";
            } catch (Throwable failed) { executionHooks += " " + name + "=" + failed.getClass().getSimpleName(); }
        }
        try {
            java.lang.reflect.Field resolvedCallback = null;
            try { resolvedCallback = android.os.Handler.class.getDeclaredField("mCallback"); resolvedCallback.setAccessible(true); }
            catch (Throwable ignored) {}
            final java.lang.reflect.Field callbackField = resolvedCallback;
            gateway.install(android.os.Handler.class.getDeclaredMethod("dispatchMessage", android.os.Message.class),"TASK",new Hooker() {
                public Object intercept(Chain chain) throws Throwable {
                    TaskExecutions.Scope execution = null;
                    if (executionWorker()) try {
                        android.os.Handler handler = (android.os.Handler) chain.getThisObject();
                        android.os.Message message = (android.os.Message) chain.getArg(0);
                        Runnable callback = message.getCallback(); Object business = callback == null ? handler : callback;
                        String method = callback == null ? "handleMessage" : "run", route = callback == null ? "handler" : "runnable";
                        if (callback == null) try {
                            if (callbackField == null) throw new IllegalAccessException("Handler.Callback field unavailable");
                            Object receiver = callbackField.get(handler);
                            if (receiver instanceof android.os.Handler.Callback) { business = receiver; route = "Handler.Callback"; }
                        } catch (Throwable unavailable) { route = "Handler.Callback读取受限，分发入口已观测"; method = "dispatchMessage"; }
                        if (callback != null) prepareExecution(callback);
                        else {
                            prepareHandlerBody(business); if (business != handler) prepareHandlerBody(handler);
                        }
                        String metadata = "handler=" + handler.getClass().getName() + " messageWhat=" + message.what
                                + " callbackRoute=" + route;
                        execution = beginExecution(new TaskExecutions.DispatchTask(business,method,metadata),"Handler.dispatchMessage");
                    } catch (Throwable ignored) {}
                    try { return chain.proceed(); } finally { endExecution(execution); }
                }
            });
            executionHooks += " Handler.dispatchMessage=ready";
        } catch (Throwable failed) { executionHooks += " Handler.dispatchMessage=" + failed.getClass().getSimpleName(); }
        TaskDiagnostics.hookState += " execution=" + executionHooks;
        logger.accept(TAG + " execution hooks · " + executionHooks);
    }

    // Method hooks observe actual execution only. No business value/toString is invoked.
    private final java.util.Map<Method, String> executionMethods = new java.util.HashMap<Method, String>();
    private static boolean executionWorker() {
        return android.os.Process.myTid() != android.os.Process.myPid() && !Thread.currentThread().getName().startsWith("qqwatch-");
    }
    private void prepareExecution(Object task) {
        if (task == null || Thread.currentThread().getName().startsWith("qqwatch-")) return;
        TaskExecutions.Profile profile = null;
        try {
            profile = TaskServices.executions.profile(task, System.nanoTime(), "prepare");
            Object body = profile.info.body.get();
            if (body == null) { profile.info.hook = "业务对象不可用"; return; }
            gifAdapter.prepare(body);
            Method method = body.getClass().getMethod(profile.info.method);
            String owner = method.getDeclaringClass().getName();
            if (owner.startsWith("java.") || owner.startsWith("android.") || owner.startsWith("com.lqb9.qqwatchmod.")) {
                profile.info.hook = "平台或模块入口未重复挂钩"; return;
            }
            synchronized (executionMethods) {
                String status = executionMethods.get(method);
                if (status != null) { profile.info.hook = status; return; }
                if (executionMethods.size() >= 256) { profile.info.hook = "业务方法挂钩上限256"; return; }
                executionMethods.put(method, "installing");
            }
            String status;
            try {
                final String methodName = profile.info.method;
                gateway.install(method,"TASK",new Hooker() {
                    public Object intercept(Chain chain) throws Throwable {
                        GifTaskRule.Run gifRun=TaskServices.gif.enter(chain.getThisObject());
                        if(gifRun!=null && gifRun.blocked)return null;
                        TaskExecutions.Body body = null;
                        if (executionWorker()) try {
                            body = TaskServices.executions.beginBody(chain.getThisObject(), methodName, android.os.Process.myTid(),
                                    Thread.currentThread(), System.nanoTime(), android.os.Debug.threadCpuTimeNanos());
                        } catch (Throwable ignored) {}
                        try { return chain.proceed(); } finally {
                            try { TaskServices.executions.endBody(body, System.nanoTime(), android.os.Debug.threadCpuTimeNanos()); } catch (Throwable ignored) {}
                            TaskServices.gif.exit(gifRun);
                        }
                    }
                });
                status = "ready";
            } catch (Throwable failed) { status = "failed:" + failed.getClass().getSimpleName(); }
            synchronized (executionMethods) { executionMethods.put(method, status); }
            profile.info.hook = status;
        } catch (Throwable failed) { if (profile != null) profile.info.hook = "unavailable:" + failed.getClass().getSimpleName(); }
    }
    private static TaskExecutions.Scope beginExecution(Object task, String source) {
        if (!executionWorker()) return null;
        try { return TaskServices.executions.begin(task, android.os.Process.myTid(), Thread.currentThread(), System.nanoTime(),
                android.os.Debug.threadCpuTimeNanos(), source); } catch (Throwable ignored) { return null; }
    }
    private static void endExecution(TaskExecutions.Scope execution) {
        try { TaskServices.executions.end(execution, System.nanoTime(), android.os.Debug.threadCpuTimeNanos()); } catch (Throwable ignored) {}
    }
    private void prepareHandlerBody(Object receiver) {
        if (receiver == null) return;
        try {
            Method method = receiver.getClass().getMethod("handleMessage",android.os.Message.class);
            String owner = method.getDeclaringClass().getName();
            if (owner.startsWith("android.") || owner.startsWith("java.") || owner.startsWith("com.lqb9.qqwatchmod.")) return;
            synchronized (executionMethods) {
                if (executionMethods.containsKey(method) || executionMethods.size() >= 256) return;
                executionMethods.put(method,"installing");
            }
            String status;
            try {
                gateway.install(method,"TASK",new Hooker() {
                    public Object intercept(Chain chain) throws Throwable {
                        TaskExecutions.Body body = null;
                        if (executionWorker()) try {
                            body = TaskServices.executions.beginBody(chain.getThisObject(),"handleMessage",android.os.Process.myTid(),
                                    Thread.currentThread(),System.nanoTime(),android.os.Debug.threadCpuTimeNanos());
                        } catch (Throwable ignored) {}
                        try { return chain.proceed(); } finally {
                            try { TaskServices.executions.endBody(body,System.nanoTime(),android.os.Debug.threadCpuTimeNanos()); } catch (Throwable ignored) {}
                        }
                    }
                }); status = "ready";
            } catch (Throwable failed) { status = "failed:" + failed.getClass().getSimpleName(); }
            synchronized (executionMethods) { executionMethods.put(method,status); }
        } catch (Throwable unavailable) {}
    }

}

