package com.lqb9.qqwatchmod;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/** Overview shared by the live panel and offscreen visual checks. No fabricated live data. */
final class DashboardView extends LinearLayout {
    final TextView value, state, equivalent, freshness, frequencyNote;
    final CpuCharts.Trend trend;
    final CpuCharts.CoreMetrics coreMetrics;
    final TextView scopeNote;
    final ThreadRuleStatusView singleRule;
    final TextView ruleMode;

    DashboardView(Context c) {
        super(c);
        setOrientation(VERTICAL);
        setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 16));
        setBackgroundColor(0xFFF5F6FA);
        LinearLayout hero = card(c, this, "总负载持续超限规则", "所选核心");
        ruleMode=label(c,"等待规则状态",11,CpuCharts.SUB);ruleMode.setPadding(0,0,0,dp(c,7));hero.addView(ruleMode);
        scopeNote = label(c, "等待精确核心采集", 10, CpuCharts.SUB); hero.addView(scopeNote);
        LinearLayout row = new LinearLayout(c); row.setGravity(Gravity.CENTER_VERTICAL);
        value = label(c, "—", 36, CpuCharts.INK); value.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        value.setSingleLine(true);
        value.setMinHeight(dp(c, 48));
        value.setAutoSizeTextTypeUniformWithConfiguration(24, 36, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        row.addView(value, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        state = label(c, "等待采样", 11, CpuCharts.SUB);
        state.setPadding(dp(c, 9), dp(c, 6), dp(c, 9), dp(c, 6));
        row.addView(state); hero.addView(row);
        equivalent = label(c, "等待首次有效采样", 11, CpuCharts.SUB); hero.addView(equivalent);
        trend = new CpuCharts.Trend(c);
        hero.addView(trend, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 122)));
        freshness = label(c, "最近 60 次采样 · 缺失数据留空", 9.5f, CpuCharts.SUB); hero.addView(freshness);

        LinearLayout single = card(c, this, "单线程持续超限规则", "TOP 3");
        singleRule = new ThreadRuleStatusView(c, 3); single.addView(singleRule, match());

        LinearLayout load = card(c, this, "QQ 各核心负载", "% · MHz");
        coreMetrics = new CpuCharts.CoreMetrics(c); load.addView(coreMetrics, match());
        frequencyNote = label(c, "负载条 0—100% · ● 为所选核心；频率不参与阈值计算", 9.5f, CpuCharts.SUB);
        frequencyNote.setPadding(0, dp(c, 5), 0, 0); load.addView(frequencyNote);
    }

    void update(CpuLoadMonitor.Sample sample, QqCpuTracker.Snapshot processes,
                ThreadCpuTracker.Snapshot threadData, List<CoreFrequency.Core> frequency,
                List<LoadHistory.Point> history, boolean enabled, long ageSeconds, boolean stale) {
        double cpu = sample == null ? -1 : sample.cpu;
        int threshold = sample == null ? 200 : sample.settings.threshold;
        boolean valid = cpu >= 0 && !stale;
        set(value, valid ? CpuCharts.percent(cpu) : "—");
        String badge = !enabled ? "监控已暂停" : stale ? "等待更新" : !valid ? "等待采样"
                : cpu > threshold ? "负载偏高" : "监控中";
        int color = !enabled || stale || !valid ? CpuCharts.SUB : cpu > threshold ? CpuCharts.RED : 0xFF1B9073;
        set(state, badge); state.setTextColor(color);
        GradientDrawable bg = new GradientDrawable(); bg.setCornerRadius(dp(getContext(), 8));
        bg.setColor(!valid || !enabled ? 0xFFEDF0F5 : cpu > threshold ? 0xFFFFEBEF : 0xFFEAF7F2);
        state.setBackground(bg);
        set(equivalent, valid ? "相当于占用 " + String.format(java.util.Locale.ROOT, "%.2f", cpu / 100) + " 个核心"
                + " · 合计阈值 " + threshold + "%" : "等待有效 CPU 数据 · 合计阈值 " + threshold + "%");
        set(freshness, "最近 " + history.size() + "/60 次采样 · "
                + (ageSeconds < 0 ? "等待更新" : ageSeconds + " 秒前更新")
                + (stale ? "（已过期）" : "")
                + (valid && enabled && sample.settings.totalRule ? "\n连续超限 "
                + String.format(java.util.Locale.ROOT, "%.1f", sample.highMs / 1000d)
                + " / " + sample.settings.durationSeconds + " 秒" : ""));
        trend.update(history, threshold, stale);
        coreMetrics.updateFrequency(frequency, stale);
        if(sample!=null)updateRuleSettings(sample.settings,sample,stale);
    }
    void updateRuleSettings(CpuLoadMonitor.Settings current,CpuLoadMonitor.Sample sample,boolean stale){
        boolean matching=sample!=null && sample.settings.sameAs(current);
        set(ruleMode,"合计检测 "+(current.totalRule?"开启":"关闭")+" · "+ThreadRuleReport.totalMode(current));
        ruleMode.setTextColor(!current.enabled || !current.totalRule?CpuCharts.SUB:current.totalHandle?CpuCharts.PINK:0xFF1B9073);
        trend.showLimit(current.enabled && current.totalRule);
        if(!matching){set(value,"—");set(state,"等待采样");state.setTextColor(CpuCharts.SUB);set(freshness,"设置已保存，等待新配置采样；旧计时不沿用。");trend.update(java.util.Collections.<LoadHistory.Point>emptyList(),current.threshold,stale);}
        else if(!current.enabled || !current.totalRule){set(state,"负载参考");state.setTextColor(CpuCharts.SUB);GradientDrawable bg=new GradientDrawable();bg.setColor(0xFFEDF0F5);bg.setCornerRadius(dp(getContext(),8));state.setBackground(bg);set(freshness,current.enabled?"合计检测关闭，不计时、不触发；单线程规则独立运行。":"总监控已关闭，两套规则暂停。");}
    }

    void updateScope(CoreTracker.Result data, int mask, boolean stale) {
        double[] unknown = {-1,-1,-1,-1,-1,-1,-1,-1};
        coreMetrics.updateLoads(data == null ? unknown : data.cores, mask, stale);
        String focus = data == null || data.snapshot == null || data.snapshot.foreground < 0 ? "前后台未知"
                : data.snapshot.foreground == 1 ? "QQ 前台" : "QQ 后台";
        set(scopeNote, "CPU " + CoreSnapshot.selection(mask) + " · " + focus + "\n" + (data == null ? "精确采集未启用，暂停处理" : data.note));

    }
    static List<CpuCharts.Rank> coreRanks(CoreTracker.Result data) {
        List<CpuCharts.Rank> rows = new ArrayList<CpuCharts.Rank>();
        for (CoreTracker.Detail t : data.threads) rows.add(new CpuCharts.Rank(t.thread.name,
                "TID " + t.thread.tid + " · PID " + t.thread.pid + " · 所选核心实际时间", t.cpu));
        return rows;
    }

    static List<CpuCharts.Rank> threadRanks(ThreadCpuTracker.Snapshot data) {
        List<CpuCharts.Rank> rows = new ArrayList<CpuCharts.Rank>();
        for (ThreadCpuTracker.Detail t : data.threads) rows.add(new CpuCharts.Rank(t.reading.name,
                "TID " + t.reading.tid + " · PID " + t.reading.pid + " · 最后核心 " + t.reading.lastCore, t.cpu));
        return rows;
    }
    static List<CpuCharts.Rank> processRanks(QqCpuTracker.Snapshot data) {
        List<CpuCharts.Rank> rows = new ArrayList<CpuCharts.Rank>();
        for (QqCpuTracker.Detail p : data.processes) {
            String name = p.reading.name;
            if ("com.tencent.mobileqq".equals(name)) name = "QQ 主进程";
            else if (name.startsWith("com.tencent.mobileqq:")) name = name.substring("com.tencent.mobileqq:".length());
            rows.add(new CpuCharts.Rank(name, "PID " + p.reading.pid, p.cpu));
        }
        return rows;
    }
    static int dp(Context c, float value) { return (int) (value * c.getResources().getDisplayMetrics().density + 0.5f); }
    static LinearLayout.LayoutParams match() { return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); }
    static TextView label(Context c, String text, float size, int color) {
        TextView view = new TextView(c); view.setText(text); view.setTextSize(size); view.setTextColor(color);
        view.setIncludeFontPadding(false); view.setLineSpacing(dp(c, 3), 1f); return view;
    }
    static void set(TextView view, String text) { if (!text.contentEquals(view.getText())) view.setText(text); }
    static LinearLayout card(Context c, LinearLayout parent, String title, String subtitle) {
        LinearLayout box = new LinearLayout(c); box.setOrientation(VERTICAL);
        box.setPadding(dp(c, 14), dp(c, 13), dp(c, 14), dp(c, 13));
        GradientDrawable bg = new GradientDrawable(); bg.setColor(0xFFFFFFFF); bg.setCornerRadius(dp(c, 14)); box.setBackground(bg);
        LinearLayout.LayoutParams params = match(); params.bottomMargin = dp(c, 10); parent.addView(box, params);
        LinearLayout header = new LinearLayout(c); header.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = label(c, title, 13, CpuCharts.INK); name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        header.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView note = label(c, subtitle, 9.5f, CpuCharts.SUB); header.addView(note);
        header.setPadding(0, 0, 0, dp(c, 10)); box.addView(header); return box;
    }
}
