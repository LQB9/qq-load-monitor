# -*- coding: utf-8 -*-
"""单文件 HTML：长按优先级倒过来 + 看门狗面板排版 + 锁屏抢红包定位（2026-09-30 晚）。

用法：uv run --with pillow python tools\report_v149.py
"""
import base64
import datetime
import io
import os
import sys

WD = r'D:\deepseek\qq-watchdog'
QM = r'D:\deepseek\qq-module'
IMG = os.path.join(WD, 'ref')
OUT_WD = os.path.join(WD, 'docs-html', '两模块-长按优先级与排版-2026-09-30.html')
OUT_QM = os.path.join(QM, 'docs-html', 'qq模块-v149-长按优先级.html')

CSS = """
*{box-sizing:border-box}
body{max-width:980px;margin:0 auto;padding:28px 22px 90px;background:#f6f7f9;
font-family:"Microsoft YaHei","Segoe UI",system-ui,sans-serif;font-size:15px;line-height:1.85;color:#1f2328}
h1{font-size:25px;margin:0 0 6px;color:#c2185b}
h2{font-size:19px;margin:32px 0 10px;padding-left:10px;border-left:5px solid #ff4d8d}
h3{font-size:16px;margin:18px 0 6px;color:#374151}
.sub{color:#6b7280;font-size:13.5px;margin-bottom:18px}
.card{background:#fff;border-radius:14px;padding:16px 20px;margin:12px 0;
box-shadow:0 1px 3px rgba(16,24,40,.07),0 1px 2px rgba(16,24,40,.04)}
.ok{border-left:5px solid #16a34a}.warn{border-left:5px solid #d97706}.info{border-left:5px solid #2563eb}
table{border-collapse:collapse;width:100%;font-size:14px;margin:8px 0}
th,td{border:1px solid #e5e7eb;padding:7px 10px;text-align:left;vertical-align:top}
th{background:#f9fafb}code{background:#f3f4f6;border-radius:5px;padding:1px 6px;font-size:13px}
pre{background:#0f172a;color:#e2e8f0;border-radius:10px;padding:12px 14px;overflow-x:auto;font-size:12.5px;line-height:1.6}
figure{margin:14px 0;text-align:center}
figure img{max-width:min(400px,100%);border-radius:12px;border:1px solid #e5e7eb;background:#fff}
figcaption{color:#6b7280;font-size:12.5px;margin-top:6px}
.two{display:flex;gap:14px;flex-wrap:wrap;justify-content:center}.two figure{flex:0 1 320px}
ul{margin:6px 0 6px 4px;padding-left:20px}li{margin:3px 0}.kv{color:#6b7280}
"""


def b64(p, max_w=430):
    from PIL import Image
    im = Image.open(p).convert('RGB')
    if im.width > max_w:
        im = im.resize((max_w, int(im.height * max_w / im.width)))
    buf = io.BytesIO()
    im.save(buf, 'JPEG', quality=82)
    return 'data:image/jpeg;base64,' + base64.b64encode(buf.getvalue()).decode('ascii')


def fig(name, cap, max_w=430):
    p = os.path.join(IMG, name)
    if not os.path.exists(p):
        return '<figure><figcaption>（缺图 %s）</figcaption></figure>' % name
    return '<figure><img src="%s" alt="%s"><figcaption>%s</figcaption></figure>' % (b64(p, max_w), cap, cap)


def build_index(dst, title):
    items = []
    for n in sorted(os.listdir(dst)):
        if not n.endswith('.html') or n == 'index.html':
            continue
        p = os.path.join(dst, n)
        when = datetime.datetime.fromtimestamp(os.path.getmtime(p)).strftime('%Y-%m-%d %H:%M')
        items.append('<li><a href="%s">%s</a> <span class="kv">%s · %.1f KB</span></li>'
                     % (n, n[:-5], when, os.path.getsize(p) / 1024.0))
    open(os.path.join(dst, 'index.html'), 'w', encoding='utf-8').write(
        '<!doctype html><meta charset="utf-8"><title>%s</title><style>%s</style><h1>%s</h1><ul>%s</ul>'
        % (title, CSS, title, '\n'.join(items)))


def main():
    b = []
    a = b.append
    a('<h1>QQ 增强 v1.49 + QQ 看门狗 v1.12</h1>')
    a('<div class="sub">2026-09-30 晚 · REDMI K90 Pro Max（QQ 9.2.85）· 全部真机实测</div>')

    a('<div class="card ok"><h3>这一版干了四件事</h3><ul>'
      '<li><b>长按「+」现在优先出 QQ 增强面板</b>（以前是看门狗抢走）；看门狗面板从增强面板里跳过去。</li>'
      '<li><b>两模块的「互跳」换了机制</b>：不再靠抢长按监听，改成在「+」这个 View 上互放一个 <code>Runnable</code> tag —— 稳定、不会互相顶掉。</li>'
      '<li><b>看门狗面板重排</b>：所有文字都是一行，状态区从 8 行压到 6 行，三个数字输入框并成一行，面板加宽到 320dp。</li>'
      '<li><b>锁屏不抢红包的原因查清了</b>：息屏 90 秒后整个 QQ 进程组被 HyperOS 冻结（<code>cgroup.freeze=1</code>），冻结期间一行代码都跑不了。</li>'
      '</ul></div>')

    a('<h2>1. 长按「+」的优先级翻过来了</h2>')
    a('<table><tr><th>场景</th><th>长按「+」出什么</th><th>怎么进另一个</th></tr>'
      '<tr><td>两个模块都装（你现在）</td><td><b>QQ 增强面板</b></td><td>增强面板「实验 → 线程泄露看门狗」→ 看门狗面板</td></tr>'
      '<tr><td>只装 QQ 增强</td><td>QQ 增强面板</td><td>点那行会 Toast「没检测到 QQ 看门狗模块…」</td></tr>'
      '<tr><td>只装看门狗</td><td><b>看门狗面板</b>（它自动接管长按）</td><td>底部「打开 QQ 增强面板」→ Toast 提示没装增强</td></tr></table>')
    a('<div class="card info"><h3>机制（为什么这次稳）</h3>'
      '<p>两个模块在同一个 QQ 进程里，但 LSPosed 给每个模块单独的 ClassLoader，<b>没法 Class.forName 对方的类</b>。'
      '旧办法是抢「+」的 <code>OnLongClickListener</code>（覆盖语义，谁后挂谁赢）；新办法是往「+」上挂 tag：</p>'
      '<pre>增强：plus.setTag(0x7f0f0001, openerRunnable)   // "打开增强面板"\n'
      '看门狗：plus.setTag(0x7f0f0002, openerRunnable)  // "打开看门狗面板"\n'
      '跳转 = 对方 getTag(...) 拿出来 instanceof Runnable → run()</pre>'
      '<p>tag 的 key 要资源 id 形态（<code>View.setTag(int,Object)</code> 要求 key ≥ 0x02000000）；'
      '值的类型只能用 <b>boot classpath 的 <code>java.lang.Runnable</code></b> —— 自定义接口跨 ClassLoader 会 ClassCastException。</p>'
      '<p>看门狗每次 onResume 都会检查：<b>看到 0x7f0f0001 就主动让出长按</b>（日志 <code>long-press left to QQ 增强 (tag found)</code>），'
      '没看到才自己接管 —— 所以「只装看门狗」时它照样能长按呼出。</p></div>')
    a('<div class="two">')
    a(fig('v149_longpress.png', 'A · 长按「+」→ QQ 增强 v1.49 面板（长按优先级已经归增强）'))
    a(fig('v149_backjump.png', 'B · 看门狗面板底部「打开 QQ 增强面板」→ 又跳回增强面板（走 tag，后台不叠窗）'))
    a('</div>')

    a('<h2>2. 看门狗面板重排</h2>')
    a(fig('v149_wdpanel.png', 'C · 新版排版：守着呢 + 采样时间一行、状态 5 行每行一句话、三个输入框一行、提示一行', 400))
    a('<table><tr><th>位置</th><th>以前</th><th>现在</th></tr>'
      '<tr><td>状态区</td><td>7 行 + 「范围」那行折成两行</td><td>5 行，每行一句话，超长自动省略号</td></tr>'
      '<tr><td>基线 / K / 间隔</td><td>三个大输入框，各占一整行 + 各自一段说明</td><td><b>并成一行三格</b>（标签在上、框在下），说明合成一行</td></tr>'
      '<tr><td>面板宽度</td><td>300dp</td><td><b>320dp</b>（给「仅非主进程」这类字样留位置）</td></tr>'
      '<tr><td>底部按钮</td><td>「打开 QQ 增强面板」只在检测到对手时才出现</td><td><b>一直显示</b>；没对手就 Toast 说清楚</td></tr></table>')
    a('<div class="card"><h3>「保存」按钮的语义（你问的）</h3>'
      '<p><b>是：先在输入框里改成你要的数字，再点下面的「保存」。</b>点完写 4 个文件：</p>'
      '<pre>watchdog.base   基线（0 或空 = 自动）\nwatchdog.k      系数 K\nwatchdog.iv     采样间隔秒\nwatchdog.scope  all / main / worker（点分段控件选，也靠保存落盘）</pre>'
      '<p>守护线程<b>每一轮都重读文件</b>，所以最迟「一个采样间隔」（默认 30 秒）生效 —— 不用重启 QQ。'
      '保存后事件列表里会留一条 <code>保存设置 base=… k=… iv=… scope=…</code>，可以拿它核对按对了没有。</p></div>')

    a('<h2>3. 锁屏不抢红包：定位到「进程被冻结」</h2>')
    a('<div class="card warn"><h3>实测数据（息屏 90 秒）</h3>'
      '<pre>息屏前：pid 29201(主) cpu=1187 threads=291 freeze=0\n'
      '        pid 8326(MSF) cpu=575  threads=118 freeze=0\n'
      '        pid 9049(ilink) cpu=74  threads=81  freeze=0\n'
      '息屏 90 秒后：三个进程 freeze=<b>1</b>（cpu 只涨了 151/44/11 tick，基本停在冻结那一刻）\n'
      '顺带查到：QQ 早就在 Doze 白名单里（user,com.tencent.mobileqq），RUN_ANY_IN_BACKGROUND=allow，\n'
      '           MIUI 省电策略表 user_configure.db 里 bgControl=<b>miuiAuto</b>（默认智能省电）</pre>'
      '<p><b>结论</b>：息屏后 HyperOS 把 QQ 整个 uid 的进程组冻进 cgroup freezer。被冻住的进程不执行任何代码 —— '
      '内核消息回调、抢红包的 HTTP 全是死路，所以「锁屏就抢不到」不是模块写错，是系统不给跑。'
      '（Doze 白名单和后台数据权限都已经是放行的，管不住 MIUI 自己这层冻结。）</p></div>')
    a('<div class="card info"><h3>能怎么办（按推荐顺序）</h3><ul>'
      '<li><b>① 给 QQ 设「无限制」省电策略</b>（推荐先试）：设置 → 省电与电池 → 应用智能省电 / 应用管理 → QQ → 电池 → 选「无限制」；'
      '顺手在最近任务里把 QQ 上锁。改完我可以<b>立刻复测</b> freezer 是不是变成 0（90 秒就能出结果）。</li>'
      '<li>② 如果 MIUI 照样冻：模块里塞一个 PARTIAL_WAKE_LOCK（由 QQ 的 uid 持锁）也许能让系统不把它当"可冻结"，'
      '但这条没把握，得先做实验；代价是常驻唤醒锁 + 耗电，还要在 QQ 进程里多一层可见行为。</li>'
      '<li>③ 折中：充电时别让它息屏（开发者选项「充电时屏幕不休眠」）—— 但那就不叫锁屏场景了。</li>'
      '</ul><p class="kv">要真正验收「锁屏也能抢到」，得有人在小号上发红包 —— 你那个 <code>514662509</code>（昵称 112233）还能用吗？'
      '能用我就按剧本跑一遍（锁屏 → 发红包 → 看 <code>rp-seen.txt</code>/面板事件）。</p></div>')

    a('<h2>4. 版本与文件</h2>')
    a('<table><tr><th>东西</th><th>路径</th><th>大小 / SHA-256</th></tr>'
      '<tr><td>QQ 增强 v1.49（vc149）</td><td><code>D:\\deepseek\\qq-module\\probe\\build\\qqprobe.apk</code></td>'
      '<td>144,388 B · <code>6DF45B4A46BF10332B70B7060E0AD13A852D05D37E9D6F09E018B42E53C72A80</code></td></tr>'
      '<tr><td>QQ 看门狗 v1.12（vc13）</td><td><code>D:\\deepseek\\qq-watchdog\\mod\\build\\qqwatchmod.apk</code></td>'
      '<td>50,182 B · <code>B351A625CA7EE352479AB5FC59D32FFEE0B7C3EFE657A1368BC03AAFFC1E5E86</code></td></tr></table>'
      '<p class="kv">装机回读：<code>versionCode=149/versionName=1.49</code>、<code>versionCode=13/versionName=1.12</code>；'
      '增强面板「已挂钩 157」。</p>')

    a('<h2>5. 复测配方</h2>')
    a('<div class="card"><pre>长按「+」            input touchscreen swipe 1114 220 1114 220 1300   → 应出 QQ 增强面板\n'
      '进看门狗           增强面板点「线程泄露看门狗」行 (600,1786)\n'
      '看门狗滚到底        swipe 250 700 250 200 600   ×3\n'
      '看门狗底部按钮      (788,2316)「打开 QQ 增强面板」 / (349,2316)「关闭」 / (600,2175)「清理遗留文件」\n\n'
      '# 冻结复测（90 秒）\n'
      '$pid = ($(adb shell pidof com.tencent.mobileqq) -split "\\s+")[0]\n'
      'adb shell su -c "cat /sys/fs/cgroup/apps/uid_10265/pid_$pid/cgroup.freeze"   # 1 = 被冻住</pre></div>')

    html = ('<!doctype html><html lang="zh-CN"><head><meta charset="utf-8">'
            '<meta name="viewport" content="width=device-width,initial-scale=1">'
            '<title>QQ 增强 v1.49 + 看门狗 v1.12</title><style>%s</style></head><body>%s</body></html>'
            % (CSS, '\n'.join(b)))
    open(OUT_WD, 'w', encoding='utf-8').write(html)
    open(OUT_QM, 'w', encoding='utf-8').write(html)
    build_index(os.path.dirname(OUT_WD), 'QQ 看门狗 · 文档目录')
    build_index(os.path.dirname(OUT_QM), 'QQ 增强（qq-module） · 文档目录')
    print('written %s (%d bytes)' % (OUT_WD, os.path.getsize(OUT_WD)))
    print('copy    %s (%d bytes)' % (OUT_QM, os.path.getsize(OUT_QM)))


if __name__ == '__main__':
    sys.stdout.reconfigure(encoding='utf-8')
    main()
