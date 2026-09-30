# AGENTS.md — qq-watchdog（QQ负载监控 · 自用）

> 给接手的人/AI：**本文件是唯一入口，读完就能继续**。
> 沟通用**中文、短句、先结论**；用户**打不开 .md**，给他看的文档出单文件 HTML 放工作区（**别放桌面**）。

## 0. 当前代码：v1.17 核心选择与图标修复（2026-10-01）

- 用户截图：QQ 的主题将系统 CheckBox 选中图案放大，核心选择绿勾重叠；要求模块图标参考 QQ 增强、APK 改名为「QQ负载监控」。本次仅改 UI、图标、名称，v1.16 的采集与任务处理逻辑沿用，详情在下节。
- `CoreSelectorView.java`：两列四行固定几何的原生 Canvas 核心按钮，48dp 最小点击区域、18dp 勾选标记，粉色选中态；不使用 QQ 的主题 CheckBox 图片。点选可逆、读初始 mask、保存仍拒绝空选择；辅助功能提供 checked/checkable 状态。`WatchPanel.java` 接入并移除过时的「0 秒…即记录并提示」文字。
- 名称统一「QQ负载监控」：Manifest/桌面/模块管理器、说明页标题、面板标题、提示和新日志标题。包名、签名、增强 tag 互通协议保持。`tools/mk_module_icon.py` 生成 5 个密度的粉色渐变/白色负载曲线图标，参考实际 QQ 增强图标；旧 `tools/mkicon.py` 仅针对已停用的独立版。图标生成依赖 Pillow，使用 Codex bundled Python 即可。
- 当前 v1.17 / vc18：2026-10-01 01:16:00 root pm install Success，回读正确；QQ PID 30002，精确采集器 `CORE_READY pid=30011`，真实日志有新版 SESSION。更新前后阅读器保持前台。`watchdog.load` 原字节内容保留：cpu=200、cores=255、duration=0、interval=1、action=stop_task。
- 验证：生产 BUILD OK、原签名验证通过；原生隔离渲染 3 组（全选/截图混选/260dp 窄屏与1.6倍字体），勾选状态切换、辅助功能检查通过，目视无重叠或截字。临时渲染包已卸载，本轮未重复 CPU 高负载或业务取消测试；QQ 内完整面板未再次点击截图。
- APK 128,006 B，SHA256 `5EC34DECB5B291BBB0D55EBB7662A3E47806DFD5613AE54AAB52D9F593F28358`。交付 `D:/ChatGPT/2026-09-30/xu/outputs/QQ负载监控.apk`；生产构建额外生成 `mod/build/QQ负载监控.apk`，`qqwatchmod.apk` 保留供既有工具使用。build.ps1/ui-test.ps1 保持 ASCII。
- 本轮证据：工作区 `work/v1.17-ui-check/`（3张PNG与渲染结果），`work/v1.17-final-evidence/`（安装记录、实际日志、精确核心快照）。修改前备份 `work/qq-watchdog-before-v1.17/`。故障机真实线程处理仍待验证。

## 0.0a. v1.16 核心负载与任务处理测试版（历史，2026-10-01）

用户最新授权：故障机不在手边、不能复现，先按已约定口径开发再验证。已替代下方 v1.15“先不做检测与线程处理”的旧要求；无需再追问复现条件。沟通中文短句，交付 HTML/APK。

- 约定：CPU 0—7 任意组合；仅 QQ 在所选核心上的合计 CPU 时间，单核 100%；阈值默认 200% 可设，严格大于才触发；前后台同阈值，首轮有效样本尝试处理；目标工作线程/任务，保留 QQ 进程；处理状态表，不导出。没有故障机真实停止验证，不能宣称任意线程可强杀或泄露已解决。
- 当前生产源码/APK v1.16 / vc17，2026-10-01 00:36:53 root pm install Success；最后 QQ PID 29168，采集器 CORE_READY pid=29275，回读正确、签名校验通过。最后安装时 QQ 已在前台，重载回到桌面后已恢复 QQ；没有继续打开模块面板/点聊天。后台识别与精确采样已进入生产日志，Java 任务 hook 已实际捕获任务。
- `CoreCollector.java`：独立 root app_process，QQ UID 过滤，sched_stat_runtime 的实际 CPU runtime 累计；`set_event_pid` + event-fork，独立 tracefs 实例，mono 时钟，256KiB/核。每秒发现进程/线程、差分完整性检查、丢事件统计、原子发布 `watchdog.core`；会话 generation 跨丢事件/进程变化断开差分。每 2 秒 dumpsys activity 确认 QQ 前后台（兼容 ResumedActivity）。不修改全局 sched_schedstats、全局 trace 或 hsuart。停止可能留下已禁用实例，下一次启动清理自己 UID/目录哈希前缀；本轮测试实例已手动 rmdir 清理。
- `CoreSnapshot.java` / `CoreTracker.java` / `RuntimeLine.java`：版本化有界数据、UTF8 名称、PID/TID 启动时间身份；累加所选核心实际时间，未选核心不触发。拒绝 UID 错误、无效/过期/间断/计数回退/不完整，重复序列不重复判定；换核心清空 `LoadHistory`，避免混合口径。只展示全核心进程 CPU 为参考，精确数据缺失时不以 lastCore 猜测。
- `WatchModule.java`：QQ 各进程 hook Application.attach、FutureTask.run/runAndReset、ThreadPoolExecutor.beforeExecute/afterExecute，仅主进程采样。设置迁移为 `cores=255` 默认全选、duration 固定 0、action=stop_task；已有阈值/间隔保留。新全开时严格 >200%，同阈值前后台；实际动作前复核设置。现有 QQ 增强 tag/长按协议保留。
- `TaskRegistry.java` / `TaskBridge.java`：注册活动任务及 generation/startNs；只处理覆盖整个采样窗口的目标，复核 proc 身份。Android13+ NOT_EXPORTED 同 UID 控制及结果广播，16 条有界控制队列、8 秒结果超时、5 秒确认。只允许标准 FutureTask 的 cancel/done 实现；普通 Runnable、自定义取消回调、原生线程需专用停止接口，当前不盲目 interrupt。取消位不算成功，观察任务返回才“任务已结束”；忽略中断显示“仍在运行”；线程池线程可保留。主线程/Binder/Render/GC/模块线程受保护。无 SIGKILL/tgkill/killProcess/Thread.stop；协作取消仍可能影响正常业务，不能保证所有 QQ 功能无影响。
- 最忙未保护工作线程优先；同一未成功身份每10秒重试，确认结束或换任务后解除。阈值不能证明泄露，前后台未知暂停；QQ主进程被系统冻结时处理延迟。独立 UID 隔离进程未采样。跨QQ子进程实际业务停止尚未实测。
- `WatchPanel.java` / `DashboardView.java` / `CpuCharts.java`：概览/明细/处理/设置四页，新增真实逐核百分比图、所选核心合计、前后台/采样状态。CPU0—7复选，默认200%可设。`StatusTableView.java` / `ProcessingHistory.java` 表格最近100条，时间/线程/合计/状态，展开PID/TID/核心/原因/详情，当前QQ会话内存保留，不加入导出。保留原监控日志及 Download/QQWatchdog 导出。
- `RootControl.java` / `MainActivity.java`：说明页启动/停止 root 精确采集；su 内 nohup app_process +独立输出文件，最多10秒等待 READY/FAILED，避免 Activity/launcher 长期引用，Root守护独立于页面；手机重启后手动再次启动。内核须 tracefs/sched_stat_runtime/sched_schedstats=1，缺少时明确不可用，当前不会开启全局统计。
- 验证：`test.ps1` 101 项（53 CPU+12日志+36核心与任务）；`core-test.ps1` 真机7个有效固定CPU0/1/2样本，最终合计最高228.8%，仅选0/1后CPU2最小排除37.8%；前轮286.1%/85.4%。`bridge-test.ps1` 2个隔离UID生产协议案例，任务返回且工作线程保留、取消位已设但执行仍继续如实报告。`ui-test.ps1` 7种状态×概览/表格14张PNG（演示数据）；后台无测试窗口。测试不打入生产APK，临时包已卸载。
- APK 95,238 B，SHA256 D07AB1AEA6FEF9016C31FFA1AB6C2A9081D0EC4A26E004488D470DC2C472C2D8，当前工作区 `outputs/QQ看门狗-v1.16-核心负载与任务处理测试版.apk` / `outputs/QQ负载监控-v1.16-测试版说明与验证.html`（docs-html镜像）。安装/真实日志/采集快照/演示图在 `work/v1.16-final-evidence/`；测试证据 `work/v1.16-core-device/`。
- 未验证：故障机 GIF/pool 异常的真实取消、跨QQ子进程真实回传、QQ内四页点击保存互跳、长时间功耗；原生停止接口仍需业务适配。本版是可运行的测试版，不能写成“已实现任意线程终结”。修改前备份 `work/qq-watchdog-before-v1.16/`。非 Git 项目，无 commit/PR。

## 0.0. v1.15 日志记录与导出（历史，2026-09-30）

用户最新要求：**核心0–3检测、后台判定及终止线程先别做；只补日志记录，并能导出到手机下载目录。** 上述检测调整之前仅只读分析，未改代码。本版保留v1.14负载判定，不终止线程/进程。

- 当前代码及装机 **v1.15（versionCode 16）**；2026-09-30 21:58:19 ADB/root覆盖安装 Success、回读正确，重启QQ后PID `21466`。仅1个 `qqwatch-dog`、1个 `qqwatch-log`，正式日志已连续记录负载/线程/频率，启动记录到222.7%超限事件。
- `RollingLog.java`：UTF-8完整记录、日期毫秒+时区、类别；4份×512KiB合计最多2MiB，轮换旧记录；固定白名单文件 `watchdog.0..3.log`，跨QQ重启保留；单条最多约16KiB且不拆Unicode；导出与写入同步保证完整记录。
- `WatchLog.java`：仅QQ主进程初始化，专用 `qqwatch-log` 后台线程，128条有界队列；溢出计数恢复后记WARN、写失败显示状态；导出排队在先前日志之后，重复导出防并发，不持唤醒锁。
- `WatchModule.java`：保存 SESSION/INFO/SETTINGS/EVENT/SAMPLE；快照不快于5秒且不快于采样，包含原有全部QQ进程、TOP12线程、PID/TID/最后核心、频率；只监控记录，不读取聊天内容。内部日志位于手机 `/sdcard/Android/data/com.tencent.mobileqq/files/watchdog-logs/`。
- `DownloadExporter.java`：Android10+ `MediaStore.Downloads`、UTF-8 text/plain、`IS_PENDING`完成后发布；失败删除半成品，不申请额外存储权限。Android8/9沿用QQ存储授权，未授权明确失败；旧系统分支未真机验证。
- `WatchPanel.java`：「明细」最上方新增日志状态及**导出日志到下载目录**，进度禁重入，完成显示 `Download/QQWatchdog/QQWatchdog_日期_时间_随机码.txt`；导出到 `/storage/emulated/0/Download/QQWatchdog/`。`MainActivity.java`补使用说明。QQ面板按钮未实际点击，已后台验证同一生产导出实现。
- `mod/test.ps1`：原52项负载检查 + 12项日志检查通过。`mod/log-test.ps1` / `android-tests/.../DownloadCheck.java`：隔离临时包 `com.lqb9.qqwatchlog.check`（target31与QQ相同），不打开窗口；真机11项存储/UTF8/发布/失败清理/异步排队检查通过，无存储权限；临时测试包及测试导出已清理。PS1纯ASCII，测试不进入正式APK。
- BUILD OK、签名验证通过；APK **74,758 B**，SHA256 `D2B74504FD5BEF45F52AACFDA27B1D8FFC78A0B3CCF116F0E264A975F84E55BD`。源码静态无 killProcess/tgkill/sendSignal/Thread.stop。监控配置和增强互通保持；长期性能未测。
- 交付 `outputs/QQ看门狗-v1.15-日志导出.apk`、`outputs/QQ负载监控-v1.15-日志导出说明.html`；HTML镜像 `docs-html/QQ负载监控-v1.15-日志导出说明.html`。安装记录/实际日志证据在当前工作区 `work/v1.15-install-record.json` / `work/v1.15-actual-module-log.txt`。
- 修改前v1.14源码、manifest、APK、AGENTS备份：`D:/ChatGPT/2026-09-30/xu/work/qq-watchdog-before-logs/`。

## 0.1. v1.14 图表 UI（历史，2026-09-30）

用户最新要求：**先优化 UI，用图表一眼看懂；先不做终止线程。** 先前希望最终实现逐核高负载归因及处理，当前不实现该动作。

- 当前代码/APK **v1.14（versionCode 15）**，包名、签名和 QQ 增强 tag 互通协议保持。
- 正式装机 **v1.14（vc15）**：用户随后明确要求直接安装，2026-09-30 21:02:17 已通过 ADB/root `pm install -r` 覆盖安装 Success；回读版本正确、已重启 QQ（PID `12714`）。主进程仅1个 `qqwatch-dog`（TID `12980`）；新日志有 loaded/packageReady/summon hook ok/tag found，冷启动记录到总 CPU `227.9%` 和热点线程。未进行点击/滑动或打开新版图表面板。
- `WatchPanel.java` 重写为固定标题/关闭、**概览 / 明细 / 设置**三页；宽度最高 420dp 且不超过屏宽94%，高度88%，正文独立滚动，保留保存、总开关、事件、增强互跳、遗留白名单清理。
- `DashboardView.java` / `CpuCharts.java` 原生 Canvas：大数字 QQ 总 CPU、最近60次采样趋势/阈值虚线、全部核心MHz条形图、TOP4线程横条；明细有进程横条、TOP12线程横条及可展开完整名称/PID/TID。
- **核心图展示频率，当前未采集逐核占用率**；硬件/驱动读数标明。最后核心只表示最近位置，线程可能迁移。不能将频率条当成负载或拿最后核心做可靠归因。
- `LoadHistory.java` 按监控线程的真实时间追加，最多60点；重复/倒序时间不追加、无效数据和长间断断线、不把不可用当0；immutable历史随LoadStatus一起发布，面板重开不丢历史，QQ重启清空。仅增加趋势历史，CPU判定逻辑保持v1.13。
- `mod/test.ps1`：**52项** JVM检查通过（旧45项 + 超100%、重复时间、无效/长间断、缓存上限/快照等7项历史检查）。
- `mod/ui-test.ps1` / `mod/android-tests/.../RenderCheck.java`：独立临时包 `com.lqb9.qqwatchui.preview`，仅编译生产图表与采样模型，**不进入正式APK**。连接手机后台绘制7种状态：普通、窄屏、大字体、数据缺失、过期、高负载、窄屏+大字体+高负载；检查PNG文字/条形/单位，无前台窗口；临时包已卸载。构建/测试PS1纯ASCII。
- 正式模块 `BUILD OK`，签名验证通过；APK **66,566 B**，SHA256 `5A7EEB06DC7D7E1417CD2CBD8E77AADE41D19DE4BF40991FA8D3236EAB1814C9`。静态源码不含killProcess、tgkill、sendSignal、Thread.stop动作。
- 未验证：QQ内新版三页点击、保存实际配置及增强互跳，真实长期刷新开销。原生截图使用**演示数据**，不冒充实机QQ采样。
- 单文件说明/预览：`docs-html/QQ负载监控-v1.14-图表UI说明.html`；交付APK、HTML、演示图在 `D:/ChatGPT/2026-09-30/xu/outputs/`。
- 本轮前的源码/已装v1.13 APK备份：`D:/ChatGPT/2026-09-30/xu/work/qq-watchdog-before-ui/`。
- 后续用户要求分析终止线程的最佳实现；本轮**仅分析**，未改模块/配置/追踪开关、未重启或处理QQ线程。只读核对Android16/SDK36、8核、KSU root、Perfetto/simpleperf、sched_switch/sched_stat_runtime事件和root可读proc/stat；实查系统libc动态符号pthread_cancel不存在，pthread_exit/pthread_kill存在。建议逐核归因+任务注册+白名单合作取消，先诊断后手动、再考虑自动；实际热点栈/QQ取消接口/调度录制开销尚未验证。详细分析在 `docs-html/QQ线程处理-方案分析与设备核对.html`，交付副本在当前工作区outputs。

## 0a. v1.13 负载监控与装机记录（历史）

**用户最终要求：先完成线程、核心频率和负载监控。此次只记录并提示，不终结任何线程或进程。**

- 模块代码和本地 APK 已更新到 **v1.13（versionCode 14）**，包名和签名沿用。
- 真机状态：用户随后连接 ADB 并明确要求安装，**2026-09-30 20:04 已覆盖安装 v1.13 成功**（设备 `642ac39a`，`pm install -r` 返回 Success）。回读 `versionCode=14 / versionName=1.13`，已强停重启 QQ，主进程 PID `22644`。下文 v1.12 装机状态是此前记录。
- 本轮真机验证：新进程日志有 `loaded`、`packageReady`、`summon hook ok` 和 `long-press left to QQ 增强 (tag found)`；主进程仅 1 个 `qqwatch-dog` 线程（TID `20616`）。冷启动记录到总 CPU `215.3%` 的超限事件及 3 个热点线程，之后 PID 未改变。面板实读 8 个核心的驱动频率（截图时核心 0~5 为 787 MHz、6~7 为 1017 MHz），固定标题/关闭按钮及事件区可见。用户在使用手机，停止后续界面操作；完整进程明细、额外互跳点击、后台 Toast 和采样开销未全部实测。
- 口径：**单核满载=100%**；汇总 QQ 同 UID 全部进程的 CPU 时间差分。200%~300% 表示约占用 2~3 个核心。不能再除以手机核心数压成 0~100%。独立 UID 的隔离进程不在此采样范围。
- 主进程只开一份监控线程；各 worker 不再重复启动引擎。QQ 主进程被系统冻结时，监控也暂停，不持唤醒锁。
- 新类 `QqCpuTracker.java` 处理进程身份/CPU 差分；`ThreadCpuTracker.java` 读 `/proc/<pid>/task/<tid>/stat`，显示最忙的 12 个线程（名称、PID、TID、CPU、最后核心）。线程数量只供查看，**不参与判定**。
- `CoreFrequency.java` 优先读 `cpuinfo_cur_freq`（硬件），否则读 `scaling_cur_freq`（驱动请求）；读不到写不可读。**频率和占用分别显示**，最后核心不代表窗口内完整驻留。
- 默认：**阈值 200%、持续 0 秒、间隔 1 秒**；首轮有效超限采样即提示，连续高负载只提示一次，降到阈值以下后允许再次提示。停用且面板关闭时降低采样频率；打开面板/保存设置/点击开关会唤醒采样。
- 配置：沿用 `watchdog.on`；`watchdog.load` 是原子保存的 Properties 文本，包含 `cpu=200`、`duration=0`、`interval=1`、`action=record`。没有新配置时仅沿用旧 `watchdog.iv`；旧 `.base/.k/.scope` 不读、不自动删。即便写入 `action=restart` 也只记录。
- CPU、持续时长、样本年龄均使用 `elapsedRealtime`。缺失/过期数据显示未知，采样失败/进程变化/复用/长间断不沿用旧超限累计。
- 面板新增进程、热点线程、核心频率三张卡；设置在顶部，标题和关闭按钮固定，不随内容滚动；原 QQ 增强 tag 互跳和长按优先级保留。
- 事件保留最近 20 条于内存，超限同时进入 LSPosed 日志并 Toast 提示；QQ 重启后内存事件清空。
- 本地检查：`mod\test.ps1`（纯 ASCII）运行 **45 项 JVM 回归检查**，覆盖差分汇总、TID/PID 复用、频率回退与重复提示；APK `BUILD OK`，签名验证通过，DEX 不含 `killProcess` 方法名。
- APK：`mod\build\qqwatchmod.apk`，**58,374 B**；SHA-256 `B76470C40F8A45EBF25ECDD9A1E7A641983E8B91A68BAAE968388098CFD8FE77`。
- 给用户的说明：`docs-html\QQ负载监控-v1.13-说明与验证.html`。本轮交付副本在 `D:\ChatGPT\2026-09-30\xu\outputs\`。

测试命令：`powershell -ExecutionPolicy Bypass -File D:\deepseek\qq-watchdog\mod\test.ps1`。模块构建仍按下文 §2。

**以下判定逻辑、设置项和面板说明是 v1.12 及以前的历史记录，不用于当前负载监控；构建/签名/模块互通的通用约束仍适用。**

## 0b. 旧版现状（v1.12 存档）

给手机 QQ 做「线程泄露看门狗」：线程数异常膨胀时把 QQ 进程杀掉，让它自己重启。
判定式：**动态上限 = 基线 + K × 本进程 CPU%**，连续 3 次超限才动手，启动 60 秒内只观察。

**当前装机 = 模块版 v1.12（versionCode 13）**，包名 `com.lqb9.qqwatchmod`，label「QQ 看门狗」，libxposed API 102，作用域 QQ。
APK：`mod\build\qqwatchmod.apk` 50,182 B，SHA-256 `B351A625CA7EE352479AB5FC59D32FFEE0B7C3EFE657A1368BC03AAFFC1E5E86`。
看门狗当前**开着**（`watchdog.on` 存在），自动基线、K=3、间隔 30 秒、范围 **`worker`（仅非主进程，用户自己选的）**。

**⭐ 长按「+」归 QQ 增强（用户 2026-09-30 明确要求"优先 QQ 增强"）**：本模块**看到增强的 tag 就主动让出长按**
（日志 `long-press left to QQ 增强 (tag found)`），自己只留一个 tag；没装增强时它才接管长按。
两个面板**互相能跳**：增强面板「实验 → 线程泄露看门狗」↔ 看门狗面板底部「打开 QQ 增强面板」。
面板底部还有 `killleak.*` 遗留提示 + 「清理遗留文件」（白名单删 5 个名字）。报告：`docs-html\两模块-长按优先级与排版-2026-09-30.html`。

这个工程里有两份实现，**模块版才是要用的那份**：

| 版本 | 包名 | 原理 | 状态 |
|---|---|---|---|
| **LSPosed 模块版**（`mod\`） | `com.lqb9.qqwatchmod` | 跑在 QQ 进程里，读自己的 `/proc/self`、`killProcess(自己)` | ✅ **装机在用**，已真机验证 |
| 独立版 App（根目录） | `com.lqb9.qqwatchdog` | 前台服务用 root 拉 shell 脚本，跨进程监控 | 引擎跑通过，但**用户已卸载**（要 root 授权，麻烦） |

## 1. 目录结构

```
D:\deepseek\qq-watchdog\
  mod\                         ← 模块版（在用）
    src\com\lqb9\qqwatchmod\
      WatchModule.java         XposedModule 入口 + 看门狗引擎 + 长按呼出
      WatchPanel.java          面板（代码画的 Dialog，粉皮，1 秒刷新）
      MainActivity.java        模块自带的说明页（点图标进去那个）
    libs\102.0.0-{api,interface,service}-\*.jar   libxposed 102（从 qq-module 拷过来的）
    xposed\{module.prop,java_init.list,scope.list}
    res\  AndroidManifest.xml  build.ps1  build\qqwatchmod.apk
  src\ assets\ res\ build.ps1  ← 独立版 App（root 版，已卸载但代码留着）
  tools\mkicon.py              图标生成（手写 PNG，不依赖 PIL）
  tools\report.py              交付报告生成（v1.6 那版「三件事」）
  tools\report_split.py        v1.9 拆分/互通报告 + 重建两边 docs-html\index.html（图片从 `ref\` 读）
  ref\crop_*.png               报告里那 5 张截证据图（面板、跳转、遗留提示/已清理、说明页）
  docs-html\                   给用户看的单文件 HTML（`看门狗-v1.9-两模块拆分与互通.html` 是最新的）
```

## 2. 构建 / 安装（模块版，照抄）

```powershell
$env:JAVA_HOME='C:\Users\98380\AppData\Roaming\CherryStudio\Toolchain\mise\installs\java\21.0.2'
$env:ANDROID_HOME='D:\deepseek\_work\android-sdk'
powershell -ExecutionPolicy Bypass -File D:\deepseek\qq-watchdog\mod\build.ps1   # 成功标志：BUILD OK
```
```powershell
$adb='D:\deepseek\_work\android-sdk\platform-tools\adb.exe'
& $adb push D:\deepseek\qq-watchdog\mod\build\qqwatchmod.apk /data/local/tmp/qqwatchmod.apk
& $adb shell su -c "pm install -r -d /data/local/tmp/qqwatchmod.apk"
& $adb shell su -c "am force-stop com.tencent.mobileqq"
& $adb shell monkey -p com.tencent.mobileqq -c android.intent.category.LAUNCHER 1
```
- **改完必须重启 QQ 才会生效**（LSPosed 在进程启动时加载 dex）。重装 APK 不用重新在 LSPosed 里勾选。
- ⚠️ `build.ps1` **必须纯 ASCII**：PS 5.1 把没 BOM 的 .ps1 按 GBK 读，中文注释会把脚本拆乱（踩过：报 `Test-Path 参数为 null`）。

## 3. 开关（文件即开关）

全在 `/sdcard/Android/data/com.tencent.mobileqq/files/`：

| 文件 | 语义 |
|---|---|
| `watchdog.on` | **存在 = 开**（默认不存在 = 关）。面板总开关就是建/删这个文件 |
| `watchdog.base` | 基线线程数；不存在或 0 = **自动**（每个进程各自取首次采样值） |
| `watchdog.k` | 系数 K，0~200，默认 3 |
| `watchdog.iv` | 采样间隔秒，默认 30（最小 5） |
| `watchdog.scope` | `all`（默认）/ `main`（只杀主进程）/ `worker`（只杀非主进程） |

**改了不用重启 QQ**：守护线程每轮重读文件，最多一个采样间隔生效。
⚠️ `watchdog.base` 压到低于真实线程数（比如 160）就是**触发实验**，会每 20 秒杀一次 QQ —— 测完记得删掉。

同目录里还有 5 个 **`killleak.*`（QQ 增强 v1.47 及以前的老看门狗开关）**：`killleak.on/.scope/.base/.k/.limit`。
**本模块不读它们，QQ 增强 v1.48 起也不读了** —— 纯遗留。面板里有「清理遗留文件」按钮一键删（§6/§9）。
`threads-last.txt`（老线程快照）同理，故意不自动删。

## 4. 判定逻辑（WatchModule.startWatch）

- `/proc/self/task` 目录条目数 = 本进程线程数；`/proc/self/stat` 的 utime+stime 差分算 CPU%。
  ⚠️ 从**最后一个 `)`** 切开再取 `f[11]+f[12]`（进程名里可能有空格/括号）。
- `CPU% = dc × 1000 / dt_ms`（USER_HZ=100）。
- ⚠️ **绝不能拿 `/proc/loadavg` 当负载**：实测 QQ 空闲时自己只占 1.8~3.5%，同一时刻系统 loadavg 能到 9.69（别的 App 在忙）。
- 主进程判定 = 进程名**精确等于** `com.tencent.mobileqq`（不是"没有冒号"，`com.tencent.ilink.ServiceProcess` 也没冒号）。
- 守护线程在**每个进程**里各跑一份（main / :MSF / ilink 都加载）。

## 5. 面板与「长按 +」的共存（踩过大坑）

面板呼出点跟 QQ 增强模块**完全一样**（长按 QQ 右上角「+」，`ImageView id=ba3`，中心 (1114,220)），
而 `setOnLongClickListener` 是**覆盖**语义，两个模块会互相顶掉。

**实测经过（2026-09-30）**：我们先挂上（日志 `armed prevListener=false`），QQ 增强在同一次 onResume 稍后又挂了一次
→ 长按出来的是**它的**面板。

**修法（v1.3 起）**：`arm()` 在每次 onResume 后**连查 5 轮**（`RETRY_MS = {150,350,700,1500,2500}ms`），
每轮读 `View.mListenerInfo.mOnLongClickListener` 比对是不是自己的；
不是就接管，并把对方的存进 `prevLongClick`。面板底部因此多一个「打开 QQ 增强面板」按钮还回去。
修完日志：`armed(try=2) prevListener=true` ✅

⚠️ **别退回"只查一次"**：那样谁后挂谁赢，随机的。

## 6. 面板（WatchPanel）

- 每秒刷新：状态（守着呢/已关闭）、最近采样、线程数、动态上限、算式、CPU、超限连击、已守时长、范围。
- 设置：总开关（建/删 `watchdog.on`）、基线、K、间隔、范围（三选一）、保存。
- ⚠️ **整块面板必须自己带底色**（v1.5 修）：原来只有每张卡片是白的，卡片之间和分组标题那里是透明的，
  底下的 QQ 界面直接透出来，用户一眼就看出「没底色」。现在 root 上挂一层圆角白底（`panelBg`，
  半径 18 跟头部对齐），卡片改成极浅灰 `#F7F8FB`，输入框/分段控件/开关行未选中态都是白底 + 细边。
- **头部右上角有 ✕**（v1.6 加）：内容比一屏高，底下那个「关闭」要滚很久才看得到。已验（截图 A/B：有面板 → 点 ✕ → 没面板）。
- ⚠️ **总开关行必须进轮询**：原来只在点击/打开时重绘，外部删了 `watchdog.on` 之后顶部大字已变「已关闭」而开关行还写「开」，自相矛盾（v1.4 修）。
- 面板底部（滚到底）：`清理遗留文件`（仅当存在 `killleak.*` 时）+ `关闭` + `打开 QQ 增强面板`（仅当检测到对手的长按监听时）+ 脚注。
- **v1.7~v1.9 新增的处理**：原来那条"QQ 增强里那个看门狗也开着"的黄字提示**已过时**（QQ 增强 v1.48 起那段代码删了），
  现在改成灰色 ℹ️ 提示 + 「清理遗留文件」按钮（§9）；说明页（MainActivity）加了「和 QQ 增强怎么配合」一节。
- ⚠️ 面板里有 `EditText`：**点它会弹软键盘把 Dialog 顶上去**，测的时候先 dump 再点（老坑）。
- ⚠️ 面板内容比一屏高时，`input swipe` 往上滚经常滚不动（实测 8 次没动）—— 想点顶部的 ✕ 就重开一次面板，别硬滚。
- ⚠️ **滚到底要用 600~700 ms 的慢滑**（`swipe 250 700 250 180 600`，起手点选在卡片正文上，别起手在按钮上），约 4 次到底。
- ⚠️⚠️ **这个面板 `uiautomator dump` 一定失败**：它每秒刷新一次文本，永远等不到 idle（`ERROR: could not get idle state.` + 拉不到 xml）。
  取坐标改用**截图 + 像素测量**（`_buildlog\bbox.py` 扫按钮底色 `#F1F3F7`；`col.py`/`rows.py` 看色带；`crop.py` 裁图核对）。
  滚到底后的实测坐标（1200x2608 / 480dpi）：清理遗留文件 **(600,2175)**、关闭 **(344,2316)**、打开 QQ 增强面板 **(776,2316)**。
  QQ 增强的面板没有每秒刷新，`dump` 正常；它里面的跳转行 bounds = `[715,1764][1008,1809]` → 点 **(600,1786)**。
- ⚠️ **别用"滑"代替"点"**：从按钮上起手的滑动实测有一次把按钮按下去了（好在是清理按钮）。要按按钮就单发 `input touchscreen tap`。
- ⚠️ **两个面板会叠**（2026-09-30 实测）：面板开着时，如果长按「+」时手指落在**面板矩形之外**，触摸会穿透到 QQ，
  看门狗面板就叠在 QQ 增强面板上面 —— 关掉上面那个会露出下面那个。两个模块互相看不见对方的 Dialog，没法根治；
  面板里那两个「互跳」按钮是干净的（跳之前各自 `dismiss()` 自己）。复现路径：开增强面板 → 长按 (1114,220) → 截图看不到粉色底 = 正常。

## 7. 独立版（已卸载，留档）

- 引擎 = `assets\qqwatch.sh`，前台服务用 `su` 拉起，脚本每轮 stdout 打 `BEGIN/CFG/P/END` 给 App 解析。
- ⚠️ **App exec su 会 EACCES，`/system/bin/su` 连 exists 都是 false** —— 不是权限不是 SELinux：
  `ksud feature list` 里 `su_compat` 是开的，但它只管 **authorized apps**；
  得在 **KernelSU 管理器 → 超级用户 → 搜包名 → 打开开关** 之后 su 才对 App 可见。
- ⚠️ **Android 的 sh 是 32 位整数**：`$((1790740393*1000))` 变负数 → 时间只存「秒+百分秒」，先减后乘。
- ⚠️ **`Process.destroy()` 只杀 su**，脚本 `sh` 会被 init 收养继续跑 → 要按脚本自报的 pid 补 `kill -9`。

## 8. 真机验证记录（2026-09-30）

- 模块加载：LSPosed 日志 `[QQWATCH] loaded in com.tencent.mobileqq / :MSF / com.tencent.ilink.ServiceProcess`。
- 面板：抬头 `com.tencent.mobileqq · pid 21947`，实时数字正常。
- 开关：点总开关 → `watchdog.on` 内容 = `1` ✅
- **真实触发**：`base=160` + `iv=5` → 连续 3 次超限 → **pid 21827 → 27756**（被杀 + QQ 自己拉起）✅
- 恢复自动基线后：算式 `337 + 3 × 5.0% = 352`，超限 0/3，pid 21947 没被动过 → **自动基线不误杀** ✅
- 触发实验后**记得清掉** `watchdog.base` / `watchdog.iv`（本次已清）。

### 二次验证（2026-09-30 下午，v1.7 / v1.8 / v1.9）

- 装机链：`v1.7(vc8)` → `v1.8(vc9)`（补 `killleak.limit`）→ `v1.9(vc10)`（说明页加一节），每个都 `BUILD OK` + `pm install` `Success`
  + 强停重启 QQ；`dumpsys package` 回读 `versionCode=10 / versionName=1.9`。
- 模块加载（v1.9 重启后日志）：`[QQWATCH] loaded in com.tencent.mobileqq / com.tencent.mobileqq:MSF / com.tencent.ilink.ServiceProcess`
  + `summon hook ok` + `armed(try=1) on ImageView prevListener=true`（14:16:51 那一批）。
- **双向跳转实测**：看门狗面板 →（776,2316）「打开 QQ 增强面板」→ QQ 增强 v1.48 面板（徽章 `v1.48`，`已挂钩 157`）
  →（600,1786）「线程泄露看门狗」→ 回到看门狗面板（`pid 19017 · 守着呢 · 线程数 326`）✅
- **清理按钮实测**：v1.7 时表里只有 4 个名字 → 删掉 4 个、`killleak.limit` 漏在设备上（面板却写着"再没有 killleak.*"）
  → v1.8 把 `.limit` 也加进白名单 → 重测：`14:18:07 清理 QQ 增强遗留文件 5 个`、绿字回执、按钮自隐、`ls` 确认 5 个文件都没了 ✅
- ⚠️ 测试完**把 5 个文件按原字节还原**（`/sdcard/kkbk/` 有备份 + `chown u0_a265:ext_data_rw`），
  下次再测记得还原：设备上现在仍然是 5 个 `killleak.*` 文件 + `threads-last.txt`。
- 说明页（MainActivity）v1.9 渲染正常：`模块 v1.9 · 线程数异常时自动重启 QQ 进程` + 「和 QQ 增强怎么配合」一节 + `本机 QQ 9.2.85（vc 13860）`。

## 9. 和 QQ 增强模块的关系（2026-09-30 已拆干净 + 长按归它）

- QQ 增强（`com.lqb9.qqprobe`）**v1.48 起把老看门狗整块删掉了**；**v1.49 起长按「+」归它**（用户要求）。
  装机自检 `已挂钩` 从 160 掉到 **157**（少的就是看门狗那批 hook）✅
- **⭐ 会合点 = 「+」这个 View 上的 tag（v1.11 起）**，不再靠抢长按监听：
  `View.setTag(0x7f0f0001, 增强的 opener)` / `View.setTag(0x7f0f0002, 看门狗的 opener)`，值是 `java.lang.Runnable`
  （两个模块 ClassLoader 不同，只能用 boot classpath 的类型，自定义接口会 ClassCastException；key 必须 ≥ 0x02000000）。
  看门狗 `arm()` 每轮先看 `0x7f0f0001` 在不在：在 → **让出长按**，只留自己的 tag；不在 → 才 `setOnLongClickListener(MY_LONG_CLICK)` 接管。
  跳转：增强面板那行读 `0x7f0f0002` 直接 `run()`（拿不到才退回老的 `performLongClick()` 兼容路），
  看门狗面板底部按钮读 `0x7f0f0001` 直接 `run()`；两边都先 `dismiss()` 自己。
- **只有其中一个模块时**：只剩增强 → 那行 Toast「没检测到 QQ 看门狗模块…」；只剩看门狗 → 长按出它自己的面板，
  底部按钮 Toast「没检测到 QQ 增强模块…」（按钮 v1.11 起一直显示，不再"没对手就隐藏"）。
- **`killleak.*` 已成孤儿**：`killleak.on/.scope/.base/.k/.limit` 谁都不读了。
  看门狗面板检测到就显示灰色 ℹ️ 提示 + 「清理遗留文件」按钮（`WatchModule.legacyLeftover/legacyCount/legacyPurge`，
  白名单 5 个名字，**不通配删除**）；删完变绿字 `✅ 已清理 N 个遗留文件` 并把自己隐藏。
  ⚠️ 只删这 5 个名字，绝不动 `rp-seen.txt` / `rp-peers.txt` / `redpacket.*` / `noupdate.on` / `syswebview.on`。
  `threads-last.txt`（v1.42 时代的线程快照）也还在，**故意不自动删**。

## 10. ⭐ 锁屏后 QQ 被系统冻结（2026-09-30 实测，抢红包锁屏不触发的根因）

```
息屏前：pid 29201(主) cpu=1187 threads=291 freeze=0 / pid 8326(MSF) freeze=0 / pid 9049(ilink) freeze=0
灭屏 90 秒：三个进程 freeze=1（cgroup freezer），cpu 只涨了一点点就停住
```
- 查法：`cat /sys/fs/cgroup/apps/uid_10265/pid_<pid>/cgroup.freeze`（1 = 被冻住），
  `uid_10265` = QQ 的 uid，`/sys/fs/cgroup/apps/` 是 Android 16 的 app cgroup。
- **被冻住的进程不执行任何代码** → 内核消息回调、抢红包的 HTTP 全都没机会跑。「锁屏抢不到红包」不是模块的 bug。
- 已经放行的没用：QQ 本来就在 Doze 白名单（`cmd deviceidle whitelist` 里有 `user,com.tencent.mobileqq`），
  `RUN_ANY_IN_BACKGROUND=allow`；MIUI 自己的省电策略表是
  `/data/data/com.miui.powerkeeper/databases/user_configure.db` → `userTable.bgControl`，QQ 现在是 `miuiAuto`（默认智能省电）。
- 待验证的方向：① 系统设置里把 QQ 的省电策略改成**无限制**（改完复测 freeze 是否变 0）；
  ② 不行再试模块里持 PARTIAL_WAKE_LOCK；③ 折中是充电时不息屏。⚠️ 真正验收要有第二个账号发红包。
- 测完记得把屏幕点亮（`input keyevent 26` + 上滑解锁）。
