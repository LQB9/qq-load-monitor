# -*- coding: utf-8 -*-
"""生成给用户看的单文件 HTML：QQ 看门狗 v1.9 —— 两模块拆分与互通。

用法：uv run python tools\report_split.py
产物：D:\deepseek\qq-watchdog\docs-html\看门狗-v1.9-两模块拆分与互通.html
      D:\deepseek\qq-watchdog\docs-html\index.html
      D:\deepseek\qq-module\docs-html\qq模块-v148-看门狗拆分.html（同一份内容的工作区副本索引）
"""
import base64
import io
import os
import sys

WD = r'D:\deepseek\qq-watchdog'
QM = r'D:\deepseek\qq-module'
LOG = os.path.join(WD, '_buildlog')
IMG = os.path.join(WD, 'ref')          # 截图证据放这里（_buildlog 只留脚本）

OUT_WD = os.path.join(WD, 'docs-html', '看门狗-v1.9-两模块拆分与互通.html')
OUT_QM = os.path.join(QM, 'docs-html', 'qq模块-v148-看门狗拆分.html')

CSS = """
*{box-sizing:border-box}
body{max-width:980px;margin:0 auto;padding:28px 22px 90px;background:#f6f7f9;
font-family:"Microsoft YaHei","Segoe UI",system-ui,sans-serif;font-size:15px;line-height:1.85;color:#1f2328}
h1{font-size:26px;margin:0 0 6px;color:#c2185b}
h2{font-size:19px;margin:34px 0 10px;padding-left:10px;border-left:5px solid #ff4d8d}
h3{font-size:16px;margin:20px 0 6px;color:#374151}
.sub{color:#6b7280;font-size:13.5px;margin-bottom:18px}
.card{background:#fff;border-radius:14px;padding:16px 20px;margin:12px 0;
box-shadow:0 1px 3px rgba(16,24,40,.07),0 1px 2px rgba(16,24,40,.04)}
.ok{border-left:5px solid #16a34a}
.warn{border-left:5px solid #d97706}
.info{border-left:5px solid #2563eb}
table{border-collapse:collapse;width:100%;font-size:14px;margin:8px 0}
th,td{border:1px solid #e5e7eb;padding:7px 10px;text-align:left;vertical-align:top}
th{background:#f9fafb;font-weight:600}
code{background:#f3f4f6;border-radius:5px;padding:1px 6px;font-family:Consolas,"Courier New",monospace;font-size:13px}
pre{background:#0f172a;color:#e2e8f0;border-radius:10px;padding:12px 14px;overflow-x:auto;
font-family:Consolas,"Courier New",monospace;font-size:12.5px;line-height:1.6}
figure{margin:14px 0;text-align:center}
figure img{max-width:min(420px,100%);border-radius:12px;border:1px solid #e5e7eb;background:#fff}
figcaption{color:#6b7280;font-size:12.5px;margin-top:6px}
.two{display:flex;gap:14px;flex-wrap:wrap;justify-content:center}
.two figure{flex:0 1 320px}
ul{margin:6px 0 6px 4px;padding-left:20px}
li{margin:3px 0}
.kv{color:#6b7280}
"""


def b64(path, max_w=None):
    try:
        from PIL import Image
    except Exception:
        Image = None
    raw = open(path, 'rb').read()
    if Image is not None and max_w:
        im = Image.open(io.BytesIO(raw))
        if im.width > max_w:
            im = im.convert('RGB')
            im = im.resize((max_w, int(im.height * max_w / im.width)))
            buf = io.BytesIO()
            im.save(buf, 'JPEG', quality=82)
            raw = buf.getvalue()
            return 'data:image/jpeg;base64,' + base64.b64encode(raw).decode('ascii')
    return 'data:image/png;base64,' + base64.b64encode(raw).decode('ascii')


def fig(name, cap, max_w=430):
    p = os.path.join(IMG, name)
    if not os.path.exists(p):
        p = os.path.join(LOG, name)
    if not os.path.exists(p):
        return '<figure><figcaption>（缺图 %s）</figcaption></figure>' % name
    return ('<figure><img src="%s" alt="%s"><figcaption>%s</figcaption></figure>'
            % (b64(p, max_w), cap, cap))


def build_index(dst_dir, title, only=None):
    items = []
    for name in sorted(os.listdir(dst_dir)):
        if not name.endswith('.html') or name == 'index.html':
            continue
        if only and name not in only:
            continue
        p = os.path.join(dst_dir, name)
        import datetime
        when = datetime.datetime.fromtimestamp(os.path.getmtime(p)).strftime('%Y-%m-%d %H:%M')
        items.append('<li><a href="%s">%s</a> <span class="kv">%s · %.1f KB</span></li>'
                     % (name, name[:-5], when, os.path.getsize(p) / 1024.0))
    page = ('<!doctype html><meta charset="utf-8"><title>%s</title><style>%s</style>'
            '<h1>%s</h1>\n<ul>%s</ul>\n' % (title, CSS, title, '\n'.join(items)))
    open(os.path.join(dst_dir, 'index.html'), 'w', encoding='utf-8').write(page)
    return len(items)


def main():
    body = []
    a = body.append

    a('<h1>QQ 看门狗 v1.9 · 和 QQ 增强拆成两个模块</h1>')
    a('<div class="sub">2026-09-30 真机实测 · REDMI K90 Pro Max（QQ 9.2.85 / vc13860）</div>')

    a('<div class="card ok"><h3>一句话结论</h3>'
      '<p><b>拆完了，两个模块都能用，互相还能跳。</b>'
      'QQ 增强 v1.48 里已经<b>没有</b>看门狗；看门狗 = 独立模块「QQ 看门狗」v1.9。'
      '长按 QQ 主界面右上角「+」出的是看门狗面板，两个面板底部/列表里各有一个入口互相打开，'
      '实测来回都通。老的 killleak.* 开关文件现在没人读了，面板底部有提示 + 一键清理按钮。</p></div>')

    a('<h2>1. 现在的样子</h2>')
    a('<table><tr><th>模块</th><th>包名 / label</th><th>版本</th><th>干什么</th><th>面板怎么开</th></tr>'
      '<tr><td>QQ 增强</td><td><code>com.lqb9.qqprobe</code> / QQ 增强</td><td><b>v1.48</b>（vc148）</td>'
      '<td>防撤回 / 闪照留存 / 语音转发 / 抢红包 / 屏蔽更新 / 系统 WebView 内核</td>'
      '<td>长按「+」→ <b>看门狗面板</b>（v1.48 起让给看门狗了）→ 底部「打开 QQ 增强面板」</td></tr>'
      '<tr><td>QQ 看门狗</td><td><code>com.lqb9.qqwatchmod</code> / QQ 看门狗</td><td><b>v1.9</b>（vc10）</td>'
      '<td>线程数异常膨胀时杀掉 QQ 进程让它自己重启</td><td>长按QQ主界面右上角「+」</td></tr></table>')

    a('<div class="card info"><h3>为什么要拆</h3>'
      '<ul><li>看门狗和抢红包/防撤回没有关系，出问题时要能单独关掉（LSPosed 里取消勾选就行），不用动整个增强模块。</li>'
      '<li>两个模块各自一个 APK，改一个不影响另一个。</li>'
      '<li>代价：两个模块要在 LSPosed 里分别勾选、分别给作用域 QQ；<b>两个都得勾</b>，只勾一个就少一半功能。</li></ul></div>')

    a('<h2>2. 双向跳转（实测）</h2>')
    a('<div class="two">')
    a(fig('crop_g1.png', 'A · QQ 增强 v1.48 面板：「实验」组里新增「线程泄露看门狗 → 打开看门狗面板」，原来那 4 行看门狗开关已经撤掉', 430))
    a(fig('crop_h1.png', 'B · 点上面那行 → 回到看门狗面板（pid 变化说明是新拉起的 QQ 进程，面板实时数据正常）', 430))
    a('</div>')
    a('<div class="card"><h3>实测路径（每一步都截了图）</h3>'
      '<ol><li>长按「+」→ 看门狗面板：<code>守着呢 / 总开关：开（watchdog.on 存在）</code></li>'
      '<li>面板底部「打开 QQ 增强面板」→ QQ 增强 v1.48 面板（头部徽章写着 v1.48）</li>'
      '<li>面板里「线程泄露看门狗 · 打开看门狗面板」→ 回到看门狗面板</li></ol>'
      '<p class="kv">实现：看门狗在每次 QQ 的 onResume 后连查 5 轮长按监听（150/350/700/1500/2500 ms），'
      '谁后挂谁赢的问题靠「发现不是自己就接管，并把对方的存起来」解决；两边按钮就是替对方调一次长按回调。</p></div>')

    a('<h2>3. 遗留文件：一键清理</h2>')
    a('<div class="card warn"><h3>为什么要清</h3>'
      '<p>QQ 增强 v1.43~v1.47 时代的看门狗用的是同一批文件开关，名字是 <code>killleak.*</code>。'
      'v1.48 把那段代码整块删掉之后，<b>这些文件没有任何代码再读</b>——留着只是垃圾，'
      '但也不会影响任何功能。看门狗面板检测到就提示，并给一个按钮删掉它。</p>'
      '<table><tr><th>文件</th><th>原来是</th><th>现在</th></tr>'
      '<tr><td><code>killleak.on</code></td><td>总开关</td><td rowspan="5">没人读（可以删）</td></tr>'
      '<tr><td><code>killleak.scope</code></td><td>all / main / worker</td></tr>'
      '<tr><td><code>killleak.base</code></td><td>基线线程数</td></tr>'
      '<tr><td><code>killleak.k</code></td><td>系数 K</td></tr>'
      '<tr><td><code>killleak.limit</code></td><td>老阈值（v1.39 起已弃用）</td></tr></table>'
      '<p class="kv">清理按钮只删上面这 5 个名字（白名单，不用通配），绝不碰别的文件：'
      '<code>rp-seen.txt</code> / <code>rp-peers.txt</code>（红包功能状态）、'
      '<code>redpacket.*</code> / <code>noupdate.on</code> / <code>syswebview.on</code>（QQ 增强的开关）都不动。</p></div>')
    a('<div class="two">')
    a(fig('crop_text2.png', 'C · 面板底部提示：还留着 killleak.on（剩下 5 个）+「清理遗留文件」按钮', 430))
    a(fig('crop_purge2.png', 'D · 点一下之后：删除 5 个 → 绿字回执，按钮自己消失，事件列表留痕', 430))
    a('</div>')
    a('<div class="card"><h3>实测读数</h3>'
      '<pre>删除前：-rw-rw---- u0_a265 killleak.base(3B) killleak.k(1B) killleak.limit(3B) killleak.on(0B) killleak.scope(7B)\n'
      '点「清理遗留文件」→ 事件：14:18:07 清理 QQ 增强遗留文件 5 个\n'
      '删除后：只剩 threads-last.txt（v1.42 时代的线程快照，也是老文件，故意不自动删）\n'
      '⚠️ 测试完我把这 5 个文件原样放回去了（属主 u0_a265、字节数一致）—— 你自己点一下按钮就干净了</pre></div>')

    a('<h2>4. 怎么复测（照抄）</h2>')
    a('<div class="card"><pre>$adb = \'D:\\deepseek\\_work\\android-sdk\\platform-tools\\adb.exe\'\n\n'
      '# ① 长按「+」出面板（QQ 主界面）\n'
      '&amp; $adb shell input touchscreen swipe 1114 220 1114 220 1300\n\n'
      '# ② 面板滚到底（一次 600ms，约 4 次；用慢滑，快滑滚不动）\n'
      '&amp; $adb shell input touchscreen swipe 250 700 250 180 600\n\n'
      '# ③ 两个按钮的实测坐标（1200x2608 / 480dpi，滚到底后固定）\n'
      '#    清理遗留文件          (600, 2175)\n'
      '#    关闭                 (344, 2316)\n'
      '#    打开 QQ 增强面板      (776, 2316)\n'
      '#    QQ 增强面板里的跳转行 (600, 1786)\n\n'
      '# ④ 查文件\n'
      '&amp; $adb shell ls -l /sdcard/Android/data/com.tencent.mobileqq/files/</pre>'
      '<p class="kv">坐标怎么来的：这个面板每秒刷新一次文本，<code>uiautomator dump</code> 永远等不到 idle（报 '
      '<code>ERROR: could not get idle state.</code>），所以改用<b>截图 + 像素测量</b>：'
      '<code>_buildlog\\bbox.py</code> 扫按钮底色 <code>#F1F3F7</code> 得到矩形，'
      '<code>col.py / rows.py</code> 看竖直/水平色带，<code>crop.py</code> 裁图人工核对。'
      'QQ 增强的面板没有每秒刷新，<code>dump</code> 正常，跳转行的 bounds 就是从节点树拿的'
      '（<code>[715,1764][1008,1809]</code>）。</p></div>')

    a('<h2>5. 版本与文件</h2>')
    a('<table><tr><th>东西</th><th>路径</th><th>大小 / SHA-256</th></tr>'
      '<tr><td>QQ 看门狗 v1.9（vc10）</td><td><code>D:\\deepseek\\qq-watchdog\\mod\\build\\qqwatchmod.apk</code></td>'
      '<td>50,182 B<br><code>E3CE1C4311919B107A85D8FB9367AA8DA0307FA99E0A979B76D55FC045200612</code></td></tr>'
      '<tr><td>QQ 增强 v1.48（vc148）</td><td><code>D:\\deepseek\\qq-module\\dist\\qqprobe-1.48-正式版.apk</code><br>'
      '（= <code>probe\\build\\qqprobe.apk</code>）</td>'
      '<td>144,388 B<br><code>E26D3A0F32DA6A2FDB1AD036707381CEC7518FCFB03F0BEB66262D0E8CB37A12</code></td></tr></table>'
      '<p class="kv">装机核对：<code>dumpsys package com.lqb9.qqwatchmod | findstr version</code> → '
      '<code>versionCode=10 / versionName=1.9</code>；'
      '<code>aapt2 dump badging</code> 看增强 → <code>versionCode=\'148\' versionName=\'1.48\'</code>；'
      'QQ 增强面板「关于 → 已挂钩」= <code>157</code>（拆掉看门狗后从 160 掉到 157，符合预期）。</p>')

    a('<h2>6. 说明页也改了</h2>')
    a(fig('crop_app.png', 'E · 点「QQ 看门狗」图标进去的说明页：新增「和 QQ 增强怎么配合」一节', 460))

    a('<h2>7. 注意 / 已知限制</h2>')
    a('<div class="card warn"><ul>'
      '<li><b>两个模块都要在 LSPosed 里勾选并给作用域 QQ</b>，否则只有一半功能；改完模块要重启 QQ（强停再开）。</li>'
      '<li>重装 APK 后 LSPosed 会提示「Xposed 模块已更新」，不用重新勾作用域，但要重启 QQ。</li>'
      '<li><b>面板开着的时候长按「+」（手指落在面板之外）会把另一个面板叠在上面</b>：'
      '因为两个模块都往同一个 Activity 上挂 Dialog，互相看不见对方。关掉上面那个就会露出下面那个 —— 不是坏了，'
      '想避免就先关面板再长按（面板里那两个互跳按钮不会叠）。</li>'
      '<li>这个面板滚到底之后，<b>不要用「滑」代替「点」</b>：实测有一次从按钮上起手的滑动把按钮按下去了（'
      '好在那次是清理按钮，后果可接受）。要按按钮就老老实实 tap 坐标。</li>'
      '<li>面板每秒刷新 → 截图取证很好用，但 uiautomator 拿不到它的节点树；按坐标点没问题（按钮高 123px）。</li>'
      '<li>设备的 killleak.* 我按原样还原了；<code>threads-last.txt</code>（1713 B，9/29 的线程快照）也留着没删，'
      '想一起清就说一声。</li>'
      '</ul></div>')

    html = ('<!doctype html><html lang="zh-CN"><head><meta charset="utf-8">'
            '<meta name="viewport" content="width=device-width,initial-scale=1">'
            '<title>QQ 看门狗 v1.9 · 两模块拆分与互通</title><style>%s</style></head><body>%s</body></html>'
            % (CSS, '\n'.join(body)))

    os.makedirs(os.path.dirname(OUT_WD), exist_ok=True)
    open(OUT_WD, 'w', encoding='utf-8').write(html)
    open(OUT_QM, 'w', encoding='utf-8').write(html)
    n1 = build_index(os.path.dirname(OUT_WD), 'QQ 看门狗 · 文档目录')
    n2 = build_index(os.path.dirname(OUT_QM), 'QQ 增强（qq-module） · 文档目录')
    print('written: %s (%d bytes)' % (OUT_WD, os.path.getsize(OUT_WD)))
    print('copy   : %s (%d bytes)' % (OUT_QM, os.path.getsize(OUT_QM)))
    print('index  : qq-watchdog %d pages / qq-module %d pages' % (n1, n2))


if __name__ == '__main__':
    sys.stdout.reconfigure(encoding='utf-8')
    main()
