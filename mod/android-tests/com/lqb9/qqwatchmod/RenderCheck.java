package com.lqb9.qqwatchmod;

import android.app.Instrumentation;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.view.View;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Isolated offscreen native rendering. Never opens a window or operates QQ. */
public class RenderCheck extends Instrumentation {
    private boolean selectorsOnly;
    private boolean threadRuleOnly;
    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        selectorsOnly = arguments != null && "selectors".equals(arguments.getString("mode"));
        threadRuleOnly = arguments != null && "thread-rule".equals(arguments.getString("mode"));
        start();
    }
    @Override public void onStart() {
        final Bundle result = new Bundle();
        try {
            if(threadRuleOnly){result.putString("stream",UiRuleCheck.run(this));finish(-1,result);return;}
            runOnMainSync(new Runnable() {
                public void run() {
                    try {
                        renderSelector("cores-selected", 360, 1f, 255);
                        renderSelector("cores-mixed", 360, 1f, 247);
                        renderSelector("cores-narrow-font", 260, 1.6f, 15);
                        if (selectorsOnly) {
                            result.putString("stream", "PASS 3 native core selector renders and click state checks; no foreground window opened\n");
                            return;
                        }
                        render("normal", 360, 1f, false, false, false);
                        render("narrow", 300, 1f, false, false, false);
                        render("large-font", 360, 1.3f, false, false, false);
                        render("stale", 360, 1f, true, false, false);
                        render("missing", 360, 1f, false, true, false);
                        render("high-load", 360, 1f, false, false, true);
                        render("high-narrow-font", 300, 1.3f, false, false, true);
                        result.putString("stream", "PASS 7 native dashboard renders; no foreground window opened\n");
                    } catch (Exception e) { throw new RuntimeException(e); }
                }
            });
            finish(-1, result);
        } catch (Throwable e) { result.putString("stream", "FAILED " + e); finish(1, result); }
    }
    private void renderSelector(String name, int widthDp, float fontScale, int mask) throws Exception {
        Configuration config = new Configuration(getTargetContext().getResources().getConfiguration());
        config.fontScale = fontScale;
        Context context = getTargetContext().createConfigurationContext(config);
        CoreSelectorView selector = new CoreSelectorView(context, mask);
        View first = ((android.view.ViewGroup) selector.getChildAt(0)).getChildAt(0);
        first.performClick();
        if (selector.getMask() != (mask ^ 1)) throw new AssertionError("Core selection did not toggle");
        first.performClick();
        if (selector.getMask() != mask) throw new AssertionError("Core selection did not restore");
        android.view.accessibility.AccessibilityNodeInfo info = android.view.accessibility.AccessibilityNodeInfo.obtain();
        first.onInitializeAccessibilityNodeInfo(info);
        if (!info.isCheckable() || info.isChecked() != ((mask & 1) != 0)) throw new AssertionError("Core accessibility state incorrect");
        info.recycle();
        int width = DashboardView.dp(context, widthDp);
        selector.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        selector.layout(0, 0, width, selector.getMeasuredHeight());
        if (first.getHeight() < DashboardView.dp(context, 48)) throw new AssertionError("Core touch target too small");
        Bitmap bitmap = Bitmap.createBitmap(width, selector.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap); canvas.drawColor(0xFFFFFFFF); selector.draw(canvas);
        try (FileOutputStream out = new FileOutputStream(new File(getTargetContext().getCacheDir(), name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        bitmap.recycle();
    }
    private void render(String name, int widthDp, float fontScale, boolean stale, boolean missing, boolean high) throws Exception {
        Configuration config = new Configuration(getTargetContext().getResources().getConfiguration());
        config.fontScale = fontScale;
        Context context = getTargetContext().createConfigurationContext(config);
        DashboardView dashboard = new DashboardView(context);
        List<CoreFrequency.Core> frequencies = new ArrayList<CoreFrequency.Core>();
        long[] mhz = {787, 787, 1100, 1100, 1401, 1401, 2016, 2016};
        for (int i = 0; i < mhz.length; i++) frequencies.add(new CoreFrequency.Core(i, !(missing && i == 7),
                missing && i == 6 ? -1 : mhz[i] * 1000, false));
        List<ThreadCpuTracker.Detail> hot = new ArrayList<ThreadCpuTracker.Detail>();
        String[] names = {"NtStartup_pool_", "QQ_MainThread", "ThreadPool_LongWorkerName_0123456789", "MSF_Network"};
        double[] loads = {42.6, 25.8, 12.3, 3.2};
        for (int i = 0; i < names.length; i++) hot.add(new ThreadCpuTracker.Detail(
                new ThreadCpuTracker.Reading(22644, 20628 + i, "QQ", names[i], 100, 100, i * 2), missing ? -1 : loads[i]));
        ThreadCpuTracker.Snapshot threads = new ThreadCpuTracker.Snapshot(hot, 386, missing ? 2 : 0);
        QqCpuTracker.Snapshot processes = new QqCpuTracker.Snapshot(87.4, 8, 60000,
                Collections.<QqCpuTracker.Detail>emptyList(), "");
        LoadHistory history = new LoadHistory();
        for (int i = 0; i < 60; i++) history.add(1000 + i * 1000,
                missing ? -1 : i == 59 ? (high ? 245.3 : 87.4) : i >= 23 && i <= 25 ? -1
                        : 60 + Math.sin(i * 0.25) * 30 + (i == 42 ? 185 : 0), 1);
        CpuLoadMonitor.Sample sample = new CpuLoadMonitor.Sample(new CpuLoadMonitor.Settings(true, 200, 0, 1, "record"),
                60000, missing ? -1 : high ? 245.3 : 87.4, 1000, false, high ? "reported" : "normal");
        dashboard.update(sample, processes, threads, frequencies, history.snapshot(), true, stale ? 15 : 0, stale);
        double[] cores = missing ? new double[]{-1,-1,-1,-1,-1,-1,-1,-1} : high ? new double[]{66.3,63.3,62.2,53.5,5,4,1,0} : new double[]{33,25,20,9.4,5,4,1,0};
        CoreSnapshot coreSnapshot = new CoreSnapshot("demo",10265,1,60000,60000000000L,!missing,0,"演示数据",new long[8],Collections.<CoreSnapshot.Counter>emptyList());
        List<CoreTracker.Detail> coreHot = new ArrayList<CoreTracker.Detail>();
        for (int i=0;i<4;i++) coreHot.add(new CoreTracker.Detail(new CoreSnapshot.Counter(22644,100,20628+i,100,"pool-40-thread-",new long[8]),cores[i]));
        dashboard.updateScope(new CoreTracker.Result(missing ? -1 : high ? 245.3 : 87.4,cores,coreSnapshot,59000000000L,coreHot,"演示数据 · 所选核心实际时间"),15,stale);
        int width = DashboardView.dp(context, widthDp);
        dashboard.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        dashboard.layout(0, 0, width, dashboard.getMeasuredHeight());
        if (dashboard.getHeight() < DashboardView.dp(context, 450)) throw new AssertionError("Dashboard collapsed");
        Bitmap bitmap = Bitmap.createBitmap(width, dashboard.getHeight(), Bitmap.Config.ARGB_8888);
        dashboard.draw(new Canvas(bitmap));
        try (FileOutputStream out = new FileOutputStream(new File(getTargetContext().getCacheDir(), name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        bitmap.recycle();
        ProcessingHistory status = new ProcessingHistory();
        String[] states = {"GIF待确认", "GIF已暂停", "GIF仍在运行", "GIF已解除暂停"};
        for (int i=0;i<4;i++) status.add(new ProcessingHistory.Row("demo"+i,System.currentTimeMillis(),
                new CoreSnapshot.Counter(23430,100,27049+i,200,i==3 ? "GifRenderingExe" : "pool-40-thread-",new long[8]),15,253.9,62.1,
                200,"QQ 后台超限 · 持续5秒，要求5秒",states[i],i==3 ? "手动恢复 · 恢复播放=1 · 不可见/已释放=0 · 渲染未返回=0 · 失败=0。不可见、已释放或渲染未返回对象不主动重播。" : i==1 ? "GIF对象=3 · 正在渲染=0 · 仍播放=0 · 已拦截后续渲染=18；仅暂停对应GIF，工作线程保留。可从处理页恢复。\n复采QQ合计=72.5% · 已回落至阈值内" : "GIF对象=1 · 正在渲染=1 · 仍播放=0；已请求暂停，等待原渲染返回。尚不能确认停止。",
                i==3 ? "GifRenderingExecutor-worker-003" : "pool-40-thread-"+(i+1),
                "task#21 BusinessGifWorker.run · 窗口 8 次 / 125.0ms CPU · 近10秒 64 次 · fixed-rate",
                "business=com.tencent.example.BusinessGifWorker.run schedule=fixed-rate periodMs=40 windowRuns=8 executionStack=BusinessGifWorker.run [执行入口]；全部核心参考，不参与所选核心阈值判定"));
        StatusTableView table = new StatusTableView(context);table.update(status);
        table.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
        table.layout(0,0,width,table.getMeasuredHeight());
        Bitmap statusBitmap=Bitmap.createBitmap(width,table.getHeight(),Bitmap.Config.ARGB_8888);Canvas statusCanvas=new Canvas(statusBitmap);
        statusCanvas.drawColor(0xFFFFFFFF);table.draw(statusCanvas);
        try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getCacheDir(),name+"-status.png"))) {statusBitmap.compress(Bitmap.CompressFormat.PNG,100,out);}statusBitmap.recycle();
    }
}
