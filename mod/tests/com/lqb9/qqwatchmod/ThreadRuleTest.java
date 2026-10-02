package com.lqb9.qqwatchmod;

import java.util.*;
import java.util.concurrent.FutureTask;

public final class ThreadRuleTest {
    private static int checks;
    private static void check(boolean b,String message){if(!b)throw new AssertionError(message);checks++;}
    private static CpuLoadMonitor.Settings s(int seconds,boolean handle){return new CpuLoadMonitor.Settings(true,200,2,1,"stop_task",15,true,true,80,seconds,handle);}
    private static CoreSnapshot.Counter t(int tid,long identity,String name){return new CoreSnapshot.Counter(100,1,tid,identity,name,new long[8]);}
    private static CoreTracker.Result data(long start,long end,String session,int fg,double total,CoreTracker.Detail...threads){
        List<CoreSnapshot.Counter> counters=new ArrayList<>();for(CoreTracker.Detail d:threads)counters.add(d.thread);
        CoreSnapshot snap=new CoreSnapshot(session,1000,end,end,end*1000000L,true,fg,"fixture",new long[8],counters);
        return new CoreTracker.Result(total,new double[8],snap,start*1000000L,Arrays.asList(threads),"fixture");
    }
    private static ThreadLoadMonitor.Result step(ThreadLoadMonitor m,long start,long end,CpuLoadMonitor.Settings s,CoreTracker.Detail...threads){
        return m.sample(data(start,end,"one",0,120,threads),s,end,0);
    }
    private static ThreadLoadMonitor.Candidate c(ThreadLoadMonitor.Result r,int tid){for(ThreadLoadMonitor.Candidate c:r.high)if(c.hot.thread.tid==tid)return c;throw new AssertionError("missing candidate");}
    public static void main(String[] args)throws Exception {
        CpuLoadMonitor.Settings defaults=SettingsCodec.decode("cpu=285\nduration=3\ninterval=2\ncores=255",true,1);
        check(defaults.threadRule && !defaults.threadHandle && defaults.threadThreshold==80 && defaults.threadDuration==10,"new rule observes by default without automatic action");
        check(defaults.threshold==285 && defaults.durationSeconds==3 && defaults.intervalSeconds==2,"old settings unchanged");
        check(defaults.sameAs(SettingsCodec.decode(SettingsCodec.encode(defaults),true,1)),"all new fields roundtrip");
        check(SettingsCodec.encode(new CpuLoadMonitor.Settings(true,10000,3600,3600,"stop_task",255,true,false,100,3600,true)).getBytes("UTF-8").length<=256,"configuration fits existing persistence bound");
        check(SettingsCodec.decode("threadCpu=999\nthreadDuration=-1",true,1).threadThreshold==100,"single threshold clamp");
        check(SettingsCodec.decode("threadCpu=bad\nthreadDuration=bad",true,1).threadDuration==10,"bad fields fallback");
        check(!s(2,false).sameAs(s(2,true)),"action mode participates in configuration identity");
        CoreTracker.Detail a=new CoreTracker.Detail(t(101,1,"pool-worker"),90),b=new CoreTracker.Detail(t(102,1,"other-worker"),85);
        ThreadLoadMonitor m=new ThreadLoadMonitor();
        ThreadLoadMonitor.Result first=step(m,1000,2000,s(2,false),a);
        check(c(first,101).highMs==1000 && !c(first,101).ready,"initial full valid window counted");
        ThreadLoadMonitor.Result second=step(m,2000,3000,s(2,false),a,b);
        check(c(second,101).ready && c(second,101).trigger && c(second,102).highMs==1000,"threads have independent clocks");
        check(!c(step(m,3000,4000,s(2,false),a),101).trigger,"record trigger once per continuous streak");
        check(step(m,4000,5000,s(2,false),new CoreTracker.Detail(a.thread,80)).high.isEmpty(),"equal threshold resets");
        check(c(step(m,5000,6000,s(2,false),a),101).highMs==1000,"rebound starts new streak");
        check(c(step(m,6000,7000,s(2,false),new CoreTracker.Detail(t(101,2,"pool-worker"),90)),101).highMs==1000,"TID reuse resets");
        check(c(step(m,8000,9000,s(2,false),a),101).highMs==1000,"missing window resets");
        check(step(m,9000,13000,s(2,false),a).high.isEmpty(),"long gap rejected");
        check(step(m,13000,14000,s(2,false),new CoreTracker.Detail(t(100,1,"main"),95),new CoreTracker.Detail(t(105,1,"qqwatch-dog"),95),new CoreTracker.Detail(t(106,1,"RenderThread"),95)).high.isEmpty(),"protected targets excluded");
        check(step(m,14000,15000,s(2,false),new CoreTracker.Detail(a.thread,Double.NaN)).high.isEmpty(),"NaN rejected");
        check(c(step(m,15000,16000,s(0,false),a),101).ready,"zero duration immediate valid window");
        check(m.sample(data(16000,17000,"one",-1,120,a),s(2,false),17000,0).high.isEmpty(),"unknown foreground resets");
        check(c(step(m,17000,18000,s(2,false),a),101).highMs==1000,"foreground recovery rebuilds streak");
        check(c(m.sample(data(18000,19000,"new",0,120,a),s(2,false),19000,0),101).highMs==1000,"collector session resets");
        check(m.sample(data(19000,20000,"new",0,120,a),s(2,false),25000,0).high.isEmpty(),"stale rejected");
        check(m.sample(data(20000,21000,"one",0,120,a),s(2,false),21000,22000).high.isEmpty(),"startup guard resets");
        step(m,21000,22000,s(2,false),a);check(m.sample(data(21000,22000,"one",0,120,a),s(2,false),22000,0).high.isEmpty(),"duplicate does not count twice");
        check(c(step(m,22000,23000,s(2,false),a),101).highMs==2000,"duplicate leaves prior clock intact");
        CpuLoadMonitor.Settings off=new CpuLoadMonitor.Settings(true,200,2,1,"stop_task",15,true,false,80,2,false);
        check(step(m,23000,24000,off,a).high.isEmpty() && m.size()==0,"detector off resets");
        step(m,24000,25000,s(2,false),a);step(m,25000,26000,s(2,false));check(m.size()==0,"absent thread resets");
        List<CoreTracker.Detail> many=new ArrayList<>();for(int i=0;i<1100;i++)many.add(new CoreTracker.Detail(t(1000+i,1,"worker"),90));
        step(m,26000,27000,s(2,false),many.toArray(new CoreTracker.Detail[0]));check(m.size()==ThreadLoadMonitor.MAX_THREADS,"bounded counters");
        // Actual core counters: the same thread migrates between selected CPUs. Its summed occupancy remains 90%.
        CoreTracker tracker=new CoreTracker();CoreSnapshot.Counter ca=new CoreSnapshot.Counter(100,1,101,1,"moving",new long[8]);
        CoreSnapshot old=new CoreSnapshot("move",1000,1,1000,1000000000L,true,0,"ok",new long[8],Arrays.asList(ca));
        long[] moved={300000000L,300000000L,300000000L,0,0,0,0,0};
        CoreSnapshot next=new CoreSnapshot("move",1000,2,2000,2000000000L,true,0,"ok",moved,Arrays.asList(new CoreSnapshot.Counter(100,1,101,1,"moving",moved)));
        tracker.sample(old,15,1000,1000,1);CoreTracker.Result movedResult=tracker.sample(next,15,1000,2000,1);
        check(movedResult.threads.get(0).cpu==90 && movedResult.cores[0]==30,"migration occupancy summed without per-core rule");
        check(new ThreadLoadMonitor().sample(movedResult,s(0,false),2000,0).readyCount()==1,"migration detected despite each core below threshold");
        CoreTracker.Result high=data(1000,2000,"one",0,250,a);
        CpuLoadMonitor.Sample aggregate=new CpuLoadMonitor.Sample(s(2,true),2000,250,2000,true,"reported");
        ThreadLoadMonitor.Candidate qualified=new ThreadLoadMonitor.Candidate(a,1000000000L,2000,true,true);
        ThreadLoadMonitor.Result single=new ThreadLoadMonitor.Result(Arrays.asList(qualified),"ok");
        List<DualLoadPolicy.Target> merged=DualLoadPolicy.targets(high,s(2,true),aggregate,single,1000);
        check(merged.size()==1 && merged.get(0).sources()==3,"same target merged");
        check(merged.get(0).reason(high,s(2,true),aggregate).contains("合计规则") && merged.get(0).reason(high,s(2,true),aggregate).contains("单线程规则"),"both reasons retained");
        CoreTracker.Result low=data(1000,2000,"one",0,120,a);
        List<DualLoadPolicy.Target> independent=DualLoadPolicy.targets(low,s(2,true),aggregate,single,1000);
        check(independent.size()==1 && independent.get(0).sources()==2 && independent.get(0).action(s(2,true)),"single handles without total threshold");
        check(!independent.get(0).action(s(2,false)) && independent.get(0).report(s(2,false)),"record mode does not stop");
        check(DualLoadPolicy.targets(low,s(2,true),aggregate,single,1001).isEmpty(),"foreign UID rejected");
        check(DualLoadPolicy.qualifies(2,s(2,true),120,0,90,2000),"single receiver qualification independent");
        check(!DualLoadPolicy.qualifies(2,s(2,true),120,0,90,1999),"duration required");
        check(!DualLoadPolicy.qualifies(3,s(2,true),120,2000,90,2000),"invalid advertised total rejected");
        check(!DualLoadPolicy.qualifies(8,s(2,true),250,2000,90,2000),"unknown source rejected");
        ThreadTaskAttribution attribution=new ThreadTaskAttribution();Object business=new Object(),replacement=new Object();
        ThreadTaskAttribution.Binding fixed=new ThreadTaskAttribution.Binding(business,null,"task");
        check(attribution.observe("thread","one",fixed,1000000000L,2000000000L,s(2,true))==1000,"first business window");
        check(attribution.observe("thread","one",fixed,2000000000L,3000000000L,s(2,true))==2000,"same business accumulates");
        check(attribution.observe("thread","one",fixed,2000000000L,3000000000L,s(2,true))==2000,"business duplicate ignored");
        check(attribution.observe("thread","one",new ThreadTaskAttribution.Binding(replacement,null,"new"),3000000000L,4000000000L,s(2,true))==1000,"task switch resets business time");
        check(attribution.observe("thread","one",new ThreadTaskAttribution.Binding(null,null,"unknown"),4000000000L,5000000000L,s(2,true))==0,"unknown business clears qualification");
        ThreadTaskAttribution.Binding gif=new ThreadTaskAttribution.Binding(null,business,"gif");
        attribution.observe("thread","one",gif,5000000000L,6000000000L,s(2,true));
        check(attribution.observe("thread","one",new ThreadTaskAttribution.Binding(null,business,"new short future, same GIF"),6000000000L,7000000000L,s(2,true))==2000,"short re-submissions retain same owner");
        check(attribution.observe("thread","two",gif,7000000000L,8000000000L,s(2,true))==1000,"business session reset");
        check(attribution.observe("thread","two",gif,8000000000L,9000000000L,s(3,true))==1000,"business settings reset");
        check(attribution.observe("thread","two",gif,10000000000L,11000000000L,s(3,true))==1000,"business missing window reset");
        for(int i=0;i<600;i++)attribution.observe("key"+i,"one",fixed,1000000000L,2000000000L,s(2,true));
        check(attribution.size()==512,"business bounds");
        TaskRegistry tasks=new TaskRegistry();TaskExecutions executions=new TaskExecutions();
        FutureTask<Void> original=new FutureTask<>(()->null),newTask=new FutureTask<>(()->null);
        TaskRegistry.Entry e=tasks.begin(101,Thread.currentThread(),original,100000000L);
        check(ThreadTaskAttribution.identify(tasks,executions,101,1000000000L,2000000000L,90,90,s(2,true)).task==e,"covering active task identified");
        tasks.end(e);TaskRegistry.Entry newer=tasks.begin(101,Thread.currentThread(),newTask,100000000L);
        HandlingDecision changed=new FutureHandlingRule(tasks,executions).apply(new HandlingRequest("changed",101,1000000000L,2000000000L,90,90,s(2,true),e,null));
        check(changed.code.equals("THREAD_BUSINESS_CHANGED") && !newTask.isCancelled(),"switch before action never cancels new task");
        tasks.end(newer);
        ProcessingHistory.Row row=new ProcessingHistory.Row("row",1,a.thread,15,120,90,200,"single","仅记录","","","","").rule("单线程","80%/2秒");
        check(row.state("未处理","reason").evidence("full","summary","text").diagnostic("detail").rule.equals("单线程"),"row updates preserve trigger source");
        check(row.state("未处理","reason").ruleParameters.equals("80%/2秒"),"row preserves settings at trigger");
        System.out.println("PASS "+checks+" independent thread streak, unified policy and continuous business checks");
    }
}
