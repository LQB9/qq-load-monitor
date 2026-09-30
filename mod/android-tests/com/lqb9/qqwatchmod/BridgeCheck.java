package com.lqb9.qqwatchmod;

import android.app.*;
import android.os.*;
import java.io.File;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Exercises the unmodified production QQ-UID control protocol in an isolated UID. */
public final class BridgeCheck extends Instrumentation {
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result=new Bundle();
        try {
            runOnMainSync(() -> TaskBridge.attach((Application)getTargetContext().getApplicationContext()));
            trial(true); trial(false);
            result.putString("stream","PASS 2 Android production control trials: task returned with pool thread preserved; cancelled Future ignoring interruption remains running; QQ untouched\n");finish(-1,result);
        }catch(Throwable fail){result.putString("stream","FAILED "+fail);finish(1,result);}
    }
    private void trial(boolean cooperative) throws Exception {
        AtomicBoolean release=new AtomicBoolean();AtomicInteger tid=new AtomicInteger();CountDownLatch began=new CountDownLatch(1);
        AtomicReference<FutureTask<?>> futureRef=new AtomicReference<FutureTask<?>>();
        ThreadPoolExecutor pool=(ThreadPoolExecutor)Executors.newFixedThreadPool(1);
        FutureTask<Void> future=new FutureTask<Void>(() -> {
            tid.set(android.os.Process.myTid());
            TaskRegistry.Entry task=TaskBridge.tasks.begin(tid.get(),Thread.currentThread(),futureRef.get(),System.nanoTime());began.countDown();
            try { while(!release.get()) {
                if(cooperative && Thread.currentThread().isInterrupted())break;
                Thread.yield();
            }} finally { TaskBridge.tasks.end(task); }
            return null;
        });futureRef.set(future);pool.execute(future);
        try {
            if(!began.await(3,TimeUnit.SECONDS))throw new AssertionError("task not started");
            int pid=android.os.Process.myPid();
            QqCpuTracker.Reading p=QqCpuTracker.Reading.parse(pid,"QA",CoreCollector.read(new File("/proc/self/stat")));
            ThreadCpuTracker.Reading t=ThreadCpuTracker.Reading.parse(pid,tid.get(),"QA",CoreCollector.read(new File("/proc/self/task/"+tid.get()+"/stat")));
            CoreSnapshot.Counter counter=new CoreSnapshot.Counter(pid,p.startTicks,tid.get(),t.startTicks,"qa-bridge",new long[8]);
            CoreSnapshot snap=new CoreSnapshot("qa",android.os.Process.myUid(),1,SystemClock.elapsedRealtime(),System.nanoTime(),true,0,"CONTROL FIXTURE",new long[8],Arrays.asList(counter));
            CoreTracker.Result data=new CoreTracker.Result(250,new double[8],snap,System.nanoTime(),Arrays.asList(new CoreTracker.Detail(counter,90)),"CONTROL FIXTURE");
            int before=TaskBridge.history.snapshot().size();TaskBridge.consider(data,WatchModule.readSettings());
            if(TaskBridge.history.snapshot().size()!=before+1)throw new AssertionError("no request row");
            String id=TaskBridge.history.snapshot().get(0).id;long deadline=SystemClock.elapsedRealtime()+7500;
            ProcessingHistory.Row row=null;
            while(SystemClock.elapsedRealtime()<deadline) {
                row=TaskBridge.history.find(id);
                if(row.state.equals("任务已结束") || row.state.equals("仍在运行") || row.state.equals("无法处理") || row.state.equals("未处理"))break;
                SystemClock.sleep(100);
            }
            if(!row.state.equals(cooperative?"任务已结束":"仍在运行"))throw new AssertionError("unexpected result "+row.state+" "+row.detail);
            if(pool.getPoolSize()!=1)throw new AssertionError("pool worker lost");
            if(!future.isCancelled())throw new AssertionError("not requested");
        }finally{release.set(true);pool.shutdownNow();pool.awaitTermination(2,TimeUnit.SECONDS);}
    }
}
