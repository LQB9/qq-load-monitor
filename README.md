# QQ负载监控

当前版 **v1.34（versionCode 35）**，包名 `com.lqb9.qqwatchmod`，libxposed API 102。包含v1.33采集器优化及新图标；Android 16、QQ 9.3.70（vc16410）原签名安装与有效采样已核对。QQ 9.3.70专用Job停止适配、故障机实际异常任务停止和长期温升仍待验证。

[v1.34 APK、源码与说明](https://github.com/LQB9/qq-load-monitor/releases/tag/load-monitor-v1.34) · [v1.34发布说明](docs-html/QQ负载监控-v1.34-版本说明.html) · [v1.33采集器优化](docs-html/QQ负载监控-v1.33-采集器优化说明.html) · [v1.34图标重绘](docs-html/QQ负载监控-v1.34-应用图标重绘.html) · [v1.32五版QQ任务适配](docs-html/QQ负载监控-v1.32-五版QQ任务适配.html) · [v1.31独立规则开关与单线程趋势](docs-html/QQ负载监控-v1.31-独立规则开关与单线程趋势.html) · [v1.30监控布局与共用图表](docs-html/QQ负载监控-v1.30-监控布局与共用图表.html) · [v1.29统一开关与日志表格](docs-html/QQ负载监控-v1.29-统一开关与单线程日志表格.html) · [v1.28单线程图表与开关](docs-html/QQ负载监控-v1.28-单线程图表与开关说明.html) · [v1.27单线程持续超限](docs-html/QQ负载监控-v1.27-单线程持续超限规则.html) · [v1.26线程池归属与QQ9.2.25核对](docs-html/QQ负载监控-v1.26-线程池归属与QQ9.2.25核对.html) · [v1.25模块架构与验证](docs-html/QQ负载监控-v1.25-模块架构与验证.html) · [v1.24GIF专项说明](docs-html/QQ负载监控-v1.24-GIF专项处理与验证.html) · [v1.23持续时长说明](docs-html/QQ负载监控-v1.23-超限持续时长说明.html) · [v1.22修复与验证说明](docs-html/QQ负载监控-v1.22-修复与验证说明.html) · [v1.21采集异常日志说明](docs-html/QQ负载监控-v1.21-采集异常日志说明.html) · [v1.20表格层次与预览](docs-html/QQ负载监控-v1.20-表格层次优化.html) · [执行诊断说明](docs-html/QQ负载监控-v1.19-执行诊断与表格说明.html) · [版本记录](CHANGELOG.md)

v1.18 在现有日志新增失败诊断：目标负载、任务类型、窗口时间差与可取得的 Java 栈。处理状态清单仍不导出。仅使用准确的 Java 线程/TID 映射；缺少映射不能据此认定为原生线程。原生用户栈尚未采集，该历史版本未适配实际GIF停止，当前专项规则见下文。

v1.19 新增任务真正执行时的 EXEC 证据：业务方法、调度类型/周期、次数、CPU耗时与入口栈；等待后保留最近10秒。执行CPU为全核心诊断参考，不参与触发。处理表默认补全PID/TID、线程自身负载、核心/阈值、前后台与具体原因，展开查看执行/失败证据。

v1.20 为处理记录增加独立边框、间距、时间/状态栏、大号分色负载、原因/最近执行分区；展开证据保留在同一记录内。监控、取消和日志逻辑沿用v1.19。

v1.21 补充采集异常/恢复独立事件、具体原因、CPU时间校验值及逐核丢事件计数；采集器有界保留短暂异常，延迟读取时可补记，轮换缺失以CORE_GAP标明。升级后需重启QQ与精确采集器。

v1.22 将线程身份复核交给目标进程，区分读失败/退出/身份变化；新增Handler消息执行入口和失败分类；线程扫描后夹取固定调度计数，用进程CPU时间下限进行原完整性校验，减少读取边界偏差。真实QQ已收到MSF执行记录和Handler业务入口/栈；无通用停止接口的任务仍需专用适配。

v1.23 恢复超限持续时长输入和配置，并接入真实任务处理条件。0秒首轮有效超限即尝试，其他值连续超限满时长后尝试；前后台共用。概览显示超限累计时间，目标拒绝时长设置变更后的旧控制请求。

v1.24 新增GIF对象专项暂停：通过实际RenderTask.run窗口证据定位GifDrawable，公开stop接口加对象级启动/渲染拦截；确认停止后显示GIF已暂停，提供手动恢复和关闭/配置变化自动释放。默认开启，不更改已有负载设置；故障机真实效果仍待验证。

v1.25 完成模块架构整理：入口从1016行收敛为22行；WatchRuntime统一启动，HookGateway登记真实句柄并去重。配置、采样、纯判定、QQ业务适配/规则、UI/日志分开；GIF专项与Future协作取消按顺序注册，原规则和用户配置保持。

v1.26 增加真实线程池 → 工作线程 → 任务的准确归属，区分提交观察和实际执行，历史任务保留当时池信息；原负载/触发/停止策略不变。用户提供QQ9.2.25（11820）原包已完成静态路径及Android真实类加载反射核对，现有GIF专项接口匹配；原生渲染不返回时stop仍可能等待锁，实际故障机停止效果待验证。344项JVM、28个Android案例通过，本机真实QQ池/任务证据与原签名安装确认通过。

v1.27 新增独立单线程持续超限：默认开启检测、80%/10秒可设、仅记录，自动处理单独开启。共用核心选择和采样，每个线程身份分别计时；与合计规则OR触发、同目标合并去重。目标进程逐窗口确认同一任务或GIF对象的持续业务归属，切换/缺证据重新计时，执行前再核对；原合计行为保持。处理表与原日志补充触发来源，控制V5。397项JVM、37个Android案例和3组原生控件渲染通过；原签名装机、实际采样和配置保留确认。

v1.28 为概览/明细补充单线程独立负载和持续时长进度、已保存参数、检测/处理模式；明确关闭/暂停/无效/过期/新配置等待状态，旧计时不冒充有效结果。设置采用独立绘制的开关，区分开启、关闭与仅记录，提示修改未保存；52个后端相关源码不变。421项JVM、39组原生渲染及状态/布局检查通过，原签名装机、实际运行与原设置保留确认。

v1.29 统一StateSwitchView用于总监控、GIF、单线程检测/处理及精确采集；采集确认真实运行锁，操作未确认/失败不乐观切换。SAMPLE/THREAD_LIMIT补完整单线程配置、身份、累计/要求时长、模式和窗口，表格保留触发当时两套累计及模式。444JVM、66原生UI、3Android控制案例及实际Downloads导出通过，原签名装机/实际加载/原设置保留确认。

v1.30 将监控范围（核心选择/共用采样）、合计规则、单线程规则和GIF处理方式分块。概览精简为合计趋势、单线程TOP3和逐核负载附频率三个卡片；明细共用单线程图表组件展示TOP12，全核心参考/完整线程信息/事件按需展开。57原生产源文件不变；444JVM、84原生UI/4847检查通过，原签名装机、实际采样及保存配置保留确认。

v1.31 为合计规则补独立检测/自动处理开关，与单线程规则独立，目标按允许处理的来源复核，控制V6。概览/设置统一9字名称“总负载持续超限规则”“单线程持续超限规则”；单线程新增完整身份匹配的独立TOP3趋势，60点有界，缺失断线。日志/表格保留两套模式，TOTAL_LIMIT补合计事件；533JVM、102原生UI/8089检查、12Android控制及实际原子配置/Downloads导出通过，原签名装机与旧配置保留确认。

v1.32 用五个下载QQ原包核对GIF/Job接口，新增精确版本与运行时结构双重验证的Job内层Future执行身份。只对真正执行且绑定一致的平台Future协作取消；外层时长不转移、热补丁或自定义回调拒绝，普通Job不猜停止接口。568JVM、37原Android、12双规则与实际导出、新Job实际取消/返回/保留池、五原包真实类加载核验通过；62原生产文件不变，原签名装机及当前配置原文保留，真实故障降载待验证。

v1.33 降低精确采集器自身开销：按需解析/proc、复用有界读取缓冲、按文件描述符软上限缓存实时读取、直接解析调度事件字节流。逐秒采集、QQ UID范围、完整性校验及双规则保持。同一隔离600休眠worker场景，旧版两轮CPU平均12.29%，新版6.02%，下降51.0%；动态场景与设备会影响降幅，不能泛化。1194项JVM及Android原生读取/缓存、固定核心采集验证通过；后台高温原因与长期降温效果仍待观察。

v1.34 更新独立CPU芯片与负载曲线图标，支持五种PNG密度、adaptive和monochrome资源；保留v1.33采集器优化，其他原生产Java仅版本常量改变。已原签名安装1.34/vc35，手机APK哈希匹配、26个设置原文保留、3个连续有效采样通过；没有把v1.33压力测试称为v1.34重新实测。

## 模块架构

| 部分 | 主要实现 |
|---|---|
| 单线程补充 | ThreadLoadMonitor / DualLoadPolicy / ThreadTaskAttribution / ThreadRuleSettingsView；独立线程时钟、统一请求、目标业务连续确认 |
| 运行与配置 | WatchModule / WatchRuntime / HookGateway / ModuleInfo / WatchSettings / SettingsStore / SettingsCodec |
| 负载采样 | MonitorEngine / QqLoadSampler / CoreCollector / CoreTracker / LoadSnapshot |
| 触发判定 | CpuLoadMonitor / LoadPolicy；纯Java，核心合计、阈值、持续时长和受保护目标 |
| 任务处理 | TaskServices / ExecutorRegistry / TaskObservationHooks / GifHookAdapter / GifHostAdapter / TaskBridge / TaskRuleRegistry / GifHandlingRule / FutureHandlingRule |
| 展示与记录 | PanelEntryHooks / WatchPanel / MonitorState / MonitorReport / StatusTableView / WatchLog |

WatchRuntime管理QQ进程生命周期，主进程一份采样，root精确采集独立运行；HookGateway只有实际安装成功才登记，重复Method共享一个句柄，关闭时释放。启动失败捕获并清理；暂停GIF租约尚存时保留保护Hook。关闭监控仍沿用诊断和降低频率，不等于卸载Hook。TaskBridge继续做目标身份/UID/配置/时效复核；TaskRuleRegistry首个明确结果终结匹配，仅“不适用”进入后续规则。新增规则实现TaskHandlingRule；QQ公开接口与保护Hook放入适配层。

完整说明：[v1.25模块架构与验证](docs-html/QQ负载监控-v1.25-模块架构与验证.html)。

## 当前规则

单线程补充规则默认开启检测、80%/10秒、仅记录；用户可启用自动处理。两规则共用勾选核心与采样，前后台相同，合计已达条件不等待单线程规则。同一目标两种来源合并，目标业务持续归属不足或变化时不处理。详见[v1.27说明](docs-html/QQ负载监控-v1.27-单线程持续超限规则.html)。

| 项目 | 行为 |
|---|---|
| 监控对象 | 仅 QQ 同 UID 的进程与线程 |
| 核心范围 | CPU 0—7 可任意组合，默认全选，至少选一个 |
| 负载口径 | QQ 在所选核心上实际运行的 CPU 时间合计；一个核心满载为100% |
| 触发条件 | 严格超过设置阈值，默认200%；连续超限达到设置时长后尝试处理，0秒立即 |
| 持续时长 | 0—3600秒可设，默认0；按有效采样窗口累计，回落/无效/过长间断/设置变化后重计 |
| 前后台 | 确认 QQ 是否在前台；前后台共用同一阈值与持续时长 |
| 处理状态 | 最近100条表格：线程/PID/TID、QQ合计/线程自身、核心/当时阈值、前后台、实际结果/原因；展开查看执行与失败证据，当前 QQ 会话内保留，不导出 |
| 无效数据 | 缺失、过期、丢事件、前后台未知时暂停处理 |

MHz 表示核心频率，与占用百分比分开显示。线程数量与最后运行核心仅供参考，不参与所选核心的负载归因。

## 任务处理能做到什么

按所选核心上的占用贡献排序，优先定位最忙的未保护工作任务。由目标进程复核 PID/TID 启动时间和任务身份，标准Future取消要求活动任务覆盖整个采样窗口；GIF专项可使用已返回短任务的完整窗口执行证据。

GIF专项默认开启：已观测GIF业务CPU至少占该线程全核CPU的50%；扣除全部未选核心CPU后的GIF贡献保守下限也必须达到所选核心线程负载50%，且公开接口及start/recycle保护入口匹配时，暂停最多3个实际关联对象。停止播放且正在渲染数为0才显示「GIF已暂停」；5秒未返回显示「GIF仍在运行」。处理页可恢复；关闭/更改设置自动解除；不可见、已释放或仍渲染对象不主动重播。完整暂停后窗口提供QQ合计复采参考；不以下降单独证明因果。

当前支持对标准 `FutureTask` 发起协作取消，只有实际观察到任务返回才显示「任务已结束」；线程池的工作线程可以保留。取消标记已设置，但任务仍在执行，会显示「仍在运行」。普通 `Runnable`、自定义取消回调和原生线程没有通用停止接口时，显示「无法处理」或「未处理」。同一未成功目标最多每10秒重试。

不强杀 QQ 进程，不使用 `SIGKILL`、`tgkill` 或 `Thread.stop`。主线程、Binder、渲染、GC 与模块线程受保护。高 CPU 占用是触发条件，不能单凭阈值证明线程泄露；真实 GIF/pool 故障任务和跨 QQ 子进程的业务停止尚待验证。

## 安装与使用

1. 安装 Release 中的 **QQ负载监控.apk**，在 LSPosed / ReVanced Xposed 启用，作用域勾选 QQ。
2. 强行停止 QQ，再打开，使新模块生效。
3. 从桌面打开「QQ负载监控」，点击「启动精确核心采集」，按手机提示授权 root。
4. 长按 QQ 主界面右上角「+」打开面板。一起安装 QQ 增强时，先进入增强面板，再点击「实验 → 线程泄露看门狗」。
5. 设置页选择核心、合计阈值、超限持续时长、采样间隔及GIF专项开关，保存后开启监控；在概览确认精确采样有效。

手机重启后需手动再次启动精确采集。采集要求 root、tracefs 的 `sched_stat_runtime` 事件及已开启的内核调度统计；当前实现不会主动修改全局调度统计开关。QQ 主进程被系统冻结时，模块判定与任务控制会延迟，独立采集器仍可采集运行中的 QQ 进程。

## 面板与日志

- **概览**：总负载持续超限规则、单线程持续超限规则各自趋势与计时，QQ 逐核占用附频率。
- **明细**：单线程持续规则 TOP12；全核心进程与完整线程诊断按需展开，保留日志导出。
- **处理**：实际处理状态表及“恢复已暂停GIF”；GIF待确认/仍在运行/已暂停/恢复结果与原任务状态并列显示。
- **设置**：总监控、独立监控范围与共用采样；两规则分别设置检测、自动处理、阈值和持续时长；另有 GIF 专项开关。

监控日志为 UTF-8、4份×512KiB，位于 `/sdcard/Android/data/com.tencent.mobileqq/files/watchdog-logs/`，重启 QQ 后保留。明细页可导出至 `Download/QQWatchdog/`。处理状态表不加入导出；模块不读取聊天内容。

配置目录：`/sdcard/Android/data/com.tencent.mobileqq/files/`。`watchdog.on` 存在表示开启，`watchdog.load` 示例：

```properties
cpu=200
duration=0
interval=1
cores=255
action=stop_task
gifRule=true
threadRule=true
threadCpu=80
threadDuration=10
threadHandle=false
totalRule=true
totalHandle=true
```

`threadHandle=false` 表示单线程默认仅记录；两规则的检测/自动处理开关分别独立。`totalRule`/`totalHandle` 缺失时按旧设置兼容为开启。

`cores` 是 CPU0—7 的位掩码，255为全选、15为CPU0—3。`gifRule`缺失时默认开启；只有保存才写入该字段。精确采集文件为 `watchdog.core`。

## 构建与验证

现有构建脚本按本机路径配置：工程 `D:\deepseek\qq-watchdog`、JDK21、Android SDK build-tools37.0.0/platforms android-37.0。换机器时需调整各脚本的工具链路径。原签名私钥不入库；要覆盖当前已装模块，须恢复原 `mod\watchmod-key.jks`。发布副本的构建脚本从环境变量 `QQWATCH_SIGN_PASSWORD` 读取签名密码；构建前在本地安全输入，不能把密码写入仓库。

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
| v1.22逻辑/装机 | 206项JVM、10个Android生产协议与实际导出、隔离逐核7个有效样本；原签名/实际APK哈希一致，真实QQ子进程与Handler入口/栈观察通过 |
| v1.24 GIF专项 | 295项JVM、23个Android生产协议/实际导出、原生渲染通过；实际QQ9.2.85接口只读核对，新模块装机/哈希一致；GIF停止实验使用隔离夹具 |
| v1.25架构整理 | 319项JVM、26个Android协议/配置/运行容器案例通过；21个关键源文件逐字节不变；原签名装机/实际APK哈希一致，QQ新运行容器和精确采集有效、用户配置保留 |
| v1.26线程池归属/QQ9.2.25核对 | 344项JVM、28个Android案例通过；真实QQ9.2.25 APK接口反射匹配，33个关键源文件不变；本机实际执行归属、原签名APK哈希、采集和配置保留确认 |
| v1.27单线程持续规则 | 397项JVM、37个Android案例通过；真实Future结束与GIF接口夹具回执、双规则去重/任务切换拒绝；3组设置渲染，原签名装机/哈希/配置确认。负载及业务观察为隔离模拟窗口，未制造QQ故障 |
| v1.31 UI与双规则 | 533项JVM、102组原生UI/8089检查、12个Android控制案例及实际配置/Downloads导出通过 |
| v1.32五版适配 | 568项JVM、原37个Android案例、12组双规则与导出、新Job平台Future取消后实际返回且池继续工作、五原包真实类加载核对通过；原签名安装与配置保留确认 |
| v1.33采集器 | 1194项JVM、Android有界读取/实时缓存与固定核心采集通过；相同隔离负载旧→新→旧比较，精确采样无丢事件 |
| v1.34图标/安装 | 构建、APK版本/签名/资源检查、图标离线渲染检查通过；原签名装机/哈希一致、26个设置原文保留、3连续有效采样；采集优化测试沿用v1.33证据 |
| 待验证 | 故障机真实异常任务、跨QQ子进程业务停止、长时间功耗、QQ内完整四页点击流程 |

离屏图片使用演示数据，不能当作故障机已经修复的证据。

## 源码目录

- `mod/`：生产模块、依赖库、资源、测试与手工构建脚本。
- `tools/mk_module_icon.py`：CPU芯片/负载曲线图标生成器（Pillow），五档PNG、原生adaptive/monochrome矢量及SVG预览。
- `docs-html/`：当前单文件说明及历史文档。当前口径以 README 和 v1.34版本说明为准；历史验证按原版本标明。
- `AGENTS.md`：开发交接与历史验证记录。
- 早期独立版仅保留于历史备份；当前发布源码聚焦 `mod/`。原始QQ安装包、DEX与真实手机日志不纳入发布。

QQ 增强互通保留 `View` tag `0x7f0f0001` / `0x7f0f0002`，值使用跨 ClassLoader 可识别的 `java.lang.Runnable`。增强模块存在时长按优先归增强。

签名私钥、访问令牌、真实设备日志和聊天数据不入库。仓库与云盘备份的可见性沿用已有设置。

## APK 校验

文件 **QQ负载监控.apk**，214,443 B，SHA-256：

```text
942FAD121AB2D78FBC27E90AA5F0BA629814B31D4241F6D35529E070855E5B06
```
