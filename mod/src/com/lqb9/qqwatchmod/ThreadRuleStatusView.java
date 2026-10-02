package com.lqb9.qqwatchmod;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import java.util.*;

/** Selected-core CPU and independent per-thread duration bars, without mixing thread identities into a trend. */
final class ThreadRuleStatusView extends LinearLayout {
    final TextView badge,parameters,summary,legend;
    final ThreadTrendView trend;
    private final LinearLayout rows;
    private final int limit;
    private String painted="";
    ThreadRuleStatusView(Context c,int limit){
        super(c);this.limit=limit;setOrientation(VERTICAL);
        badge=DashboardView.label(c,"等待单线程状态",13,CpuCharts.SUB);
        badge.setTypeface(Typeface.DEFAULT,Typeface.BOLD);badge.setPadding(dp(10),dp(9),dp(10),dp(9));addView(badge,DashboardView.match());
        parameters=DashboardView.label(c,"",10,CpuCharts.SUB);parameters.setPadding(0,dp(9),0,dp(6));addView(parameters);
        summary=DashboardView.label(c,"",11,CpuCharts.INK);summary.setPadding(0,0,0,dp(9));addView(summary);
        trend=new ThreadTrendView(c);addView(trend,DashboardView.match());
        rows=new LinearLayout(c);rows.setOrientation(VERTICAL);addView(rows,DashboardView.match());
        legend=DashboardView.label(c,"红线：单线程阈值；时长：各线程独立累计。\n主线程和受保护线程除外；实际结果见「处理」。",9.5f,CpuCharts.SUB);
        legend.setPadding(0,dp(8),0,0);addView(legend);
    }
    void update(ThreadRuleDisplay model){
        StringBuilder signature=new StringBuilder(model.badge).append(model.reason).append(model.parameters());
        for(int i=0;i<Math.min(limit,model.rows.size());i++){
            ThreadRuleDisplay.Row r=model.rows.get(i);signature.append(r.thread.thread.key()).append(r.thread.thread.name).append(r.thread.cpu).append(model.timing(r));
        }
        signature.append(model.rows.size());String key=signature.toString();if(key.equals(painted))return;painted=key;
        int color=!model.active?CpuCharts.SUB:!model.valid?0xFFAE731E:model.settings.threadHandle?CpuCharts.PINK:0xFF1B9073;
        DashboardView.set(badge,model.badge);badge.setTextColor(color);badge.setBackground(bg(!model.active?0xFFF0F2F6:!model.valid?0xFFFFF4DE:model.settings.threadHandle?0xFFFFEDF4:0xFFEAF7F2));
        DashboardView.set(parameters,"已保存：> "+model.settings.threadThreshold+"% · 持续 "+model.settings.threadDuration+" 秒");DashboardView.set(summary,model.reason);rows.removeAllViews();legend.setVisibility(model.valid?VISIBLE:GONE);
        if(!model.valid){trend.setVisibility(GONE);return;}
        if(model.rows.isEmpty()){rows.addView(DashboardView.label(getContext(),"当前没有可检测的活动线程",11,CpuCharts.SUB));return;}
        for(int i=0;i<Math.min(limit,model.rows.size());i++)addRow(model,model.rows.get(i));
        if(model.rows.size()>limit){TextView rest=DashboardView.label(getContext(),"显示负载最高的 "+limit+" / "+model.rows.size()+" 个可检测线程"+(limit<12?"，更多见明细":""),9.5f,CpuCharts.SUB);rest.setPadding(0,dp(6),0,0);rows.addView(rest);}
    }
    void updateTrend(List<ThreadLoadHistory.Frame> frames,ThreadRuleDisplay model,boolean stale){trend.update(frames,model,stale);}
    private void addRow(ThreadRuleDisplay m,ThreadRuleDisplay.Row r){
        boolean compact=limit<12;
        LinearLayout box=new LinearLayout(getContext());box.setOrientation(VERTICAL);int padding=dp(compact?8:10);box.setPadding(padding,padding,padding,padding);box.setBackground(bg(0xFFF7F8FC));
        LayoutParams p=DashboardView.match();p.bottomMargin=dp(8);rows.addView(box,p);
        TextView name=DashboardView.label(getContext(),r.thread.thread.name,12,CpuCharts.INK);name.setTypeface(Typeface.DEFAULT,Typeface.BOLD);box.addView(name);
        box.addView(DashboardView.label(getContext(),(limit<12?"TID "+r.thread.thread.tid:"PID "+r.thread.thread.pid+" · TID "+r.thread.thread.tid),9.5f,CpuCharts.SUB));
        String timing=m.timing(r);
        String line=compact?CpuCharts.percent(r.thread.cpu)+" · "+timing:"负载 "+CpuCharts.percent(r.thread.cpu)+" · 阈值 "+m.settings.threadThreshold+"%";
        TextView load=DashboardView.label(getContext(),line,10.5f,r.thread.cpu>m.settings.threadThreshold?CpuCharts.RED:CpuCharts.INK);
        load.setPadding(0,dp(7),0,dp(5));box.addView(load);
        box.addView(new Bar(getContext(),r.thread.cpu,Math.max(100,r.thread.cpu),m.settings.threadThreshold,r.thread.cpu>m.settings.threadThreshold?CpuCharts.PINK:CpuCharts.BLUE),new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(10)));
        if(!compact){TextView time=DashboardView.label(getContext(),timing,10.5f,r.streak!=null && r.streak.ready?CpuCharts.RED:CpuCharts.SUB);time.setPadding(0,dp(8),0,dp(5));box.addView(time);}
        double elapsed=r.streak==null?0:r.streak.highMs/1000d;
        double progress=m.settings.threadDuration==0?(r.streak!=null && r.streak.ready?1:0):Math.min(1,elapsed/m.settings.threadDuration);
        if(r.thread.cpu>m.settings.threadThreshold){LayoutParams bar=new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(6));bar.topMargin=dp(compact?5:0);box.addView(new Bar(getContext(),progress,1,-1,r.streak!=null && r.streak.ready?CpuCharts.PINK:CpuCharts.BLUE),bar);}
    }
    private GradientDrawable bg(int fill){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(10));return d;}
    private int dp(float n){return DashboardView.dp(getContext(),n);}
    private static final class Bar extends View {
        final double value,max,threshold;final int color;final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);final RectF rect=new RectF();
        Bar(Context c,double value,double max,double threshold,int color){super(c);this.value=value;this.max=max;this.threshold=threshold;this.color=color;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas canvas){
            float inset=1,h=getHeight(),right=getWidth()-inset;rect.set(inset,inset,right,h-inset);paint.setColor(CpuCharts.LINE);canvas.drawRoundRect(rect,h/2,h/2,paint);
            rect.right=inset+(right-inset)*(float)Math.max(0,Math.min(1,value/max));paint.setColor(color);canvas.drawRoundRect(rect,h/2,h/2,paint);
            if(threshold>=0){float x=inset+(right-inset)*(float)(threshold/max);paint.setColor(CpuCharts.RED);paint.setStrokeWidth(Math.max(2,DashboardView.dp(getContext(),1.5f)));canvas.drawLine(x,0,x,h,paint);}
        }
    }
}
