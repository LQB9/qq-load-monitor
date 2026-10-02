package com.lqb9.qqwatchmod;

import java.util.*;
import java.util.concurrent.*;
import com.tencent.libra.extension.gif.*;

public final class GifTaskRuleTest {
    static int checks;
    static void check(boolean condition,String note) {checks++;if(!condition)throw new AssertionError(note);}
    static CpuLoadMonitor.Settings settings(boolean gif) {return new CpuLoadMonitor.Settings(true,130,2,1,"stop_task",15,gif);}
    static final class ChildDrawable extends GifDrawable {}
    public static void main(String[] args) throws Exception {
        GifTaskRule rule=new GifTaskRule(); GifDrawable owner=new GifDrawable();RenderTask render=new RenderTask(owner);
        check(rule.adapter(new Object())==null,"unknown class never admitted");
        check(rule.adapter(render).owner(render)==owner,"owner field resolved by type through superclass");
        check(!rule.pause("unsupported",Arrays.<Object>asList(render),settings(true)).state.equals("GIF已暂停"),"missing restart/recycle hooks rejected");
        rule.adapter(render).hooksReady=true;
        check(settings(true).gifRule && !settings(true).sameAs(settings(false)),"GIF toggle participates in configuration identity");
        check(rule.pause("disabled",Arrays.<Object>asList(render),settings(false)).state.equals("未处理") && owner.stops==0,"disabled rule does not stop");
        GifTaskRule.Run inFlight=rule.enter(render);
        check(inFlight!=null && !inFlight.blocked,"normal business admitted");
        GifTaskRule.Result result=rule.pause("live",Arrays.<Object>asList(render),settings(true));
        check(result.state.equals("GIF待确认") && !owner.playing,"stop flag is not confused with renderer return");
        check(rule.blockStart(owner),"same owner restart gated");
        check(!rule.blockStart(new GifDrawable()),"unrelated GIF unaffected");
        GifTaskRule.Run denied=rule.enter(render);
        check(denied.blocked,"same owner's replacement task blocked even after old Future ends");
        rule.exit(denied);check(rule.status("live").state.equals("GIF待确认"),"blocked invocation cannot decrement active rendering");
        rule.exit(inFlight);check(rule.status("live").state.equals("GIF已暂停"),"confirmed only after original render returns");
        check(rule.status("live").detail.contains("后续渲染=1"),"blocked entry counter recorded");
        check(rule.restore(settings(true),false).isEmpty(),"same configuration retains pause despite reduced CPU");
        List<GifTaskRule.Result> restored=rule.restore(settings(true),true);
        check(restored.size()==1 && restored.get(0).state.equals("GIF已解除暂停") && owner.playing && owner.starts==1,"manual visible resume calls verified start API");
        check(!rule.blockStart(owner) && rule.pausedCount()==0,"restore removes restart guard");
        owner.visible=false;rule.pause("hidden",Arrays.<Object>asList(render),settings(true));
        restored=rule.restore(settings(false),false);
        check(restored.size()==1 && !owner.playing && owner.starts==1,"disabled rule clears hidden owner without replay");
        owner.visible=true;owner.playing=true;rule.pause("disposed",Arrays.<Object>asList(render),settings(true));
        rule.recycled(owner);owner.recycle();restored=rule.restore(settings(true),false);
        check(restored.size()==1 && owner.starts==1,"recycled owner pruned and never resumed");
        GifDrawable faulty=new GifDrawable();faulty.failStop=true;RenderTask bad=new RenderTask(faulty);
        check(rule.pause("failed",Arrays.<Object>asList(bad),settings(true)).state.equals("GIF处理失败"),"stop exception explicit");
        faulty.failStop=false;rule.restore(settings(true),true);
        GifDrawable busyOwner=new GifDrawable();RenderTask busyRender=new RenderTask(busyOwner);
        GifTaskRule.Run busyRun=rule.enter(busyRender);rule.pause("busy-restore",Arrays.<Object>asList(busyRender),settings(true));
        restored=rule.restore(settings(true),true);
        check(restored.get(0).detail.contains("渲染未返回=1") && busyOwner.starts==0 && !rule.blockStart(busyOwner),"restore clears guard without restarting in-flight decoder");
        rule.exit(busyRun);
        GifDrawable ignoring=new GifDrawable();ignoring.ignoreStop=true;
        check(rule.pause("ignored",Arrays.<Object>asList(new RenderTask(ignoring)),settings(true)).state.equals("GIF待确认"),"stop returns without changing playback cannot report success");
        ignoring.ignoreStop=false;rule.restore(settings(true),true);
        check(rule.pause("subclass",Arrays.<Object>asList(new RenderTask(new ChildDrawable())),settings(true)).state.equals("未处理"),"unverified owner override rejected");
        check(rule.pause("null",Arrays.<Object>asList(new RenderTask(null)),settings(true)).state.equals("未处理"),"missing owner rejected");
        GifDrawable a=new GifDrawable(),b=new GifDrawable(),c=new GifDrawable(),d=new GifDrawable();
        result=rule.pause("bounded",Arrays.<Object>asList(new RenderTask(a),new RenderTask(b),new RenderTask(c),new RenderTask(d)),settings(true));
        check(result.state.equals("GIF已暂停") && a.stops==1 && b.stops==1 && c.stops==1 && d.stops==0,"maximum three distinct owners per action");
        rule.restore(settings(true),true);
        for(int i=0;i<32;i++)check(rule.pause("limit"+i,Arrays.<Object>asList(new RenderTask(new GifDrawable())),settings(true)).state.equals("GIF已暂停"),"bounded pause "+i);
        check(rule.pause("overflow",Arrays.<Object>asList(new RenderTask(new GifDrawable())),settings(true)).state.equals("未处理"),"32-owner limit enforced");
        rule.restore(settings(true),true);
        // Actual executed body CPU is attributed through many completed short Future instances.
        TaskExecutions x=new TaskExecutions();Thread thread=Thread.currentThread(); RenderTask shared=new RenderTask(new GifDrawable());
        long start=1000000000L;
        for(int i=0;i<20;i++) {
            FutureTask<Void> future=new FutureTask<Void>(shared,null);
            long t=start+i*40000000L;
            TaskExecutions.Scope scope=x.begin(future,91,thread,t,0,"fixture-short-wrapper");
            TaskExecutions.Body body=x.beginBody(shared,"run",91,thread,t+1,1);
            x.endBody(body,t+30000000L,20000000L);x.end(scope,t+30000001L,20000001L);
        }
        TaskExecutions.GifEvidence evidence=x.gifEvidence(91,start,start+1000000000L);
        check(evidence.renders.size()==1 && evidence.renders.get(0)==shared,"replacement Future instances aggregate same render owner");
        check(evidence.dominant(60) && !evidence.dominant(90),"dominance compares observed business CPU against full-core thread CPU");
        check(!evidence.dominant(Double.NaN) && !evidence.dominant(-1),"invalid attribution reference rejected");
        check(evidence.dominatesSelected(60,60),"full selected set retains dominant GIF attribution");
        check(!evidence.dominatesSelected(20,60),"GIF possibly entirely on unselected cores cannot justify action");
        check(!evidence.dominatesSelected(30,60) && evidence.dominatesSelected(45,60),"conservative selected-core share must reach half the selected thread load");
        check(!evidence.dominatesSelected(Double.NaN,60) && !evidence.dominatesSelected(70,60),"invalid selected/all-core relationship rejected");
        check(x.gifEvidence(92,start,start+1000000000L).renders.isEmpty(),"foreign TID history excluded");
        check(x.gifEvidence(91,start+2000000000L,start+3000000000L).renders.isEmpty(),"old history outside CPU window excluded");
        check(!x.gifEvidence(91,start,start+100000000L).dominant(100),"partial short CPU window rejected");
        TaskExecutions.Scope wrapper=x.begin(new RenderTask(new GifDrawable()),93,thread,start,0,"wrapper-only");
        x.end(wrapper,start+800000000L,700000000L);
        check(x.gifEvidence(93,start,start+1000000000L).renders.isEmpty(),"class name without observed body CPU cannot activate rule");
        check(x.gifEvidence(91,start+50000000L,start+1000000000L).cpuNs<evidence.cpuNs,"cross-boundary execution not allocated");
        System.out.println("PASS "+checks+" GIF object rule and completed-window attribution checks");
    }
}
