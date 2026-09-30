package com.lqb9.qqwatchmod;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public final class CoreHandlingTest {
    static int checks;
    static void check(boolean b, String name) { checks++; if (!b) throw new AssertionError(name); }
    static CoreSnapshot snapshot(long seq, long ms, long a, long b, long c, String session, boolean valid, int uid, long tidStart) {
        long[] cores = {a,b,0,0,0,0,c,0};
        return new CoreSnapshot(session, uid, seq, ms, ms*1000000, valid, 0, "ok", cores,
                Arrays.asList(new CoreSnapshot.Counter(100, 10, 101, tidStart, "worker 中文", new long[]{a,0,0,0,0,0,0,0}),
                        new CoreSnapshot.Counter(100,10,102,20,"worker 2",new long[]{0,b,0,0,0,0,0,0}),
                        new CoreSnapshot.Counter(100,10,103,30,"worker 6",new long[]{0,0,0,0,0,0,c,0})));
    }
    static CoreSnapshot s(long seq, long ms, long a, long b, long c) { return snapshot(seq,ms,a,b,c,"session",true,10265,11); }
    static CoreTracker.Result sample(CoreTracker tracker, CoreSnapshot s, int mask) { return tracker.sample(s,mask,10265,s.elapsedMs,1); }
    public static void main(String[] args) throws Exception {
        CoreSnapshot first = s(1,1000,0,0,0), next=s(2,2000,800000000,700000000,900000000);
        CoreSnapshot copy=CoreSnapshot.decode(next.encode());
        check(copy.threads.get(0).name.equals("worker 中文") && copy.cores[6]==900000000,"UTF8 codec");
        try { CoreSnapshot.decode(next.encode().trim()); throw new AssertionError("partial accepted"); } catch (java.io.IOException good) { checks++; }
        try { CoreSnapshot.decode(next.encode().replace("QQCORE2","INVALID")); throw new AssertionError("version accepted"); } catch (java.io.IOException good) { checks++; }
        CoreTracker tracker=new CoreTracker(); check(sample(tracker,first,3).cpu<0,"first unknown");
        CoreTracker.Result result=sample(tracker,next,3);
        check(Math.abs(result.cpu-150)<.001,"only selected cores total");
        check(result.threads.size()==2 && result.threads.get(0).thread.tid==101,"unselected hotspot excluded");
        check(result.cores[6]==90,"unselected core still displayed independently");
        check(sample(tracker,next,3)==null,"duplicate not new sample");
        check(sample(tracker,s(3,3000,1600000000,1400000000,1800000000),255).cpu<0,"mask change baseline reset");
        check(sample(tracker,s(4,4000,2400000000L,2100000000L,2700000000L),255).cpu==240,"all cores single core percentage");
        check(tracker.sample(next,3,999,2000,1).cpu<0,"foreign UID rejected");
        check(tracker.sample(next,3,10265,6000,1).cpu<0,"stale rejected");
        sample(tracker,first,3);
        check(sample(tracker,snapshot(2,2000,800000000,700000000,0,"restart",true,10265,11),3).cpu<0,"session change rejected");
        sample(tracker,first,3); check(sample(tracker,snapshot(2,2000,800000000,700000000,0,"session",false,10265,11),3).cpu<0,"lost event rejected");
        sample(tracker,first,3); check(sample(tracker,s(2,5000,800000000,0,0),3).cpu<0,"long gap rejected");
        sample(tracker,first,3); check(sample(tracker,s(2,2000,1500000000,0,0),3).cpu<0,"impossible per core rejected");
        check(new CpuLoadMonitor.Settings(true,200,0,1,"stop_task",15).coreMask==15,"core selection retained");
        check(!new CpuLoadMonitor.Settings(true,200,0,1,"stop_task",15).sameAs(new CpuLoadMonitor.Settings(true,200,0,1,"stop_task",255)),"mask participates config identity");
        check(CoreSnapshot.selection(129).equals("0,7"),"arbitrary combo selectable");
        ProcessingHistory history=new ProcessingHistory();
        for(int i=0;i<105;i++) history.add(new ProcessingHistory.Row("r"+i,0,next.threads.get(0),3,240,80,"background","pending",""));
        check(history.snapshot().size()==100 && history.snapshot().get(0).id.equals("r104"),"table bounded newest first");
        List<ProcessingHistory.Row> old=history.snapshot();history.update("r104","ended","returned");
        check(old.get(0).state.equals("pending") && history.find("r104").state.equals("ended"),"immutable table snapshot");
        TaskRegistry tasks=new TaskRegistry(); CountDownLatch began=new CountDownLatch(1), ended=new CountDownLatch(1);
        AtomicReference<TaskRegistry.Entry> active=new AtomicReference<TaskRegistry.Entry>(); AtomicReference<FutureTask<?>> futureRef=new AtomicReference<FutureTask<?>>();
        FutureTask<Void> future=new FutureTask<Void>(() -> {
            TaskRegistry.Entry entry=tasks.begin(201,Thread.currentThread(),futureRef.get(),1);active.set(entry);began.countDown();
            try { while(!Thread.currentThread().isInterrupted()) Thread.yield(); } finally { tasks.end(entry);ended.countDown(); }
            return null;
        });futureRef.set(future);Thread worker=new Thread(future);worker.start();check(began.await(2,TimeUnit.SECONDS),"cooperative worker started");
        check(tasks.current(201,0)==null,"later task cannot be mistaken for sampled task");
        check(tasks.request(active.get()).startsWith("已请求"),"cancel request accepted");
        check(ended.await(2,TimeUnit.SECONDS) && active.get().ended,"confirmed actual task return");worker.join(1000);
        AtomicBoolean release=new AtomicBoolean();CountDownLatch stubbornBegan=new CountDownLatch(1);AtomicReference<TaskRegistry.Entry> stubborn=new AtomicReference<TaskRegistry.Entry>();
        FutureTask<Void> ignored=new FutureTask<Void>(() -> {TaskRegistry.Entry entry=tasks.begin(202,Thread.currentThread(),futureRef.get(),1);stubborn.set(entry);stubbornBegan.countDown();try{while(!release.get())Thread.yield();}finally{tasks.end(entry);}return null;});
        futureRef.set(ignored);Thread ignoring=new Thread(ignored);ignoring.start();check(stubbornBegan.await(2,TimeUnit.SECONDS),"ignoring worker started");
        check(tasks.request(stubborn.get()).startsWith("已请求") && ignored.isCancelled(),"Future cancel bit set");
        check(!stubborn.get().ended && ignoring.isAlive(),"cancel bit is not execution termination");
        release.set(true);ignoring.join(2000);check(stubborn.get().ended,"ended only after return");
        check(!tasks.request(stubborn.get()).startsWith("已请求"),"obsolete entry cannot affect reused thread");
        FutureTask<Void> custom=new FutureTask<Void>(() -> null) { @Override public boolean cancel(boolean interrupt) { throw new AssertionError("custom callback invoked"); } };
        TaskRegistry.Entry customEntry=tasks.begin(203,Thread.currentThread(),custom,1);
        check(tasks.request(customEntry).contains("自定义"),"custom cancel callback never executed under identity lock");tasks.end(customEntry);
        FutureTask<Void> customDone=new FutureTask<Void>(() -> null) { @Override protected void done() { throw new AssertionError("done callback invoked"); } };
        TaskRegistry.Entry doneEntry=tasks.begin(204,Thread.currentThread(),customDone,1);
        check(tasks.request(doneEntry).contains("自定义"),"custom done callback never executed under identity lock");tasks.end(doneEntry);
        TaskRegistry.Entry plain=tasks.begin(205,Thread.currentThread(),new Object(),1);
        check(tasks.request(plain).contains("专用") && !Thread.currentThread().isInterrupted(),"unknown runnable is not blindly interrupted");tasks.end(plain);
        LoadHistory chart=new LoadHistory();chart.add(1000,300,1);chart.clear();
        check(chart.snapshot().isEmpty(),"scope change can clear incomparable trend history");
        RuntimeLine line=RuntimeLine.parse(" worker-101 [006] d..2 3667.125: sched_stat_runtime: comm=worker pid=101 runtime=123456 [ns]");
        check(line!=null && line.core==6 && line.tid==101 && line.runtime==123456,"actual event CPU parser");
        check(RuntimeLine.parse("LOST 13 EVENTS")==null,"unknown record not zero time");
        System.out.println("PASS "+checks+" selected-core and cooperative handling checks");
    }
}
