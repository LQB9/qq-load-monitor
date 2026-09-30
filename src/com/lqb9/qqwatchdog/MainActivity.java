package com.lqb9.qqwatchdog;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 看门狗的界面：状态 + 设置 + 事件。
 *
 * 所有状态都从 Watchdog 那几个静态字段读（WatchService 写的），这里每秒刷一次。
 */
public class MainActivity extends Activity {

    private static final int PINK  = 0xFFFF3D7F;
    private static final int PINK2 = 0xFFFFA3CB;
    private static final int INK   = 0xFF1C1C22;
    private static final int SUB   = 0xFF6B7280;
    private static final int LINE  = 0xFFE8EAF0;
    private static final int OK    = 0xFF16A34A;
    private static final int WARN  = 0xFFD97706;
    private static final int BAD   = 0xFFDC2626;

    private SharedPreferences sp;
    private final Handler h = new Handler(Looper.getMainLooper());

    private TextView tvState, tvLast, tvProcs, tvEvents, tvHint, tvBtn;
    private EditText etBase, etK, etIv;
    private TextView segAll, segMain, segWorker;
    private String scope = "all";
    private boolean polling;

    private float dp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("cfg", MODE_PRIVATE);
        scope = sp.getString(Watchdog.K_SCOPE, Watchdog.DEF_SCOPE);

        ScrollView sc = new ScrollView(this);
        sc.setBackgroundColor(0xFFF5F6FA);
        sc.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, 0, 0, (int) dp(30));
        sc.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(header());

        // ---------------- 状态 ----------------
        root.addView(section("现在什么情况"));
        LinearLayout c1 = card();
        tvState = big();
        c1.addView(tvState);
        tvLast = note("还没采样过");
        c1.addView(tvLast);
        c1.addView(divider());
        tvProcs = body("—");
        c1.addView(tvProcs);
        root.addView(c1);

        tvHint = note2("");
        tvHint.setPadding((int) dp(22), (int) dp(8), (int) dp(22), 0);
        root.addView(tvHint);

        // ---------------- 最近事件 ----------------
        root.addView(section("最近事件"));
        LinearLayout c2 = card();
        tvEvents = body("还没有触发过。正常情况下一整天都不会有东西。");
        c2.addView(tvEvents);
        root.addView(c2);

        // ---------------- 设置 ----------------
        root.addView(section("怎么判（改了要点保存）"));
        LinearLayout c3 = card();
        etBase = numField(c3, "基线线程数", "0 = 自动（每个进程各自记住第一次看到的线程数）");
        etK = numField(c3, "系数 K", "上限 = 基线 + K × 本进程 CPU%");
        etIv = numField(c3, "采样间隔（秒）", "最少 5 秒");
        c3.addView(divider());

        LinearLayout scopeRow = new LinearLayout(this);
        scopeRow.setOrientation(LinearLayout.VERTICAL);
        scopeRow.setPadding((int) dp(16), (int) dp(12), (int) dp(16), (int) dp(6));
        TextView sl = new TextView(this);
        sl.setText("监控范围");
        sl.setTextSize(13.5f);
        sl.setTextColor(SUB);
        scopeRow.addView(sl);
        LinearLayout segs = new LinearLayout(this);
        segs.setOrientation(LinearLayout.HORIZONTAL);
        segs.setPadding(0, (int) dp(8), 0, 0);
        segAll = seg("全部进程", segs, 0);
        segMain = seg("只看主进程", segs, 1);
        segWorker = seg("只看子进程", segs, 2);
        scopeRow.addView(segs);
        c3.addView(scopeRow);

        TextView save = btn("保存并重启看门狗", new int[]{PINK, PINK2}, 0xFFFFFFFF,
                new View.OnClickListener() {
                    public void onClick(View v) {
                        saveCfg();
                    }
                });
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.setMargins((int) dp(16), (int) dp(6), (int) dp(16), (int) dp(14));
        save.setLayoutParams(slp);
        c3.addView(save);
        root.addView(c3);

        // ---------------- 说明 ----------------
        root.addView(section("这个判定式怎么来的"));
        LinearLayout c4 = card();
        c4.addView(para("一开始是直接数线程条数，超过阈值就杀 —— 那样误杀太多。"
                + "现在改成「上限自己跟着负载走」："));
        c4.addView(para("动态上限 = 基线 + K × 本进程 CPU%\n"
                + "连续 3 次超过上限才动手，守护刚启动的 60 秒内不判。"));
        c4.addView(para("K 越大越宽松。K=0 是合法的，意思是上限固定等于基线。"));
        c4.addView(para("⚠️ CPU% 必须取「本进程」的，不能用 /proc/loadavg："
                + "实测 QQ 空闲时它自己只占 1.8~3.5%，而系统 loadavg 能到 9.69（别的 App 在忙），"
                + "拿 loadavg 当负载会乱杀。"));
        c4.addView(para("杀的是进程，不是数据：QQ 被 kill -9 之后会自己重新拉起来，"
                + "聊天记录不受影响（顶多断开一下连接）。"));
        root.addView(c4);

        // ---------------- 底部按钮 ----------------
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.setMargins((int) dp(16), (int) dp(6), (int) dp(16), 0);
        bar.setLayoutParams(blp);

        tvBtn = btn("启动", new int[]{PINK, PINK2}, 0xFFFFFFFF, new View.OnClickListener() {
            public void onClick(View v) {
                toggle();
            }
        });
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f);
        l1.rightMargin = (int) dp(5);
        bar.addView(tvBtn, l1);

        TextView qq = btn("打开 QQ", new int[]{0xFFF1F3F7, 0xFFF1F3F7}, INK,
                new View.OnClickListener() {
                    public void onClick(View v) {
                        try {
                            Intent it = getPackageManager()
                                    .getLaunchIntentForPackage("com.tencent.mobileqq");
                            if (it != null) startActivity(it);
                        } catch (Throwable ignored) {}
                    }
                });
        LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        l2.leftMargin = (int) dp(5);
        bar.addView(qq, l2);

        TextView clr = btn("清事件", new int[]{0xFFF1F3F7, 0xFFF1F3F7}, INK,
                new View.OnClickListener() {
                    public void onClick(View v) {
                        Watchdog.events = new String[0];
                        refresh();
                    }
                });
        LinearLayout.LayoutParams l3 = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        l3.leftMargin = (int) dp(5);
        bar.addView(clr, l3);
        root.addView(bar);

        TextView foot = new TextView(this);
        foot.setText("自用 · 需要 root · 不联网 · 除了自己的状态目录不写任何地方");
        foot.setTextSize(11.5f);
        foot.setTextColor(SUB);
        foot.setGravity(Gravity.CENTER);
        foot.setPadding(0, (int) dp(16), 0, 0);
        root.addView(foot);

        setContentView(sc);

        loadCfg();
        askNotif();
        refresh();
        new Thread(new Runnable() {
            public void run() {
                Watchdog.probe(MainActivity.this);
            }
        }).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        polling = true;
        h.post(tick);
        // 如果用户之前点了启动但服务没在跑（比如被系统杀了），这里补上
        if (sp.getBoolean(Watchdog.K_WANT, false) && !Watchdog.running) {
            startSvc(WatchService.ACTION_START);
        }
    }

    @Override
    protected void onPause() {
        polling = false;
        h.removeCallbacks(tick);
        super.onPause();
    }

    private final Runnable tick = new Runnable() {
        public void run() {
            refresh();
            if (polling) h.postDelayed(this, 1000);
        }
    };

    // ==================== 交互 ====================

    private void toggle() {
        if (Watchdog.running) {
            startSvc(WatchService.ACTION_STOP);
        } else {
            Watchdog.pushEvent("点击启动");
            startSvc(WatchService.ACTION_START);
        }
        h.postDelayed(new Runnable() {
            public void run() {
                refresh();
            }
        }, 400);
    }

    private void startSvc(String action) {
        Intent i = new Intent(this, WatchService.class).setAction(action);
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
            else startService(i);
        } catch (Throwable t) {
            Watchdog.err = "启动服务失败：" + t;
        }
    }

    private void saveCfg() {
        int base = parseInt(etBase, Watchdog.DEF_BASE, 0, 5000);
        int k = parseInt(etK, Watchdog.DEF_K, 0, 200);
        int iv = parseInt(etIv, Watchdog.DEF_IV, 5, 3600);
        sp.edit()
                .putString(Watchdog.K_BASE, String.valueOf(base))
                .putString(Watchdog.K_K, String.valueOf(k))
                .putString(Watchdog.K_IV, String.valueOf(iv))
                .putString(Watchdog.K_SCOPE, scope)
                .putBoolean(Watchdog.K_WANT, true)
                .apply();
        loadCfg();
        Watchdog.pushEvent("保存设置 base=" + base + " k=" + k + " iv=" + iv + " scope=" + scope);
        startSvc(WatchService.ACTION_KICK);
    }

    private int parseInt(EditText et, int def, int lo, int hi) {
        try {
            int v = Integer.parseInt(et.getText().toString().trim());
            if (v < lo) v = lo;
            if (v > hi) v = hi;
            return v;
        } catch (Throwable t) {
            return def;
        }
    }

    private void loadCfg() {
        etBase.setText(sp.getString(Watchdog.K_BASE, String.valueOf(Watchdog.DEF_BASE)));
        etK.setText(sp.getString(Watchdog.K_K, String.valueOf(Watchdog.DEF_K)));
        etIv.setText(sp.getString(Watchdog.K_IV, String.valueOf(Watchdog.DEF_IV)));
        scope = sp.getString(Watchdog.K_SCOPE, Watchdog.DEF_SCOPE);
        paintSegs();
    }

    private void paintSegs() {
        style(segAll, "all".equals(scope));
        style(segMain, "main".equals(scope));
        style(segWorker, "worker".equals(scope));
    }

    private void style(TextView t, boolean on) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(10));
        g.setColor(on ? PINK : 0xFFF1F3F7);
        g.setStroke(1, on ? PINK : LINE);
        t.setBackgroundDrawable(g);
        t.setTextColor(on ? 0xFFFFFFFF : INK);
    }

    private void askNotif() {
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                if (checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                        != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 7);
                }
            }
        } catch (Throwable ignored) {}
    }

    // ==================== 刷新 ====================

    private void refresh() {
        boolean run = Watchdog.running;
        tvBtn.setText(run ? "停止" : "启动");
        tvBtn.setBackgroundDrawable(grad(run ? new int[]{0xFF9CA3AF, 0xFF6B7280}
                : new int[]{PINK, PINK2}));

        if (run) {
            tvState.setText("运行中");
            tvState.setTextColor(OK);
        } else if (Watchdog.rootDenied) {
            tvState.setText("没拿到 root");
            tvState.setTextColor(BAD);
        } else {
            tvState.setText("已停止");
            tvState.setTextColor(SUB);
        }

        long ago = Watchdog.lastRound > 0
                ? (System.currentTimeMillis() - Watchdog.lastRound) / 1000 : -1;
        tvLast.setText(ago < 0 ? "还没采样过"
                : (run ? "最近一次采样：" + ago + " 秒前"
                       : "上次采样：" + ago + " 秒前（已停）"));

        String[] ps = Watchdog.procs;
        if (ps.length == 0) {
            tvProcs.setText(run ? "还没有采样结果…\n（第一轮要等一个采样间隔）"
                    : "没在跑。点下面「启动」开始看着。");
        } else {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < ps.length; i++) {
                String[] v = kv(ps[i]);
                if (sb.length() > 0) sb.append('\n');
                sb.append("main".equals(v[0]) ? "主进程" : "子进程")
                  .append("  pid ").append(v[1]).append('\n')
                  .append("线程 ").append(v[2]).append(" / 上限 ").append(v[3])
                  .append("   CPU ").append(v[4]).append('%');
                if (!"0".equals(v[6])) sb.append("   超限 ").append(v[6]).append(" 次");
            }
            tvProcs.setText(sb.toString());
        }

        StringBuilder ev = new StringBuilder();
        String[] es = Watchdog.events;
        for (int i = 0; i < es.length && i < 12; i++) {
            if (ev.length() > 0) ev.append('\n');
            ev.append("· ").append(es[i]);
        }
        tvEvents.setText(ev.length() == 0 ? "还没有触发过。正常情况下一整天都不会有东西。"
                : ev.toString());
        tvEvents.setTextColor(hasKill() ? BAD : INK);

        String hint = Watchdog.err;
        tvHint.setText(hint == null ? "" : hint);
        tvHint.setTextColor(Watchdog.rootDenied ? BAD : WARN);
    }

    private boolean hasKill() {
        String[] es = Watchdog.events;
        for (int i = 0; i < es.length; i++) if (es[i].startsWith("KILL")) return true;
        return false;
    }

    /** "kind=main pid=1 threads=2 …" → [main,1,2,…] */
    public static String[] kv(String row) {
        String[] out = new String[]{"?", "0", "0", "0", "0.0", "0", "0", "0"};
        String[] parts = row.split(" ");
        int n = 0;
        for (int i = 0; i < parts.length && n < out.length; i++) {
            int e = parts[i].indexOf('=');
            if (e > 0) {
                out[n++] = parts[i].substring(e + 1);
            }
        }
        return out;
    }

    // ==================== 小组件 ====================

    private View header() {
        LinearLayout hh = new LinearLayout(this);
        hh.setOrientation(LinearLayout.VERTICAL);
        hh.setPadding((int) dp(20), (int) dp(20), (int) dp(20), (int) dp(18));
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{PINK, PINK2});
        g.setCornerRadii(new float[]{0, 0, 0, 0, dp(20), dp(20), dp(20), dp(20)});
        hh.setBackgroundDrawable(g);
        TextView t = new TextView(this);
        t.setText("QQ 看门狗");
        t.setTextSize(22f);
        t.setTextColor(0xFFFFFFFF);
        hh.addView(t);
        TextView s = new TextView(this);
        s.setText("QQ 线程数异常膨胀时自动杀掉它 · 独立运行，不依赖 Xposed");
        s.setTextSize(12.5f);
        s.setTextColor(0xFFFFFFFF);
        s.setAlpha(0.92f);
        s.setPadding(0, (int) dp(4), 0, 0);
        hh.addView(s);
        return hh;
    }

    private View section(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(12.5f);
        v.setTextColor(SUB);
        v.setPadding((int) dp(22), (int) dp(18), 0, (int) dp(7));
        return v;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xFFFFFFFF);
        g.setCornerRadius(dp(14));
        g.setStroke(1, LINE);
        c.setBackgroundDrawable(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins((int) dp(16), 0, (int) dp(16), 0);
        c.setLayoutParams(lp);
        return c;
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(0xFFF1F3F7);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) dp(1));
        lp.setMargins((int) dp(16), 0, (int) dp(16), 0);
        v.setLayoutParams(lp);
        return v;
    }

    private TextView big() {
        TextView t = new TextView(this);
        t.setTextSize(19f);
        t.setTextColor(SUB);
        t.setPadding((int) dp(16), (int) dp(14), (int) dp(16), 0);
        return t;
    }

    private TextView note(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(12.5f);
        t.setTextColor(SUB);
        t.setPadding((int) dp(16), (int) dp(5), (int) dp(16), (int) dp(14));
        return t;
    }

    private TextView note2(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(12.5f);
        t.setTextColor(WARN);
        t.setLineSpacing(dp(3), 1f);
        return t;
    }

    private TextView body(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(13.5f);
        t.setTextColor(INK);
        t.setLineSpacing(dp(4), 1f);
        t.setPadding((int) dp(16), (int) dp(12), (int) dp(16), (int) dp(12));
        return t;
    }

    private TextView para(String s) {
        return body(s);
    }

    private EditText numField(LinearLayout parent, String label, String hint) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding((int) dp(16), (int) dp(10), (int) dp(16), (int) dp(10));
        TextView l = new TextView(this);
        l.setText(label);
        l.setTextSize(13.5f);
        l.setTextColor(SUB);
        box.addView(l);
        EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        e.setTextSize(15f);
        e.setBackgroundDrawable(round(0xFFF4F6F9, 10));
        e.setPadding((int) dp(12), (int) dp(8), (int) dp(12), (int) dp(8));
        box.addView(e);
        TextView hh = new TextView(this);
        hh.setText(hint);
        hh.setTextSize(11.5f);
        hh.setTextColor(SUB);
        hh.setPadding(0, (int) dp(3), 0, 0);
        box.addView(hh);
        parent.addView(box);
        return e;
    }

    private TextView seg(String text, LinearLayout parent, final int idx) {
        final TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(12.5f);
        t.setGravity(Gravity.CENTER);
        t.setPadding((int) dp(4), (int) dp(9), (int) dp(4), (int) dp(9));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.rightMargin = idx < 2 ? (int) dp(6) : 0;
        t.setLayoutParams(lp);
        t.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                scope = idx == 1 ? "main" : (idx == 2 ? "worker" : "all");
                paintSegs();
            }
        });
        parent.addView(t);
        return t;
    }

    private TextView btn(String text, int[] colors, int fg, View.OnClickListener l) {
        TextView b = new TextView(this);
        b.setText(text);
        b.setTextSize(14.5f);
        b.setTextColor(fg);
        b.setGravity(Gravity.CENTER);
        b.setPadding(0, (int) dp(13), 0, (int) dp(13));
        b.setBackgroundDrawable(grad(colors));
        b.setOnClickListener(l);
        return b;
    }

    private GradientDrawable grad(int[] colors) {
        GradientDrawable g = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT, colors);
        g.setCornerRadius(dp(14));
        return g;
    }

    private GradientDrawable round(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radius));
        return g;
    }

    private String selfVersion() {
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            return pi.versionName;
        } catch (Throwable t) {
            return "?";
        }
    }
}
