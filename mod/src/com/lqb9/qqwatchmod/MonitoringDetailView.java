package com.lqb9.qqwatchmod;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

/** Expanded version of the same selected-core thread chart used by the overview. */
final class MonitoringDetailView extends LinearLayout {
    final ThreadRuleStatusView singleRule;
    final TextView note,referenceToggle,threadToggle,threadInfo,events;
    final LinearLayout referenceBody;
    final CpuCharts.Ranking processesChart,threadsChart;
    MonitoringDetailView(Context c){
        super(c);setOrientation(VERTICAL);
        note=DashboardView.label(c,"等待采样",10,CpuCharts.SUB);note.setPadding(0,0,0,DashboardView.dp(c,10));addView(note);
        LinearLayout selected=DashboardView.card(c,this,"单线程负载与持续时长","TOP 12");
        singleRule=new ThreadRuleStatusView(c,12);selected.addView(singleRule,DashboardView.match());
        LinearLayout reference=DashboardView.card(c,this,"全核心参考与诊断","按需展开");
        referenceBody=new LinearLayout(c);referenceBody.setOrientation(VERTICAL);referenceBody.setVisibility(GONE);
        referenceToggle=disclosure(c,"展开全核心参考与诊断");reference.addView(referenceToggle,DashboardView.match());reference.addView(referenceBody,DashboardView.match());
        referenceToggle.setOnClickListener(v->{boolean open=referenceBody.getVisibility()!=VISIBLE;referenceBody.setVisibility(open?VISIBLE:GONE);referenceToggle.setText(open?"收起全核心参考与诊断":"展开全核心参考与诊断");});
        caption(referenceBody,"QQ 进程 · 全部核心");processesChart=new CpuCharts.Ranking(c,false,128);referenceBody.addView(processesChart,DashboardView.match());
        caption(referenceBody,"热点线程 · 全部核心 TOP 12");threadsChart=new CpuCharts.Ranking(c,true,12);referenceBody.addView(threadsChart,DashboardView.match());
        caption(referenceBody,"全核心数值供参考，判断规则使用所选核心负载。最后核心是最近运行位置，线程可迁移。");
        threadInfo=DashboardView.label(c,"",11,CpuCharts.INK);threadInfo.setTextIsSelectable(true);threadInfo.setVisibility(GONE);
        threadToggle=disclosure(c,"展开完整线程信息");referenceBody.addView(threadToggle,DashboardView.match());referenceBody.addView(threadInfo,DashboardView.match());
        threadToggle.setOnClickListener(v->{boolean open=threadInfo.getVisibility()!=VISIBLE;threadInfo.setVisibility(open?VISIBLE:GONE);threadToggle.setText(open?"收起完整线程信息":"展开完整线程信息");});
        caption(referenceBody,"最近事件 · 最多 8 条");events=DashboardView.label(c,"",11,CpuCharts.SUB);referenceBody.addView(events,DashboardView.match());
    }
    void update(ThreadRuleDisplay model,QqCpuTracker.Snapshot processes,ThreadCpuTracker.Snapshot threads,String state,boolean stale,String[] records){
        singleRule.update(model);
        DashboardView.set(note,"CPU "+CoreSnapshot.selection(model.settings.coreMask)+" · 共用采样 "+model.settings.intervalSeconds+" 秒\n"+(stale?"采样已过期 · ":"")+processes.processes.size()+" 个 QQ 进程 · "+threads.scanned+" 个线程已扫描\n"+state);
        processesChart.update(DashboardView.processRanks(processes),stale);threadsChart.update(DashboardView.threadRanks(threads),stale);
        if(threadInfo.getVisibility()==VISIBLE){StringBuilder full=new StringBuilder();for(ThreadCpuTracker.Detail t:threads.threads)full.append(t.reading.name).append("\nCPU ").append(t.cpu<0?"—":CpuCharts.percent(t.cpu)).append(" · PID ").append(t.reading.pid).append(" · TID ").append(t.reading.tid).append("\n最后核心 ").append(t.reading.lastCore).append("\n\n");DashboardView.set(threadInfo,full.length()==0?"暂无线程数据":full.toString());}
        StringBuilder log=new StringBuilder();for(int i=0;i<Math.min(8,records.length);i++)log.append(i==0?"":"\n\n").append(records[i]);DashboardView.set(events,log.length()==0?"暂无事件":log.toString());
    }
    private void caption(LinearLayout parent,String s){TextView v=DashboardView.label(getContext(),s,10,CpuCharts.SUB);v.setPadding(0,DashboardView.dp(getContext(),14),0,DashboardView.dp(getContext(),7));parent.addView(v,DashboardView.match());}
    private TextView disclosure(Context c,String s){TextView v=DashboardView.label(c,s,11,CpuCharts.SUB);v.setGravity(Gravity.CENTER);int p=DashboardView.dp(c,11);v.setPadding(p,p,p,p);v.setMinimumHeight(DashboardView.dp(c,44));GradientDrawable bg=new GradientDrawable();bg.setColor(0xFFF3F5FA);bg.setCornerRadius(DashboardView.dp(c,10));v.setBackground(bg);return v;}
}
