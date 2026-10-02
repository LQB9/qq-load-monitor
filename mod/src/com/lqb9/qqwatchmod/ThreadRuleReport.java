package com.lqb9.qqwatchmod;

/** Immutable rule metadata captured at the triggering sample, shared by logs and status records. */
final class ThreadRuleReport {
    static String mode(CpuLoadMonitor.Settings s){return !s.enabled?"暂停（总监控关闭）":!s.threadRule?"未生效（检测关闭）":s.threadHandle?"自动处理":"仅记录";}
    static String totalMode(CpuLoadMonitor.Settings s){return !s.enabled?"暂停（总监控关闭）":!s.totalRule?"未生效（检测关闭）":s.totalHandle?"自动处理":"仅记录";}
    static String totalSettings(CpuLoadMonitor.Settings s){return "合计检测="+(s.totalRule?"开启":"关闭")+" 合计阈值="+s.threshold+"% 合计持续="+s.durationSeconds+"秒 合计处理="+totalMode(s);}
    static String settings(CpuLoadMonitor.Settings s){return "单线程="+(s.threadRule?"开启":"关闭")+" 单线程阈值="+s.threadThreshold+"% 单线程持续="+s.threadDuration+"秒 单线程处理="+mode(s);}
    static String parameters(CpuLoadMonitor.Settings s,CpuLoadMonitor.Sample sample,ThreadLoadMonitor.Candidate single){
        return "合计 >"+s.threshold+"% / "+s.durationSeconds+" 秒 · 本次累计 "+seconds(sample==null?0:sample.highMs)+" 秒"
                +"\n单线程 >"+s.threadThreshold+"% / "+s.threadDuration+" 秒 · 本次累计 "+(single==null?"未计时":seconds(single.highMs)+" 秒")
                +"\n合计检测 "+(s.totalRule?"开启":"关闭")+" · 合计模式 "+totalMode(s)
                +"\n单线程检测 "+(s.threadRule?"开启":"关闭")+" · 单线程模式 "+mode(s)+" · 共用采样 "+s.intervalSeconds+" 秒";
    }
    static String seconds(long ms){return String.format(java.util.Locale.ROOT,"%.1f",ms/1000d);}
}
