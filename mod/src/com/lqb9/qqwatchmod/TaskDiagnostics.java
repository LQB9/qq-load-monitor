package com.lqb9.qqwatchmod;

import java.lang.ref.WeakReference;
import java.util.LinkedHashMap;
import java.util.Map;

/** Technical evidence only. Never chooses a task, changes it, or guesses TID from a name. */
final class TaskDiagnostics {
    static final int MAX_TEXT = 4096, MAX_FRAMES = 24;
    private static final int MAX_OWNERS = 1024, MAX_LIMITS = 128;
    static volatile String hookState = "等待任务 hook";
    private static final class Owner {
        final WeakReference<Thread> thread; final String source;
        Owner(Thread thread, String source) { this.thread = new WeakReference<Thread>(thread); this.source = source; }
    }
    private final Map<Integer, Owner> owners = new LinkedHashMap<Integer, Owner>();
    private final Map<String, Long> captured = new LinkedHashMap<String, Long>();
    private long lastCaptureMs = -1;

    synchronized void observe(int tid, Thread thread, String source) {
        if (tid <= 0 || thread == null) return;
        Owner old = owners.get(tid);
        if (old != null && old.thread.get() == thread) return;
        owners.put(tid, new Owner(thread, source));
        while (owners.size() > MAX_OWNERS) owners.remove(owners.keySet().iterator().next());
    }

    // Once per target identity per minute; at most one stack capture every three
    // seconds per process. Small reason records still identify every failed request.
    synchronized boolean allow(String identity, long nowMs) {
        Long previous = captured.get(identity);
        if ((previous != null && nowMs - previous < 60000) || (lastCaptureMs >= 0 && nowMs - lastCaptureMs < 3000)) return false;
        captured.put(identity, nowMs); lastCaptureMs = nowMs;
        while (captured.size() > MAX_LIMITS) captured.remove(captured.keySet().iterator().next());
        return true;
    }

    private synchronized Owner owner(int tid) { return owners.get(tid); }
    String threadName(int tid) {
        Owner mapped = owner(tid); Thread thread = mapped == null ? null : mapped.thread.get();
        return thread == null || !thread.isAlive() ? "" : thread.getName();
    }

    String describe(int tid, TaskRegistry.Entry task, long windowStartNs, long sampleNs, boolean stack) {
        StringBuilder out = new StringBuilder("taskHooks=").append(hookState);
        if (task == null) out.append(" | task=未捕获活动任务（不能据此认定为原生线程）");
        else {
            out.append(" | taskClass=").append(task.task == null ? "null" : task.task.getClass().getName())
                    .append(" taskSource=").append(task.source).append(" generation=").append(task.generation)
                    .append(" taskEnded=").append(task.ended).append(" windowCovered=").append(task.startNs <= windowStartNs)
                    .append(" taskStartMinusWindowMs=").append((task.startNs - windowStartNs) / 1000000L)
                    .append(" sampleMinusTaskStartMs=").append((sampleNs - task.startNs) / 1000000L);
        }
        Owner mapped = owner(tid);
        Thread target = task != null ? task.thread : mapped == null ? null : mapped.thread.get();
        if (target == null || !target.isAlive()) return limit(out.append(" | javaStack=未建立存活 Java 线程与 TID 的映射；本版未采集原生用户栈").toString());
        out.append(" | javaThread=").append(target.getName()).append(" state=").append(target.getState())
                .append(" ownerSource=").append(mapped == null ? "active-task" : mapped.source);
        if (!stack) return limit(out.append(" | javaStack=限频，本次未采集；同一目标每分钟最多一次").toString());
        try {
            StackTraceElement[] frames = target.getStackTrace();
            out.append(" | javaStack=");
            if (frames.length == 0) out.append("Java 栈为空；不能据此认定线程类型");
            for (int i = 0; i < Math.min(MAX_FRAMES, frames.length); i++) out.append(i == 0 ? "" : " <- ").append(frames[i]);
            if (frames.length > MAX_FRAMES) out.append(" <- [栈截断]");
        } catch (Throwable failed) { out.append(" | javaStack=读取失败:").append(failed.getClass().getSimpleName()); }
        return limit(out.toString());
    }

    static String limit(String value) {
        if (value == null) return "";
        String clean = value.replace('\n', ' ').replace('\r', ' ');
        if (clean.length() <= MAX_TEXT) return clean;
        int end = MAX_TEXT - 12;
        if (Character.isHighSurrogate(clean.charAt(end - 1))) end--;
        return clean.substring(0, end) + " [诊断截断]";
    }
}
