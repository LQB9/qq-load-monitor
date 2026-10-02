package com.lqb9.qqwatchmod;

import java.util.*;

/** Read-only interpretation of applied settings and the existing identity-keyed detector result. */
final class ThreadRuleDisplay {
    static final class Row {
        final CoreTracker.Detail thread;
        final ThreadLoadMonitor.Candidate streak;
        Row(CoreTracker.Detail t,ThreadLoadMonitor.Candidate s){thread=t;streak=s;}
    }
    final CpuLoadMonitor.Settings settings;
    final String badge,reason;
    final boolean active,valid;
    final List<Row> rows;
    final int high,ready;
    private ThreadRuleDisplay(CpuLoadMonitor.Settings s,String badge,String reason,boolean active,boolean valid,List<Row> rows,int high,int ready){
        settings=s;this.badge=badge;this.reason=reason;this.active=active;this.valid=valid;
        this.rows=Collections.unmodifiableList(rows);this.high=high;this.ready=ready;
    }
    static ThreadRuleDisplay from(CpuLoadMonitor.Settings current,CpuLoadMonitor.Settings sampled,
            CoreTracker.Result core,ThreadLoadMonitor.Result single,boolean stale){
        if(!current.enabled)return empty(current,"总监控关闭 · 单线程暂停","不计时、不处理；开启总监控后恢复。",false);
        if(!current.threadRule)return empty(current,"单线程检测已关闭","不计时、不处理；合计规则继续独立运行。",false);
        String mode=current.threadHandle?"自动处理":"仅记录";
        if(sampled!=null && !current.sameAs(sampled))return empty(current,"检测开启 · 等待新设置采样","设置已保存，等待新配置的有效采样；旧计时不沿用。",true);
        if(stale)return empty(current,"检测开启 · 数据已过期","等待有效采样，暂停单线程计时与处理。",true);
        if(core==null || sampled==null)return empty(current,"检测开启 · 等待采样","当前模式："+mode+"；等待精确核心负载。",true);
        if(core.snapshot==null || !core.snapshot.valid || !Double.isFinite(core.cpu) || core.cpu<0 || core.snapshot.foreground<0)
            return empty(current,"检测开启 · 采样无效",core.snapshot!=null && core.snapshot.foreground<0?"QQ 前后台未知，暂停计时与处理。":core.note,true);
        if(single==null || !"逐线程独立计时".equals(single.state))
            return empty(current,"检测开启 · 等待有效计时",single==null?"等待单线程检测结果。":single.state,true);
        Map<String,ThreadLoadMonitor.Candidate> clocks=new HashMap<>();
        for(ThreadLoadMonitor.Candidate c:single.high)clocks.put(c.hot.thread.key(),c);
        List<Row> rows=new ArrayList<>();int high=0,ready=0;
        for(CoreTracker.Detail t:core.threads){
            if(t.thread.tid==t.thread.pid || LoadPolicy.protectedName(t.thread.name) || !Double.isFinite(t.cpu) || t.cpu<0 || t.cpu>110)continue;
            ThreadLoadMonitor.Candidate c=clocks.get(t.thread.key());
            // Identity AND current CPU must match; a reused TID never inherits another row's clock.
            if(c!=null && (c.hot.cpu!=t.cpu || t.cpu<=current.threadThreshold))c=null;
            if(c!=null){high++;if(c.ready)ready++;}
            rows.add(new Row(t,c));
        }
        Collections.sort(rows,(a,b)->Double.compare(b.thread.cpu,a.thread.cpu));
        return new ThreadRuleDisplay(current,"检测开启 · "+mode,
                high+" 个超限 · "+ready+" 个达到持续时长",true,true,rows,high,ready);
    }
    private static ThreadRuleDisplay empty(CpuLoadMonitor.Settings s,String badge,String reason,boolean active){
        return new ThreadRuleDisplay(s,badge,reason,active,false,new ArrayList<Row>(),0,0);
    }
    String parameters(){return "已保存：> "+settings.threadThreshold+"% · 持续 "+settings.threadDuration+" 秒\nCPU "+CoreSnapshot.selection(settings.coreMask)+" · 共用采样 "+settings.intervalSeconds+" 秒";}
    String timing(Row row){
        if(row.streak==null)return row.thread.cpu>settings.threadThreshold?"超限 · 等待有效计时":"未超限 · 累计 0 秒";
        String time=String.format(Locale.ROOT,"%.1f / %d 秒",row.streak.highMs/1000d,settings.threadDuration);
        return time+(row.streak.ready?(settings.threadHandle?" · 已达时长，待处理复核":" · 已达时长，仅记录"):" · 超限计时中");
    }
}
