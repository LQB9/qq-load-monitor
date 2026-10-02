package com.lqb9.qqwatchmod;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.text.Editable;
import android.text.TextWatcher;
import android.graphics.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.*;

/** Independent rule controls; sampling/core selection are deliberately shared with the total rule. */
class LoadRuleSettingsView extends LinearLayout {
    private boolean enabled,handling;
    private final boolean single;
    private CpuLoadMonitor.Settings applied;
    final EditText cpu,duration;
    final StateSwitchView toggle,action;
    final TextView savedState;
    LoadRuleSettingsView(Context context,CpuLoadMonitor.Settings initial,boolean single){
        super(context);setOrientation(VERTICAL);this.single=single;enabled=single?initial.threadRule:initial.totalRule;handling=single?initial.threadHandle:initial.totalHandle;applied=initial;
        savedState=DashboardView.label(context,"",11,CpuCharts.SUB);addView(savedState,match());
        toggle=new StateSwitchView(context);addView(toggle,match());toggle.setOnClickListener(v->{enabled=!enabled;paint();});
        cpu=input(single?"单线程阈值（%）":"QQ 合计阈值（%）",single?initial.threadThreshold:initial.threshold);
        duration=input(single?"单线程超限持续时长（秒）":"合计超限持续时长（秒）",single?initial.threadDuration:initial.durationSeconds);
        action=new StateSwitchView(context);addView(action,match());action.setOnClickListener(v->{handling=!handling;paint();});
        TextView hint=DashboardView.label(context,single?"只统计线程在勾选核心上的负载，每个线程独立计时。阈值1—100%，时长0—3600秒；0秒为首个有效超限窗口。默认仅记录，自动处理需连续确认同一任务或GIF对象。":"所选核心的 QQ 负载相加。单核满载 100%，200% 相当于两个核心；0 秒为首个有效超限窗口。关闭自动处理后只记录和提示，另一规则独立运行。",11,CpuCharts.SUB);
        hint.setPadding(0,dp(8),0,0);addView(hint);
        TextWatcher watcher=new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){paint();}public void afterTextChanged(Editable e){}};
        cpu.addTextChangedListener(watcher);duration.addTextChangedListener(watcher);paint();
    }
    boolean ruleEnabled(){return enabled;}
    boolean handlingEnabled(){return handling;}
    int threshold(){return number(cpu,single?80:200,1,single?100:10000);}
    int seconds(){return number(duration,single?10:0,0,3600);}
    void normalize(){cpu.setText(String.valueOf(threshold()));duration.setText(String.valueOf(seconds()));}
    void markSaved(CpuLoadMonitor.Settings value){applied=value;paint();}
    void updateApplied(CpuLoadMonitor.Settings value){if(!value.sameAs(applied)){applied=value;paint();}}
    private void paint(){
        toggle.show(single?"单线程检测":"合计检测",enabled?"已开启":"已关闭",enabled,true);
        action.show("自动处理",!enabled?"未生效 · 检测已关闭":handling?"已开启 · 达标后复核处理":"已关闭 · 仅记录",enabled && handling,enabled);action.setAlpha(enabled?1:.55f);
        cpu.setEnabled(enabled);duration.setEnabled(enabled);cpu.setAlpha(enabled?1:.55f);duration.setAlpha(enabled?1:.55f);
        boolean savedRule=single?applied.threadRule:applied.totalRule,savedHandle=single?applied.threadHandle:applied.totalHandle;
        int savedCpu=single?applied.threadThreshold:applied.threshold,savedDuration=single?applied.threadDuration:applied.durationSeconds;
        boolean dirty=enabled!=savedRule || handling!=savedHandle || !String.valueOf(savedCpu).equals(cpu.getText().toString().trim()) || !String.valueOf(savedDuration).equals(duration.getText().toString().trim());
        String status=!savedRule?"检测关闭":savedHandle?"检测开启 · 自动处理":"检测开启 · 仅记录";
        DashboardView.set(savedState,"已保存："+status+" · "+savedCpu+"% / "+savedDuration+" 秒"
                +(!applied.enabled?"\n总监控已关闭，此规则暂停":"")
                +"\n"+(dirty?"有修改未保存 · 点击下方「保存设置」":"下方开关与数值修改后，需点击「保存设置」。"));
        savedState.setTextColor(dirty?0xFFAE731E:CpuCharts.SUB);
    }
    private EditText input(String title,int initial){
        TextView label=DashboardView.label(getContext(),title,12,CpuCharts.INK);label.setPadding(0,dp(13),0,dp(6));addView(label);
        EditText field=new EditText(getContext());field.setTextColor(CpuCharts.INK);field.setTextSize(15);
        field.setSingleLine(true);field.setInputType(InputType.TYPE_CLASS_NUMBER);field.setText(String.valueOf(initial));
        field.setPadding(dp(12),dp(10),dp(12),dp(10));field.setBackground(background(0xFFF5F6FA));field.setMinimumHeight(dp(48));
        addView(field,match());return field;
    }
    private GradientDrawable background(int fill){GradientDrawable b=new GradientDrawable();b.setColor(fill);b.setCornerRadius(dp(10));return b;}
    private LayoutParams match(){LayoutParams p=new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);p.topMargin=dp(6);return p;}
    private int dp(float n){return DashboardView.dp(getContext(),n);}
    private static int number(EditText field,int fallback,int min,int max){try{return Math.max(min,Math.min(max,Integer.parseInt(field.getText().toString().trim())));}catch(Exception invalid){return fallback;}}
}
