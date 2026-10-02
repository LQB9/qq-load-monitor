package com.lqb9.qqwatchmod;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.FutureTask;

/** Execution-time evidence. It never chooses or cancels a task. CPU is all-core reference data. */
final class TaskExecutions {
    private final ExecutorRegistry executors;
    TaskExecutions(){this(new ExecutorRegistry());}
    TaskExecutions(ExecutorRegistry executors){this.executors=executors;}
    static final int MAX_TEXT = 3600, MAX_PROFILES = 512, MAX_EVENTS = 4096;
    private static final long RECENT_NS = 10000000000L;
    /** Contains only route metadata and a weak business identity; never retains Message/obj/data. */
    static final class DispatchTask {
        final WeakReference<Object> business;
        final String method, metadata;
        DispatchTask(Object business, String method, String metadata) {
            this.business = new WeakReference<Object>(business); this.method = method;
            this.metadata = limit(metadata, 700);
        }
    }
    static final class Identity extends WeakReference<Object> {
        final int hash;
        Identity(Object task, ReferenceQueue<Object> queue) { super(task, queue); hash = System.identityHashCode(task); }
        public int hashCode() { return hash; }
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Identity)) return false;
            Object value = get(); return value != null && value == ((Identity) other).get();
        }
    }
    static final class Info {
        final String taskClass, bodyClass, method, note; final WeakReference<Object> body;
        final boolean handlerMessage;
        volatile long periodNs; volatile boolean scheduleKnown;
        volatile String scheduleSource = "platform-field";
        volatile String hook = "等待执行入口";
        Info(Object task) {
            if (task instanceof DispatchTask) {
                DispatchTask message = (DispatchTask) task; Object target = message.business.get();
                taskClass = "android.os.Handler.dispatchMessage"; body = message.business;
                bodyClass = target == null ? "Handler业务对象已回收" : target.getClass().getName();
                method = message.method; note = message.metadata; handlerMessage = true;
                scheduleSource = "Handler.dispatchMessage"; hook = "已观测消息分发，业务入口待确认"; return;
            }
            handlerMessage = false;
            taskClass = task.getClass().getName(); Object payload = task;
            String methodName = task instanceof java.util.concurrent.Callable && !(task instanceof Runnable) ? "call" : "run", problem = "";
            long period = 0; boolean scheduled = false;
            try {
                Object business=QqTaskAdapter.body(payload,false);
                if(business!=null){payload=business;problem="QQ Job业务绑定已匹配";}
                if (payload instanceof FutureTask) {
                    payload = readField(FutureTask.class, "callable", payload); methodName = "call";
                    if (payload != null && payload.getClass().getName().equals("java.util.concurrent.Executors$RunnableAdapter")) {
                        payload = readField(payload.getClass(), "task", payload); methodName = "run";
                    }
                }
                business=QqTaskAdapter.body(payload,false);
                if(business!=null){payload=business;methodName="run";problem="QQ Job业务绑定已匹配";}
            } catch (Throwable failed) { payload = null; problem = "业务类型读取失败:" + failed.getClass().getSimpleName()+" "+limit(String.valueOf(failed.getMessage()),180); }
            try {
                for (Class<?> type = task.getClass(); type != null; type = type.getSuperclass()) {
                    if (type.getName().equals("java.util.concurrent.ScheduledThreadPoolExecutor$ScheduledFutureTask")) {
                        period = ((Long) readField(type, "period", task)).longValue(); scheduled = true; break;
                    }
                }
            } catch (Throwable failed) { problem += " 调度参数读取失败:" + failed.getClass().getSimpleName(); }
            body = new WeakReference<Object>(payload); bodyClass = payload == null ? "未取得业务对象" : payload.getClass().getName();
            method = methodName; note = problem; periodNs = period; scheduleKnown = scheduled;
        }
        private static Object readField(Class<?> type, String name, Object target) throws Exception {
            Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(target);
        }
        String schedule() {
            if (handlerMessage) return "handler-message";
            return !scheduleKnown ? "unknown" : periodNs == 0 ? "one-shot" : periodNs > 0 ? "fixed-rate" : "fixed-delay";
        }
        String scheduleLabel() { return handlerMessage ? "Handler消息" : !scheduleKnown ? "调度未知" : periodNs==0 ? "一次性" : periodNs>0 ? "固定频率" : "固定延迟"; }
    }
    static final class Profile {
        final long id; final Info info;
        long dispatches, completed, cpuNs, bodyRuns, bodyCpuNs, firstNs, lastNs;
        volatile long stackNs = -1; volatile String stack = "执行入口栈尚未采集";
        String sources;
        Profile(long id, Info info, long now, String source) { this.id = id; this.info = info; firstNs = now; sources = source; }
    }
    static final class Scope {
        final Profile profile; final Object task; final Scope parent; final ThreadState owner;
        final String executor;
        final long startNs, cpuStartNs; int depth = 1; long childCpuNs, bodyCpuNs; int bodyRuns;
        Body body;
        Scope(Profile p, Object task, Scope parent, ThreadState owner, long start, long cpu,String executor) {
            profile = p; this.task = task; this.parent = parent; this.owner = owner; startNs = start; cpuStartNs = cpu;this.executor=executor;
        }
    }
    static final class Body {
        final Scope scope; final boolean ownsScope; final long startNs, cpuStartNs;
        int depth = 1;
        Body(Scope s, boolean ownsScope, long start, long cpu) { scope = s; this.ownsScope = ownsScope; startNs = start; cpuStartNs = cpu; }
    }
    private static final class ThreadState {
        final int tid; final long javaId; final WeakReference<Thread> thread; volatile Scope active;
        ThreadState(int tid, Thread thread) { this.tid = tid; javaId = thread.getId(); this.thread = new WeakReference<Thread>(thread); }
    }
    private static final class Event {
        final String executor;
        final Profile profile; final int tid; final long javaId, startNs, endNs, cpuNs, bodyCpuNs; final int bodyRuns;
        Event(Scope s, long end, long cpu) { profile = s.profile; tid = s.owner.tid; javaId = s.owner.javaId; startNs = s.startNs; endNs = end; cpuNs = cpu; bodyRuns = s.bodyRuns; bodyCpuNs = s.bodyCpuNs;executor=s.executor; }
    }
    static final class Report {
        final String threadName, summary, text;
        Report(String name, String summary, String text) { threadName = name; this.summary = summary; this.text = text; }
    }
    static final class GifEvidence {
        final List<Object> renders; final long cpuNs, windowNs;
        GifEvidence(List<Object> renders,long cpu,long window) {this.renders=renders;cpuNs=cpu;windowNs=window;}
        boolean dominant(double threadAllCpu) {
            return windowNs>=500000000L && threadAllCpu>0 && Double.isFinite(threadAllCpu)
                    && cpuNs>=1000000L && cpuNs*100d/windowNs>=threadAllCpu*0.5;
        }
        double selectedLowerPercent(double selectedCpu,double allCpu) {
            if(!dominant(allCpu) || selectedCpu<=0 || selectedCpu>allCpu || allCpu>110
                    || !Double.isFinite(selectedCpu))return Double.NaN;
            // Even if every unselected-core nanosecond was GIF work, this much must remain in the selected set.
            return Math.max(0,Math.min(allCpu,cpuNs*100d/windowNs)-(allCpu-selectedCpu));
        }
        boolean dominatesSelected(double selectedCpu,double allCpu) {
            double lower=selectedLowerPercent(selectedCpu,allCpu);
            return Double.isFinite(lower) && lower>=selectedCpu*0.5 && lower*windowNs/100d>=1000000L;
        }
    }
    /** Completed, observed business CPU in this exact window; all-core attribution only, never a load trigger. */
    synchronized GifEvidence gifEvidence(int tid,long windowStartNs,long sampleNs) {
        ThreadState owner=threads.get(tid); Thread thread=owner==null?null:owner.thread.get();
        IdentityHashMap<Object,Long> totals=new IdentityHashMap<Object,Long>();
        if(thread!=null && thread.isAlive()) for(Event event:events) {
            Info info=event.profile.info;
            if(event.tid!=tid || event.javaId!=owner.javaId || event.startNs<windowStartNs || event.endNs>sampleNs
                    || event.bodyRuns<=0 || !info.bodyClass.equals(GifTaskRule.RENDER) || !info.method.equals("run")) continue;
            Object render=info.body.get(); if(render==null)continue;
            Long before=totals.get(render); totals.put(render,(before==null?0:before)+Math.min(event.cpuNs,event.bodyCpuNs));
        }
        List<Object> ranked=new ArrayList<Object>(totals.keySet());
        Collections.sort(ranked,(a,b)->Long.compare(totals.get(b),totals.get(a)));
        List<Object> picked=new ArrayList<Object>(); long cpu=0;
        for(Object render:ranked) {if(picked.size()==3)break;picked.add(render);cpu+=totals.get(render);}
        return new GifEvidence(picked,cpu,sampleNs-windowStartNs);
    }
    private static final class Totals {
        String executor;
        final Profile profile; long recentCpu, windowCpu, windowBodyCpu; int recentCount, windowCount, bodies, windowBodies;
        Totals(Profile p) { profile = p; }
    }

    private final ReferenceQueue<Object> collected = new ReferenceQueue<Object>();
    private final LinkedHashMap<Identity, Profile> profiles = new LinkedHashMap<Identity, Profile>();
    private final LinkedHashMap<Identity, Long> schedules = new LinkedHashMap<Identity, Long>();
    private final LinkedHashMap<Integer, ThreadState> threads = new LinkedHashMap<Integer, ThreadState>();
    private final ArrayDeque<Event> events = new ArrayDeque<Event>();
    private final ThreadLocal<Scope> active = new ThreadLocal<Scope>();
    private long nextId, droppedEvents, lastStackNs = -1;

    synchronized Profile profile(Object task, long now, String source) {
        if (task == null) return null;
        Identity removed; while ((removed = (Identity) collected.poll()) != null) { profiles.remove(removed); schedules.remove(removed); }
        Profile profile = profiles.get(new Identity(task, null));
        if (profile == null) {
            profile = new Profile(++nextId, new Info(task), now, source);
            Long schedule = schedules.get(new Identity(task,null));
            if (schedule != null) { profile.info.periodNs=schedule; profile.info.scheduleKnown=true; profile.info.scheduleSource="schedule-API"; }
            profiles.put(new Identity(task, collected), profile);
            while (profiles.size() > MAX_PROFILES) {
                Identity evict = null;
                for (Map.Entry<Identity, Profile> item : profiles.entrySet()) {
                    if (!item.getValue().info.scheduleKnown || item.getValue().info.periodNs==0) { evict=item.getKey(); break; }
                }
                if (evict==null) evict=profiles.keySet().iterator().next();
                profiles.remove(evict);
            }
        } else if (!profile.sources.contains(source) && profile.sources.length() < 180) profile.sources += "+" + source;
        return profile;
    }
    synchronized void scheduled(Object task, long periodNs) {
        if (task==null) return;
        schedules.put(new Identity(task,collected),periodNs);
        while(schedules.size()>256) schedules.remove(schedules.keySet().iterator().next());
        Profile profile = profile(task, System.nanoTime(), "schedule-API");
        if (profile == null) return;
        profile.info.periodNs = periodNs; profile.info.scheduleKnown = true; profile.info.scheduleSource = "schedule-API";
    }

    Scope begin(Object task, int tid, Thread thread, long now, long cpu, String source) {
        if (task == null || thread == null || tid <= 0 || cpu < 0) return null;
        Scope parent = active.get();
        if (parent != null && parent.task == task) { profile(task, now, source); parent.depth++; return parent; }
        int depth = 0; for (Scope s = parent; s != null; s = s.parent) if (++depth > 16) return null;
        Profile profile = profile(task, now, source); ThreadState owner;
        synchronized (this) {
            owner = threads.get(tid);
            if (owner == null || owner.thread.get() != thread) {
                owner = new ThreadState(tid, thread); threads.put(tid, owner);
                while (threads.size() > 1024) threads.remove(threads.keySet().iterator().next());
            }
            profile.dispatches++;
        }
        String executor=parent==null?executors.execution(tid,thread,task):parent.executor;
        Scope scope = new Scope(profile, task, parent, owner, now, cpu,executor); active.set(scope); owner.active = scope; return scope;
    }

    void end(Scope scope, long now, long cpu) {
        if (scope == null || active.get() != scope || --scope.depth != 0) return;
        long inclusive = Math.max(0, cpu - scope.cpuStartNs);
        long exclusive = Math.max(0, inclusive - scope.childCpuNs);
        synchronized (this) {
            scope.profile.completed++; scope.profile.cpuNs += exclusive; scope.profile.lastNs = now;
            scope.profile.bodyRuns += scope.bodyRuns; scope.profile.bodyCpuNs += scope.bodyCpuNs;
            events.addLast(new Event(scope, now, exclusive));
            while (!events.isEmpty() && now - events.peekFirst().endNs > RECENT_NS) events.removeFirst();
            while (events.size() > MAX_EVENTS) { events.removeFirst(); droppedEvents++; }
        }
        if (scope.parent != null) { scope.parent.childCpuNs += inclusive; active.set(scope.parent); }
        else active.remove();
        scope.owner.active = scope.parent;
    }

    Body beginBody(Object target, String method, int tid, Thread thread, long now, long cpu) {
        if (target == null || cpu < 0) return null;
        Scope scope = active.get(); boolean own = false;
        if (scope == null) { scope = begin(target, tid, thread, now, cpu, "body-only"); own = true; }
        else if (scope.profile.info.handlerMessage && (scope.profile.info.body.get() != target || !scope.profile.info.method.equals(method))) {
            // A Handler.Callback can return false, then the actual Handler.handleMessage runs.
            scope = begin(new DispatchTask(target, method, scope.profile.info.note),tid,thread,now,cpu,"Handler.business-entry"); own = true;
        }
        if (scope == null || scope.profile.info.body.get() != target || !scope.profile.info.method.equals(method)) {
            if (own) end(scope, now, cpu); return null;
        }
        if (scope.body != null) { scope.body.depth++; return scope.body; }
        Body body = new Body(scope, own, now, cpu); scope.body = body;
        scope.profile.info.hook = "observed:" + method;
        boolean capture;
        synchronized (this) {
            capture = (scope.profile.stackNs < 0 || now - scope.profile.stackNs >= 60000000000L)
                    && (lastStackNs < 0 || now - lastStackNs >= 3000000000L);
            if (capture) { scope.profile.stackNs = now; lastStackNs = now; }
        }
        if (capture) {
            StringBuilder stack = new StringBuilder("stackOwnerTid=").append(tid).append(" javaThreadId=").append(thread.getId()).append(' ')
                    .append(scope.profile.info.bodyClass).append('.').append(method).append(" [执行入口]");
            try {
                int frames = 0;
                for (StackTraceElement frame : thread.getStackTrace()) {
                    if (frame.getClassName().startsWith("com.lqb9.qqwatchmod.") || frame.getClassName().startsWith("io.github.libxposed.")) continue;
                    if (++frames > 12) break; stack.append(" <- ").append(frame);
                }
            } catch (Throwable failed) { stack.append(" 栈读取失败:").append(failed.getClass().getSimpleName()); }
            scope.profile.stack = limit(stack.toString(), 1200);
        } else if (scope.profile.stackNs<0) scope.profile.stack="执行入口栈限频，本次未采集；每进程至少间隔3秒";
        return body;
    }

    void endBody(Body body, long now, long cpu) {
        if (body == null || body.scope.body != body || --body.depth != 0) return;
        body.scope.bodyRuns++; body.scope.bodyCpuNs += Math.max(0, cpu - body.cpuStartNs); body.scope.body = null;
        if (body.ownsScope) end(body.scope, now, cpu);
    }

    synchronized Report describe(int tid, long windowStartNs, long sampleNs) {
        ThreadState owner = threads.get(tid); Thread thread = owner == null ? null : owner.thread.get();
        String name = thread == null ? "" : thread.getName();
        String prefix = "TID=" + tid + " 执行CPU=全部核心参考，不参与所选核心阈值判定 windowMs="
                + Math.max(0, sampleNs - windowStartNs) / 1000000L + " historyDropped=" + droppedEvents;
        if (owner == null) return new Report(name, "尚未捕获执行入口记录", prefix + " | 执行历史=无；执行入口可能未覆盖");
        if (thread == null || !thread.isAlive()) return new Report(name, "已观测的 Java 线程已退出", prefix + " | 历史线程已退出，不将其记录套用到新线程");
        prefix+=" | "+executors.describe(tid,thread);
        Map<Long, Totals> totals = new LinkedHashMap<Long, Totals>();
        for (Event event : events) {
            if (event.tid != tid || event.javaId != owner.javaId || event.endNs > sampleNs || event.endNs < sampleNs - RECENT_NS) continue;
            Totals t = totals.get(event.profile.id);
            if (t == null) { t = new Totals(event.profile); totals.put(event.profile.id, t); }
            if(t.executor==null)t.executor=event.executor;
            else if(!t.executor.equals(event.executor))t.executor="线程池执行归属=多个或不完整，不合并推定";
            t.recentCount++; t.recentCpu += event.cpuNs; t.bodies += event.bodyRuns;
            if (event.startNs >= windowStartNs && event.endNs <= sampleNs) {
                t.windowCount++; t.windowCpu += event.cpuNs; t.windowBodies += event.bodyRuns; t.windowBodyCpu += event.bodyCpuNs;
            }
        }
        List<Totals> ranked = new ArrayList<Totals>(totals.values());
        Collections.sort(ranked, (a,b) -> Long.compare(b.recentCpu,a.recentCpu));
        StringBuilder detail = new StringBuilder(prefix).append(" | 近10秒完成任务实例=").append(ranked.size());
        Scope running = owner.active;
        Scope message = running;
        while (message != null && !message.profile.info.handlerMessage) message = message.parent;
        Profile lastMessage = message == null ? null : message.profile;
        if (lastMessage == null) for (Event event : events)
            if (event.tid == tid && event.javaId == owner.javaId && event.endNs <= sampleNs && event.endNs >= sampleNs - RECENT_NS
                    && event.profile.info.handlerMessage) lastMessage = event.profile;
        if (lastMessage != null) detail.append(" | Handler消息=").append(lastMessage.info.note)
                .append(" route=").append(lastMessage.info.bodyClass).append('.').append(lastMessage.info.method)
                .append(" routeObserved=").append(lastMessage.info.hook);
        if (running != null) detail.append(" | 当前执行=task#").append(running.profile.id).append(' ').append(running.profile.info.bodyClass)
                .append('.').append(running.profile.info.method).append(" 已运行Ms=").append(Math.max(0, sampleNs - running.startNs) / 1000000L)
                .append(" startedAfterSample=").append(running.startNs > sampleNs).append(" schedule=").append(running.profile.info.schedule())
                .append(" periodMs=").append(running.profile.info.periodNs/1000000d).append(" bodyHook=").append(running.profile.info.hook)
                .append(" executor={").append(running.executor).append('}')
                .append(" activeExecutionStack=").append(running.profile.stack);
        StringBuilder summary = new StringBuilder();
        for (int i = 0; i < Math.min(3, ranked.size()); i++) {
            Totals t = ranked.get(i); Profile p = t.profile;
            detail.append(" | task#").append(p.id).append(" taskClass=").append(p.info.taskClass).append(" business=").append(p.info.bodyClass)
                    .append('.').append(p.info.method).append(" executor={").append(t.executor).append('}')
                    .append(" schedule=").append(p.info.schedule()).append(" scheduleSource=").append(p.info.scheduleSource).append(" periodMs=").append(p.info.periodNs / 1000000d)
                    .append(" windowRuns=").append(t.windowCount).append(" windowCpuMs=").append(t.windowCpu / 1000000d)
                    .append(" windowBodyRuns=").append(t.windowBodies).append(" windowBodyCpuMs=").append(t.windowBodyCpu/1000000d)
                    .append(" windowRunsPerSec=").append(String.format(Locale.ROOT,"%.1f",t.windowCount * 1000000000d / Math.max(1,sampleNs-windowStartNs)))
                    .append(" recent10sRuns=").append(t.recentCount).append(" recent10sCpuMs=").append(t.recentCpu / 1000000d)
                    .append(" recentBodyRuns=").append(t.bodies).append(" lifetimeRuns=").append(p.completed).append(" lifetimeBodyRuns=").append(p.bodyRuns)
                    .append(" source=").append(p.sources).append(" bodyHook=").append(p.info.hook).append(' ').append(p.info.note)
                    .append(" executionStack=").append(p.stack);
            if (i == 0) summary.append("task#").append(p.id).append(' ').append(simple(p.info.bodyClass)).append('.').append(p.info.method)
                    .append(" · 窗口 ").append(t.windowCount).append(" 次 / ").append(String.format(Locale.ROOT,"%.1f",t.windowCpu/1000000d))
                    .append("ms CPU · 业务 ").append(t.windowBodies).append(" 次 · 近10秒 ").append(t.recentCount).append(" 次 · ").append(p.info.scheduleLabel());
        }
        if (summary.length() == 0) summary.append(running == null ? "近10秒暂无已完成执行记录" : "task#" + running.profile.id + " "
                + simple(running.profile.info.bodyClass)+"."+running.profile.info.method+" 正在执行；耗时待返回后统计");
        detail.append(" | windowRuns仅含完整落在窗口内的执行；跨界或仍在执行的CPU未分摊；嵌套入口CPU去重");
        return new Report(name, limit(summary.toString(),400), limit(detail.toString(),MAX_TEXT));
    }
    synchronized boolean activeMessage(int tid) {
        ThreadState owner = threads.get(tid);
        for (Scope scope = owner == null ? null : owner.active; scope != null; scope = scope.parent)
            if (scope.profile.info.handlerMessage) return true;
        return false;
    }
    synchronized boolean recentMessage(int tid, long sampleNs) {
        ThreadState owner = threads.get(tid); if (owner == null) return false;
        for (Event event : events) if (event.tid == tid && event.javaId == owner.javaId && event.endNs <= sampleNs
                && event.endNs >= sampleNs - RECENT_NS && event.profile.info.handlerMessage) return true;
        return false;
    }
    static String simple(String name) { int dot = name.lastIndexOf('.'); return dot < 0 ? name : name.substring(dot+1); }
    static String limit(String text, int max) {
        text = text.replace('\n',' ').replace('\r',' ');
        if (text.length() <= max) return text;
        String suffix = " [执行记录截断]";
        int end = max-suffix.length(); if (Character.isHighSurrogate(text.charAt(end-1))) end--;
        return text.substring(0,end)+suffix;
    }
}
