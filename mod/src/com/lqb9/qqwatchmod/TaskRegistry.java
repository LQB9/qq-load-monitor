package com.lqb9.qqwatchmod;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;

/** Only active executor tasks, never an arbitrary native thread exit. */
final class TaskRegistry {
    static final class Entry {
        final long generation, startNs; final int tid; final Thread thread; final Object task; final String source;
        int depth = 1; volatile boolean ended;
        Entry parent;
        Entry(long generation, int tid, Thread thread, Object task, long startNs, String source) {
            this.generation = generation; this.tid = tid; this.thread = thread; this.task = task; this.startNs = startNs; this.source = source;
        }
    }
    private final Map<Integer, Entry> running = new HashMap<Integer, Entry>();
    final TaskDiagnostics diagnostics = new TaskDiagnostics();
    private long generation;
    synchronized long observed() { return generation; }
    synchronized Entry begin(int tid, Thread thread, Object task, long startNs) {
        return begin(tid, thread, task, startNs, "manual");
    }
    synchronized Entry begin(int tid, Thread thread, Object task, long startNs, String source) {
        diagnostics.observe(tid, thread, source);
        Entry current = running.get(tid);
        if (current != null) { current.depth++; return current; }
        if (running.size() >= 2048) return null;
        Entry entry = new Entry(++generation, tid, thread, task, startNs, source); running.put(tid, entry); return entry;
    }
    synchronized void end(Entry entry) {
        if (entry == null || running.get(entry.tid) != entry) return;
        if (--entry.depth == 0) {
            entry.ended = true;
            if(entry.parent!=null && !entry.parent.ended)running.put(entry.tid,entry.parent);
            else running.remove(entry.tid);
        }
    }
    /** Called at actual platform FutureTask.run/runAndReset entry, never at submission. */
    synchronized Entry beginFuture(int tid,Thread thread,Object task,long startNs,String source) {
        Entry outer=running.get(tid);
        if(QqTaskAdapter.platformRun(task) && outer!=null && outer.thread==thread
                && QqTaskAdapter.activeBusiness(outer.task,task)) {
            Entry inner=new Entry(++generation,tid,thread,task,startNs,source+" QQ Job内层业务");
            inner.parent=outer;running.put(tid,inner);return inner;
        }
        return begin(tid,thread,task,startNs,source);
    }
    synchronized Entry current(int tid, long windowStartNs) {
        Entry entry = running.get(tid);
        return entry != null && entry.startNs <= windowStartNs ? entry : null;
    }
    synchronized String request(Entry entry) {
        if (entry == null || running.get(entry.tid) != entry || entry.ended) return "任务已变化，未处理";
        if(entry.parent!=null && !QqTaskAdapter.activeBusiness(entry.parent.task,entry.task))
            return "QQ Job业务绑定已变化或热补丁未验证，未处理";
        try {
            if (entry.task instanceof Future) {
                // Application-defined Future.cancel/done callbacks may block or take
                // unrelated locks. Only the platform FutureTask implementation is
                // used while holding the task identity lock.
                Class<?> cancelOwner = entry.task.getClass().getMethod("cancel", boolean.class).getDeclaringClass();
                Class<?> doneOwner = entry.task.getClass();
                while (doneOwner != null) {
                    try { doneOwner.getDeclaredMethod("done"); break; }
                    catch (NoSuchMethodException inherited) { doneOwner = doneOwner.getSuperclass(); }
                }
                if (cancelOwner != java.util.concurrent.FutureTask.class || doneOwner != java.util.concurrent.FutureTask.class)
                    return "任务使用自定义取消回调，需要专用停止接口";
                if (!((Future<?>) entry.task).cancel(true)) return "任务拒绝取消，未处理";
            } else return "任务没有已验证的取消接口，需要专用停止接口";
            return "已请求停止，待确认";
        } catch (Throwable failure) { return "处理失败：" + failure.getClass().getSimpleName(); }
    }
}
