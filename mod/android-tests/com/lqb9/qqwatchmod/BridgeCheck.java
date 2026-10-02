package com.lqb9.qqwatchmod;

import android.app.*;
import android.os.*;
import java.io.File;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Exercises the unmodified production QQ-UID control protocol in an isolated UID. */
public final class BridgeCheck extends Instrumentation {
    private boolean rulesOnly;
    private String mode;
    @Override public void onCreate(Bundle args) { super.onCreate(args);mode=args==null?"":args.getString("mode","");rulesOnly="rule-export".equals(mode); start(); }
    @Override public void onStart() {
        Bundle result=new Bundle();
        try {
            WatchLog.start(new File(getTargetContext().getFilesDir(), "diagnostics-log"), "QQ DIAGNOSTICS CONTROL FIXTURE - not QQ activity");
            if("qq-reference".equals(mode)){RuntimeCheck.qqReferenceApi(this);result.putString("stream","PASS real QQ reference GIF and Job class contracts; no QQ object/JNI initialized\n");finish(-1,result);return;}
            if("job-hooks".equals(mode)){RuntimeCheck.jobHooks(this);result.putString("stream","PASS actual Job-wrapped Future hook/cancel/return/window/hotfix and retained pool trials; isolated fixture\n");finish(-1,result);return;}
            if(rulesOnly){
                MonitorState.isMain=true;runOnMainSync(()->TaskBridge.attach((Application)getTargetContext().getApplicationContext()));
                singleTrial(0);singleTrial(1);singleTrial(4);for(int mode=0;mode<9;mode++)totalTrial(mode);RuleExportCheck.run(this);runOnMainSync(TaskBridge::detach);
                result.putString("stream","PASS 12 Android independent-total/single/merged/config-revalidation trials + actual rule log Downloads export; isolated fixtures, QQ untouched\n");finish(-1,result);return;
            }
            RuntimeCheck.settings(this);RuntimeCheck.gatewayAndRuntime(this);RuntimeCheck.executorHooks(this);RuntimeCheck.qq925Api(this);RuntimeCheck.threadSettings(this);
            MonitorState.isMain=true;
            runOnMainSync(() -> TaskBridge.attach((Application)getTargetContext().getApplicationContext()));
            trial(true); trial(false);
            missingTrial(false, false); missingTrial(true, false); missingTrial(true, true);
            executionTrial();
            handlerTrial(true,false); handlerTrial(false,false); handlerTrial(true,true);
            crossProcessTrial();
            durationTrial(0); durationTrial(1); changedDurationTrial();
            gifTrial(1,0);gifTrial(0,0);gifTrial(1,1);gifTrial(1,2);gifTrial(0,3);
            gifTrial(1,4);gifTrial(1,5);gifTrial(1,6);gifTrial(1,7);
            gifTrial(1,8);
            for(int mode=0;mode<8;mode++)singleTrial(mode);
            detachTrial();
            verifyExport();
            result.putString("stream","PASS 37 Android production trials + Downloads export: original 28; 8 independent-thread/merge/business cases and native settings rendering. Hook backend, load windows and GIF execution are isolated fixtures; QQ untouched\n");finish(-1,result);
        }catch(Throwable fail){result.putString("stream","FAILED "+fail);finish(1,result);}
    }
    private void totalTrial(int mode)throws Exception {
        CpuLoadMonitor.Settings saved=WatchSettings.read();
        boolean totalRule=mode!=1 && mode!=6,totalAuto=mode==4 || mode>=7,singleRule=mode>=1 && mode<=5,singleAuto=mode>=1 && mode<=3;
        CpuLoadMonitor.Settings settings=new CpuLoadMonitor.Settings(true,200,0,1,"stop_task",15,true,singleRule,80,0,singleAuto,totalRule,totalAuto);WatchSettings.settings=settings;
        AtomicBoolean release=new AtomicBoolean();AtomicInteger tid=new AtomicInteger();AtomicReference<TaskRegistry.Entry> entry=new AtomicReference<>();CountDownLatch ready=new CountDownLatch(1);
        FutureTask<Void> work=new FutureTask<>(()->{while(!release.get()){if(Thread.currentThread().isInterrupted())return null;SystemClock.sleep(10);}return null;});
        Thread worker=new Thread(()->{tid.set(android.os.Process.myTid());entry.set(TaskBridge.tasks.begin(tid.get(),Thread.currentThread(),work,System.nanoTime()-(mode==3?1000000L:5000000000L),"total-switch-fixture"));ready.countDown();try{work.run();}finally{TaskBridge.tasks.end(entry.get());}},"qa-total-"+mode);worker.start();
        try{
            if(!ready.await(3,TimeUnit.SECONDS))throw new AssertionError("total fixture not ready");
            CoreTracker.Result d=singleData(tid.get(),System.nanoTime()-1000000000L,250,mode==4?1:0);
            ThreadLoadMonitor.Result single=new ThreadLoadMonitor().sample(d,settings,d.snapshot.elapsedMs,0);CpuLoadMonitor.Sample aggregate=immediateSample(d,settings);
            int before=TaskBridge.history.snapshot().size();
            if(mode>=7)runOnMainSync(()->{TaskBridge.consider(d,settings,aggregate,single);WatchSettings.settings=new CpuLoadMonitor.Settings(true,200,0,1,"stop_task",15,true,singleRule,80,0,singleAuto,mode==7, false);});
            else TaskBridge.consider(d,settings,aggregate,single);
            if(mode==6){SystemClock.sleep(200);if(TaskBridge.history.snapshot().size()!=before || work.isCancelled())throw new AssertionError("both disabled produced action");return;}
            if(TaskBridge.history.snapshot().size()!=before+1)throw new AssertionError("total row missing/duplicated mode="+mode);
            String id=TaskBridge.history.snapshot().get(0).id;ProcessingHistory.Row row=awaitRow(id,4000);
            if(mode==0 || mode==5){
                if(!row.state.equals("仅记录") || work.isCancelled())throw new AssertionError("record-only total cancelled task "+row.detail);
                TaskBridge.consider(d,settings,new CpuLoadMonitor.Sample(settings,aggregate.elapsedMs,250,aggregate.highMs,false,"reported"),new ThreadLoadMonitor.Result(Collections.emptyList(),"fixture"));
                if(TaskBridge.history.snapshot().size()!=before+1)throw new AssertionError("continuous record duplicated");
            }else if(mode==3){if(!row.diagnostic.contains("THREAD_ATTRIBUTION_INCOMPLETE") || work.isCancelled())throw new AssertionError("total record bypassed single business duration "+row.detail);}
            else if(mode>=7){if(!row.detail.contains("配置已变化") || work.isCancelled())throw new AssertionError("queued request ignored changed total flags "+row.detail);}
            else if(!row.state.equals("任务已结束") || !work.isCancelled())throw new AssertionError("enabled handling source failed "+mode+" "+row.detail);
            if(mode==1 && !row.rule.equals("单线程"))throw new AssertionError("disabled total still advertised");
            if(mode>=2 && mode<=5 && !row.rule.equals("合计＋单线程"))throw new AssertionError("both detected sources missing");
            if(!row.ruleParameters.contains("合计模式") || !row.ruleParameters.contains("单线程模式"))throw new AssertionError("historical modes missing");
        }finally{release.set(true);worker.join(1500);WatchSettings.settings=saved;}
    }
    private void singleTrial(int mode)throws Exception {
        CpuLoadMonitor.Settings saved=WatchSettings.read();
        int duration=mode==2 || mode==3 || mode>=6?2:0;
        boolean gifMode=mode>=6;
        CpuLoadMonitor.Settings settings=new CpuLoadMonitor.Settings(true,200,0,1,"stop_task",15,true,true,80,duration,mode!=0);
        WatchSettings.settings=settings;
        AtomicBoolean release=new AtomicBoolean(),switchTask=new AtomicBoolean();AtomicInteger tid=new AtomicInteger();
        AtomicLong emitStart=new AtomicLong();AtomicReference<TaskRegistry.Entry> entry=new AtomicReference<>();
        CountDownLatch ready=new CountDownLatch(1);AtomicInteger emitted=new AtomicInteger();
        com.tencent.libra.extension.gif.GifDrawable firstOwner=new com.tencent.libra.extension.gif.GifDrawable(),secondOwner=new com.tencent.libra.extension.gif.GifDrawable();
        com.tencent.libra.extension.gif.RenderTask firstRender=new com.tencent.libra.extension.gif.RenderTask(firstOwner),secondRender=new com.tencent.libra.extension.gif.RenderTask(secondOwner);
        TaskBridge.gif.adapter(firstRender).hooksReady=true;TaskBridge.gif.adapter(secondRender).hooksReady=true;
        FutureTask<Void> replacement=new FutureTask<>(()->null);
        FutureTask<Void> work=new FutureTask<>(()->{while(!release.get()){
            if(switchTask.getAndSet(false)){
                TaskBridge.tasks.end(entry.get());entry.set(TaskBridge.tasks.begin(tid.get(),Thread.currentThread(),replacement,System.nanoTime()-5000000000L,"changed-task-fixture"));
            }
            long start=emitStart.getAndSet(0);
            if(start>0){
                com.tencent.libra.extension.gif.RenderTask render=mode==7 && emitted.get()>0?secondRender:firstRender;
                for(int i=0;i<25;i++){
                    long at=start+i*37000000L;FutureTask<Void> wrapper=new FutureTask<>(render,null);
                    TaskExecutions.Scope scope=TaskBridge.executions.begin(wrapper,tid.get(),Thread.currentThread(),at,0,"single-GIF-window-fixture");
                    TaskExecutions.Body body=TaskBridge.executions.beginBody(render,"run",tid.get(),Thread.currentThread(),at+1,1);
                    TaskBridge.executions.endBody(body,at+35000000L,25000000L);TaskBridge.executions.end(scope,at+35000001L,25000001L);
                }
                emitted.incrementAndGet();
            }
            if(Thread.currentThread().isInterrupted())return null;SystemClock.sleep(10);
        }return null;});
        Thread worker=new Thread(()->{tid.set(android.os.Process.myTid());
            if(!gifMode)entry.set(TaskBridge.tasks.begin(tid.get(),Thread.currentThread(),work,System.nanoTime()-5000000000L,"single-Future-fixture"));
            ready.countDown();try{work.run();}finally{TaskBridge.tasks.end(entry.get());}},"qa-single-"+mode);
        worker.start();
        try{
            if(!ready.await(3,TimeUnit.SECONDS))throw new AssertionError("single worker not ready");
            long start=System.nanoTime()-1000000000L;
            if(gifMode){emitStart.set(start);awaitEmission(emitted,1);}
            CoreTracker.Result d=singleData(tid.get(),start,mode==4?250:120,mode==2?1:0);
            ThreadLoadMonitor monitor=new ThreadLoadMonitor();ThreadLoadMonitor.Result single=monitor.sample(d,settings,d.snapshot.elapsedMs,0);
            int before=TaskBridge.history.snapshot().size();
            if(duration>0){
                TaskBridge.consider(d,settings,immediateSample(d,settings),single);SystemClock.sleep(150);
                if(TaskBridge.history.snapshot().size()!=before || work.isCancelled() || firstOwner.stops>0)throw new AssertionError("single acted before duration");
                if(mode==3){switchTask.set(true);long limit=SystemClock.elapsedRealtime()+1000;while(entry.get().task!=replacement && SystemClock.elapsedRealtime()<limit)SystemClock.sleep(10);}
                long nextStart=d.snapshot.monoNs;SystemClock.sleep(1100);
                if(gifMode){emitStart.set(nextStart);awaitEmission(emitted,2);}
                d=singleData(tid.get(),nextStart,120,mode==2?1:0);single=monitor.sample(d,settings,d.snapshot.elapsedMs,0);
                if(single.readyCount()!=1)throw new AssertionError("single duration did not accumulate");
            }
            final CoreTracker.Result data=d;final ThreadLoadMonitor.Result result=single;
            if(mode==4)runOnMainSync(()->{TaskBridge.consider(data,settings,immediateSample(data,settings),result);TaskBridge.consider(data,settings,immediateSample(data,settings),result);});
            else if(mode==5)runOnMainSync(()->{TaskBridge.consider(data,settings,immediateSample(data,settings),result);
                WatchSettings.settings=new CpuLoadMonitor.Settings(true,200,0,1,"stop_task",15,true,true,80,3,true);});
            else TaskBridge.consider(data,settings,immediateSample(data,settings),result);
            if(TaskBridge.history.snapshot().size()!=before+1)throw new AssertionError("single row missing or duplicate");
            String id=TaskBridge.history.snapshot().get(0).id;
            if(mode==6){awaitGif(id,"GIF已暂停",3500);if(firstOwner.stops!=1 || secondOwner.stops!=0)throw new AssertionError("single GIF owner action mismatch");}
            else {
                ProcessingHistory.Row row=awaitRow(id,4000);
                if(mode==0 && (!row.state.equals("仅记录") || work.isCancelled()))throw new AssertionError("record mode cancelled work");
                if((mode==1 || mode==2 || mode==4) && (!row.state.equals("任务已结束") || !work.isCancelled()))throw new AssertionError("qualified single task not ended "+row.detail);
                if(mode==3 && (!row.diagnostic.contains("THREAD_ATTRIBUTION_INCOMPLETE") || replacement.isCancelled()))throw new AssertionError("replacement task inherited old time "+row.detail);
                if(mode==5 && (!row.detail.contains("配置已变化") || work.isCancelled()))throw new AssertionError("new thread setting not revalidated");
                if(mode==7 && (!row.diagnostic.contains("THREAD_ATTRIBUTION_INCOMPLETE") || firstOwner.stops>0 || secondOwner.stops>0))throw new AssertionError("new GIF inherited old duration "+row.detail);
                if(mode==4 && !row.rule.equals("合计＋单线程"))throw new AssertionError("merged trigger source missing");
            }
        }finally{release.set(true);worker.join(1500);TaskBridge.gif.restore(settings,true);WatchSettings.settings=saved;}
    }
    private void awaitEmission(AtomicInteger value,int wanted)throws Exception{
        long until=SystemClock.elapsedRealtime()+1500;while(value.get()<wanted && SystemClock.elapsedRealtime()<until)SystemClock.sleep(10);
        if(value.get()<wanted)throw new AssertionError("GIF history fixture not emitted");
    }
    private CoreTracker.Result singleData(int tid,long window,double total,int foreground)throws Exception{
        CoreTracker.Result original=withForeground(data(tid,window),foreground);CoreSnapshot.Counter t=original.threads.get(0).thread;
        return new CoreTracker.Result(total,original.cores,original.snapshot,window,Arrays.asList(new CoreTracker.Detail(t,90,90)),"simulated CPU window; isolated UID");
    }
    private ProcessingHistory.Row awaitRow(String id,long timeoutMs) throws Exception {
        long until=SystemClock.elapsedRealtime()+timeoutMs;ProcessingHistory.Row row;
        do {row=TaskBridge.history.find(id);if(!row.state.equals("等待处理") && !row.state.equals("待确认"))return row;SystemClock.sleep(30);}
        while(SystemClock.elapsedRealtime()<until);
        throw new AssertionError("response timeout "+row.state+" "+row.detail);
    }
    private void detachTrial()throws Exception {
        runOnMainSync(TaskBridge::detach);
        AtomicInteger tid=new AtomicInteger();AtomicBoolean release=new AtomicBoolean();CountDownLatch began=new CountDownLatch(1);
        Thread worker=new Thread(() -> {tid.set(android.os.Process.myTid());began.countDown();while(!release.get())SystemClock.sleep(10);},"qa-detached-control");
        worker.start();
        try {
            if(!began.await(3,TimeUnit.SECONDS))throw new AssertionError("detach fixture did not start");
            consider(data(tid.get(),System.nanoTime()-1000000000L));
            ProcessingHistory.Row row=TaskBridge.history.snapshot().get(0);
            if(!row.state.equals("未处理") || !row.detail.contains("控制通道不可用"))throw new AssertionError("detached channel still accepted processing");
        }finally{release.set(true);worker.join(1500);}
        runOnMainSync(() -> TaskBridge.attach((Application)getTargetContext().getApplicationContext()));
        trial(true);
    }
    private static CpuLoadMonitor.Sample immediateSample(CoreTracker.Result data, CpuLoadMonitor.Settings settings) {
        CpuLoadMonitor monitor=new CpuLoadMonitor();long now=data.snapshot.elapsedMs;
        monitor.sample(-1,now-1000,settings,0);return monitor.sample(data.cpu,now,settings,0);
    }
    private void consider(CoreTracker.Result data) {
        CpuLoadMonitor.Settings settings=WatchSettings.read();
        TaskBridge.consider(data,settings,immediateSample(data,settings));
    }
    private ProcessingHistory.Row awaitGif(String id,String state,long ms) throws Exception {
        long until=SystemClock.elapsedRealtime()+ms;ProcessingHistory.Row row;
        do {row=TaskBridge.history.find(id);if(row.state.equals(state))return row;SystemClock.sleep(40);}while(SystemClock.elapsedRealtime()<until);
        throw new AssertionError("GIF expected "+state+" got "+row.state+" "+row.detail);
    }
    private void gifTrial(int foreground,int mode) throws Exception {
        CpuLoadMonitor.Settings saved=WatchSettings.read();
        CpuLoadMonitor.Settings settings=new CpuLoadMonitor.Settings(true,200,mode==0?2:0,1,"stop_task",15,mode!=2);
        WatchSettings.settings=settings;
        com.tencent.libra.extension.gif.GifDrawable drawable=new com.tencent.libra.extension.gif.GifDrawable();
        com.tencent.libra.extension.gif.RenderTask render=new com.tencent.libra.extension.gif.RenderTask(drawable);
        TaskBridge.gif.adapter(render).hooksReady=true; // Simulates the validated Xposed start/recycle hooks, not QQ's decoder.
        AtomicBoolean release=new AtomicBoolean();AtomicInteger tid=new AtomicInteger();AtomicLong window=new AtomicLong();
        CountDownLatch ready=new CountDownLatch(1);AtomicReference<GifTaskRule.Run> running=new AtomicReference<>();
        ExecutorService pool=Executors.newSingleThreadExecutor(r->new Thread(r,"qa-gif-"+foreground+"-"+mode));
        pool.execute(() -> {
            tid.set(android.os.Process.myTid());long end=System.nanoTime(),start=end-1000000000L;window.set(start);
            for(int i=0;i<20;i++) {
                FutureTask<Void> wrapper=new FutureTask<Void>(render,null);long t=start+i*40000000L;
                TaskExecutions.Scope scope=TaskBridge.executions.begin(wrapper,tid.get(),Thread.currentThread(),t,0,"GIF-completed-hook-fixture");
                TaskExecutions.Body body=TaskBridge.executions.beginBody(render,"run",tid.get(),Thread.currentThread(),t+1,1);
                TaskBridge.executions.endBody(body,t+30000000L,20000000L);TaskBridge.executions.end(scope,t+30000001L,20000001L);
            }
            if(mode==1)running.set(TaskBridge.gif.enter(render));
            ready.countDown();while(!release.get())SystemClock.sleep(10);
            TaskBridge.gif.exit(running.get());
        });
        try {
            if(!ready.await(3,TimeUnit.SECONDS))throw new AssertionError("GIF worker not ready");
            CoreTracker.Result original=withForeground(data(tid.get(),window.get()),foreground);
            CoreTracker.Detail hot=original.threads.get(0);
            CoreTracker.Result data=new CoreTracker.Result(original.cpu,original.cores,original.snapshot,original.windowStartNs,
                    Arrays.asList(new CoreTracker.Detail(hot.thread,mode==8?20:60,mode==4?100:60)),original.note);
            int before=TaskBridge.history.snapshot().size();CpuLoadMonitor monitor=new CpuLoadMonitor();long now=data.snapshot.elapsedMs;
            monitor.sample(-1,now-2000,settings,0);
            if(mode==0) {
                TaskBridge.consider(data,settings,monitor.sample(data.cpu,now-1000,settings,0));SystemClock.sleep(80);
                if(drawable.stops!=0 || TaskBridge.history.snapshot().size()!=before)throw new AssertionError("GIF stopped before duration");
            }
            CpuLoadMonitor.Sample sample=monitor.sample(data.cpu,now,settings,0);
            if(mode==3)runOnMainSync(() -> {TaskBridge.consider(data,settings,sample);WatchSettings.settings=new CpuLoadMonitor.Settings(true,200,0,1,"stop_task",15,false);});
            else TaskBridge.consider(data,settings,sample);
            if(TaskBridge.history.snapshot().size()!=before+1)throw new AssertionError("GIF handling row absent");
            String id=TaskBridge.history.snapshot().get(0).id;
            if(mode==2 || mode==4) {
                ProcessingHistory.Row row=awaitRow(id,3500);
                if(!row.diagnostic.contains("NO_ACTIVE_TASK") || drawable.stops!=0)throw new AssertionError("disabled/unattributed GIF incorrectly stopped "+row.detail);
            } else if(mode==3) {
                ProcessingHistory.Row row=awaitRow(id,3500);
                if(!row.state.equals("未处理") || !row.detail.contains("配置已变化") || drawable.stops!=0)throw new AssertionError("queued GIF toggle not revalidated");
            } else if(mode==8) {
                ProcessingHistory.Row row=awaitRow(id,3500);
                if(!row.state.equals("未处理") || !row.diagnostic.contains("GIF_SELECTED_ATTRIBUTION_INSUFFICIENT") || drawable.stops!=0)
                    throw new AssertionError("unselected-core GIF load incorrectly used for action "+row.detail);
            } else {
                if(mode==1) {
                    awaitGif(id,"GIF仍在运行",7500);
                    release.set(true); // An ignored/interminable render is not reported as successful until it actually returns.
                }
                ProcessingHistory.Row row=awaitGif(id,"GIF已暂停",3500);
                if(drawable.playing || drawable.stops!=1 || !row.executionText.contains("RenderTask.run") || !TaskBridge.gif.blockStart(drawable))
                    throw new AssertionError("GIF owner pause/entry evidence incomplete "+row.detail);
                if(mode==0) {
                    GifTaskRule.Run blocked=TaskBridge.gif.enter(new com.tencent.libra.extension.gif.RenderTask(drawable));
                    if(blocked==null || !blocked.blocked || TaskBridge.gif.blockStart(new com.tencent.libra.extension.gif.GifDrawable()))
                        throw new AssertionError("replacement task or unrelated owner gate wrong");
                    TaskBridge.gif.exit(blocked);TaskBridge.consider(data,settings,sample);
                    if(TaskBridge.history.find(id).detail.contains("复采QQ合计="))throw new AssertionError("old pre-pause sample accepted as recheck");
                    CoreTracker.Result mixed=data(tid.get(),window.get());TaskBridge.consider(mixed,settings,immediateSample(mixed,settings));
                    if(TaskBridge.history.find(id).detail.contains("复采QQ合计="))throw new AssertionError("mixed pre/post-pause window accepted as recheck");
                    long freshStart=System.nanoTime();SystemClock.sleep(510);
                    CoreTracker.Result fresh=data(tid.get(),freshStart);TaskBridge.consider(fresh,settings,immediateSample(fresh,settings));
                    if(!TaskBridge.history.find(id).detail.contains("复采QQ合计="))throw new AssertionError("load recheck missing");
                }
                if(mode==5)WatchSettings.settings=new CpuLoadMonitor.Settings(false,200,0,1,"stop_task",15,true);
                else {
                    if(mode==6)drawable.visible=false;
                    if(mode==7){TaskBridge.gif.recycled(drawable);drawable.recycle();}
                    TaskBridge.restoreGifManually();
                }
                awaitGif(id,"GIF已解除暂停",3500);
                if(TaskBridge.gif.blockStart(drawable) || (mode==6 || mode==7 ? drawable.starts!=0 : !drawable.playing || drawable.starts!=1))
                    throw new AssertionError("GIF restore incorrectly replayed hidden/recycled owner or failed visible resume");
            }
        } finally {
            release.set(true);pool.shutdown();pool.awaitTermination(2,TimeUnit.SECONDS);
            WatchSettings.settings=saved;TaskBridge.restoreGifManually();SystemClock.sleep(100);
        }
    }
    private void durationTrial(int foreground) throws Exception { timedTrial(foreground,false); }
    private void changedDurationTrial() throws Exception { timedTrial(0,true); }
    private void timedTrial(int foreground,boolean changeBeforeDelivery) throws Exception {
        CpuLoadMonitor.Settings saved=WatchSettings.read();
        CpuLoadMonitor.Settings settings=new CpuLoadMonitor.Settings(true,200,changeBeforeDelivery?0:2,1,"stop_task",15);
        WatchSettings.settings=settings;
        ExecutorService pool=Executors.newSingleThreadExecutor();AtomicBoolean release=new AtomicBoolean();
        AtomicInteger tid=new AtomicInteger();AtomicLong taskStart=new AtomicLong();CountDownLatch began=new CountDownLatch(1);
        AtomicReference<FutureTask<Void>> reference=new AtomicReference<>();
        FutureTask<Void> future=new FutureTask<Void>(() -> {
            tid.set(android.os.Process.myTid());taskStart.set(System.nanoTime());
            TaskRegistry.Entry entry=TaskBridge.tasks.begin(tid.get(),Thread.currentThread(),reference.get(),taskStart.get(),"duration-fixture");
            began.countDown();try{while(!release.get())Thread.sleep(25);}finally{TaskBridge.tasks.end(entry);}return null;
        });reference.set(future);pool.execute(future);
        try {
            if(!began.await(3,TimeUnit.SECONDS))throw new AssertionError("timed task did not begin");
            long now=SystemClock.elapsedRealtime();CpuLoadMonitor monitor=new CpuLoadMonitor();monitor.sample(-1,now,settings,0);
            int before=TaskBridge.history.snapshot().size();
            if(!changeBeforeDelivery) {
                SystemClock.sleep(1000);
                CoreTracker.Result first=withForeground(data(tid.get(),taskStart.get()+1),foreground);
                CpuLoadMonitor.Sample waiting=monitor.sample(first.cpu,now+1000,settings,0);
                TaskBridge.consider(first,settings,waiting);SystemClock.sleep(100);
                if(future.isCancelled() || TaskBridge.history.snapshot().size()!=before)
                    throw new AssertionError("cancel/history occurred before configured duration foreground="+foreground);
                SystemClock.sleep(1000);
            }
            CoreTracker.Result second=withForeground(data(tid.get(),taskStart.get()+1),foreground);
            CpuLoadMonitor.Sample ready=monitor.sample(second.cpu,now+(changeBeforeDelivery?1000:2000),settings,0);
            if(changeBeforeDelivery) {
                runOnMainSync(() -> {
                    TaskBridge.consider(second,settings,ready);
                    WatchSettings.settings=new CpuLoadMonitor.Settings(true,200,5,1,"stop_task",15);
                });
            } else TaskBridge.consider(second,settings,ready);
            if(TaskBridge.history.snapshot().size()!=before+1)throw new AssertionError("duration reached without request");
            ProcessingHistory.Row row=awaitRow(TaskBridge.history.snapshot().get(0).id,3500);
            if(changeBeforeDelivery) {
                if(!row.state.equals("未处理") || !row.detail.contains("配置已变化") || future.isCancelled())
                    throw new AssertionError("changed duration did not reject queued cancellation "+row.state+" "+row.detail);
            } else if(!row.state.equals("任务已结束") || !future.isCancelled() || !row.reason.contains("要求2秒"))
                throw new AssertionError("two-second handling failed "+row.state+" "+row.detail);
        } finally {
            release.set(true);pool.shutdownNow();pool.awaitTermination(2,TimeUnit.SECONDS);WatchSettings.settings=saved;
        }
    }
    private static CoreTracker.Result withForeground(CoreTracker.Result source,int foreground) {
        CoreSnapshot old=source.snapshot;
        CoreSnapshot now=new CoreSnapshot(old.session,old.uid,old.sequence,old.elapsedMs,old.monoNs,old.valid,foreground,old.note,old.cores,old.threads);
        return new CoreTracker.Result(source.cpu,source.cores,now,source.windowStartNs,source.threads,source.note);
    }
    private static final class ObservedHandler extends Handler {
        final AtomicBoolean release; final CountDownLatch began,finished; final AtomicInteger tid;
        final boolean idle, futureMode; final AtomicReference<FutureTask<?>> future;
        volatile long taskStart;
        ObservedHandler(Looper looper,AtomicBoolean release,CountDownLatch began,CountDownLatch finished,AtomicInteger tid,
                boolean idle,boolean futureMode,AtomicReference<FutureTask<?>> future) {
            super(looper);this.release=release;this.began=began;this.finished=finished;this.tid=tid;
            this.idle=idle;this.futureMode=futureMode;this.future=future;
        }
        public void dispatchMessage(Message message) {
            int t=android.os.Process.myTid();TaskBridge.tasks.diagnostics.observe(t,Thread.currentThread(),"Handler-fixture");
            Object body=message.getCallback()==null?this:message.getCallback();String method=message.getCallback()==null?"handleMessage":"run";
            TaskExecutions.Scope scope=TaskBridge.executions.begin(new TaskExecutions.DispatchTask(body,method,
                    "handler=ObservedHandler messageWhat="+message.what+" callbackRoute=fixture"),t,Thread.currentThread(),System.nanoTime(),Debug.threadCpuTimeNanos(),"Handler.dispatchMessage-fixture");
            try {super.dispatchMessage(message);}finally{TaskBridge.executions.end(scope,System.nanoTime(),Debug.threadCpuTimeNanos());}
        }
        public void handleMessage(Message message) {
            TaskExecutions.Body body=TaskBridge.executions.beginBody(this,"handleMessage",android.os.Process.myTid(),Thread.currentThread(),System.nanoTime(),Debug.threadCpuTimeNanos());
            taskStart=System.nanoTime();tid.set(android.os.Process.myTid());began.countDown();
            try {while(!idle && !release.get())SystemClock.sleep(10);}
            finally{TaskBridge.executions.endBody(body,System.nanoTime(),Debug.threadCpuTimeNanos());finished.countDown();}
        }
    }
    private void handlerTrial(boolean active,boolean futureMode) throws Exception {
        AtomicBoolean release=new AtomicBoolean();AtomicInteger tid=new AtomicInteger();
        CountDownLatch began=new CountDownLatch(1),finished=new CountDownLatch(1);AtomicReference<FutureTask<?>> future=new AtomicReference<>();
        HandlerThread worker=new HandlerThread("qa-handler-"+active+"-"+futureMode);worker.start();
        ObservedHandler handler=new ObservedHandler(worker.getLooper(),release,began,finished,tid,!active,futureMode,future);
        long window;
        try {
            if(futureMode) {
                FutureTask<Void> task=new FutureTask<Void>(() -> {
                    tid.set(android.os.Process.myTid());handler.taskStart=System.nanoTime();
                    TaskRegistry.Entry entry=TaskBridge.tasks.begin(tid.get(),Thread.currentThread(),future.get(),handler.taskStart,"Handler-Future-fixture");
                    began.countDown();try {while(!release.get() && !Thread.currentThread().isInterrupted())Thread.yield();}
                    finally{TaskBridge.tasks.end(entry);finished.countDown();}return null;
                });future.set(task);handler.post(task);
            } else handler.sendEmptyMessage(314);
            if(!began.await(3,TimeUnit.SECONDS))throw new AssertionError("Handler message not started");
            window=handler.taskStart+1;
            if(!active && !finished.await(2,TimeUnit.SECONDS))throw new AssertionError("Handler did not return to waiting");
            SystemClock.sleep(40);
            consider(data(tid.get(),window));
            ProcessingHistory.Row row=awaitRow(TaskBridge.history.snapshot().get(0).id,3500);
            if(futureMode) {
                if(!row.state.equals("任务已结束") || !future.get().isCancelled())throw new AssertionError("Handler Future not actually cancelled "+row.state);
            } else {
                String code=active?"HANDLER_NO_CANCEL_INTERFACE":"HANDLER_TASK_NOT_ACTIVE";
                if(!row.state.equals("无法处理") || !row.diagnostic.contains("code="+code) || !row.executionText.contains("messageWhat=314"))
                    throw new AssertionError("Handler evidence missing "+row.state+" "+row.diagnostic+" "+row.executionText);
                if(worker.isInterrupted())throw new AssertionError("ordinary Handler was interrupted");
            }
            release.set(true);if(!finished.await(2,TimeUnit.SECONDS))throw new AssertionError("message failed to return");
            CountDownLatch next=new CountDownLatch(1);handler.post(next::countDown);
            if(!next.await(2,TimeUnit.SECONDS) || !worker.isAlive())throw new AssertionError("Handler worker no longer accepts work");
        }finally{release.set(true);worker.quitSafely();worker.join(2500);}
    }
    private void crossProcessTrial() throws Exception {
        AtomicReference<Bundle> target=new AtomicReference<>();CountDownLatch ready=new CountDownLatch(1);
        Messenger incoming=new Messenger(new Handler(Looper.getMainLooper()) {
            public void handleMessage(Message m){target.set(m.getData());ready.countDown();}
        });
        android.content.ServiceConnection connection=new android.content.ServiceConnection() {
            public void onServiceConnected(android.content.ComponentName name,IBinder binder) {
                Message m=Message.obtain();m.replyTo=incoming;
                try{new Messenger(binder).send(m);}catch(Exception e){Bundle error=new Bundle();error.putString("error",e.toString());target.set(error);ready.countDown();}
            }
            public void onServiceDisconnected(android.content.ComponentName name){}
        };
        boolean bound=getTargetContext().bindService(new android.content.Intent(getTargetContext(),BridgeTargetService.class),connection,android.content.Context.BIND_AUTO_CREATE);
        if(!bound)throw new AssertionError("second process not bound");
        try {
            if(!ready.await(5,TimeUnit.SECONDS))throw new AssertionError("second process not ready");
            Bundle targetData=target.get();if(targetData.containsKey("error"))throw new AssertionError(targetData.getString("error"));
            CoreSnapshot.Counter actual=new CoreSnapshot.Counter(targetData.getInt("pid"),targetData.getLong("pidStart"),targetData.getInt("tid"),targetData.getLong("tidStart"),"qa-cross-process",new long[8]);
            if(actual.pid==android.os.Process.myPid() || !TaskBridge.localIdentity(actual).code.equals("TARGET_PROCESS_MISMATCH"))throw new AssertionError("sender performed foreign identity read");
            CoreSnapshot.Counter wrong=new CoreSnapshot.Counter(actual.pid,actual.pidStart+1,actual.tid,actual.tidStart,actual.name,new long[8]);
            consider(foreignData(wrong,targetData.getLong("taskStart")));
            ProcessingHistory.Row rejected=awaitRow(TaskBridge.history.snapshot().get(0).id,2500);
            if(!rejected.diagnostic.contains("PROCESS_IDENTITY_CHANGED"))throw new AssertionError("reused process identity not rejected by owner "+rejected.detail);
            consider(foreignData(actual,targetData.getLong("taskStart")));
            ProcessingHistory.Row accepted=awaitRow(TaskBridge.history.snapshot().get(0).id,3500);
            if(!accepted.state.equals("任务已结束"))throw new AssertionError("cross-process cancellation failed "+accepted.state+" "+accepted.detail);
            CoreSnapshot.Counter exited=new CoreSnapshot.Counter(android.os.Process.myPid(),1,Integer.MAX_VALUE,1,"missing",new long[8]);
            if(!TaskBridge.localIdentity(exited).code.equals("THREAD_EXITED"))throw new AssertionError("exited TID not distinguished from read failure");
        }finally{getTargetContext().unbindService(connection);}
    }
    private CoreTracker.Result foreignData(CoreSnapshot.Counter target,long window) {
        CoreSnapshot snapshot=new CoreSnapshot("cross",android.os.Process.myUid(),1,SystemClock.elapsedRealtime(),System.nanoTime(),true,0,"fixture",new long[8],Arrays.asList(target));
        return new CoreTracker.Result(250,new double[8],snapshot,window,Arrays.asList(new CoreTracker.Detail(target,60)),"fixture");
    }
    private CoreTracker.Result data(int tid, long windowStart) throws Exception {
        int pid=android.os.Process.myPid();
        QqCpuTracker.Reading p=QqCpuTracker.Reading.parse(pid,"QA",CoreCollector.read(new File("/proc/self/stat")));
        ThreadCpuTracker.Reading t=ThreadCpuTracker.Reading.parse(pid,tid,"QA",CoreCollector.read(new File("/proc/self/task/"+tid+"/stat")));
        CoreSnapshot.Counter counter=new CoreSnapshot.Counter(pid,p.startTicks,tid,t.startTicks,"qa-diagnostics",new long[8]);
        CoreSnapshot snap=new CoreSnapshot("qa",android.os.Process.myUid(),1,SystemClock.elapsedRealtime(),System.nanoTime(),true,0,"CONTROL FIXTURE",new long[8],Arrays.asList(counter));
        return new CoreTracker.Result(250,new double[]{80,70,60,40,0,0,0,0},snap,windowStart,
                Arrays.asList(new CoreTracker.Detail(counter,60)),"CONTROL FIXTURE");
    }
    private void missingTrial(boolean mapOwner, boolean newerTask) throws Exception {
        SystemClock.sleep(3100); // Production stack-capture limiter is intentionally exercised.
        AtomicBoolean release=new AtomicBoolean();AtomicInteger tid=new AtomicInteger();CountDownLatch began=new CountDownLatch(1);
        long window=System.nanoTime();
        Thread worker=new Thread(() -> {
            tid.set(android.os.Process.myTid()); TaskRegistry.Entry task=null;
            if(mapOwner)TaskBridge.tasks.diagnostics.observe(tid.get(),Thread.currentThread(),"owner-fixture");
            if(newerTask)task=TaskBridge.tasks.begin(tid.get(),Thread.currentThread(),new Runnable(){public void run(){}},System.nanoTime(),"newer-task-fixture");
            began.countDown();
            try {while(!release.get()){try{Thread.sleep(10);}catch(InterruptedException unexpected){throw new AssertionError("diagnostics interrupted unknown task");}}}
            finally{TaskBridge.tasks.end(task);}
        },"qa-diagnostics");
        worker.start();
        try {
            if(!began.await(3,TimeUnit.SECONDS))throw new AssertionError("diagnostics task not started");
            consider(data(tid.get(),window));
            ProcessingHistory.Row first=TaskBridge.history.snapshot().get(0);
            long deadline=SystemClock.elapsedRealtime()+3500; ProcessingHistory.Row row=first;
            while(SystemClock.elapsedRealtime()<deadline){row=TaskBridge.history.find(first.id);if(!row.state.equals("等待处理"))break;SystemClock.sleep(30);}
            if(!row.state.equals(newerTask?"任务已变化":"无法处理"))throw new AssertionError("wrong diagnostic state "+row.state+" "+row.detail);
            if(!worker.isAlive() || worker.isInterrupted())throw new AssertionError("unknown task altered");
        } finally {release.set(true);worker.join(2000);}
    }
    private void verifyExport() throws Exception {
        CountDownLatch finished=new CountDownLatch(1);AtomicReference<String> path=new AtomicReference<String>(),error=new AtomicReference<String>();
        if(!WatchLog.export(getTargetContext(),"QQ DIAGNOSTICS EXPORT FIXTURE\n",(p,e)->{path.set(p);error.set(e);finished.countDown();}))throw new AssertionError("export not queued");
        if(!finished.await(8,TimeUnit.SECONDS) || path.get()==null)throw new AssertionError("export failed "+error.get());
        String name=path.get().substring(path.get().lastIndexOf('/')+1);
        android.content.ContentResolver resolver=getTargetContext().getContentResolver();android.net.Uri uri=null;
        try(android.database.Cursor c=resolver.query(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                new String[]{"_id"},"_display_name=?",new String[]{name},null)){
            if(c!=null && c.moveToFirst())uri=android.content.ContentUris.withAppendedId(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,c.getLong(0));
        }
        if(uri==null)throw new AssertionError("published export missing");
        try {
            java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
            try(java.io.InputStream in=resolver.openInputStream(uri)){byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1)bytes.write(buffer,0,n);}
            String log=new String(bytes.toByteArray(),java.nio.charset.StandardCharsets.UTF_8);
            for(String wanted:new String[]{"[DIAG]","[EXEC]","code=CANCEL_NO_RETURN","code=NO_ACTIVE_TASK","code=TASK_WINDOW_MISMATCH","QQ合计=250.0% 目标线程=60.0%","javaThread=qa-diagnostics","javaStack=未建立","owner-fixture","BridgeCheck","PeriodicWork.run","schedule=fixed-rate","[执行入口]","全部核心参考","HANDLER_NO_CANCEL_INTERFACE","HANDLER_TASK_NOT_ACTIVE","messageWhat=314","PROCESS_IDENTITY_CHANGED","identityOwner=target-process"})
                if(!log.contains(wanted))throw new AssertionError("missing exported evidence "+wanted);
            if(log.contains("qa-bridge") && log.contains("任务已结束"))throw new AssertionError("processing success table unexpectedly exported");
        }finally{resolver.delete(uri,null,null);}
    }
    private static final class PeriodicWork implements Runnable {
        final AtomicInteger tid = new AtomicInteger(), runs = new AtomicInteger();
        public void run() {
            tid.set(android.os.Process.myTid());
            TaskExecutions.Body body=TaskBridge.executions.beginBody(this,"run",tid.get(),Thread.currentThread(),System.nanoTime(),Debug.threadCpuTimeNanos());
            try {long end=Debug.threadCpuTimeNanos()+2000000;while(Debug.threadCpuTimeNanos()<end)Thread.yield();runs.incrementAndGet();}
            finally {TaskBridge.executions.endBody(body,System.nanoTime(),Debug.threadCpuTimeNanos());}
        }
    }
    private void executionTrial() throws Exception {
        PeriodicWork work=new PeriodicWork();
        ScheduledThreadPoolExecutor pool=new ScheduledThreadPoolExecutor(1,r->new Thread(r,"qa-exec-periodic")) {
            protected <V> RunnableScheduledFuture<V> decorateTask(Runnable task,RunnableScheduledFuture<V> delegate) {
                // Admission metadata is supplied exactly as the production public-API hook does.
                TaskBridge.executions.scheduled(delegate,40000000);
                return new RunnableScheduledFuture<V>() {
                    public void run() {
                        TaskExecutions.Scope scope=TaskBridge.executions.begin(delegate,android.os.Process.myTid(),Thread.currentThread(),System.nanoTime(),Debug.threadCpuTimeNanos(),"ScheduledFutureTask.run-fixture");
                        try {delegate.run();} finally {TaskBridge.executions.end(scope,System.nanoTime(),Debug.threadCpuTimeNanos());}
                    }
                    public boolean isPeriodic(){return delegate.isPeriodic();}
                    public long getDelay(TimeUnit unit){return delegate.getDelay(unit);}
                    public int compareTo(Delayed d){return delegate.compareTo(d);}
                    public boolean cancel(boolean interrupt){return delegate.cancel(interrupt);}
                    public boolean isCancelled(){return delegate.isCancelled();}
                    public boolean isDone(){return delegate.isDone();}
                    public V get()throws InterruptedException,ExecutionException{return delegate.get();}
                    public V get(long timeout,TimeUnit unit)throws InterruptedException,ExecutionException,TimeoutException{return delegate.get(timeout,unit);}
                };
            }
        };
        long window=System.nanoTime();
        ScheduledFuture<?> scheduled=pool.scheduleAtFixedRate(work,0,40,TimeUnit.MILLISECONDS);
        try {
            long deadline=SystemClock.elapsedRealtime()+2500;while(work.runs.get()<6 && SystemClock.elapsedRealtime()<deadline)SystemClock.sleep(20);
            if(work.runs.get()<6)throw new AssertionError("periodic executions not observed");
            scheduled.cancel(false);SystemClock.sleep(100);
            TaskExecutions.Report report=TaskBridge.executions.describe(work.tid.get(),window,System.nanoTime());
            if(!report.text.contains("schedule=fixed-rate") || !report.text.contains("PeriodicWork.run") || !report.text.contains("[执行入口]"))throw new AssertionError(report.text);
            CoreTracker.Result data=data(work.tid.get(),window);
            int before=TaskBridge.history.snapshot().size();TaskBridge.logExecutions(data);
            SystemClock.sleep(200);
            if(TaskBridge.history.snapshot().size()!=before)throw new AssertionError("diagnostic query caused handling row");
            consider(data);String id=TaskBridge.history.snapshot().get(0).id;
            deadline=SystemClock.elapsedRealtime()+2500;ProcessingHistory.Row row;
            do {row=TaskBridge.history.find(id);if(!row.state.equals("等待处理"))break;SystemClock.sleep(20);}while(SystemClock.elapsedRealtime()<deadline);
            if(!row.state.equals("无法处理") || row.threshold!=200 || !row.fullName.equals("qa-exec-periodic") || !row.executionText.contains("schedule=fixed-rate"))throw new AssertionError("execution evidence/table reply incomplete "+row.executionText);
            if(pool.getPoolSize()!=1)throw new AssertionError("idle worker changed");
        } finally {pool.shutdownNow();pool.awaitTermination(2,TimeUnit.SECONDS);}
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
            int before=TaskBridge.history.snapshot().size();consider(data);
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
