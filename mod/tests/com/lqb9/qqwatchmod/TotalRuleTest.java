package com.lqb9.qqwatchmod;
import java.util.*;

public final class TotalRuleTest {
    static int checks;static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;}
    static CpuLoadMonitor.Settings s(boolean total,boolean auto,boolean single,boolean singleAuto){return new CpuLoadMonitor.Settings(true,200,2,1,"stop_task",15,true,single,80,2,singleAuto,total,auto);}
    static CoreTracker.Detail hot(int tid,long identity,double cpu){return new CoreTracker.Detail(new CoreSnapshot.Counter(100,1,tid,identity,"worker-"+tid,new long[8]),cpu);}
    static CoreTracker.Result data(long start,long end,String session,boolean valid,int foreground,CoreTracker.Detail...threads){return new CoreTracker.Result(valid?250:-1,new double[8],new CoreSnapshot(session,1000,end,end,end*1000000L,valid,foreground,"fixture",new long[8],Collections.emptyList()),start*1000000L,Arrays.asList(threads),"fixture");}
    public static void main(String[] args)throws Exception{
        CpuLoadMonitor.Settings old=SettingsCodec.decode("cpu=285\nduration=3\ninterval=2\ncores=255\nthreadHandle=true",true,1);
        check(old.totalRule && old.totalHandle && old.threadHandle,"legacy total behavior and single mode preserved");
        for(boolean rule:new boolean[]{true,false})for(boolean auto:new boolean[]{true,false}){
            CpuLoadMonitor.Settings settings=s(rule,auto,true,true);check(settings.sameAs(SettingsCodec.decode(SettingsCodec.encode(settings),true,1)),"independent total modes round trip");
            check(!settings.sameAs(s(!rule,auto,true,true)) && !settings.sameAs(s(rule,!auto,true,true)),"both switches participate in config identity");
        }
        check(SettingsCodec.encode(new CpuLoadMonitor.Settings(true,10000,3600,3600,"stop_task",255,false,false,100,3600,false,false,false)).getBytes("UTF-8").length<=256,"worst-case full configuration fits original read bound");
        CpuLoadMonitor monitor=new CpuLoadMonitor();CpuLoadMonitor.Settings record=s(true,false,true,true);
        monitor.sample(-1,0,record,0);check(!monitor.sample(250,1000,record,0).trigger,"record duration required");
        CpuLoadMonitor.Sample detected=monitor.sample(250,2000,record,0);check(detected.trigger && detected.detectedReady() && !detected.handlingReady(),"total record keeps detection without handling");
        check(!monitor.sample(250,3000,record,0).trigger,"sustained record is not repeatedly reported");
        CpuLoadMonitor.Settings off=s(false,true,true,true);CpuLoadMonitor.Sample paused=monitor.sample(250,4000,off,0);
        check(paused.cpu==250 && paused.highMs==0 && !paused.trigger && !paused.detectedReady(),"off clears streak but preserves reference load");
        check(!monitor.sample(250,5000,off,0).handlingReady(),"retained auto cannot act while detector off");
        CpuLoadMonitor.Settings auto=s(true,true,true,true);check(!monitor.sample(250,6000,auto,0).trigger,"reenabling starts a new streak");
        monitor.sample(250,7000,auto,0);check(monitor.sample(250,8000,auto,0).handlingReady(),"auto enabled after full new duration");
        CoreTracker.Detail a=hot(101,1,90);CoreTracker.Result high=data(1000,2000,"a",true,0,a);
        ThreadLoadMonitor.Candidate ready=new ThreadLoadMonitor.Candidate(a,1,2000,true,true);
        ThreadLoadMonitor.Result single=new ThreadLoadMonitor.Result(Arrays.asList(ready),"逐线程独立计时");
        for(boolean total:new boolean[]{true,false})for(boolean totalAuto:new boolean[]{true,false})for(boolean thread:new boolean[]{true,false})for(boolean threadAuto:new boolean[]{true,false}){
            CpuLoadMonitor.Settings cfg=s(total,totalAuto,thread,threadAuto);CpuLoadMonitor.Sample sample=new CpuLoadMonitor.Sample(cfg,2000,250,total?2000:0,total,"reported");
            List<DualLoadPolicy.Target> targets=DualLoadPolicy.targets(high,cfg,sample,single,1000);
            int expected=(total?1:0)|(thread?2:0);check(targets.size()==(expected==0?0:1),"switch combination selects independent targets");
            if(expected!=0){DualLoadPolicy.Target t=targets.get(0);check(t.sources()==expected,"disabled source omitted");check(t.action(cfg)==((total && totalAuto)||(thread && threadAuto)),"handling is independent OR");check(DualLoadPolicy.actionSources(t.sources(),cfg)==((total && totalAuto?1:0)|(thread && threadAuto?2:0)),"record source never grants handling authority");}
        }
        check(!DualLoadPolicy.qualifies(1,off,250,2000,90,2000),"receiver rejects disabled total source");
        check(DualLoadPolicy.qualifies(3,record,250,2000,90,2000) && DualLoadPolicy.actionSources(3,record)==2,"both detected but single alone permits action");
        check(!DualLoadPolicy.qualifies(3,record,250,2000,90,1999),"total record cannot bypass missing single duration");
        ThreadLoadMonitor detector=new ThreadLoadMonitor();ThreadLoadMonitor.Result one=detector.sample(data(1000,2000,"a",true,0,a),off,2000,0);
        check(one.high.size()==1 && !one.high.get(0).ready,"single starts with total detector off");
        check(detector.sample(data(2000,3000,"a",true,0,a),off,3000,0).readyCount()==1,"single reaches duration with total disabled");
        String parameters=ThreadRuleReport.parameters(record,detected,ready);check(parameters.contains("合计模式 仅记录") && parameters.contains("单线程模式 自动处理"),"immutable table modes distinct");
        ThreadLoadHistory history=new ThreadLoadHistory();history.add(data(1000,2000,"a",true,0,a),auto,2000);List<ThreadLoadHistory.Frame> snapshot=history.snapshot();
        check(snapshot.size()==1 && !snapshot.get(0).connected,"history begins isolated identity point");
        history.add(data(2000,3000,"a",true,0,a),auto,3000);check(history.snapshot().get(1).connected,"contiguous same-identity series");check(snapshot.size()==1,"published history immutable");
        history.add(data(2000,3000,"a",true,0,a),auto,3000);check(history.snapshot().size()==2,"duplicate window not plotted twice");
        CoreTracker.Detail reused=hot(101,2,85);history.add(data(3000,4000,"a",true,0,reused),auto,4000);
        check(history.snapshot().get(2).threads.containsKey(reused.thread.key()) && !history.snapshot().get(2).threads.containsKey(a.thread.key()),"TID reuse has separate series key");
        history.add(data(4000,5000,"a",false,0,reused),auto,5000);history.add(data(5000,6000,"a",true,0,reused),auto,6000);
        check(history.snapshot().get(3).threads.isEmpty() && !history.snapshot().get(4).connected,"invalid sample creates gap and never bridges");
        history.add(data(6500,7500,"a",true,0,reused),auto,7500);check(!history.snapshot().get(5).connected,"missing interval breaks curve");
        history.add(data(7500,8500,"b",true,0,reused),auto,8500);check(history.snapshot().size()==1,"collector session clears history");
        history.add(data(8500,9500,"b",true,0,reused),record,9500);check(history.snapshot().size()==1,"config mode change clears previous stats");
        history.add(data(9500,10500,"b",true,-1,reused),record,10500);check(history.snapshot().get(1).threads.isEmpty(),"unknown foreground cannot plot valid single progress");
        history.add(data(10500,11500,"b",true,0,reused),s(true,true,false,false),11500);check(history.snapshot().isEmpty(),"single off clears series");
        for(int i=0;i<100;i++)history.add(data(1000+i*1000,2000+i*1000,"bounded",true,0,reused),auto,2000+i*1000);check(history.snapshot().size()==60,"history capped at sixty frames");
        List<CoreTracker.Detail> many=new ArrayList<>();for(int i=0;i<100;i++)many.add(hot(200+i,1,90));history.clear();history.add(data(1000,2000,"many",true,0,many.toArray(new CoreTracker.Detail[0])),auto,2000);check(history.snapshot().get(0).threads.size()==64,"per-window identity bound");
        check(ThreadRuleReport.totalMode(off).contains("未生效") && ThreadRuleReport.totalMode(record).equals("仅记录"),"total modes explicit");
        System.out.println("PASS "+checks+" independent total switches, handling authority and thread identity history checks");
    }
}
