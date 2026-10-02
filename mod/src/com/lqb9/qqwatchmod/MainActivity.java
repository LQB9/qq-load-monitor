package com.lqb9.qqwatchmod;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** 模块自带的说明页：LSPosed 怎么勾、面板怎么呼出、开关文件在哪 */
public class MainActivity extends Activity {
    private CollectorSwitchView collectorSwitch;

    private static final int PINK = 0xFFFF3D7F;
    private static final int PINK2 = 0xFFFFA3CB;
    private static final int INK = 0xFF1C1C22;
    private static final int SUB = 0xFF6B7280;
    private static final int LINE = 0xFFE8EAF0;

    private float dp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView sc = new ScrollView(this);
        sc.setBackgroundColor(0xFFF5F6FA);
        sc.setFillViewport(true);
        final LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        sc.addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 头
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setPadding((int) dp(20), (int) dp(20), (int) dp(20), (int) dp(18));
        GradientDrawable hg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{PINK, PINK2});
        hg.setCornerRadii(new float[]{0, 0, 0, 0, dp(20), dp(20), dp(20), dp(20)});
        head.setBackgroundDrawable(hg);
        TextView h1 = new TextView(this);
        h1.setText("QQ负载监控");
        h1.setTextSize(22f);
        h1.setTextColor(0xFFFFFFFF);
        head.addView(h1);
        TextView h2 = new TextView(this);
        h2.setText("模块 " + ver() + "  ·  核心负载与任务处理");
        h2.setTextSize(12.5f);
        h2.setTextColor(0xFFFFFFFF);
        h2.setAlpha(0.92f);
        head.addView(h2);
        root.addView(head);

        root.addView(sec("怎么用（只有两步）"));
        LinearLayout c1 = card();
        c1.addView(row("1", "在 LSPosed 里启用本模块", "作用域勾上「QQ」，勾完强行停止 QQ 再打开"));
        c1.addView(div());
        c1.addView(row("2", "打开 QQ，长按主界面右上角的「+」", "如果先出现 QQ 增强面板，从「实验」组进入负载监控；设置 CPU 阈值后打开监控"));
        root.addView(c1);

        root.addView(sec("精确核心采集（需要 root）"));
        LinearLayout collector = card();
        collectorSwitch=new CollectorSwitchView(this,new CollectorSwitchView.Control(){
            public void query(java.util.function.Consumer<CollectorStatus> done){RootControl.status(done);}
            public void start(java.util.function.Consumer<String> done){RootControl.start(getApplicationContext(),done::accept);}
            public void stop(java.util.function.Consumer<String> done){RootControl.stop(done::accept);}
        });
        collectorSwitch.setPadding((int)dp(16),(int)dp(12),(int)dp(16),(int)dp(12));
        collector.addView(collectorSwitch);root.addView(collector);

        root.addView(sec("新版图表面板"));
        LinearLayout charts = card();
        charts.addView(para("概览：总负载持续超限规则显示合计折线；单线程持续超限规则显示按线程身份区分的独立趋势，以及负载和连续超限进度；逐核负载与频率合并展示。\n"
                + "明细：共用单线程趋势组件，前12个可检测线程的完整负载与计时；日志导出、全核心参考榜和完整线程信息按需展开。\n"
                + "处理：完整线程名、PID/TID、QQ合计/线程自身负载、合计/单线程触发来源、当时阈值/时长/累计时间/模式、前后台、实际结果和原因；点击展开执行记录与调用栈，清单不导出。\n"
                + "设置：合计与单线程都有独立检测和自动处理开关；CPU 0—7自由选择，两种规则各自阈值/时长、共用采样。总监控即时生效，其他设置统一保存。关闭某一检测不影响另一规则；关闭自动处理则仅记录。\n\n"
                + "百分比图只统计 QQ 在所选核心上的实际 CPU 时间；MHz 读数显示频率，不参与阈值计算。缺失或过期时暂停处理。"));
        root.addView(charts);
        root.addView(sec("GIF专项处理（测试规则）"));
        LinearLayout gif=card();
        gif.addView(para("合计自动处理规则达到所设阈值与持续时长，或单线程自动处理规则达标并连续确认同一业务后，按已观测的RenderTask业务定位具体GIF，调用播放停止接口并阻止该对象继续渲染。画面保留当前帧，线程池工作线程保留。\n\n处理页显示停止确认、恢复和复采结果，可点击“恢复已暂停 GIF”。关闭监控或更改设置会解除暂停；不可见或已释放对象不主动重播。\n\n接口或业务归属不能确认时如实记录，其他任务仍沿用原取消策略。故障机是否降载需实际验证。"));
        root.addView(gif);

        root.addView(sec("日志记录与导出"));
        LinearLayout logs = card();
        logs.addView(para("模块自动记录运行事件、负载快照、热点线程和核心频率，QQ 重启后仍保留。"
                + "负载快照约每 5 秒记录一次（不快于采样），日志最多 2 MiB，滚动覆盖最旧记录。\n\n"
                + "包含合计和单线程的检测开关、自动处理开关、阈值、持续时长与模式；快照保留准确PID/TID启动身份、所选核心负载与累计时长，TOTAL_LIMIT/THREAD_LIMIT记录达标事件、检测来源和允许处理的来源。仅记录模式也会写入日志，处理清单不导出。\n\n"
                + "采集异常、原因变化和恢复单独记录，包含CPU时间校验与逐核丢事件计数；短暂异常恢复后仍可补记。更新后需停止并重新启动精确采集。\n\n"
                + "执行记录自动保留任务实例、业务方法、调度周期、执行次数、CPU耗时和执行入口栈；任务回到等待后也能查询最近10秒的记录。"
                + "Handler消息补充消息编号、回调入口和执行情况；没有取消接口时说明具体限制。线程身份由目标进程自行复核，读取失败与已退出分开显示。"
                + "执行CPU为全部核心的诊断参考，不参与所选核心阈值判定。未覆盖、限频、截断会明确标记。\n\n"
                + "在 QQ 监控面板进入「明细」，点击「导出日志到下载目录」。"
                + "文件管理器打开「下载 / QQWatchdog」，可找到按时间命名的 UTF-8 文本日志。"
                + "Android 10 及以上无需额外存储授权；Android 8/9 需要 QQ 的存储权限。日志不包含聊天内容。"));
        root.addView(logs);

        root.addView(sec("它到底在干什么"));
        LinearLayout c2 = card();
        c2.addView(para("汇总 QQ 所属 UID 全部进程的 CPU 时间增量，同时显示最忙的 12 个线程："
                + "线程名、PID、TID、CPU 占用和最后运行核心。线程数量仅供查看，不参与负载判定。\n\n"
                + "单核满载 = 100%，QQ 总负载 200%~300% 表示约占用 2~3 个核心。"
                + "4 个核心全部满载是 400%，不会按整部手机折算成 0~100%。"));
        c2.addView(para("默认每 1 秒采样，所选核心的 QQ 合计 CPU 严格超过 200% 并持续达到设置时长后尝试处理，阈值和持续时长均可改。"
                + "持续时长默认 0 秒：首轮有效超限即尝试处理；设为 5 秒则连续超限达到 5 秒后尝试。前台与后台共用阈值和持续时长。"
                + "负载回落、采样无效或设置变化会重新计时；按有效采样窗口累积，达到时长后的首次有效采样触发。"
                + "优先定位占用最高的工作线程；只请求取消覆盖该窗口的 Java 任务，并确认任务是否结束。"
                + "任务不响应中断、原生线程没有专用停止接口时显示实际限制，不强杀 QQ 进程。"));
        c2.addView(para("核心频率单独显示：优先硬件读数，取不到时显示驱动请求频率。"
                + "读不到就写「频率不可读」。1100 MHz 与 CPU 占用是两个指标，"
                + "不能单凭某个频率判定线程异常。线程会迁移，「最后核心」不是整个采样窗口都在那个核心。"));
        root.addView(c2);

        root.addView(sec("开关在哪"));
        LinearLayout c3 = card();
        c3.addView(para("都是文件，一个文件一个开关：\n"
                + "/sdcard/Android/data/com.tencent.mobileqq/files/\n\n"
                + "watchdog.on      存在 = 开（默认不开，面板里点一下）\n"
                + "watchdog.load    整组负载设置\n\n"
                + "cpu=200         单核百分比阈值\n"
                + "cores=255       核心选择位掩码（0—7 全选）\n"
                + "interval=1      采样间隔秒\n"
                + "action=stop_task 请求合作停止任务"));
        c3.addView(para("改设置后下次采样生效，并重新累计超限时间。"
                + "旧 watchdog.base / .k / .scope 不再参与判定；"
                + "首次升级如果存在 watchdog.iv，则沿用原采样间隔。最近事件最多保留 20 条，QQ 重启后清空。"));
        root.addView(c3);

        root.addView(sec("和 QQ 增强怎么配合"));
        LinearLayout c5 = card();
        c5.addView(para("安装 QQ 增强时，长按「+」优先打开它的面板，从「实验」组的看门狗入口进入这里。"
                + "只安装本模块时，长按直接打开负载面板。"));
        c5.addView(para("两个面板可以互相跳，底部/列表里各有一个入口：\n"
                + "· 本面板底部「打开 QQ 增强面板」\n"
                + "· QQ 增强里「实验 → 线程泄露看门狗」"));
        c5.addView(para("⚠️ 老版本 QQ 增强留下的 killleak.* 开关文件本模块不读、也没人读了。"
                + "面板底下会有一行提示，点「清理遗留文件」一次删干净"
                + "（只删 killleak.on / .scope / .base / .k / .limit 这 5 个名字）。"));
        root.addView(c5);

        root.addView(sec("本机状态"));
        LinearLayout c4 = card();
        c4.addView(info("模块版本", ver()));
        c4.addView(div());
        c4.addView(info("本机 QQ", qqVer()));
        root.addView(c4);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.setMargins((int) dp(16), (int) dp(8), (int) dp(16), 0);
        bar.setLayoutParams(blp);
        TextView open = btn("打开 QQ", new int[]{PINK, PINK2}, 0xFFFFFFFF,
                new View.OnClickListener() {
                    public void onClick(View v) {
                        try {
                            Intent it = getPackageManager()
                                    .getLaunchIntentForPackage("com.tencent.mobileqq");
                            if (it != null) startActivity(it);
                        } catch (Throwable ignored) {}
                    }
                });
        bar.addView(open, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(bar);

        TextView foot = new TextView(this);
        foot.setText("本机采样 · 仅监控 QQ · 不联网");
        foot.setTextSize(11.5f);
        foot.setTextColor(SUB);
        foot.setGravity(Gravity.CENTER);
        foot.setPadding(0, (int) dp(16), 0, (int) dp(30));
        root.addView(foot);

        sc.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @SuppressWarnings("deprecation")
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                android.view.View hd = root.getChildAt(0);
                hd.setPadding((int) dp(20), (int) dp(20) + insets.getSystemWindowInsetTop(),
                        (int) dp(20), (int) dp(18));
                return insets;
            }
        });
        setContentView(sc);
    }

    @Override protected void onResume(){super.onResume();if(collectorSwitch!=null)collectorSwitch.resume();}
    @Override protected void onPause(){if(collectorSwitch!=null)collectorSwitch.pause();super.onPause();}

    private String ver() {
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            return "v" + pi.versionName;
        } catch (Throwable t) {
            return "v?";
        }
    }

    private String qqVer() {
        try {
            PackageInfo pi = getPackageManager().getPackageInfo("com.tencent.mobileqq", 0);
            return pi.versionName + "（vc " + pi.versionCode + "）";
        } catch (Throwable t) {
            return "没装 / 读不到";
        }
    }

    private View sec(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(12.5f);
        t.setTextColor(SUB);
        t.setPadding((int) dp(22), (int) dp(18), 0, (int) dp(7));
        return t;
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

    private View div() {
        View v = new View(this);
        v.setBackgroundColor(0xFFF1F3F7);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) dp(1));
        lp.setMargins((int) dp(16), 0, (int) dp(16), 0);
        v.setLayoutParams(lp);
        return v;
    }

    private View row(String no, String title, String sub) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setPadding((int) dp(16), (int) dp(13), (int) dp(16), (int) dp(13));
        TextView n = new TextView(this);
        n.setText(no);
        n.setTextSize(13f);
        n.setTextColor(PINK);
        n.setGravity(Gravity.CENTER);
        GradientDrawable ng = new GradientDrawable();
        ng.setColor(0xFFFFF0F6);
        ng.setCornerRadius(dp(10));
        n.setBackgroundDrawable(ng);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams((int) dp(26), (int) dp(26));
        nlp.rightMargin = (int) dp(12);
        n.setLayoutParams(nlp);
        r.addView(n);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(14.5f);
        t.setTextColor(INK);
        box.addView(t);
        TextView s = new TextView(this);
        s.setText(sub);
        s.setTextSize(12.5f);
        s.setTextColor(SUB);
        s.setPadding(0, (int) dp(3), 0, 0);
        box.addView(s);
        r.addView(box);
        return r;
    }

    private View para(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(13.5f);
        t.setTextColor(INK);
        t.setLineSpacing(dp(4), 1f);
        t.setPadding((int) dp(16), (int) dp(11), (int) dp(16), (int) dp(11));
        return t;
    }

    private View info(String name, String value) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding((int) dp(16), (int) dp(12), (int) dp(16), (int) dp(12));
        TextView n = new TextView(this);
        n.setText(name);
        n.setTextSize(13.5f);
        n.setTextColor(SUB);
        n.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        r.addView(n);
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(13f);
        v.setTextColor(INK);
        r.addView(v);
        return r;
    }

    private TextView btn(String text, int[] colors, int fg, View.OnClickListener l) {
        TextView b = new TextView(this);
        b.setText(text);
        b.setTextSize(14.5f);
        b.setTextColor(fg);
        b.setGravity(Gravity.CENTER);
        b.setPadding(0, (int) dp(13), 0, (int) dp(13));
        GradientDrawable g = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT, colors);
        g.setCornerRadius(dp(14));
        b.setBackgroundDrawable(g);
        b.setOnClickListener(l);
        return b;
    }
}
