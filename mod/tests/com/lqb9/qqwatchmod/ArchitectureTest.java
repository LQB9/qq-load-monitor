package com.lqb9.qqwatchmod;

import com.tencent.libra.extension.gif.GifDrawable;
import com.tencent.libra.extension.gif.RenderTask;
import java.util.Arrays;
import java.util.concurrent.FutureTask;

/** Regression invariants across settings migration, trigger policy and ordered business strategies. */
public final class ArchitectureTest {
    private static int checks;
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    private static CpuLoadMonitor.Settings settings(boolean gif){return new CpuLoadMonitor.Settings(true,200,2,1,"stop_task",15,gif);}
    private static CoreSnapshot.Counter thread(int pid,int tid,String name){return new CoreSnapshot.Counter(pid,1,tid,1,name,new long[8]);}
    private static CoreTracker.Result data(double cpu,int foreground,CoreSnapshot.Counter...threads) {
        CoreSnapshot snapshot=new CoreSnapshot("policy",1000,2,2000,3000000000L,true,foreground,"fixture",new long[8],Arrays.asList(threads));
        java.util.List<CoreTracker.Detail> details=new java.util.ArrayList<>();for(CoreSnapshot.Counter t:threads)details.add(new CoreTracker.Detail(t,60));
        return new CoreTracker.Result(cpu,new double[8],snapshot,2000000000L,details,"fixture");
    }
    public static void main(String[] args)throws Exception {
        CpuLoadMonitor.Settings old=SettingsCodec.decode("",true,7);
        check(old.enabled && old.threshold==200 && old.durationSeconds==0 && old.intervalSeconds==7 && old.coreMask==255 && old.gifRule,"fresh migration retains only legacy interval and correct defaults");
        CpuLoadMonitor.Settings saved=SettingsCodec.decode("cpu=285\nduration=3\ninterval=2\ncores=255\naction=stop_task",true,7);
        check(saved.threshold==285 && saved.durationSeconds==3 && saved.intervalSeconds==2 && saved.gifRule,"installed legacy file survives without new GIF key");
        check(saved.sameAs(SettingsCodec.decode(SettingsCodec.encode(saved),true,99)),"roundtrip preserves every stored setting");
        check(!SettingsCodec.decode(SettingsCodec.encode(saved),false,7).enabled,"enable flag remains independent of properties");
        check(SettingsCodec.decode("cpu=bad\nduration=bad\ninterval=bad\ncores=bad",true,7).intervalSeconds==1,"malformed numbers use previous fallback rules");
        check(!SettingsCodec.decode("gifRule=false",true,7).gifRule,"explicit GIF disable retained");
        check(!SettingsCodec.decode("cpu=\\uZZZZ",true,7).enabled,"malformed properties fail closed");
        CpuLoadMonitor.Settings clamped=SettingsCodec.decode("cpu=20000\nduration=9000\ninterval=-1\ncores=0",true,7);
        check(clamped.threshold==10000 && clamped.durationSeconds==3600 && clamped.intervalSeconds==1 && clamped.coreMask==255,"settings keep original clamping and empty-core fallback");
        CpuLoadMonitor.Settings s=settings(true);CpuLoadMonitor monitor=new CpuLoadMonitor();
        monitor.sample(-1,0,s,0);CpuLoadMonitor.Sample shortHigh=monitor.sample(250,1000,s,0),ready=monitor.sample(250,2000,s,0);
        CoreSnapshot.Counter main=thread(10,10,"main"),system=thread(10,11,"RenderThread"),worker=thread(10,12,"pool-1-thread-");
        CoreTracker.Result high=data(250,0,main,system,worker);
        check(!LoadPolicy.ready(high,s,shortHigh,1000) && LoadPolicy.ready(high,s,ready,1000),"rule extraction preserves required continuous duration");
        check(!LoadPolicy.ready(data(200,0,worker),s,new CpuLoadMonitor.Sample(s,2000,200,2000,true,"high"),1000),"exact threshold does not trigger");
        check(!LoadPolicy.ready(high,s,ready,1001),"foreign UID cannot trigger");
        check(!LoadPolicy.ready(high,settings(false),ready,1000),"configuration mismatch invalidates old action decision");
        check(LoadPolicy.target(high).thread==worker,"selection skips main and protected system workers");
        check(LoadPolicy.unavailable(worker,-1,true).contains("前后台未确认"),"unknown foreground remains a processing pause");
        check(LoadPolicy.unavailable(worker,0,false).contains("控制通道"),"unavailable control remains explicit");
        check(LoadPolicy.reason(high,s,ready).equals("QQ 后台超限 · 持续2秒，要求2秒"),"background reason includes the same duration");
        check(LoadPolicy.reason(data(250,1,worker),s,ready).startsWith("QQ 前台超限"),"foreground retains the same action policy");

        TaskRegistry tasks=new TaskRegistry();TaskExecutions executions=new TaskExecutions();GifTaskRule gif=new GifTaskRule();
        TaskRuleRegistry rules=new TaskRuleRegistry(tasks,executions,gif);
        check(rules.names().equals("GIF对象暂停 / Future协作取消"),"explicit strategy order and diagnostics stay stable");
        HandlingRequest missing=new HandlingRequest("none",31,1000000000L,2000000000L,60,60,s);
        check(rules.apply(missing).code.equals("NO_ACTIVE_TASK"),"unknown work remains unsupported through registry");
        FutureTask<Void> future=new FutureTask<>(() -> null);
        TaskRegistry.Entry entry=tasks.begin(32,Thread.currentThread(),future,1500000000L,"fixture");
        check(rules.apply(new HandlingRequest("newer",32,1000000000L,2000000000L,60,60,s)).code.equals("TASK_WINDOW_MISMATCH") && !future.isCancelled(),"registry never cancels a replacement task");
        HandlingDecision pending=rules.apply(new HandlingRequest("future",32,1600000000L,2000000000L,60,60,s));
        check(pending.state.equals("待确认") && pending.task==entry && future.isCancelled() && !entry.ended,"cooperative request is not claimed as completion");
        tasks.end(entry);

        GifDrawable owner=new GifDrawable();RenderTask render=new RenderTask(owner);gif.adapter(render).hooksReady=true;
        long start=1000000000L;
        for(int i=0;i<20;i++) {
            long t=start+i*40000000L;FutureTask<Void> wrapper=new FutureTask<>(render,null);
            TaskExecutions.Scope scope=executions.begin(wrapper,33,Thread.currentThread(),t,0,"fixture");
            TaskExecutions.Body body=executions.beginBody(render,"run",33,Thread.currentThread(),t+1,1);
            executions.endBody(body,t+30000000L,20000000L);executions.end(scope,t+30000001L,20000001L);
        }
        FutureTask<Void> other=new FutureTask<>(() -> null);
        TaskRegistry.Entry otherEntry=tasks.begin(33,Thread.currentThread(),other,start-1,"fixture-other");
        HandlingDecision rejected=rules.apply(new HandlingRequest("unselected",33,start,start+1000000000L,20,60,s));
        check(rejected.code.equals("GIF_SELECTED_ATTRIBUTION_INSUFFICIENT") && !other.isCancelled() && owner.stops==0,"GIF attribution rejection never falls through to cancel another business");
        HandlingDecision paused=rules.apply(new HandlingRequest("gif",33,start,start+1000000000L,60,60,s));
        check(paused.gif!=null && paused.state.equals("GIF已暂停") && !other.isCancelled() && owner.stops==1,"completed GIF evidence takes precedence over an active replacement Future");
        gif.restore(s,true);
        HandlingDecision disabled=rules.apply(new HandlingRequest("gif-disabled",33,start,start+1000000000L,60,60,settings(false)));
        check(disabled.gif==null && disabled.state.equals("待确认") && other.isCancelled() && owner.stops==1,"disabling GIF uses original Future strategy without pausing objects");
        tasks.end(otherEntry);
        System.out.println("PASS "+checks+" architecture settings/policy/strategy invariants");
    }
}
