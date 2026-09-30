package com.lqb9.qqwatchmod;

import android.content.Context;
import java.io.*;

/** Root authorization is requested through the installed su provider. */
final class RootControl {
    static final String DIRECTORY = "/sdcard/Android/data/com.tencent.mobileqq/files";
    interface Callback { void done(String message); }
    static String quote(String s) { return "'" + s.replace("'", "'\\''") + "'"; }
    static void start(Context context, Callback callback) {
        String apk = context.getApplicationInfo().sourceDir;
        new Thread(() -> {
            Process process = null;
            try {
                int uid = context.getPackageManager().getApplicationInfo("com.tencent.mobileqq", 0).uid;
                String launchLog = quote(DIRECTORY + "/watchdog.core.service.log");
                String command = "CLASSPATH=" + quote(apk) + " nohup /system/bin/app_process /system/bin com.lqb9.qqwatchmod.CoreCollector "
                        + uid + " " + quote(DIRECTORY) + " >" + launchLog + " 2>&1 </dev/null & "
                        + "count=0; while [ $count -lt 10 ]; do "
                        + "if grep -q 'CORE_' " + launchLog + "; then cat " + launchLog + "; exit 0; fi; "
                        + "count=$((count+1)); sleep 1; done; exit 1";
                process = new ProcessBuilder("su", "-c", command).redirectErrorStream(true).start();
                final Process running = process;
                // The collector owns its stdout file and survives launcher/Activity exit.
                final java.util.concurrent.atomic.AtomicBoolean acknowledged = new java.util.concurrent.atomic.AtomicBoolean();
                Thread guard = new Thread(() -> { android.os.SystemClock.sleep(15000); if (!acknowledged.get()) running.destroy(); });
                guard.setDaemon(true); guard.start();
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line; boolean replied = false;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("CORE_READY") || line.startsWith("CORE_ALREADY_RUNNING")) {
                        acknowledged.set(true); replied = true; callback.done("精确采集已启动，数秒后建立基线。配置和处理状态请到 QQ 面板查看。");
                    } else if (line.startsWith("CORE_FAILED")) { replied = true; callback.done("采集失败：" + line.substring(12)); }
                }
                if (!replied) callback.done("未能启动精确采集，请确认 root 授权及内核支持。");
            } catch (Exception error) { callback.done("启动失败：" + error.getClass().getSimpleName()); }
        }, "qqcore-launch").start();
    }
    static void stop(Callback callback) {
        new Thread(() -> {
            try {
                Process p = new ProcessBuilder("su", "-c", "touch " + quote(DIRECTORY + "/watchdog.core.stop")).start();
                callback.done(p.waitFor() == 0 ? "已请求停止精确采集" : "停止失败，请确认 root 授权");
            } catch (Exception error) { callback.done("停止失败：" + error.getClass().getSimpleName()); }
        }, "qqcore-stop").start();
    }
}
