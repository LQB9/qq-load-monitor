# -*- coding: utf-8 -*-
"""生成交付报告 v2：三件事 + 模块版真机验证结果"""
import io, os, base64

OUT = r"D:\deepseek\qq-watchdog\docs-html\看门狗-三件事-交付报告.html"
os.makedirs(os.path.dirname(OUT), exist_ok=True)


def img(path, cap):
    try:
        b = open(path, "rb").read()
    except Exception as e:
        return "<p class=small>(截图缺失 %s)</p>" % e
    if len(b) > 900 * 1024:
        return "<p class=small>(截图过大跳过 %s)</p>" % os.path.basename(path)
    return ('<figure><img src="data:image/png;base64,%s"><figcaption>%s</figcaption></figure>'
            % (base64.b64encode(b).decode("ascii"), cap))


B = r"D:\deepseek\qq-module\_buildlog"

HTML = """<!DOCTYPE html>
<html lang="zh-CN"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>QQ 看门狗 / 模块 App 修复 · 交付报告</title>
<style>
:root{--pink:#FF3D7F;--pink2:#FFA3CB;--ink:#1c1c22;--sub:#6b7280;--bg:#f5f6fa;--line:#e8eaf0}
*{box-sizing:border-box}
body{margin:0;background:var(--bg);color:var(--ink);font:15px/1.75 -apple-system,"Segoe UI","Microsoft YaHei",sans-serif}
.wrap{max-width:960px;margin:0 auto;padding:0 18px 70px}
header{background:linear-gradient(100deg,var(--pink),var(--pink2));color:#fff;padding:30px 34px 26px;border-radius:0 0 22px 22px;margin-bottom:22px}
header h1{margin:0 0 6px;font-size:26px}
header .sub{opacity:.94;font-size:13.5px}
header .meta{margin-top:12px;font-size:12.5px;opacity:.92;font-family:Consolas,monospace}
h2{font-size:19px;margin:34px 0 12px;padding-left:11px;border-left:5px solid var(--pink)}
h3{font-size:15.5px;margin:20px 0 8px;color:#374151}
code{background:#eef1f6;border-radius:5px;padding:1px 6px;font-family:Consolas,monospace;font-size:12.5px}
pre{background:#0f172a;color:#e2e8f0;border-radius:12px;padding:14px 16px;overflow:auto;font-size:12.5px;line-height:1.6}
.card{background:#fff;border:1px solid var(--line);border-radius:14px;padding:16px 20px;margin:12px 0;box-shadow:0 1px 3px rgba(16,24,40,.04)}
table{width:100%;border-collapse:collapse;background:#fff;border:1px solid var(--line);border-radius:12px;overflow:hidden;margin:12px 0;font-size:13.5px}
th{background:#fafbfd;text-align:left;padding:10px 12px;border-bottom:1px solid var(--line);white-space:nowrap}
td{padding:9px 12px;border-bottom:1px solid #f1f3f7;vertical-align:top}
tr:last-child td{border-bottom:none}
.ok{color:#16a34a;font-weight:600}.warn{color:#d97706;font-weight:600}.bad{color:#dc2626;font-weight:600}
.key{background:linear-gradient(100deg,#fff5f9,#fff);border:1px solid #ffd9e6;border-radius:14px;padding:16px 20px;margin:16px 0}
.key b{color:var(--pink)}
figure{margin:14px 0;background:#fff;border:1px solid var(--line);border-radius:14px;padding:10px}
figure img{width:100%;border-radius:10px;display:block}
figcaption{font-size:12.5px;color:var(--sub);padding:8px 4px 2px}
.small{font-size:12.5px;color:var(--sub)}
ul{margin:8px 0 8px 20px;padding:0}li{margin:5px 0}
.tag{display:inline-block;font-size:11.5px;padding:1px 9px;border-radius:20px;background:#f1f3f7;margin-right:6px}
.tag.ok{background:#dcfce7;color:#15803d}
footer{margin-top:40px;padding-top:16px;border-top:1px dashed var(--line);font-size:12.5px;color:var(--sub)}
</style></head><body>
<header>
  <h1>看门狗 · 交付报告</h1>
  <div class="sub">模块 App 空页修复 → 独立版看门狗 APK → LSPosed 模块版看门狗（长按「+」呼出）</div>
  <div class="meta">2026-09-30 · REDMI K90 Pro Max (HyperOS OS3.0 / Android 16) · KernelSU 3.3.0 + LSPosed</div>
</header>
<div class="wrap">

<div class="key">
  <b>结论</b>
  <ul>
    <li><span class="tag ok">已完成</span>QQ 增强模块自带的 App 点进去空白 → 重做成说明页，装机验证过（v1.47）。</li>
    <li><span class="tag ok">已完成</span>独立版「QQ 看门狗」APK → 装机、root 授权后引擎跑通（后来你说老的删了，它已经卸了）。</li>
    <li><span class="tag ok">已完成</span><b>LSPosed 模块版「QQ 看门狗」</b>（你要的那版）→ 已装机、已启用、<b>长按「+」呼出面板、真实触发杀进程全部验过</b>。</li>
  </ul>
</div>

<h2>一、模块 App「点进去啥也没内容」—— 病因和修法</h2>
<div class="card">
<h3>病因（两条叠在一起）</h3>
<ul>
  <li><code>MainActivity.java</code> 还是 <b>v0.3 时代的残留桩</b>，23 行，正文写的是
      「qqprobe 0.3 / Reports are written to: …/hook-install-&lt;pid&gt;.txt」
      —— 那些文件从 v1.43 静默版起<b>就已经不写了</b>，等于在展示一份过期说明书。</li>
  <li>Activity 没指定 NoActionBar 主题，系统标题栏把正文<b>前 3 行盖住了</b>，所以你看到的就是一片空白 + 几句早就不成立的英文。</li>
</ul>
<h3>现在这一页</h3>
<ul>
  <li>粉色头：QQ 增强 / 模块 v1.47 / 按 QQ 9.2.85 · 9.2.25 适配</li>
  <li><b>怎么用（只有三步）</b>：LSPosed 启用 → 打开 QQ 长按右上角「+」 → 面板里开关</li>
  <li><b>面板呼不出来？按顺序查</b>：模块启用没 / 作用域勾了 QQ 没 / 勾完必须重启 QQ / QQ 升级过就会失效</li>
  <li><b>模块有什么功能</b>：7 条，每条一句话</li>
  <li><b>本机状态</b>（自检）：本机 QQ 版本匹不匹配、Xposed 管理器装没装</li>
  <li>按钮：打开 QQ / 重新检查</li>
</ul>
</div>
__IMG_MODULE__
<p class="small">实测：<b>本机 QQ 9.2.85（vc 13860）· 与模块匹配</b>；管理器识别为 <b>ReVanced Xposed</b>。
第一版按官方包名 <code>org.lsposed.manager</code> 查，误报「没检测到」—— 这台机器的管理器包名是
<code>io.github.chsbuffer.revancedxposed</code>，已改成按常见包名列表探测。</p>

<h2>二、独立版「QQ 看门狗」APK</h2>
<div class="card">
<p>引擎是一段 shell 脚本 <code>qqwatch.sh</code>（打进 assets，首次运行释放到自己 files 目录），由前台服务用 <code>su</code> 拉起来，
脚本每轮往 stdout 打 <code>BEGIN/CFG/P/END</code>，App 解析后显示。</p>
<ul>
  <li>每进程独立基线；<b>动态上限 = 基线 + K × 本进程 CPU%</b>；连续 3 次超限才 <code>kill -9</code>；启动 60 秒内只观察。</li>
  <li>CPU% 从 <code>/proc/&lt;pid&gt;/stat</code> 的 utime+stime 差分算，<b>不看 /proc/loadavg</b>（实测 QQ 空闲时自己 1.8~3.5%，同时系统 loadavg 9.69）。</li>
</ul>
<h3>踩到的三个坑</h3>
<ul>
  <li><b>App 执行 su 报 EACCES、<code>/system/bin/su</code> 连 exists 都是 false</b> ——
      不是权限、不是 SELinux：<code>ksud feature list</code> 里 <code>su_compat</code> 是开着的，但它的说明写着
      「allows <b>authorized</b> apps」——这个 App 没在 KernelSU 超级用户名单里，内核 hook 直接把 su 藏了。
      在 KernelSU 管理器里给它打开开关之后立刻就好。</li>
  <li><b>Android 的 sh 是 32 位整数</b>：<code>$((1790740393*1000))</code> 会变负数 → 脚本里时间只存「秒 + 百分秒」、先减后乘。</li>
  <li><b><code>Process.destroy()</code> 只杀掉 su</b>，脚本 <code>sh</code> 会被 init 收养继续跑 →
      改成按脚本自报的 pid 再补一刀（已验证：停止后进程消失）。</li>
</ul>
</div>
__IMG_KSU__

<h2>三、LSPosed 模块版「QQ 看门狗」—— 已装机验证 ✅</h2>
<div class="card">
<h3>为什么这版更干净</h3>
<p>它<b>跑在 QQ 自己的进程里</b>，看的是自己的 <code>/proc/self</code>、杀的是自己
（<code>Process.killProcess(myPid())</code>）——<b>不需要 root、不需要 su、不需要跨进程</b>。
上面那一整套授权折腾，在模块版里根本不存在。</p>
<h3>实测记录（全部真机）</h3>
<table>
<tr><th>验证项</th><th>结果</th><th>证据</th></tr>
<tr><td>模块加载</td><td class="ok">com.tencent.mobileqq / :MSF / ilink.ServiceProcess <b>三个进程各加载一份</b></td>
    <td>LSPosed 日志 <code>[QQWATCH] loaded in …</code></td></tr>
<tr><td>长按「+」呼出</td><td class="ok">面板弹出，抬头写 <code>com.tencent.mobileqq · pid 21947</code></td><td>下面截图</td></tr>
<tr><td>实时数据</td><td class="ok">线程数 341 / 动态上限 352 / 算式 337 + 3 × 5.0% / 超限连击 0/3 / 已守时长</td><td>下面截图</td></tr>
<tr><td>面板开关写文件</td><td class="ok">点总开关 → <code>watchdog.on</code> 出现（内容 <code>1</code>）</td><td><code>cat …/watchdog.on</code></td></tr>
<tr><td><b>真实触发</b></td><td class="ok"><b>pid 21827 → 27756</b>：把基线压到 160 后连续 3 次超限，QQ 自己被杀又自己拉起来</td>
    <td>触发前后 <code>pidof com.tencent.mobileqq</code></td></tr>
<tr><td>恢复默认</td><td class="ok">删除 <code>watchdog.base/.iv</code> 后回到自动基线；<code>21947</code> 没被动过 → 自动基线不误杀</td><td>面板显示「守着呢 · 337 + 3 × 5.0%」</td></tr>
</table>
<h3>验证时抓出来的两个 bug（都已修）</h3>
<ul>
  <li><b>长按被 QQ 增强顶掉</b>：我先挂上（日志 <code>armed prevListener=false</code>），QQ 增强在同一次 onResume 稍后又挂了一次，
      <code>setOnLongClickListener</code> 是覆盖语义 → 长按出来的是它的面板。
      修法：onResume 后连查 5 轮（150/350/700/1500/2500ms），谁最后挂谁赢，并且把对方的监听存下来还回去。
      修完日志：<code>armed(try=2) prevListener=true</code> ✅ —— 面板底部因此会多一个「打开 QQ 增强面板」按钮，两个模块同时开也不互相吃。</li>
  <li><b>总开关那行不跟着刷新</b>：面板顶部大字已经是「已关闭」，开关行还写着「开」（原来只在点击/打开时重绘）→ 已把开关状态纳入每秒轮询。</li>
</ul>
<h3>你反馈"没底色"之后改的（v1.5 / v1.6）</h3>
<ul>
  <li><b>v1.5 面板加整块底色</b>：原来只有每张卡片是白的，卡片之间和分组标题那里是透明的，
      底下的 QQ 界面直接透出来 —— 确实像没做完。现在整块面板挂一层圆角白底，卡片改成极浅灰 <code>#F7F8FB</code>，
      输入框 / 分段控件 / 开关行未选中态统一成白底 + 细边。</li>
  <li><b>v1.6 头部右上角加 ✕</b>：内容比一屏高，底下那个「关闭」要滚很久才看得到。已截图 A/B 验过（有面板 → 点 ✕ → 面板消失）。</li>
</ul>
<h3>顺便确认的两件事</h3>
<ul>
  <li><b>「打开 QQ 增强面板」按钮真的出来了</b> —— 面板底部两个按钮 + 黄字提示都在截图里。</li>
  <li><b>范围现在是「仅非主进程」</b>（<code>watchdog.scope=worker</code>，属主 u0_a265＝你在面板里点的）：
      主进程不会被杀，只重启 :MSF / ilink 这些子进程。如果不是你点的，说一声我改回"全部进程"。</li>
</ul>
</div>
__IMG_PANEL1__
__IMG_PANEL2__
<p class="small">两张图分别是：触发实验进行中（<b>守着呢</b>、算式 <code>160 + 3 × 14.4% = 203</code>、pid 已是重启后的新进程）；
以及最终形态（<b>守着呢</b>、自动基线 <code>337 + 3 × 5.0% = 352</code>、超限连击 0/3、QQ 没被杀过）。</p>

<h2>四、怎么用 / 开关在哪</h2>
<table>
<tr><th>东西</th><th>值</th></tr>
<tr><td>呼出</td><td>长按 QQ 主界面右上角「+」</td></tr>
<tr><td>面板里能改</td><td>总开关 / 基线线程数（0=自动）/ 系数 K / 采样间隔 / 监控范围，改完点「保存」（<b>不用重启 QQ</b>，守护线程每轮重读文件）</td></tr>
<tr><td>开关文件（一个文件一个开关）</td><td><code>/sdcard/Android/data/com.tencent.mobileqq/files/</code><br>
    <code>watchdog.on</code> 存在=开 &nbsp;·&nbsp; <code>watchdog.base</code> 0/不存在=自动 &nbsp;·&nbsp;
    <code>watchdog.k</code> 默认 3 &nbsp;·&nbsp; <code>watchdog.iv</code> 默认 30 秒 &nbsp;·&nbsp; <code>watchdog.scope</code> = all/main/worker</td></tr>
<tr><td>当前设备状态</td><td>看门狗<b>已开</b>（watchdog.on 存在），自动基线，K=3，间隔 30 秒，范围全部进程</td></tr>
</table>

<h2>五、文件清单</h2>
<table>
<tr><th>东西</th><th>路径</th></tr>
<tr><td>模块版看门狗 APK（已装机 = v1.4）</td><td><code>D:\\deepseek\\qq-watchdog\\mod\\build\\qqwatchmod.apk</code></td></tr>
<tr><td>模块版源码</td><td><code>D:\\deepseek\\qq-watchdog\\mod\\src\\com\\lqb9\\qqwatchmod\\</code></td></tr>
<tr><td>独立版看门狗（引擎脚本 + APK）</td><td><code>D:\\deepseek\\qq-watchdog\\assets\\qqwatch.sh</code> / <code>…\\build\\qqwatchdog.apk</code></td></tr>
<tr><td>QQ 增强 App 新页面源码 + 装机版</td><td><code>…\\qq-module\\probe\\src\\…\\MainActivity.java</code> / <code>…\\qq-module\\dist\\qqprobe-1.47-正式版.apk</code></td></tr>
</table>

<footer>
  所有数字都来自真机实测（adb 原始输出 / 面板 dump / 截图 / LSPosed 日志），不是推断。<br>
  模块版当前装机 v1.4；sha256 见同目录 AGENTS 记录。
</footer>
</div></body></html>
"""

HTML = (HTML
        .replace("__IMG_MODULE__", img(os.path.join(B, "m4.png"), "QQ 增强 App 新版：粉色头 + 三步说明（v1.47 装机后截图）"))
        .replace("__IMG_KSU__", img(os.path.join(B, "k6.png"), "KernelSU 管理器里给「QQ 看门狗」打开超级用户开关 —— 打开之后 su 才对 App 可见"))
        .replace("__IMG_PANEL1__", img(os.path.join(B, "wdC.png"), "模块版面板（v1.6）：整块圆角白底 + 粉色头 + 右上角 ✕ · 守着呢 · 实时数字"))
        .replace("__IMG_PANEL2__", img(os.path.join(B, "wdE.png"), "面板下半部分：保存按钮 · 最近事件 · 「QQ 增强的看门狗也开着」黄字提示 · 底部「关闭 / 打开 QQ 增强面板」")))

io.open(OUT, "w", encoding="utf-8", newline="").write(HTML)
print("WROTE %s (%d bytes)" % (OUT, len(HTML.encode("utf-8"))))
