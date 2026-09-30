package com.lqb9.qqwatchdog;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 看门狗的宿主：起一个 root shell 跑 qqwatch.sh，把它的 stdout 解析成界面状态。
 *
 * 为什么不把脚本 detach 掉自己跑：脚本的输出就是状态，管道一断就说明宿主没了。
 * 宿主没了的看门狗等于没看门狗 —— 与其留一个不可控的孤儿进程，不如让它跟着 Service 一起活。
 * （代价：Service 被系统杀了看门狗就停。所以做成前台服务 + START_STICKY。）
 */
public class WatchService extends Service {

    public static final String ACTION_START = "com.lqb9.qqwatchdog.START";
    public static final String ACTION_STOP  = "com.lqb9.qqwatchdog.STOP";
    public static final String ACTION_KICK  = "com.lqb9.qqwatchdog.KICK";

    private static final String TAG = "qqwatch";

    private Process proc;
    private Thread rd, er;
    private volatile boolean wantRun;
    private volatile int restartCount;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private SharedPreferences sp;

    @Override
    public void onCreate() {
        super.onCreate();
        sp = getSharedPreferences("cfg", MODE_PRIVATE);
        buildChannel();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String a = intent == null ? null : intent.getAction();
        Watchdog.dbg(this, "onStartCommand action=" + a + " flags=" + flags);
        if (ACTION_STOP.equals(a)) {
            Watchdog.dbg(this, "STOP intent, caller trace: "
                    + android.util.Log.getStackTraceString(new Throwable()));
            wantRun = false;
            sp.edit().putBoolean(Watchdog.K_WANT, false).apply();
            stopWatch("用户点了停止");
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        wantRun = true;
        sp.edit().putBoolean(Watchdog.K_WANT, true).apply();
        startFg();
        Watchdog.dbg(this, "startFg done");
        if (a == null || ACTION_START.equals(a) || ACTION_KICK.equals(a)) {
            if (a != null && ACTION_KICK.equals(a)) stopWatch("重新开始");
            if (proc == null || !Watchdog.running) startWatch();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopWatch("服务结束");
        super.onDestroy();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // 从最近任务划掉也算用户想让它停（免得用户以为停了、其实还在杀 QQ）
        if (!wantRun) stopWatch("任务被划掉");
        else startFg();
        super.onTaskRemoved(rootIntent);
    }

    // ==================== 守护脚本 ====================

    private int cfgInt(String k, int def) {
        try {
            return Integer.parseInt(sp.getString(k, String.valueOf(def)));
        } catch (Throwable t) {
            return def;
        }
    }

    private void startWatch() {
        Watchdog.dbg(this, "startWatch enter");
        Watchdog.reset();
        Watchdog.startedAt = System.currentTimeMillis();
        restartCount++;
        try {
            File script = new File(getFilesDir(), "qqwatch.sh");
            copyAsset("qqwatch.sh", script);
            File state = new File(getFilesDir(), "state");
            if (!state.isDirectory()) state.mkdirs();

            int base = cfgInt(Watchdog.K_BASE, Watchdog.DEF_BASE);
            int k = cfgInt(Watchdog.K_K, Watchdog.DEF_K);
            int iv = Math.max(5, cfgInt(Watchdog.K_IV, Watchdog.DEF_IV));
            String scope = sp.getString(Watchdog.K_SCOPE, Watchdog.DEF_SCOPE);

            Watchdog.dbg(this, "script=" + script.getAbsolutePath()
                    + " exists=" + script.isFile() + " len=" + script.length());
            String cmd = "sh " + script.getAbsolutePath() + " " + base + " " + k + " "
                    + scope + " " + iv + " " + state.getAbsolutePath() + " 0";
            Log.i(TAG, "spawn: " + cmd);

            Watchdog.dbg(this, "about to exec su: " + cmd);
            proc = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
            Watchdog.dbg(this, "exec ok");
            Watchdog.running = true;
            Watchdog.err = "";
            Watchdog.rootDenied = false;

            rd = new Thread(new Reader(proc.getInputStream()), "watch-out");
            rd.setDaemon(true);
            rd.start();
            er = new Thread(new Reader(proc.getErrorStream()), "watch-err");
            er.setDaemon(true);
            er.start();

            new Thread(new Waiter(proc), "watch-wait").start();
            ui.postDelayed(this.notifTask, 1500);
        } catch (Throwable t) {
            Watchdog.running = false;
            Watchdog.err = "起不来：" + t;
            Watchdog.pushEvent("ERR " + t);
            Watchdog.dbg(this, "startWatch FAILED: " + t);
        }
    }

    private void stopWatch(String why) {
        Process p = proc;
        proc = null;
        Watchdog.running = false;
        if (p != null) {
            try {
                p.destroy();
            } catch (Throwable ignored) {}
        }
        // ⚠️ destroy() 只杀 su，脚本 sh 会被 init 收养继续跑（实测踩到：停了之后 pid 26131 还在）
        //   所以要按脚本自己报的 pid 补一刀
        int sp = Watchdog.scriptPid;
        Watchdog.scriptPid = 0;
        if (sp > 0) {
            try {
                Process k = Runtime.getRuntime().exec(new String[]{"su", "-c", "kill -9 " + sp});
                k.waitFor();
                Watchdog.dbg(this, "killed script pid " + sp);
            } catch (Throwable t) {
                Watchdog.dbg(this, "kill script failed: " + t);
            }
        }
        Watchdog.pushEvent("STOP " + why);
    }

    /** 进程结束后：该不该拉起来 */
    private class Waiter extends Thread {
        private final Process p;
        private final long t0 = SystemClock.elapsedRealtime();

        Waiter(Process p) {
            this.p = p;
        }

        public void run() {
            try {
                p.waitFor();
            } catch (Throwable ignored) {}
            long lived = SystemClock.elapsedRealtime() - t0;
            Watchdog.running = false;
            Watchdog.dbg(WatchService.this, "waiter: lived=" + lived
                    + "ms everHello=" + Watchdog.everHello + " wantRun=" + wantRun);
            boolean denied = !Watchdog.everHello && lived < 4000;
            if (denied) {
                Watchdog.rootDenied = true;
                Watchdog.err = "没拿到 root：su 被拒绝或没装 su。\n"
                        + "去 KernelSU / Magisk 里给「QQ 看门狗」放行 root，再点一次启动。";
                Watchdog.pushEvent("NOROOT 4 秒内就退出了，判定为 su 被拒");
                ui.post(new Runnable() {
                    public void run() {
                        updateNotif();
                    }
                });
                return;   // 不给 root 就别无限重试了
            }
            Watchdog.pushEvent("EXIT 脚本退出（跑了 " + (lived / 1000) + " 秒）");
            if (wantRun && restartCount < 200) {
                ui.postDelayed(new Runnable() {
                    public void run() {
                        if (wantRun) startWatch();
                    }
                }, 5000);
            }
        }
    }

    /** 一行行读脚本输出 */
    private class Reader extends Thread {
        private final InputStream in;

        Reader(InputStream in) {
            this.in = in;
        }

        public void run() {
            List<String> rows = new ArrayList<String>();
            BufferedReader br = null;
            try {
                br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.startsWith("HELLO")) {
                        Watchdog.dbg(WatchService.this, "got: " + line);
                        int q = line.indexOf("pid=");
                        if (q > 0) {
                            try {
                                int sp = Integer.parseInt(
                                        line.substring(q + 4).split(" ")[0].trim());
                                Watchdog.scriptPid = sp;
                                Watchdog.dbg(WatchService.this, "script pid=" + sp);
                            } catch (Throwable ignored) {}
                        }
                        Watchdog.everHello = true;
                        Watchdog.rootDenied = false;
                        Watchdog.err = "";
                        Watchdog.pushEvent("START " + line);
                    } else if (line.startsWith("BEGIN")) {
                        rows.clear();
                    } else if (line.startsWith("P ")) {
                        rows.add(line.substring(2).trim());
                    } else if (line.startsWith("END")) {
                        Watchdog.procs = rows.toArray(new String[0]);
                        Watchdog.lastRound = System.currentTimeMillis();
                    } else if (line.startsWith("E ")) {
                        Watchdog.pushEvent(line.substring(2).trim());
                    } else if (line.length() > 0 && !line.startsWith("CFG")) {
                        Watchdog.err = line;
                    }
                }
            } catch (Throwable t) {
                Watchdog.err = "读输出失败：" + t;
                Watchdog.dbg(WatchService.this, "reader ERR: " + t);
            } finally {
                try {
                    if (br != null) br.close();
                } catch (Throwable ignored) {}
            }
        }
    }

    private void copyAsset(String name, File dst) throws Exception {
        long srcLen = -1;
        try {
            InputStream s = getAssets().open(name);
            srcLen = s.available();
            s.close();
        } catch (Throwable ignored) {}
        if (dst.isFile() && srcLen > 0 && dst.length() == srcLen) return;  // 没变就不重写
        InputStream in = getAssets().open(name);
        FileOutputStream out = new FileOutputStream(dst);
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        out.flush();
        out.close();
        in.close();
        Watchdog.dbg(this, "asset copied: " + dst.getName() + " " + dst.length() + " bytes");
        try {
            Runtime.getRuntime().exec(new String[]{"su", "-c",
                    "chmod 644 " + dst.getAbsolutePath()}).waitFor();
        } catch (Throwable ignored) {}
    }

    // ==================== 通知 ====================

    private void buildChannel() {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel c = new NotificationChannel(Watchdog.CH, "看门狗",
                    NotificationManager.IMPORTANCE_LOW);
            c.setShowBadge(false);
            c.setSound(null, null);
            nm.createNotificationChannel(c);
        } catch (Throwable ignored) {}
    }

    private PendingIntent pi(String action, int code) {
        Intent i = new Intent(this, WatchService.class).setAction(action);
        int f = Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0;
        return PendingIntent.getService(this, code, i, f);
    }

    private Notification buildNotif() {
        int f = Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0;
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), f);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, Watchdog.CH)
                : new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.ic_menu_recent_history);
        b.setContentTitle(Watchdog.running ? "QQ 看门狗运行中" : "QQ 看门狗已停止");
        b.setContentText(summary());
        b.setOngoing(Watchdog.running);
        b.setContentIntent(open);
        if (Watchdog.running) {
            b.addAction(new Notification.Action.Builder(null, "停止", pi(ACTION_STOP, 1)).build());
        } else {
            b.addAction(new Notification.Action.Builder(null, "启动", pi(ACTION_START, 2)).build());
        }
        return b.build();
    }

    private String summary() {
        String[] ps = Watchdog.procs;
        if (!Watchdog.running) {
            if (Watchdog.rootDenied) return "没拿到 root，点开看怎么办";
            return "点开可以重新启动";
        }
        if (ps.length == 0) return "等下第一轮采样…";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ps.length; i++) {
            String[] kv = MainActivity.kv(ps[i]);
            String kind = kv[0];
            String th = kv[2], lim = kv[3], cpu = kv[4];
            if (sb.length() > 0) sb.append("  ·  ");
            sb.append("main".equals(kind) ? "主" : "子").append(" ").append(th).append("/").append(lim)
              .append(" ").append(cpu).append("%");
        }
        return sb.toString();
    }

    private void startFg() {
        Notification n = buildNotif();
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(Watchdog.NOTIF, n,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(Watchdog.NOTIF, n);
            }
        } catch (Throwable t) {
            try {
                startForeground(Watchdog.NOTIF, n);
            } catch (Throwable ignored) {}
        }
    }

    private final Runnable notifTask = new Runnable() {
        public void run() {
            updateNotif();
            if (Watchdog.running) ui.postDelayed(this, 3000);
        }
    };

    private void updateNotif() {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            nm.notify(Watchdog.NOTIF, buildNotif());
        } catch (Throwable ignored) {}
    }

    public static String stamp(long ms) {
        return new SimpleDateFormat("HH:mm:ss").format(new Date(ms));
    }
}
