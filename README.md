# QQ负载监控

当前交付版 **v1.17（versionCode 18）**，包名 `com.lqb9.qqwatchmod`，libxposed API 102。已在 Android 16、QQ 9.2.85（vc13860）验证构建、安装、采样及隔离任务控制；故障机的实际异常任务停止仍待验证。

[下载当前 APK 与单文件说明](https://github.com/LQB9/qq-load-monitor/releases/tag/load-monitor-v1.17) · [完整说明与验证记录](docs-html/QQ负载监控-v1.17-最终版说明.html) · [版本记录](CHANGELOG.md)

## 当前规则

| 项目 | 行为 |
|---|---|
| 监控对象 | 仅 QQ 同 UID 的进程与线程 |
| 核心范围 | CPU 0—7 可任意组合，默认全选，至少选一个 |
| 负载口径 | QQ 在所选核心上实际运行的 CPU 时间合计；一个核心满载为100% |
| 触发条件 | 严格超过设置阈值，默认200%；首轮有效采样立即尝试处理 |
| 前后台 | 确认 QQ 是否在前台；前后台共用同一阈值，后台超限立即尝试处理 |
| 处理状态 | 最近100条表格：时间、线程、合计负载、实际结果；展开查看详情，当前 QQ 会话内保留，不导出 |
| 无效数据 | 缺失、过期、丢事件、前后台未知时暂停处理 |

MHz 表示核心频率，与占用百分比分开显示。线程数量与最后运行核心仅供参考，不参与所选核心的负载归因。

## 任务处理能做到什么

按所选核心上的占用贡献排序，优先定位最忙的未保护工作任务。复核 PID/TID 启动时间和任务身份，任务须覆盖整个采样窗口。

当前支持对标准 `FutureTask` 发起协作取消，只有实际观察到任务返回才显示「任务已结束」；线程池的工作线程可以保留。取消标记已设置，但任务仍在执行，会显示「仍在运行」。普通 `Runnable`、自定义取消回调和原生线程没有通用停止接口时，显示「无法处理」或「未处理」。同一未成功目标最多每10秒重试。

不强杀 QQ 进程，不使用 `SIGKILL`、`tgkill` 或 `Thread.stop`。主线程、Binder、渲染、GC 与模块线程受保护。高 CPU 占用是触发条件，不能单凭阈值证明线程泄露；真实 GIF/pool 故障任务和跨 QQ 子进程的业务停止尚待验证。

## 安装与使用

1. 安装 Release 中的 **QQ负载监控.apk**，在 LSPosed / ReVanced Xposed 启用，作用域勾选 QQ。
2. 强行停止 QQ，再打开，使新模块生效。
3. 从桌面打开「QQ负载监控」，点击「启动精确核心采集」，按手机提示授权 root。
4. 长按 QQ 主界面右上角「+」打开面板。一起安装 QQ 增强时，先进入增强面板，再点击「实验 → 线程泄露看门狗」。
5. 设置页选择核心、合计阈值、采样间隔，保存后开启监控；在概览确认精确采样有效。

手机重启后需手动再次启动精确采集。采集要求 root、tracefs 的 `sched_stat_runtime` 事件及已开启的内核调度统计；当前实现不会主动修改全局调度统计开关。QQ 主进程被系统冻结时，模块判定与任务控制会延迟，独立采集器仍可采集运行中的 QQ 进程。

## 面板与日志

- **概览**：所选核心的 QQ 合计、趋势/阈值线、QQ 逐核百分比、核心频率、热点线程。
- **明细**：QQ 全核心进程 CPU 参考值、热点线程与监控日志导出。
- **处理**：实际处理状态表，展示待确认、任务已结束、仍在运行、无法处理等结果。
- **设置**：总开关、核心选择、阈值及采样间隔。v1.17 使用固定尺寸的粉色核心按钮，修复 QQ 主题将复选勾选图放大重叠的问题。

监控日志为 UTF-8、4份×512KiB，位于 `/sdcard/Android/data/com.tencent.mobileqq/files/watchdog-logs/`，重启 QQ 后保留。明细页可导出至 `Download/QQWatchdog/`。处理状态表不加入导出；模块不读取聊天内容。

配置目录：`/sdcard/Android/data/com.tencent.mobileqq/files/`。`watchdog.on` 存在表示开启，`watchdog.load` 示例：

```properties
cpu=200
duration=0
interval=1
cores=255
action=stop_task
```

`cores` 是 CPU0—7 的位掩码，255为全选、15为CPU0—3。精确采集文件为 `watchdog.core`。

## 构建与验证

现有构建脚本按本机路径配置：工程 `D:\deepseek\qq-watchdog`、JDK21、Android SDK build-tools37.0.0/platforms android-37.0。换机器时需调整各脚本的工具链路径。原签名私钥不入库；要覆盖当前已装模块，须恢复原 `mod\watchmod-key.jks`。

```powershell
powershell -ExecutionPolicy Bypass -File mod\build.ps1
powershell -ExecutionPolicy Bypass -File mod\test.ps1
powershell -ExecutionPolicy Bypass -File mod\ui-test.ps1 -OutputDirectory D:\qqwatch-ui-check
powershell -ExecutionPolicy Bypass -File mod\log-test.ps1 -OutputDirectory D:\qqwatch-log-check
powershell -ExecutionPolicy Bypass -File mod\core-test.ps1 -OutputDirectory D:\qqwatch-core-check
powershell -ExecutionPolicy Bypass -File mod\bridge-test.ps1 -OutputDirectory D:\qqwatch-bridge-check
```

生产构建成功标志 `BUILD OK`，输出 `mod\build\QQ负载监控.apk`；内部兼容产物 `qqwatchmod.apk` 同内容。`.ps1` 保持 ASCII。设备隔离测试脚本生成临时测试 APK，须另外安装并运行 instrumentation，源码不打入正式 APK。

| 验证 | 结果与范围 |
|---|---|
| v1.17 构建/签名/安装 | 通过；vc18回读，QQ加载新版，精确采集恢复，用户配置保留 |
| v1.17核心选择 UI | 3组原生离屏渲染：全选、混选、260dp窄屏/1.6倍字体；点击及辅助功能状态通过 |
| v1.16基础逻辑 | 101项 JVM 检查通过；v1.17未改对应采集与处理逻辑 |
| v1.16精确核心采集 | 真机固定核心控制样本验证，未选核心排除，合计超过200% |
| v1.16任务协议 | 隔离案例验证任务返回与取消后仍运行两类状态 |
| 待验证 | 故障机真实异常任务、跨QQ子进程业务停止、长时间功耗、QQ内完整四页点击流程 |

离屏图片使用演示数据，不能当作故障机已经修复的证据。

## 源码目录

- `mod/`：生产模块、依赖库、资源、测试与手工构建脚本。
- `tools/mk_module_icon.py`：粉色渐变/白色负载曲线图标生成器（Pillow），5种密度。
- `docs-html/`：当前单文件说明及历史文档。当前口径以 README 和 v1.17说明为准。
- `AGENTS.md`：开发交接与历史验证记录。
- 根目录 `src/ assets/ res/ build.ps1`：已停用的早期独立版，保留历史。

QQ 增强互通保留 `View` tag `0x7f0f0001` / `0x7f0f0002`，值使用跨 ClassLoader 可识别的 `java.lang.Runnable`。增强模块存在时长按优先归增强。

签名私钥、访问令牌、真实设备日志和聊天数据不入库。仓库与云盘备份的可见性沿用已有设置。

## APK 校验

文件 **QQ负载监控.apk**，128,006 B，SHA-256：

```text
5EC34DECB5B291BBB0D55EBB7662A3E47806DFD5613AE54AAB52D9F593F28358
```
