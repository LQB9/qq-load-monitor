package com.lqb9.qqwatchmod;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class TaskDiagnosticsTest {
    static int checks;
    static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        TaskDiagnostics rate = new TaskDiagnostics();
        check(rate.allow("1:10", 1000), "first failure captures");
        check(!rate.allow("2:20", 2000), "global capture bound");
        check(rate.allow("2:20", 4000), "different target after global interval");
        check(!rate.allow("1:10", 20000), "same thread identity bounded for minute");
        check(rate.allow("1:11", 21000), "reused TID has distinct identity");
        check(rate.allow("1:10", 62000), "original identity eventually refreshed");
        String unknown = new TaskDiagnostics().describe(777, null, 1, 2, true);
        check(unknown.contains("不能据此认定为原生线程") && unknown.contains("未建立"), "missing mapping is explicitly unknown");

        AtomicBoolean release = new AtomicBoolean(); CountDownLatch began = new CountDownLatch(1);
        Thread worker = new Thread(() -> {
            began.countDown();
            while (!release.get()) { try { Thread.sleep(20); } catch (InterruptedException unexpected) { throw new AssertionError("diagnostics interrupted worker"); } }
        }, "same-prefix-worker-original");
        worker.start(); check(began.await(2, TimeUnit.SECONDS), "fixture started");
        try {
            TaskRegistry registry = new TaskRegistry();
            Object plain = new Runnable() { public void run() {} public String toString() { throw new AssertionError("task values read"); } };
            TaskRegistry.Entry task = registry.begin(701, worker, plain, 200, "pool-fixture");
            String active = registry.diagnostics.describe(701, task, 100, 300, true);
            check(active.contains("taskClass=") && active.contains("taskSource=pool-fixture"), "type and hook source captured without task toString");
            check(active.contains("windowCovered=false") && active.contains("generation=1"), "window evidence captured");
            check(active.contains("javaStack=") && active.contains("TaskDiagnosticsTest"), "exact registered Java thread stack available");
            check(!task.ended && registry.current(701, Long.MAX_VALUE) == task && worker.isAlive(), "diagnostic leaves execution and registry unchanged");
            registry.end(task);
            String between = registry.diagnostics.describe(701, null, 100, 300, true);
            check(between.contains("javaThread=same-prefix-worker-original") && between.contains("javaStack="), "owner remains observable between tasks");
            String skipped = registry.diagnostics.describe(701, null, 100, 300, false);
            check(skipped.contains("限频") && !skipped.contains("Thread.sleep("), "no stack read when rate limited");
            TaskDiagnostics unmapped = new TaskDiagnostics();
            check(!unmapped.describe(702, null, 1, 2, true).contains("same-prefix-worker-original"), "names never guessed as TID identity");
        } finally { release.set(true); worker.join(2000); }
        check(!worker.isAlive(), "fixture cleaned up");

        String large = new String(new char[6000]).replace('\0', 'x');
        String limited = TaskDiagnostics.limit(large + "\n");
        check(limited.length() <= TaskDiagnostics.MAX_TEXT && limited.contains("截断") && !limited.contains("\n"), "diagnostic record bounded and single line");
        String unicode = new String(new char[TaskDiagnostics.MAX_TEXT - 13]).replace('\0', 'x') + "\uD83D\uDE00" + large;
        String clean = TaskDiagnostics.limit(unicode);
        check(!Character.isHighSurrogate(clean.charAt(clean.indexOf(" [诊断截断]") - 1)), "truncation does not split surrogate pair");
        System.out.println("PASS " + checks + " task diagnostics checks");
    }
}
