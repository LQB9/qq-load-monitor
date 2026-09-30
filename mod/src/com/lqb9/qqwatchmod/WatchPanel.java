package com.lqb9.qqwatchmod;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.Collections;

/** Live chart panel. The QQ Enhancer tag handshake remains in WatchModule. */
public class WatchPanel {
    private static final int PINK = CpuCharts.PINK, INK = CpuCharts.INK, SUB = CpuCharts.SUB;
    private static Dialog cur;
    private static volatile boolean visible;
    static boolean isVisible() { return visible; }

    public static void show(final Context ctx) {
        if (ctx == null) return;
        if (cur != null && cur.isShowing()) cur.dismiss();
        final Dialog dlg = new Dialog(ctx);
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout frame = column(ctx);
        frame.setBackground(background(ctx, 0xFFF5F6FA, 18)); frame.setClipToOutline(true);
        LinearLayout header = row(ctx);
        header.setPadding(dp(ctx, 18), dp(ctx, 10), dp(ctx, 8), dp(ctx, 9));
        header.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{PINK, 0xFFFF92BD}));
        LinearLayout titles = column(ctx);
        TextView title = text(ctx, "QQ负载监控", 18, 0xFFFFFFFF);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD); titles.addView(title);
        TextView subtitle = text(ctx, "实时图表 · v" + WatchModule.VERSION, 11, 0xFFFFFFFF);
        subtitle.setPadding(0, dp(ctx, 4), 0, 0); titles.addView(subtitle);
        header.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView close = button(ctx, "✕", 0, 0xFFFFFFFF, new View.OnClickListener() {
            public void onClick(View v) { dlg.dismiss(); }
        });
        close.setContentDescription("关闭负载监控面板");
        header.addView(close, new LinearLayout.LayoutParams(dp(ctx, 44), dp(ctx, 44))); frame.addView(header);

        LinearLayout nav = row(ctx); nav.setPadding(dp(ctx, 14), dp(ctx, 10), dp(ctx, 14), dp(ctx, 2)); frame.addView(nav);
        LinearLayout pages = column(ctx); frame.addView(pages, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        final ScrollView[] scrolls = new ScrollView[4]; final TextView[] tabs = new TextView[4];
        String[] names = {"概览", "明细", "处理", "设置"};
        for (int i = 0; i < 4; i++) {
            final int index = i;
            tabs[i] = button(ctx, names[i], 0, SUB, new View.OnClickListener() {
                public void onClick(View v) {
                    for (int j = 0; j < 4; j++) {
                        scrolls[j].setVisibility(j == index ? View.VISIBLE : View.GONE); paintTab(ctx, tabs[j], j == index);
                    }
                }
            });
            LinearLayout.LayoutParams tabParams = new LinearLayout.LayoutParams(0, dp(ctx, 44), 1);
            if (i < 3) tabParams.rightMargin = dp(ctx, 5); nav.addView(tabs[i], tabParams);
            scrolls[i] = new ScrollView(ctx); scrolls[i].setFillViewport(true);
            pages.addView(scrolls[i], new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            scrolls[i].setVisibility(i == 0 ? View.VISIBLE : View.GONE); paintTab(ctx, tabs[i], i == 0);
        }
        final DashboardView dashboard = new DashboardView(ctx); scrolls[0].addView(dashboard);
        LinearLayout detail = page(ctx); scrolls[1].addView(detail);
        final TextView detailNote = text(ctx, "等待采样", 11, SUB);
        detailNote.setPadding(dp(ctx, 2), 0, 0, dp(ctx, 10)); detail.addView(detailNote);
        LinearLayout logCard = DashboardView.card(ctx, detail, "监控日志", "QQ 重启后保留");
        final TextView logState = text(ctx, WatchLog.status(), 11, SUB); logCard.addView(logState);
        final TextView exportState = text(ctx, "保存到：下载 / QQWatchdog", 11, SUB);
        exportState.setTextIsSelectable(true);
        final TextView export = button(ctx, "导出日志到下载目录", PINK, 0xFFFFFFFF, new View.OnClickListener() {
            public void onClick(View view) {
                final TextView button = (TextView) view;
                boolean queued = WatchLog.export(ctx, WatchModule.exportHeader(), new WatchLog.Callback() {
                    public void done(String path, String error) {
                        if (path != null) {
                            if (dlg.isShowing()) exportState.setText("已保存：" + path);
                            toast(ctx.getApplicationContext(), "日志已保存到下载目录 / QQWatchdog");
                        } else {
                            String message = error == null ? "导出失败，请重试" : error;
                            if (dlg.isShowing()) exportState.setText(message);
                            toast(ctx.getApplicationContext(), message);
                        }
                        if (dlg.isShowing()) { button.setEnabled(true); button.setAlpha(1); button.setText("导出日志到下载目录"); }
                    }
                });
                if (queued) {
                    button.setEnabled(false); button.setAlpha(0.6f); button.setText("正在导出…");
                    exportState.setText("正在写入下载目录…");
                } else toast(ctx, "日志正忙，请稍后重试");
            }
        });
        LinearLayout.LayoutParams exportParams = DashboardView.match(); exportParams.topMargin = dp(ctx, 10);
        logCard.addView(export, exportParams); logCard.addView(exportState);
        LinearLayout processCard = DashboardView.card(ctx, detail, "QQ 进程 CPU", "全部核心 · 参考值");
        final CpuCharts.Ranking processChart = new CpuCharts.Ranking(ctx, false, 128); processCard.addView(processChart, DashboardView.match());
        LinearLayout threadCard = DashboardView.card(ctx, detail, "热点线程 TOP 12", "单线程 0—100%");
        final CpuCharts.Ranking threadChart = new CpuCharts.Ranking(ctx, true, 12); threadCard.addView(threadChart, DashboardView.match());
        threadCard.addView(text(ctx, "最后核心是最近运行位置，线程可迁移。", 10, SUB));
        final TextView threadInfo = text(ctx, "", 11, INK); threadInfo.setTextIsSelectable(true); threadInfo.setVisibility(View.GONE);
        threadCard.addView(button(ctx, "展开完整线程信息", 0xFFF3F5FA, SUB, new View.OnClickListener() {
            public void onClick(View v) {
                boolean open = threadInfo.getVisibility() != View.VISIBLE; threadInfo.setVisibility(open ? View.VISIBLE : View.GONE);
                ((TextView) v).setText(open ? "收起完整线程信息" : "展开完整线程信息");
            }
        }), DashboardView.match()); threadCard.addView(threadInfo);
        LinearLayout eventCard = DashboardView.card(ctx, detail, "最近事件", "最多显示 8 条");
        final TextView events = text(ctx, "", 11, SUB); eventCard.addView(events);

        LinearLayout handlingPage = page(ctx); scrolls[2].addView(handlingPage);
        LinearLayout statusCard = DashboardView.card(ctx, handlingPage, "处理状态", "实际结果");
        final StatusTableView statusTable = new StatusTableView(ctx); statusCard.addView(statusTable, DashboardView.match());
        statusCard.addView(hint(ctx, "优先定位所选核心占用最高的工作线程，只尝试停止覆盖采样窗口的 Java 任务。原生线程需专用停止接口。"));
        LinearLayout settingsPage = page(ctx); scrolls[3].addView(settingsPage);
        LinearLayout settingsCard = DashboardView.card(ctx, settingsPage, "监控设置", "保存后生效");
        final TextView toggle = button(ctx, "", 0xFFEAF7F2, 0xFF1B9073, new View.OnClickListener() {
            public void onClick(View v) {
                boolean ok = WatchModule.cfgSet(WatchModule.F_ON, WatchModule.on() ? null : "1"); paintSwitch(ctx, (TextView) v);
                WatchModule.push(ok ? (WatchModule.on() ? "监控：开启" : "监控：关闭") : "开关保存失败，请重试"); WatchModule.requestSample();
            }
        }); settingsCard.addView(toggle, DashboardView.match());
        CpuLoadMonitor.Settings initial = WatchModule.readSettings();
        final EditText cpu = input(ctx, settingsCard, "所选核心的 QQ 合计阈值（%）", initial.threshold);
        settingsCard.addView(hint(ctx, "单核满载 100%；200% 相当于占用两个核心。"));
        settingsCard.addView(text(ctx, "监控核心（至少勾选一个）", 12, INK));
        final CoreSelectorView selectedCores = new CoreSelectorView(ctx, initial.coreMask);
        settingsCard.addView(selectedCores, DashboardView.match());
        final EditText interval = input(ctx, settingsCard, "采样间隔（秒）", initial.intervalSeconds);
        settingsCard.addView(hint(ctx, "图表保留最近 60 次采样，横轴按实际时间显示。"));
        TextView save = button(ctx, "保存设置", PINK, 0xFFFFFFFF, new View.OnClickListener() {
            public void onClick(View v) {
                int threshold = number(cpu, WatchModule.DEF_CPU, 1, 10000), seconds = 0;
                int mask = selectedCores.getMask();
                if (mask == 0) { toast(ctx, "请至少选择一个核心"); return; }
                int step = number(interval, WatchModule.DEF_IV, 1, 3600);
                CpuLoadMonitor.Settings settings = new CpuLoadMonitor.Settings(WatchModule.on(), threshold, seconds, step, "stop_task", mask);
                if (!WatchModule.saveSettings(settings)) { toast(ctx, "保存失败，请重试"); return; }
                cpu.setText(String.valueOf(threshold)); interval.setText(String.valueOf(step));
                WatchModule.push("保存阈值=" + threshold + "% 核心=" + CoreSnapshot.selection(mask) + " 采样=" + step + "秒");
                WatchModule.requestSample(); toast(ctx, "已保存，下次采样生效");
            }
        });
        LinearLayout.LayoutParams saveParams = DashboardView.match(); saveParams.topMargin = dp(ctx, 14); settingsCard.addView(save, saveParams);
        settingsCard.addView(hint(ctx, "前台和后台共用阈值；严格超过阈值时，在首轮有效采样立即尝试处理。可中断任务未响应时不会强杀进程。"));
        settingsCard.addView(button(ctx, "打开模块 · 启动精确核心采集", 0xFFF3F5FA, SUB, v -> {
            try { ctx.startActivity(new android.content.Intent().setClassName("com.lqb9.qqwatchmod", "com.lqb9.qqwatchmod.MainActivity")); }
            catch (Exception unavailable) { toast(ctx, "请从桌面打开 QQ负载监控并启动精确采集"); }
        }), DashboardView.match());
        LinearLayout explain = DashboardView.card(ctx, settingsPage, "图表怎么看", "单核 = 100%");
        explain.addView(text(ctx, "大数字与折线：仅 QQ 在所选核心上的合计占用。\n逐核百分比：QQ 在各核心上的实际 CPU 时间，圆点表示勾选核心。\n频率条：MHz，独立于占用率。\n进程明细：全部核心上的 QQ 进程 CPU，供参考。\n\n精确采集需要 root 和内核调度统计支持；缺失、过期、丢事件或前后台未知时暂停处理。处理表仅在当前会话保留，不导出。", 12, SUB));
        addLegacy(ctx, settingsPage);
        frame.addView(button(ctx, "打开 QQ 增强面板", 0xFFFFFFFF, SUB, new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    Runnable opener = WatchModule.enhOpener();
                    if (opener != null) { dlg.dismiss(); opener.run(); }
                    else if (WatchModule.hasPrevListener()) { dlg.dismiss(); WatchModule.callPrevListener(v); }
                    else toast(ctx, "没检测到 QQ 增强模块，请在 LSPosed 中启用并重启 QQ");
                } catch (Throwable ignored) { toast(ctx, "QQ 增强面板暂时无法打开"); }
            }
        }), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 44)));
        dlg.setContentView(frame); dlg.setCanceledOnTouchOutside(false);
        final Window window = dlg.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
            window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        final Handler handler = new Handler(Looper.getMainLooper());
        final Runnable tick = new Runnable() {
            public void run() {
                if (!dlg.isShowing()) return;
                try {
                    refresh(dashboard, processChart, threadChart, detailNote, threadInfo, events, toggle);
                    DashboardView.set(logState, WatchLog.status());
                    statusTable.update(TaskBridge.history);
                    boolean busy = WatchLog.isExporting(); export.setEnabled(!busy); export.setAlpha(busy ? 0.6f : 1);
                    export.setText(busy ? "正在导出…" : "导出日志到下载目录");
                } catch (Throwable ignored) {}
                handler.postDelayed(this, 1000);
            }
        };
        dlg.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
            public void onDismiss(android.content.DialogInterface d) { handler.removeCallbacks(tick); visible = false; if (cur == dlg) cur = null; }
        });
        cur = dlg; dlg.show(); visible = true;
        if (window != null) {
            android.util.DisplayMetrics metrics = ctx.getResources().getDisplayMetrics();
            window.setLayout(Math.min(dp(ctx, 420), (int) (metrics.widthPixels * 0.94f)), (int) (metrics.heightPixels * 0.88f));
        }
        WatchModule.requestSample(); tick.run(); WatchModule.push("图表面板打开");
    }

    private static void refresh(DashboardView dashboard, CpuCharts.Ranking processesChart,
            CpuCharts.Ranking threadsChart, TextView note, TextView info, TextView events, TextView toggle) {
        WatchModule.LoadStatus data = WatchModule.latest; boolean enabled = WatchModule.on();
        CpuLoadMonitor.Sample sample = data == null ? null : data.sample;
        int interval = sample == null ? WatchModule.DEF_IV : sample.settings.intervalSeconds;
        long age = sample == null ? -1 : Math.max(0, android.os.SystemClock.elapsedRealtime() - sample.elapsedMs) / 1000;
        boolean stale = age > Math.max(3, interval * 3);
        ThreadCpuTracker.Snapshot threads = data == null ? new ThreadCpuTracker.Snapshot(Collections.<ThreadCpuTracker.Detail>emptyList(), 0, 0) : data.threads;
        QqCpuTracker.Snapshot processes = data == null ? new QqCpuTracker.Snapshot(-1, 0, 0, Collections.<QqCpuTracker.Detail>emptyList(), "等待采样") : data.processes;
        dashboard.update(sample, processes, threads, data == null ? Collections.<CoreFrequency.Core>emptyList() : data.frequencies,
                data == null ? Collections.<LoadHistory.Point>emptyList() : data.history, enabled, age, stale);
        dashboard.updateScope(data == null ? null : data.core, sample == null ? WatchModule.readSettings().coreMask : sample.settings.coreMask, stale);
        processesChart.update(DashboardView.processRanks(processes), stale); threadsChart.update(DashboardView.threadRanks(threads), stale);
        DashboardView.set(note, (stale ? "上次采样已过期 · " : "") + processes.processes.size() + " 个 QQ 进程 · " + threads.scanned + " 个线程已扫描\n"
                + (data == null || data.core == null ? "等待精确采集" : data.core.note));
        StringBuilder complete = new StringBuilder();
        for (ThreadCpuTracker.Detail t : threads.threads) complete.append('\n').append(t.reading.name).append("\nCPU ")
                .append(t.cpu < 0 ? "—" : CpuCharts.percent(t.cpu)).append(" · PID ").append(t.reading.pid).append(" · TID ").append(t.reading.tid)
                .append("\n最后核心 ").append(t.reading.lastCore).append('\n');
        DashboardView.set(info, complete.toString());
        String[] records = WatchModule.events; StringBuilder log = new StringBuilder();
        for (int i = 0; i < Math.min(8, records.length); i++) log.append(i == 0 ? "" : "\n\n").append(records[i]);
        DashboardView.set(events, records.length == 0 ? "暂无事件。CPU 超限时记录并提示。" : log.toString()); paintSwitch(dashboard.getContext(), toggle);
    }
    private static void addLegacy(final Context ctx, LinearLayout page) {
        if (WatchModule.legacyLeftover() == null) return;
        LinearLayout card = DashboardView.card(ctx, page, "旧版遗留文件", "可选清理");
        final TextView note = text(ctx, "发现 " + WatchModule.legacyCount() + " 个 killleak.* 旧开关，当前版本已不使用。", 11, SUB); card.addView(note);
        card.addView(button(ctx, "清理遗留文件", 0xFFF3F5FA, SUB, new View.OnClickListener() {
            public void onClick(View v) {
                int count = WatchModule.legacyPurge(); WatchModule.push("清理遗留文件 " + count + " 个");
                note.setText("已清理 " + count + " 个" + (WatchModule.legacyLeftover() == null ? "，清理完成。" : "，还有文件未删除。"));
                if (WatchModule.legacyLeftover() == null) v.setVisibility(View.GONE);
            }
        }), DashboardView.match());
    }
    private static int dp(Context c, float n) { return DashboardView.dp(c, n); }
    private static LinearLayout column(Context c) { LinearLayout v = new LinearLayout(c); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private static LinearLayout row(Context c) { LinearLayout v = new LinearLayout(c); v.setGravity(Gravity.CENTER_VERTICAL); return v; }
    private static LinearLayout page(Context c) { LinearLayout v = column(c); v.setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 16)); return v; }
    private static TextView text(Context c, String s, float size, int color) { return DashboardView.label(c, s, size, color); }
    private static GradientDrawable background(Context c, int color, int radius) { GradientDrawable bg = new GradientDrawable(); bg.setColor(color); bg.setCornerRadius(dp(c, radius)); return bg; }
    private static TextView button(Context c, String s, int color, int foreground, View.OnClickListener listener) {
        TextView v = text(c, s, 13, foreground); v.setGravity(Gravity.CENTER); v.setPadding(dp(c, 8), dp(c, 11), dp(c, 8), dp(c, 11));
        v.setBackground(background(c, color, 10)); v.setOnClickListener(listener); v.setMinimumHeight(dp(c, 44)); return v;
    }
    private static void paintTab(Context c, TextView v, boolean selected) {
        v.setTextColor(selected ? PINK : SUB); v.setBackground(background(c, selected ? 0xFFFFFFFF : 0, 10));
        v.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
    }
    private static void paintSwitch(Context c, TextView v) {
        boolean enabled = WatchModule.on(); DashboardView.set(v, enabled ? "监控已开启 · 点击关闭" : "监控已关闭 · 点击开启");
        v.setTextColor(enabled ? 0xFF1B9073 : SUB); v.setBackground(background(c, enabled ? 0xFFEAF7F2 : 0xFFF0F2F6, 10));
    }
    private static EditText input(Context c, LinearLayout parent, String label, int initial) {
        TextView title = text(c, label, 12, INK); title.setPadding(0, dp(c, 15), 0, dp(c, 7)); parent.addView(title);
        EditText edit = new EditText(c); edit.setTextSize(16); edit.setTextColor(INK); edit.setSingleLine(true);
        edit.setInputType(InputType.TYPE_CLASS_NUMBER); edit.setText(String.valueOf(initial)); edit.setPadding(dp(c, 12), dp(c, 8), dp(c, 12), dp(c, 8));
        GradientDrawable bg = background(c, 0xFFF5F6FA, 9); bg.setStroke(dp(c, 1), CpuCharts.LINE); edit.setBackground(bg);
        parent.addView(edit, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 44))); return edit;
    }
    private static TextView hint(Context c, String s) { TextView v = text(c, s, 10, SUB); v.setPadding(0, dp(c, 6), 0, 0); return v; }
    private static int number(EditText e, int fallback, int min, int max) {
        try { return Math.max(min, Math.min(max, Integer.parseInt(e.getText().toString().trim()))); } catch (Exception ignored) { return fallback; }
    }
    private static void toast(Context c, String s) { android.widget.Toast.makeText(c, s, android.widget.Toast.LENGTH_LONG).show(); }
}
