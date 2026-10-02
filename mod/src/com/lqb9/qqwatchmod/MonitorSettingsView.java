package com.lqb9.qqwatchmod;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.*;

/** Scope and sampling apply to both rules. Draft controls never write configuration themselves. */
final class MonitorSettingsView extends LinearLayout {
    final StateSwitchView toggle,gifToggle;
    final CoreSelectorView selectedCores;
    final EditText cpu,duration,interval;
    final ThreadRuleSettingsView singleSettings;
    final LoadRuleSettingsView totalSettings;
    final LinearLayout scopeCard,totalCard,singleCard,handlingCard;
    private boolean gif;
    private CpuLoadMonitor.Settings applied;
    MonitorSettingsView(Context c,CpuLoadMonitor.Settings initial){
        super(c);setOrientation(VERTICAL);applied=initial;gif=initial.gifRule;
        LinearLayout master=DashboardView.card(c,this,"总监控","即时生效");
        toggle=new StateSwitchView(c);master.addView(toggle,DashboardView.match());paintMaster(initial.enabled);
        scopeCard=DashboardView.card(c,this,"监控范围","两种规则共用");
        note(scopeCard,"仅统计 QQ；选择 CPU 核心，至少保留一个。");
        selectedCores=new CoreSelectorView(c,initial.coreMask);scopeCard.addView(selectedCores,DashboardView.match());
        interval=input(scopeCard,"共用采样间隔（秒）",initial.intervalSeconds);
        note(scopeCard,"核心选择与采样间隔同时用于合计和单线程规则。前后台采用相同设置。");
        totalCard=DashboardView.card(c,this,"总负载持续超限规则","独立计时");
        totalSettings=new LoadRuleSettingsView(c,initial,false);totalCard.addView(totalSettings,DashboardView.match());
        cpu=totalSettings.cpu;duration=totalSettings.duration;
        singleCard=DashboardView.card(c,this,"单线程持续超限规则","独立计时");
        singleSettings=new ThreadRuleSettingsView(c,initial);singleCard.addView(singleSettings,DashboardView.match());
        handlingCard=DashboardView.card(c,this,"GIF 专项处理","两种规则共用");
        gifToggle=new StateSwitchView(c);handlingCard.addView(gifToggle,DashboardView.match());
        gifToggle.setOnClickListener(v->{gif=!gif;paintGif();});paintGif();
        note(handlingCard,"触发处理并确认 GIF 是主要业务后，暂停对应 GIF。处理页可恢复；关闭监控或修改设置会解除暂停。");
    }
    CpuLoadMonitor.Settings draft(boolean enabled){return new CpuLoadMonitor.Settings(enabled,number(cpu,200,1,10000),number(duration,0,0,3600),number(interval,1,1,3600),"stop_task",selectedCores.getMask(),gif,
            singleSettings.ruleEnabled(),singleSettings.threshold(),singleSettings.seconds(),singleSettings.handlingEnabled(),totalSettings.ruleEnabled(),totalSettings.handlingEnabled());}
    void markSaved(CpuLoadMonitor.Settings s){applied=s;cpu.setText(String.valueOf(s.threshold));duration.setText(String.valueOf(s.durationSeconds));interval.setText(String.valueOf(s.intervalSeconds));totalSettings.normalize();totalSettings.markSaved(s);singleSettings.normalize();singleSettings.markSaved(s);paintMaster(s.enabled);paintGif();}
    void updateApplied(CpuLoadMonitor.Settings s){applied=s;totalSettings.updateApplied(s);singleSettings.updateApplied(s);paintMaster(s.enabled);paintGif();}
    private void paintMaster(boolean enabled){toggle.show("总监控",enabled?"已开启 · 点击关闭":"已关闭 · 点击开启",enabled,true);}
    private void paintGif(){gifToggle.show("GIF 专项",(gif?"已开启":"已关闭")+(gif==applied.gifRule?" · 已保存":" · 修改待保存"),gif,true);}
    private EditText input(LinearLayout card,String name,int value){
        TextView title=DashboardView.label(getContext(),name,12,CpuCharts.INK);title.setPadding(0,dp(10),0,dp(7));card.addView(title);
        EditText e=new EditText(getContext());e.setTextSize(15);e.setTextColor(CpuCharts.INK);e.setSingleLine(true);e.setInputType(InputType.TYPE_CLASS_NUMBER);e.setText(String.valueOf(value));
        e.setPadding(dp(12),dp(10),dp(12),dp(10));GradientDrawable bg=new GradientDrawable();bg.setColor(0xFFF5F6FA);bg.setCornerRadius(dp(10));e.setBackground(bg);e.setMinimumHeight(dp(48));card.addView(e,DashboardView.match());return e;
    }
    private void note(LinearLayout card,String text){TextView v=DashboardView.label(getContext(),text,10,CpuCharts.SUB);v.setPadding(0,dp(7),0,dp(7));card.addView(v,DashboardView.match());}
    private int dp(float n){return DashboardView.dp(getContext(),n);}
    private static int number(EditText field,int fallback,int min,int max){try{return Math.max(min,Math.min(max,Integer.parseInt(field.getText().toString().trim())));}catch(Exception invalid){return fallback;}}
}
