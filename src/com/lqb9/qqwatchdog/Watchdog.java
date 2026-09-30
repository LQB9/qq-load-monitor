package com.lqb9.qqwatchdog;

/**
 * 全局状态 + 常量（这个 App 自用，不需要活得比进程久，静态字段够用）。
 *
 * 数据流：WatchService 起一个 root shell 跑 assets/qqwatch.sh，
 * 脚本每 N 秒往 stdout 打一段 BEGIN/CFG/P/END，Service 解析完塞进这里，
 * MainActivity 每秒读一次刷新界面。
 */
public final class Watchdog {

    public static final String CH = "qqwatch";
    public static final int NOTIF = 0x7101;

    // SharedPreferences 键
    public static final String K_BASE  = "base";   // 基线线程数，0 = 自动
    public static final String K_K     = "k";      // 系数
    public static final String K_SCOPE = "scope";  // all / main / worker
    public static final String K_IV    = "iv";     // 采样间隔秒
    public static final String K_WANT  = "want";   // 用户是否点了「启动」

    public static final int DEF_BASE = 0;
    public static final int DEF_K = 3;
    public static final int DEF_IV = 30;
    public static final String DEF_SCOPE = "all";

    // ---- 运行时状态（Service 写，Activity 读）----
    public static volatile boolean running;
    public static volatile long lastRound;
    public static volatile long startedAt;
    public static volatile String[] procs = new String[0];
    public static volatile String[] events = new String[0];
    public static volatile String err = "";
    public static volatile boolean rootDenied;
    public static volatile boolean everHello;
    /** 守护脚本自己的 pid（脚本启动时打的 HELLO pid=N）——停止时要靠它补刀 */
    public static volatile int scriptPid;

    /**
     * 一次性自检：这台机器上 App 进程到底能不能 exec su、卡在哪一层。
     * 结果写 debug.log。
     */
    public static void probe(final android.content.Context c) {
        StringBuilder sb = new StringBuilder("=== PROBE ===");
        try {
            java.io.File su = new java.io.File("/system/bin/su");
            sb.append("\n /system/bin/su exists=").append(su.exists())
              .append(" canRead=").append(su.canRead())
              .append(" canExec=").append(su.canExecute())
              .append(" len=").append(su.length());
            java.io.File sh = new java.io.File("/system/bin/sh");
            sb.append("\n /system/bin/sh exists=").append(sh.exists())
              .append(" canExec=").append(sh.canExecute());
            sb.append("\n PATH=").append(System.getenv("PATH"));
            sb.append("\n SELF_UID=").append(android.os.Process.myUid());
        } catch (Throwable t) {
            sb.append("\n probe stat ERR ").append(t);
        }
        dbg(c, sb.toString());
        // 直接试一批可能的 su 路径（每个错误码都不同信息量）
        String[] cands = {
                "/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su",
                "/debug_ramdisk/su", "/data/adb/ksu/bin/su", "/data/adb/magisk/su",
                "/dev/su", "/system/bin/ksud", "/data/adb/ksud",
        };
        for (int i = 0; i < cands.length; i++) {
            java.io.File f = new java.io.File(cands[i]);
            dbg(c, "cand " + cands[i] + " exists=" + f.exists()
                    + " canExec=" + f.canExecute() + " len=" + f.length());
        }
        // 用能跑起来的 sh 去问系统：App 眼里 /system/bin 长什么样、挂了什么
        run(c, "/system/bin/sh", "-c", "ls -la /system/bin/ 2>&1 | grep -i -e su -e ksud");
        run(c, "/system/bin/sh", "-c", "ls /system/xbin /sbin /su 2>&1");
        run(c, "/system/bin/sh", "-c", "ls -la /data/adb 2>&1 | head -8");
        run(c, "/system/bin/sh", "-c", "cat /proc/mounts 2>&1 | grep -i -e ' /system ' -e overlay -e ksu");
        run(c, "/system/bin/sh", "-c", "cat /proc/self/mountinfo 2>&1 | grep -i -e system -e ksu | head -8");
        run(c, "/system/bin/sh", "-c", "id; cat /proc/self/attr/current 2>&1");
        dbg(c, "=== PROBE END ===");
    }

    private static void run(android.content.Context c, final String... argv) {
        try {
            final Process p = Runtime.getRuntime().exec(argv);
            final StringBuilder out = new StringBuilder();
            Thread t = new Thread(new Runnable() {
                public void run() {
                    try {
                        java.io.BufferedReader br = new java.io.BufferedReader(
                                new java.io.InputStreamReader(p.getInputStream(), "UTF-8"));
                        String l;
                        while ((l = br.readLine()) != null && out.length() < 300) {
                            out.append(l).append(' ');
                        }
                        br = new java.io.BufferedReader(
                                new java.io.InputStreamReader(p.getErrorStream(), "UTF-8"));
                        while ((l = br.readLine()) != null && out.length() < 400) {
                            out.append("| ").append(l).append(' ');
                        }
                    } catch (Throwable ignored) {}
                }
            });
            t.setDaemon(true);
            t.start();
            int rc = -999;
            long t0 = System.currentTimeMillis();
            while (System.currentTimeMillis() - t0 < 6000) {
                try {
                    rc = p.exitValue();
                    break;
                } catch (Throwable notYet) {
                    Thread.sleep(100);
                }
            }
            if (rc == -999) {
                p.destroy();
                dbg(c, "run[" + argv[0] + "] TIMEOUT 6s（多半是 su 在等授权弹窗）out=" + out);
            } else {
                t.join(500);
                dbg(c, "run[" + argv[0] + "] rc=" + rc + " out=" + out);
            }
        } catch (Throwable t) {
            dbg(c, "run[" + argv[0] + "] EXEC-ERR " + t);
        }
    }

    private Watchdog() {}

    /**
     * 调试用落盘日志（files/debug.log）。
     * logcat 上这台机器被 MIUI 刷得看不清，出问题只能落文件看 —— 这条坑项目里记过好几次。
     */
    public static void dbg(android.content.Context c, String s) {
        try {
            java.io.File f = new java.io.File(c.getFilesDir(), "debug.log");
            if (f.length() > 60000) f.delete();
            java.io.FileOutputStream o = new java.io.FileOutputStream(f, true);
            o.write((new java.text.SimpleDateFormat("MM-dd HH:mm:ss")
                    .format(new java.util.Date()) + " " + s + "\n").getBytes("UTF-8"));
            o.close();
        } catch (Throwable ignored) {}
    }

    public static void reset() {
        running = false;
        lastRound = 0;
        procs = new String[0];
        err = "";
        rootDenied = false;
        everHello = false;
    }

    /** 事件只留最近 30 条，新的在前 */
    public static synchronized void pushEvent(String s) {
        String[] old = events;
        int n = Math.min(old.length + 1, 30);
        String[] neo = new String[n];
        neo[0] = s;
        for (int i = 1; i < n; i++) neo[i] = old[i - 1];
        events = neo;
    }

    public static String scopeText(String scope) {
        if ("main".equals(scope)) return "只看主进程";
        if ("worker".equals(scope)) return "只看子进程";
        return "全部进程";
    }
}
