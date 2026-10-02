package com.lqb9.qqwatchmod;

import java.util.*;

/** Merge trigger sources before dispatch. A thread identity gets at most one request per window. */
final class DualLoadPolicy {
    static final int TOTAL=1,THREAD=2;
    static final class Target {
        final CoreTracker.Detail hot;boolean total,totalTrigger;ThreadLoadMonitor.Candidate single;
        Target(CoreTracker.Detail hot){this.hot=hot;}
        int sources(){return (total?TOTAL:0)+(single!=null && single.ready?THREAD:0);}
        boolean action(CpuLoadMonitor.Settings s){return actionSources(sources(),s)!=0;}
        boolean report(CpuLoadMonitor.Settings s){return action(s) || total && totalTrigger || single!=null && single.trigger;}
        String reason(CoreTracker.Result data,CpuLoadMonitor.Settings s,CpuLoadMonitor.Sample aggregate){
            String reason=total?"合计规则 · "+LoadPolicy.reason(data,s,aggregate):data.snapshot.foreground==1?"QQ 前台":"QQ 后台";
            if(single!=null && single.ready)reason+=" · 单线程规则 · >"+s.threadThreshold+"% 持续"+single.highMs/1000+"秒，要求"+s.threadDuration+"秒";
            return reason;
        }
    }
    static List<Target> targets(CoreTracker.Result data,CpuLoadMonitor.Settings settings,CpuLoadMonitor.Sample aggregate,
                                ThreadLoadMonitor.Result single,int uid){
        Map<String,Target> result=new LinkedHashMap<>();
        if(LoadPolicy.detected(data,settings,aggregate,uid)){
            CoreTracker.Detail hot=LoadPolicy.target(data);Target t=new Target(hot);t.total=true;t.totalTrigger=aggregate.trigger;result.put(hot.thread.key(),t);
        }
        if(data!=null && data.cpu>=0 && data.snapshot!=null && data.snapshot.uid==uid && settings.enabled && settings.threadRule)
            for(ThreadLoadMonitor.Candidate c:single.high){
                if(result.size()>=16 && !result.containsKey(c.hot.thread.key()))break;
                Target t=result.get(c.hot.thread.key());if(t==null){t=new Target(c.hot);result.put(c.hot.thread.key(),t);}t.single=c;
            }
        return new ArrayList<>(result.values());
    }
    static int actionSources(int sources,CpuLoadMonitor.Settings s){
        if(!s.enabled || !"stop_task".equals(s.action))return 0;
        return ((sources&TOTAL)!=0 && s.totalRule && s.totalHandle?TOTAL:0)
                | ((sources&THREAD)!=0 && s.threadRule && s.threadHandle?THREAD:0);
    }
    static boolean qualifies(int sources,CpuLoadMonitor.Settings settings,double totalCpu,long totalMs,double threadCpu,long threadMs){
        if(sources<=0 || (sources & ~(TOTAL|THREAD))!=0 || !settings.enabled || !"stop_task".equals(settings.action))return false;
        boolean total=(sources&TOTAL)!=0 && settings.totalRule && Double.isFinite(totalCpu) && totalCpu>settings.threshold && totalCpu<=10000 && totalMs>=settings.durationSeconds*1000L;
        boolean thread=(sources&THREAD)!=0 && settings.threadRule && Double.isFinite(threadCpu) && threadCpu>settings.threadThreshold
                && threadCpu<=110 && threadMs>=settings.threadDuration*1000L;
        // Both advertised sources must be valid; a changed setting cannot silently fall through.
        return ((sources&TOTAL)==0 || total) && ((sources&THREAD)==0 || thread);
    }
}
