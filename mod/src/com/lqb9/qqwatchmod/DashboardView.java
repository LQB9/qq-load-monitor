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
    final TextView value, state, equivalent, freshness, coverage, frequencyNote;
    final CpuCharts.Trend trend;
    final CpuCharts.Cores cores;
    final CpuCharts.Ranking threads;
    final TextView scopeNote;
    final CpuCharts.CoreLoad coreLoad;

    DashboardView(Context c) {
        super(c);
        setOrientation(VERTICAL);
        setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 16));
        setBackgroundColor(0xFFF5F6FA);
        LinearLayout hero = card(c, this, "所选核心的 QQ 负载", "单核 = 100%");
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

        LinearLayout load = card(c, this, "QQ 各核心负载", "仅 QQ CPU 时间");
        coreLoad = new CpuCharts.CoreLoad(c); load.addView(coreLoad, match());
        LinearLayout frequency = card(c, this, "核心当前频率", "MHz");
        cores = new CpuCharts.Cores(c); frequency.addView(cores, match());
        frequencyNote = label(c, "频率单独显示；核心占用率尚未采集", 10, CpuCharts.SUB);
        frequencyNote.setPadding(0, dp(c, 5), 0, 0); frequency.addView(frequencyNote);

        LinearLayout hot = card(c, this, "热点线程 TOP 4", "按 CPU 排序");
        threads = new CpuCharts.Ranking(c, true, 4); hot.addView(threads, match());
        coverage = label(c, "最后核心是最近运行位置，线程可迁移", 10, CpuCharts.SUB);
        coverage.setPadding(0, dp(c, 5), 0, 0); hot.addView(coverage);
    }

    void update(CpuLoadMonitor.Sample sample, QqCpuTracker.Snapshot processes,
                ThreadCpuTracker.Snapshot threadData, List<CoreFrequency.Core> frequency,
                List<LoadHistory.Point> history, boolean enabled, long ageSeconds, boolean stale) {
        double cpu = sample == null ? -1 : sample.cpu;
        int threshold = sample == null ? 200 : sample.settings.threshold;
        boolean valid = cpu >= 0 && !stale;
        set(value, valid ? CpuCharts.percent(cpu) : "—");
        String badge = !enabled ? "提示已暂停" : stale ? "等待更新" : !valid ? "等待采样"
                : cpu > threshold ? "负载偏高" : "监控中";
        int color = !enabled || stale || !valid ? CpuCharts.SUB : cpu > threshold ? CpuCharts.RED : 0xFF1B9073;
        set(state, badge); state.setTextColor(color);
        GradientDrawable bg = new GradientDrawable(); bg.setCornerRadius(dp(getContext(), 8));
        bg.setColor(!valid || !enabled ? 0xFFEDF0F5 : cpu > threshold ? 0xFFFFEBEF : 0xFFEAF7F2);
        state.setBackground(bg);
        set(equivalent, valid ? "相当于占用 " + String.format(java.util.Locale.ROOT, "%.2f", cpu / 100) + " 个核心"
                + " · 提示阈值 " + threshold + "%" : "等待有效 CPU 数据 · 提示阈值 " + threshold + "%");
        set(freshness, "最近 " + history.size() + "/60 次采样 · "
                + (ageSeconds < 0 ? "等待更新" : ageSeconds + " 秒前更新")
                + (stale ? "（已过期）" : ""));
        trend.update(history, threshold, stale);
        cores.update(frequency, stale);
        boolean hardware = false, driver = false;
        for (CoreFrequency.Core core : frequency) if (core.online && core.khz > 0) {
            if (core.hardwareReading) hardware = true; else driver = true;
        }
        set(frequencyNote, (hardware && driver ? "硬件 / 驱动混合读数" : hardware ? "硬件读数" : driver ? "驱动请求频率" : "等待可读取的频率")
                + " · 核心占用率尚未采集");
        threads.update(threadRanks(threadData), stale);
        set(coverage, "已扫描 " + threadData.scanned + " 个线程"
                + (threadData.errors > 0 ? " · " + threadData.errors + " 项不可读" : "")
                + "\n最后核心是最近运行位置，线程可迁移");
    }

    void updateScope(CoreTracker.Result data, int mask, boolean stale) {
        double[] unknown = {-1,-1,-1,-1,-1,-1,-1,-1};
        coreLoad.update(data == null ? unknown : data.cores, mask, stale);
        String focus = data == null || data.snapshot == null || data.snapshot.foreground < 0 ? "前后台未知"
                : data.snapshot.foreground == 1 ? "QQ 前台" : "QQ 后台";
        set(scopeNote, "CPU " + CoreSnapshot.selection(mask) + " · " + focus + "\n" + (data == null ? "精确采集未启用，暂停处理" : data.note));
        set(frequencyNote, "频率与负载分别展示；频率不参与阈值计算");
        if (data != null && data.cpu >= 0) {
            threads.update(coreRanks(data), stale);
            set(coverage, "所选核心上的线程 CPU 排名；原生线程名可能截断\n总负载不能单独证明线程发生泄露");
        }
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
