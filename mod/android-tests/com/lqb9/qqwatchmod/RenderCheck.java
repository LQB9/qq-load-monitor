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
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    @Override public void onStart() {
        final Bundle result = new Bundle();
        try {
            runOnMainSync(new Runnable() {
                public void run() {
                    try {
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
    }
}
