package com.lqb9.qqwatchmod;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.*;

/** Grouped status rows. Each record keeps its identity, result and evidence together. */
final class StatusTableView extends LinearLayout {
    private long revision = -1; private String expanded = "";
    private List<ProcessingHistory.Row> current = Collections.emptyList();
    StatusTableView(Context c) { super(c); setOrientation(VERTICAL); }
    void update(ProcessingHistory history) {
        if (revision == history.revision()) return;
        revision = history.revision(); current = history.snapshot(); drawRows();
    }
    private void drawRows() {
        removeAllViews();
        TextView note = label("最近 100 条 · 新记录在上方\n表中负载按所选核心计算；清单仅在当前会话保留，不导出。", 10, CpuCharts.SUB);
        note.setPadding(0,0,0,dp(12)); addView(note);
        if (current.isEmpty()) {
            addView(label("暂无规则记录。合计或单线程连续超限达到所设时长后，显示仅记录或实际处理结果。", 12, CpuCharts.SUB)); return;
        }
        SimpleDateFormat time = new SimpleDateFormat("HH:mm:ss", Locale.ROOT);
        for (final ProcessingHistory.Row row : current) {
            final boolean open = expanded.equals(row.id);
            LinearLayout entry = vertical();
            entry.setPadding(dp(12),dp(12),dp(12),dp(10));
            entry.setBackground(background(0xFFFFFFFF,0xFFE1E5EE,10));
            LayoutParams entryParams = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
            entryParams.bottomMargin=dp(14); addView(entry,entryParams);

            LinearLayout heading = new LinearLayout(getContext()); heading.setGravity(Gravity.CENTER_VERTICAL);
            TextView stamp = label(time.format(new Date(row.timeMs)),11,CpuCharts.SUB);
            stamp.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
            heading.addView(stamp,new LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
            TextView badge = label(row.state,10.5f,stateColor(row.state));
            badge.setTypeface(Typeface.DEFAULT,Typeface.BOLD); badge.setGravity(Gravity.CENTER);
            badge.setPadding(dp(9),dp(4),dp(9),dp(4));
            badge.setBackground(background(stateBackground(row.state),0,6)); heading.addView(badge);
            entry.addView(heading);

            TextView name = label(row.fullName.isEmpty()?row.name:row.fullName,12.5f,CpuCharts.INK);
            name.setTypeface(Typeface.DEFAULT,Typeface.BOLD); name.setPadding(0,dp(9),0,dp(3)); entry.addView(name);
            entry.addView(label("PID "+row.pid+"  ·  TID "+row.tid,9.5f,CpuCharts.SUB));

            LinearLayout loads = new LinearLayout(getContext());
            LayoutParams loadParams = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
            loadParams.topMargin=dp(10); loadParams.bottomMargin=dp(8); entry.addView(loads,loadParams);
            LayoutParams first = new LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1); first.rightMargin=dp(8);
            loads.addView(load("QQ 合计",row.load,CpuCharts.PINK,0xFFFFF1F6),first);
            loads.addView(load("线程自身",row.threadLoad,0xFF5068C9,0xFFF1F4FF),new LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
            if(!row.rule.isEmpty())entry.addView(label("触发："+row.rule,10,CpuCharts.INK));
            entry.addView(label(row.reason+"\nCPU "+CoreSnapshot.selection(row.mask)+"  ·  "+(row.ruleParameters.isEmpty()
                    ?"阈值 "+(row.threshold<0?"未记录":row.threshold+"%") : row.ruleParameters),10,CpuCharts.SUB));

            divider(entry);
            TextView reasonTitle = label("处理原因",10,CpuCharts.INK); reasonTitle.setTypeface(Typeface.DEFAULT,Typeface.BOLD); entry.addView(reasonTitle);
            TextView reason = label(row.detail.isEmpty()?"等待目标进程回复具体结果":row.detail,10.5f,0xFF596477);
            reason.setPadding(0,dp(4),0,dp(9)); entry.addView(reason);

            LinearLayout recent = vertical(); recent.setPadding(dp(9),dp(8),dp(9),dp(8));
            recent.setBackground(background(0xFFF6F7FB,0,6)); entry.addView(recent);
            TextView recentTitle = label("最近执行 · 全核参考",9.5f,CpuCharts.SUB);
            recentTitle.setTypeface(Typeface.DEFAULT,Typeface.BOLD); recent.addView(recentTitle);
            TextView summary = label(row.executionSummary.isEmpty()?"尚未收到执行记录":row.executionSummary,10,0xFF626D82);
            summary.setPadding(0,dp(4),0,0); recent.addView(summary);

            if (open) {
                divider(entry);
                evidence(entry,"身份信息","内核线程名 "+row.name+"\nPID启动="+row.pidStart+" · TID启动="+row.tidStart);
                if (!row.diagnostic.isEmpty()) evidence(entry,"处理诊断",row.diagnostic);
                evidence(entry,"执行证据",row.executionText.isEmpty()?"尚未捕获，不能据此判断已停止或没有异常。":row.executionText);
            }
            TextView toggle = label(open?"收起详情  ▴":"展开详情  ▾",10,CpuCharts.PINK);
            toggle.setGravity(Gravity.RIGHT); toggle.setPadding(0,dp(10),0,dp(2)); entry.addView(toggle);
            View.OnClickListener expand = v -> { expanded=open?"":row.id; drawRows(); };
            entry.setOnClickListener(expand); toggle.setOnClickListener(expand);
            toggle.setContentDescription((open?"收起":"展开")+"线程 "+(row.fullName.isEmpty()?row.name:row.fullName)+" 的处理详情");
        }
    }
    private LinearLayout load(String title,double value,int color,int fill) {
        LinearLayout tile = vertical(); tile.setPadding(dp(9),dp(7),dp(9),dp(7)); tile.setBackground(background(fill,0,6));
        tile.addView(label(title,9.5f,CpuCharts.SUB));
        TextView percent=label(CpuCharts.percent(value),17,color); percent.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        percent.setPadding(0,dp(2),0,0); tile.addView(percent); return tile;
    }
    private void evidence(LinearLayout entry,String title,String text) {
        TextView caption=label(title,10,CpuCharts.INK); caption.setTypeface(Typeface.DEFAULT,Typeface.BOLD); entry.addView(caption);
        TextView detail=label(text.replace(" | ","\n\n").replace(" <- ","\n← "),10,CpuCharts.SUB);
        detail.setTextIsSelectable(true); detail.setPadding(0,dp(4),0,dp(10)); entry.addView(detail);
    }
    private void divider(LinearLayout entry) {
        View line=new View(getContext()); line.setBackgroundColor(0xFFE9ECF2);
        LayoutParams params=new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(1)); params.topMargin=dp(9); params.bottomMargin=dp(9); entry.addView(line,params);
    }
    private GradientDrawable background(int fill,int stroke,int radius) {
        GradientDrawable bg=new GradientDrawable(); bg.setColor(fill); bg.setCornerRadius(dp(radius)); if(stroke!=0)bg.setStroke(dp(1),stroke); return bg;
    }
    private LinearLayout vertical() { LinearLayout box=new LinearLayout(getContext()); box.setOrientation(VERTICAL); return box; }
    private TextView label(String text,float size,int color) { return DashboardView.label(getContext(),text,size,color); }
    private int dp(float value) { return DashboardView.dp(getContext(),value); }
    private static int stateColor(String state) {
        return state.equals("任务已结束")||state.equals("GIF已暂停")?0xFF168969:state.equals("待确认")||state.equals("等待处理")||state.equals("GIF待确认")?0xFFA76A16
                :state.equals("无法处理")||state.equals("未处理")||state.equals("仍在运行")||state.equals("处理失败")||state.equals("GIF仍在运行")||state.equals("GIF处理失败")||state.equals("GIF恢复失败")?0xFFC6326C:0xFF64728A;
    }
    private static int stateBackground(String state) {
        return state.equals("任务已结束")||state.equals("GIF已暂停")?0xFFE8F7EF:state.equals("待确认")||state.equals("等待处理")||state.equals("GIF待确认")?0xFFFFF3DD
                :state.equals("无法处理")||state.equals("未处理")||state.equals("仍在运行")||state.equals("处理失败")||state.equals("GIF仍在运行")||state.equals("GIF处理失败")||state.equals("GIF恢复失败")?0xFFFFEAF2:0xFFF0F3F8;
    }
}
