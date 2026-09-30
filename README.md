# QQ 负载监控（qq-load-monitor）

自用的手机 QQ 负载监控模块（Xposed / libxposed API 102），目标 **QQ 9.2.85（vc 13860）**。
**只记录、只提示，不杀进程、不 kill 线程。**

> 配套的另一个模块（QQ 增强：防撤回 / 闪照 / 语音转发 / 抢红包 …）在独立仓库 **`LQB9/qq-enhance`**（私有）。
> 两个模块可以只装一个，也可以一起装；一起装时长按 QQ 右上角「+」出的是**增强面板**，本模块的面板从增强面板
> 「实验 → 线程泄露看门狗」进去。

包名 `com.lqb9.qqwatchmod`，label「QQ 看门狗」（历史名字，功能已经从"杀进程看门狗"变成"负载监控 + 日志导出"）。
**当前 v1.15（versionCode 16）**。

## 它干什么

- 汇总 **QQ 同 UID 全部进程**的 CPU（口径：单核满载 = 100%，200% 表示约 2 个核心），默认**阈值 200%**、间隔 1 秒，超限**只提示**（Toast + 进 LSPosed 日志）。
- 每轮采样记录**全部 QQ 进程**、**最忙的 12 个线程**（名称/PID/TID/CPU/最后核心）、**核心频率**（优先 `cpuinfo_cur_freq`，否则 `scaling_cur_freq`）。
- 面板三页：**概览 / 明细 / 设置**，原生 Canvas 画图（QQ 总 CPU 趋势、阈值虚线、核心 MHz 条形、TOP 线程横条）。
- **日志**：UTF-8 滚动日志，4 份 × 512 KiB 上限，写到设备
  `/sdcard/Android/data/com.tencent.mobileqq/files/watchdog-logs/`（`watchdog.0..3.log`，重启 QQ 保留）。
- **导出**：面板上「导出日志到下载目录」→ `Download/QQWatchdog/QQWatchdog_日期_时间_随机码.txt`
  （走 `MediaStore.Downloads`，不申请额外存储权限；Android 8/9 沿用 QQ 的存储授权，未授权会明确失败）。
- 配置：`watchdog.on`（存在 = 开，总开关）；`watchdog.load`（原子写的 Properties 文本：`cpu=200` / `duration=0` / `interval=1` / `action=record`）。
  ⚠️ **即便写成 `action=restart` 也只记录** —— 当前版本没有任何终止动作（源码静态检查无 `killProcess` / `tgkill` / `sendSignal` / `Thread.stop`）。
- 不持唤醒锁；QQ 进程被系统冻结时监控也随之暂停（**锁屏期间不会有采样**，这是系统行为不是 bug）。

## 和另一个模块怎么配合（改代码前必读）

两个模块跑在同一个 QQ 进程里，但 **LSPosed 给每个模块单独的 ClassLoader → 不能 `Class.forName` 对方的类**。
所以约定用 **「+」这个 View 的 tag** 互放一个 `java.lang.Runnable`：

| key | 谁写 | 作用 |
|---|---|---|
| `0x7f0f0001` | QQ 增强 | 打开**增强面板** |
| `0x7f0f0002` | 本模块（负载监控） | 打开**负载监控面板** |

- key 必须是资源 id 形态（`View.setTag(int,Object)` 要求 key ≥ `0x02000000`）
- **值的类型只能是 boot classpath 的 `java.lang.Runnable`**（自定义接口跨 ClassLoader 会 `ClassCastException`）
- **长按「+」归 QQ 增强**：本模块每次 `onResume` 检查 `0x7f0f0001` —— 在就让出长按（日志 `long-press left to QQ 增强 (tag found)`），
  不在（没装增强）才自己 `setOnLongClickListener` 接管
- 本模块面板底部的「打开 QQ 增强面板」读 `0x7f0f0001` 直接 `run()`；没有就 Toast 提示

## 安装

1. 装 APK → LSPosed / ReVanced Xposed 勾选，**作用域勾 QQ**
2. 强行停止 QQ 再打开（**模块改动必须重启 QQ 才生效**）
3. 长按 QQ 右上角「+」→（装了增强时）增强面板 →「实验 → 线程泄露看门狗」

## 构建与自测

```powershell
$env:JAVA_HOME='<JDK21>'
$env:ANDROID_HOME='<Android SDK，含 build-tools 37.0.0>'
powershell -ExecutionPolicy Bypass -File mod\build.ps1      # 输出 mod\build\qqwatchmod.apk，成功标志 BUILD OK
powershell -ExecutionPolicy Bypass -File mod\test.ps1       # JVM 回归检查
powershell -ExecutionPolicy Bypass -File mod\log-test.ps1   # 日志/导出隔离测试
powershell -ExecutionPolicy Bypass -File mod\ui-test.ps1    # 图表面板离屏渲染检查
```

⚠️ `.ps1` 必须**纯 ASCII**（PowerShell 5.1 把没有 BOM 的脚本按 GBK 读，中文注释会把脚本拆坏）。
`mod\android-tests\` 里是**隔离的临时测试包**（不同包名，不进正式 APK）。

## 目录

```
mod\src\com\lqb9\qqwatchmod\   模块源码（WatchModule 入口 / WatchPanel 面板 / DashboardView+CpuCharts 图表 /
                               QqCpuTracker+ThreadCpuTracker+CoreFrequency 采样 / RollingLog+WatchLog 日志 / DownloadExporter 导出）
mod\build.ps1  AndroidManifest.xml  xposed\  libs\  res\  tests\
src\ assets\ res\ build.ps1    早期「独立版 App（root 版）」，已弃用，留档
docs-html\                     单文件说明 HTML（含每版说明与验证记录）
AGENTS.md                      本工程的权威说明（现状 / 逐版改动 / 真机验证记录 / 坑，中文）
```

## 不在这里的东西

- **签名私钥**（`mod\watchmod-key.jks`，别名 `qqwatchmod`）——不入库，只在本地和云盘备份里。**丢了就再也无法覆盖安装**。
- GitHub token、真机日志、聊天数据。本模块**不读取聊天内容**。

## ⚠️ 如果要转成公开仓库，先做这几件事

1. **改掉写死的签名口令**：`mod\build.ps1` 里有 `-storepass/-key-pass`（自用演示口令），`AGENTS.md` 里也有同一串。
   公开前改成读环境变量或本地未入库的口令文件（例如 `mod\keystore.pass`，并加进 `.gitignore`）。
2. 删掉 `AGENTS.md` 里的本机路径与个人设备信息（`D:\deepseek\...`、设备序列号、云盘备份路径等），
   或者干脆把 `AGENTS.md` 排除在公开仓库之外。
3. README 顶部补一段**免责声明**：自用模块、只在自己的设备上用于自己账号的负载观测；
   修改系统行为/第三方 App 有风险，且可能违反服务条款，使用者自负。
4. 复查 `docs-html\` 与 `ref\` 里的截图：里面会露出**聊天列表、昵称、群名、头像**等个人内容（截图是真机拍的），
   公开前要么裁掉、要么整个目录不入库。
5. 检查 git 历史（`git log -p`）里有没有早期版本误提交过密钥/口令。

## 版本线（简）

- v1.12 及以前：**看门狗** —— 动态上限 = 基线 + K × 本进程 CPU%，连续 3 次超限就 `killProcess` 自己。⚠️ 这个行为**已经去掉**。
- v1.13：改成**只记录并提示**，新采样口径（QQ 同 UID 全部进程汇总、单核=100%）。
- v1.14：面板重写成**概览 / 明细 / 设置**三页 + 原生 Canvas 图表。
- v1.15：**日志记录 + 导出到下载目录**（滚动日志、专用后台线程、MediaStore 导出）。
