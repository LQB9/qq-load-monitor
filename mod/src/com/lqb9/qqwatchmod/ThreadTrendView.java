package com.lqb9.qqwatchmod;

import android.content.Context;
import android.widget.*;
import java.util.*;

/** Same per-identity trend in overview/detail; only the current three highest-load identities. */
final class ThreadTrendView extends LinearLayout {
    final CpuCharts.ThreadTrend chart;
    final TextView legend,note;
    ThreadTrendView(Context c){
        super(c);setOrientation(VERTICAL);
        chart=new CpuCharts.ThreadTrend(c);addView(chart,new LayoutParams(LayoutParams.MATCH_PARENT,DashboardView.dp(c,148)));
        legend=DashboardView.label(c,"",9.5f,CpuCharts.SUB);addView(legend,DashboardView.match());
        note=DashboardView.label(c,"各线分别代表同一个线程 · 缺失数据断线",9.5f,CpuCharts.SUB);note.setPadding(0,DashboardView.dp(c,6),0,DashboardView.dp(c,10));addView(note);
        setVisibility(GONE);
    }
    void update(List<ThreadLoadHistory.Frame> frames,ThreadRuleDisplay model,boolean stale){
        boolean show=model.valid && !model.rows.isEmpty();setVisibility(show?VISIBLE:GONE);if(!show)return;
        List<CoreTracker.Detail> identities=new ArrayList<>();StringBuilder text=new StringBuilder();
        String[] marks={"粉线","蓝线","绿线"};
        for(int i=0;i<Math.min(3,model.rows.size());i++){
            CoreTracker.Detail t=model.rows.get(i).thread;identities.add(t);
            text.append(i==0?"":"\n").append(marks[i]).append(" · ").append(t.thread.name).append(" · PID ").append(t.thread.pid).append(" / TID ").append(t.thread.tid);
        }
        chart.update(frames,identities,model.settings.threadThreshold,stale);
        DashboardView.set(legend,text.toString());
        DashboardView.set(note,"最近 "+frames.size()+"/60 次采样 · TOP 3 独立线程曲线\n各线按完整线程身份绘制；缺失数据断线。");
    }
}
