package com.lqb9.qqwatchmod;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.Collections;

/** Live chart panel. The QQ Enhancer handshake is owned by PanelEntryHooks. */
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
        TextView subtitle = text(ctx, "实时图表 · v" + ModuleInfo.VERSION, 11, 0xFFFFFFFF);
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
        final MonitoringDetailView detailView=new MonitoringDetailView(ctx);detail.addView(detailView,DashboardView.match());
        LinearLayout logCard = DashboardView.card(ctx, detail, "监控日志", "QQ 重启后保留");
        final TextView logState = text(ctx, WatchLog.status(), 11, SUB); logCard.addView(logState);
        logCard.addView(hint(ctx, "包含合计与单线程的阈值、持续时长和模式；单线程快照保留线程身份及累计时长，THREAD_LIMIT记录达标事件。采集异常和失败诊断沿用原日志，处理清单不导出。"));
        final TextView exportState = text(ctx, "保存到：下载 / QQWatchdog", 11, SUB);
        exportState.setTextIsSelectable(true);
        final TextView export = button(ctx, "导出日志到下载目录", PINK, 0xFFFFFFFF, new View.OnClickListener() {
            public void onClick(View view) {
                final TextView button = (TextView) view;
                boolean queued = WatchLog.export(ctx, MonitorReport.exportHeader(), new WatchLog.Callback() {
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
        LinearLayout handlingPage = page(ctx); scrolls[2].addView(handlingPage);
        LinearLayout statusCard = DashboardView.card(ctx, handlingPage, "处理状态", "实际结果");
        final StatusTableView statusTable = new StatusTableView(ctx); statusCard.addView(statusTable, DashboardView.match());
        statusCard.addView(button(ctx,"恢复已暂停 GIF",0xFFF3F5FA,SUB,v -> {
            TaskBridge.restoreGifManually(); toast(ctx,"已请求恢复，表格将显示实际结果");
        }),DashboardView.match());
        statusCard.addView(hint(ctx, "GIF专项按已观测的渲染对象暂停播放，线程保留；可手动恢复。其他任务仍核对采样窗口，原生线程需专用停止接口。"));
        LinearLayout settingsPage = page(ctx); scrolls[3].addView(settingsPage);
        final MonitorSettingsView settingsView=new MonitorSettingsView(ctx,WatchSettings.read());
        settingsPage.addView(settingsView,DashboardView.match());
        settingsView.toggle.setOnClickListener(v->{
            boolean ok=WatchSettings.setEnabled(!WatchSettings.on());settingsView.updateApplied(WatchSettings.read());
            MonitorState.push(ok?(WatchSettings.on()?"监控：开启":"监控：关闭"):"开关保存失败，请重试");WatchRuntime.requestSample();
        });
        LinearLayout saveCard=DashboardView.card(ctx,settingsPage,"应用设置","统一保存");
        TextView save = button(ctx, "保存设置", PINK, 0xFFFFFFFF, new View.OnClickListener() {
            public void onClick(View v) {
                if(settingsView.selectedCores.getMask()==0){toast(ctx,"请至少选择一个核心");return;}
                CpuLoadMonitor.Settings settings=settingsView.draft(WatchSettings.on());
                if(!WatchSettings.save(settings)){toast(ctx,"保存失败，请重试");return;}
                settingsView.markSaved(settings);
                MonitorState.push("保存阈值="+settings.threshold+"% 核心="+CoreSnapshot.selection(settings.coreMask)+" 超限持续="+settings.durationSeconds+"秒 采样="+settings.intervalSeconds+"秒 GIF专项="+(settings.gifRule?"开启":"关闭"));
                WatchRuntime.requestSample(); toast(ctx, "已保存，下次采样生效");
            }
        });
        LinearLayout.LayoutParams saveParams = DashboardView.match(); saveParams.topMargin = dp(ctx, 14); saveCard.addView(save, saveParams);
        saveCard.addView(hint(ctx, "前后台共用规则；合计与单线程分别计时，任一处理规则达条件后复核目标。同一目标合并触发原因并去重。单线程仅记录时不会请求停止；未响应的任务不强杀线程或进程。"));
        saveCard.addView(button(ctx, "打开模块 · 启动精确核心采集", 0xFFF3F5FA, SUB, v -> {
            try { ctx.startActivity(new android.content.Intent().setClassName("com.lqb9.qqwatchmod", "com.lqb9.qqwatchmod.MainActivity")); }
            catch (Exception unavailable) { toast(ctx, "请从桌面打开 QQ负载监控并启动精确采集"); }
        }), DashboardView.match());
        LinearLayout explain = DashboardView.card(ctx, settingsPage, "图表说明", "按需查看");
        explain.addView(text(ctx, "大数字与折线：仅 QQ 在所选核心上的合计占用。\n单线程卡片：负载条显示所选核心百分比与阈值红线；时长条显示每个线程独立的连续超限进度。达时长后，自动处理仍须复核业务，结果见处理页。\n逐核百分比：QQ 在各核心上的实际 CPU 时间，圆点表示勾选核心。\n逐核卡片附 MHz 读数，频率不参与负载阈值。\n进程明细：全部核心上的 QQ 进程 CPU，供参考。\n\n精确采集需要 root 和内核调度统计支持；缺失、过期、丢事件或前后台未知时暂停处理。处理表仅在当前会话保留，不导出。", 12, SUB));
        addLegacy(ctx, settingsPage);
        frame.addView(button(ctx, "打开 QQ 增强面板", 0xFFFFFFFF, SUB, new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    Runnable opener = PanelEntryHooks.enhOpener();
                    if (opener != null) { dlg.dismiss(); opener.run(); }
                    else if (PanelEntryHooks.hasPrevListener()) { dlg.dismiss(); PanelEntryHooks.callPrevListener(v); }
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
                    refresh(dashboard, detailView);
                    settingsView.updateApplied(WatchSettings.read());
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
        WatchRuntime.requestSample(); tick.run(); MonitorState.push("图表面板打开");
    }

    private static void refresh(DashboardView dashboard, MonitoringDetailView detail) {
        LoadSnapshot data = MonitorState.latest; CpuLoadMonitor.Settings applied=WatchSettings.read(); boolean enabled = applied.enabled;
        CpuLoadMonitor.Sample sample = data == null ? null : data.sample;
        int interval = sample == null ? ModuleInfo.DEF_IV : sample.settings.intervalSeconds;
        long age = sample == null ? -1 : Math.max(0, android.os.SystemClock.elapsedRealtime() - sample.elapsedMs) / 1000;
        boolean stale = age > Math.max(3, interval * 3);
        ThreadCpuTracker.Snapshot threads = data == null ? new ThreadCpuTracker.Snapshot(Collections.<ThreadCpuTracker.Detail>emptyList(), 0, 0) : data.threads;
        QqCpuTracker.Snapshot processes = data == null ? new QqCpuTracker.Snapshot(-1, 0, 0, Collections.<QqCpuTracker.Detail>emptyList(), "等待采样") : data.processes;
        dashboard.update(sample, processes, threads, data == null ? Collections.<CoreFrequency.Core>emptyList() : data.frequencies,
                data == null ? Collections.<LoadHistory.Point>emptyList() : data.history, enabled, age, stale);
        dashboard.updateScope(data == null ? null : data.core, sample == null ? WatchSettings.read().coreMask : sample.settings.coreMask, stale);
        dashboard.updateRuleSettings(applied,sample,stale);
        ThreadRuleDisplay singleDisplay=ThreadRuleDisplay.from(applied,sample==null?null:sample.settings,data==null?null:data.core,data==null?null:data.single,stale);
        dashboard.singleRule.update(singleDisplay);
        java.util.List<ThreadLoadHistory.Frame> threadHistory=data==null?Collections.<ThreadLoadHistory.Frame>emptyList():data.threadHistory;
        dashboard.singleRule.updateTrend(threadHistory,singleDisplay,stale);
        detail.update(singleDisplay,processes,threads,data==null || data.core==null?"等待精确采集":data.core.note,stale,MonitorState.events);
        detail.singleRule.updateTrend(threadHistory,singleDisplay,stale);
    }
    private static void addLegacy(final Context ctx, LinearLayout page) {
        if (WatchSettings.legacyLeftover() == null) return;
        LinearLayout card = DashboardView.card(ctx, page, "旧版遗留文件", "可选清理");
        final TextView note = text(ctx, "发现 " + WatchSettings.legacyCount() + " 个 killleak.* 旧开关，当前版本已不使用。", 11, SUB); card.addView(note);
        card.addView(button(ctx, "清理遗留文件", 0xFFF3F5FA, SUB, new View.OnClickListener() {
            public void onClick(View v) {
                int count = WatchSettings.legacyPurge(); MonitorState.push("清理遗留文件 " + count + " 个");
                note.setText("已清理 " + count + " 个" + (WatchSettings.legacyLeftover() == null ? "，清理完成。" : "，还有文件未删除。"));
                if (WatchSettings.legacyLeftover() == null) v.setVisibility(View.GONE);
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
    private static TextView hint(Context c, String s) { TextView v = text(c, s, 10, SUB); v.setPadding(0, dp(c, 6), 0, 0); return v; }
    private static void toast(Context c, String s) { android.widget.Toast.makeText(c, s, android.widget.Toast.LENGTH_LONG).show(); }
}
