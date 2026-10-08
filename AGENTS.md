# AGENTS.md — qq-watchdog（QQ负载监控 · 自用）

> 给接手的人/AI：**本文件是唯一入口，读完就能继续**。
> 沟通用**中文、短句、先结论**；用户**打不开 .md**，给他看的文档出单文件 HTML 放工作区（**别放桌面**）。

## 0. 当前源码与安装：v1.34 应用图标重绘（2026-10-07，已按用户要求安装）

- 用户要求与QQ增强采用各自功能图形和配色。负载监控用深色CPU芯片、青绿引脚与黄绿负载曲线；增强用紫色聊天气泡和金色星光，两套轮廓与颜色独立。
- Manifest为1.34/vc35，增加roundIcon；ModuleInfo仅VERSION由1.33改1.34，其余原生产Java逐字节不变。新增背景/前景/monochrome原生矢量、v26/v33 adaptive资源，五档PNG更新；tools/mk_module_icon.py改为可重生成上述资源和SVG预览。应用内UI、采集器和负载处理实现未变。
- 六阶段构建通过；APK mod/build/qqwatchmod.apk及QQ负载监控.apk，214443字节，SHA-256 942fad121ab2d78fbc27e90aa5f0ba629814b31d4241f6d35529e070855e5b06，原签名与本轮前1.33一致。badging核对包名/1.34/35、5档PNG像素及原生adaptive/monochrome资源通过。圈形及32/48/64px预览由矢量离线渲染并目视检查，真机桌面尚未验证。
- 未连接/安装手机或重启QQ，本机安装仍1.33/vc34；未改设置、未发布GitHub/Drive。不得把以下1.33实机采样或测试称为1.34重新实测。
- 用户交付D:/ChatGPT/2026-10-07/ban/outputs/QQ负载监控-v1.34-新图标.apk及共用APK图标预览.html/png；mod/build有版本副本，docs-html/QQ负载监控-v1.34-应用图标重绘.html有镜像。基线/证据D:/ChatGPT/2026-10-07/ban/work/icon-redesign/monitor与verification.json，不含签名密钥。

- 用户随后要求“你安装我看下”，21:48 +0800已原签名安装1.34/vc35与增强1.86/vc186。两个手机base.apk哈希与本轮输出包相同，26个当前设置逐字节保留。QQ27372→31910，精确采集器27429→31923；3连续有效核心采样seq7→12，watchdog.1.log轮转文件确认v1.34 SESSION。未新增业务压力或处理效果测试。
- 已删除远端临时安装APK和截图文件；装机证据D:/ChatGPT/2026-10-07/ban/work/icon-redesign/installation。用户打开的模块说明页显示v1.34，未操作设置；未制作桌面图标现场截图。以下构建阶段“未安装”句为前一回合历史，以本安装补充为准。

- 2026-10-08按用户要求发布当前v1.34（包含1.33采集器优化），目标GitHub load-monitor-v1.34和原云盘备份；打包/回读证据位于D:/ChatGPT/2026-09-30/xu/work/v1.34-publish。发布完成以publication-result.json为准；本次不修改生产代码或手机设置。

## 历史：v1.33 采集器优化（2026-10-07，安装基线）

- 用户发现app_process PID18713为本模块CoreCollector，要求降低占用。本版新增ProcStat按需解析、CollectorFileReader线程独立可复用有界缓冲、ProcStatReader受FD软上限约束的pread缓存（逐秒读新值，扫描末清退，失败全量读回退）、RuntimeStream直接字节流解析。CoreCollector对非数字/proc项不再抛例外；按批更新时间仅用于lastSeen/诊断年龄，runtime及capture单调时间仍为实际值。
- 每秒发现/CPU前后括读/capture/丢事件检查/原子发布，2秒前后台查询、UID筛选、会话generation、8核心统计、双规则时长和真实任务处理协议保持；QqCpuTracker/ThreadCpuTracker只替换parse实现，新增非负与溢出检查。5个原Java改变、4新增；其余原生产Java逐字节相同，UI未改。core-test/ui-test/test显式源码依赖同步，PS1 ASCII。
- 1194JVM（568原+626解析/有界读取/并发/字节分片）通过；Android原生读容量/UTF8/FD平衡、缓存名称与CPU实时刷新/容量/线程退出/sweep通过。Thread.join须等待/proc实际退出再断言。3固定核心取得7有效样本，排除CPU2最小94.2%，最高总284.1%；byte reader用于实际生产采集器，loss=0。
- 同一隔离测试进程600休眠worker、619线程、约1400事件/秒，旧→新→旧4×3秒各轮：9.61%/6.02%/14.98%，旧均12.29%，下降51.0%；旧两轮有波动，不泛化固定降幅。QQ安装前新采集器约830跟踪线程、1053事件/秒，平均9.08%（7.37—10.28%），6有效且无丢事件；与原动态场景不视为严格配对。故障后台47.9℃未复现，热因尚未确认。
- 已原签名安装1.33/vc34，197638B，SHA256 73CF377CE4084B2FDB93619E99F7E83AA1E85C234A14DD44C77931881851D02C。QQ重新加载v1.33，原watchdog.load全文保持；安装后3个连续有效核心快照推进、QQ有效SAMPLE回读。安装后最新3窗口平均6.41%（5.82—7.19%），约461线程/615事件每秒，全有效无丢事件；不与升级前不同线程规模计算直接降幅。当前QQ9.3.70/16410，本版未新增其Job专用适配。故障任务真实停止/长时温升仍待观察。
- outputs/QQ负载监控.apk、QQ负载监控-v1.33.apk、QQ负载监控-v1.33-采集器优化说明.html与SHA256SUMS；docs-html镜像。备份work/qq-watchdog-before-v1.33（生产源码+原1.32 APK，不含原QQ包/钥匙）；证据work/v1.33-collector-evidence、v1.33-core-device、v1.33-jvm-result.txt。collector_perf_v133/switch_collector_v133/bench_collector_v133/check_v133_core/install_collector_v133/finish_collector_v133脚本均在私有工作区。仅本地交付，本轮未发布GitHub/云盘。

## 历史：v1.32 五版QQ任务适配（2026-10-03）

- 用户要求手机下载目录5个新QQ包做负载规则。私有work/v1.32-qq-references：9.3.35/15560、9.3.50/15730、9.3.55/15900、9.3.60/16070、9.3.65/16240，全部QQ/arm64、签名一致、手机/PC SHA一致；不替换现用QQ9.2.85、不上传原包/DEX/私人日志。
- QqTaskAdapter精确包/版名/版号配置（所有QQ进程attach，不只主进程），实际Job直继WeakReference、Runnable、唯一mJob、继承平台Reference.get、run签名、qfix字段验证；profile读取可用弱引用仅诊断，active必须强字段。非空redirector保守拒绝，不调用hasPatch/redirect等业务回调。
- 五版静态Job.run/checkShouldRun和GIF公共API/渲染路径核对相同；真实Android child-first五原包GifHostAdapter/JobContract核验，不初始化QQ/JNI。RenderTask仍在，最初辅助类部分输出不能误称核心类被混淆改名。
- 平台FutureTask.run/runAndReset真实入口beginFuture仅当现活动准确Job.mJob就是本Future、同worker且run归平台时建独立Entry.parent；退出恢复外层，旧inner end不能误结束outer。控制前再校验Job字段/patch，保持原平台cancel/done验证，不停池/worker。内层startNs真实时刻，原ThreadTaskAttribution以新Entry重计，不继承外层窗口/时长。TaskExecutions.Info解QQ Job并保留note；普通Job无停止接口仍拒绝，热补丁/自定义run/done不冒险。
- 568JVM（533+35），原37Android及QQ925真实原包反射/原生设置render回归、12双规则与实际Downloads导出通过。新Job真实平台Future经模拟hook后端调用生产拦截：覆盖窗口拒绝、patch拒绝、取消/真正返回、同pool下一任务通过。五原包真实类加载通过；测试负载/GIF为隔离夹具，故障真实停止/降载待验证。未改UI，不重复102UI全套。
- 62原生产Java逐字节不变，5改（ModuleInfo/TaskBridge/TaskExecutions/TaskObservationHooks/TaskRegistry）+QqTaskAdapter新增；TaskBridge HandlingDecision起后缀相同。采集/双阈值时长/策略/配置/UI/原GIF与Future方法保持；内层身份生命周期有意变。生产无QQ/测试类，PS1 ASCII，HookGateway唯一hook。
- 原签名已装1.32/vc33，APK193542B SHA256 A1E6B35779D45C7B7B9B7B46E0A432C90D20791A4732AA0DA16CFFEAA007E8A3; QQ新SESSION+采集恢复，升级当时原设置全文保留（285/3/2/255、单线程80/10/auto、totalRule/totalHandle已显式true）；原前台阅读器保留。本版发布目标：GitHub load-monitor-v1.32 与原云盘备份；发布回读校验见工作区 work/v1.32-publish。
- 交付outputs/QQ负载监控.apk、版本APK、QQ负载监控-v1.32-五版QQ任务适配.html/SHA256SUMS，docs-html镜像。备份work/qq-watchdog-before-v1.32含v1.31勿覆盖。inspect_five_qq.py、scan_five_qq.py、scan_qq_wrappers.py、verify_five_contracts.py为原包只读分析；update_v132_build.py对构建配置已幂等但文档脚本一次性。build_v132.py、run_v132_android_qa.py、audit_v132.py、install_v132.py、prepare_v132_delivery.py/verify_v132_final.py；证据v1.32-qq-references/contracts、jvm-result/tests、bridge-tests、final-evidence。

## 历史：v1.31 独立规则开关与单线程趋势（2026-10-02）

- 用户指出合计缺检测/自动处理开关，并要求继续、概览/设置合计统一命名且和单线程标题等长，单线程另一统计须图表。标题“总负载持续超限规则”“单线程持续超限规则”均9字；监控范围保持独立。LoadRuleSettingsView共享两规则开关/草稿/保存/禁用与无障碍，ThreadRuleSettingsView轻量继承；MonitorSettingsView保存两套模式，关闭检测时自动处理未生效但偏好保留。
- CpuLoadMonitor.Settings新增totalRule/totalHandle，旧文件缺字段默认true/true，旧构造兼容；encode/decode参与sameAs，<=256B实测。合计检测关仍保留CPU参考，但不计时/提示/处理；自动处理关保留检测仅记录。DualLoadPolicy分检测来源与actionSources权限，record只在首达标记录；TaskBridge仅记录无需停止/回执，不伪造超时失败。总记录+Bauto须B业务连续证据，不因TOTAL检测来源绕过；目标复核两flags/缺字段拒绝，CANCEL/RESULT升级V6，队列中关闭flags拒绝旧请求。原HandlingDecision起实际停止/结果/诊断后缀逐字节不变。
- ThreadLoadHistory按准确线程启动身份保留60窗口，每窗口最多64线程；缺失/异常断线，复用TID不继承，会话/配置变化清历史。MonitorEngine采样后发布不可变LoadSnapshot.threadHistory。ThreadTrendView/CpuCharts.ThreadTrend在概览/明细共用当前TOP3独立曲线和身份图例；原独立连续超限进度保留，规则关/采样无效隐藏旧图。基础QQ逐核%和MHz合并卡保持。
- MonitorReport/ThreadRuleReport和表格捕获两模式，TOTAL_LIMIT合计事件含身份/窗口/核心/时间/模式/检测与处理来源，THREAD_LIMIT同步新模式，原下载导出路径/清单不导出保持。MainActivity说明同步。47原生产Java不变，17更新/3新增，采集/身份/线程持续监测/业务归属/实际GIF及Future停止实现保持；策略和协议有意更新，不称所有后端未改。
- 533JVM（444+89），102原生UI/8089检查（360/300/260dp与1/1.3/1.6字体）通过，关键PNG目视。12Android生产协议（原record/auto/AB3 +合计模式/业务复核/双关闭/排队flag变更9）通过；4种实际AtomicFile保存/迁移以及实际Downloads字段导出通过，测试包/导出清理；负载/观察为隔离模拟，Future取消和返回是真，GUI未操作。故障机真正停止/降载仍待自然复现。
- 原签名已装v1.31/vc32，APK 193542B SHA256 34AA866F0BB5F70813B5987411400BEB6D92D9C80BB87153B1F4F7070F88FE55；QQ新SESSION+采集恢复，原配置文件完整保持：cpu=285 / duration=3 / interval=2 / cores=255 / action=stop_task / gifRule=true / threadRule=true / threadCpu=80 / threadDuration=10 / threadHandle=true。旧文件无total字段按兼容开启/自动处理，本机单线程80/10自动处理已保存保持。未发布GitHub/云盘。
- 交付outputs/QQ负载监控.apk、版本APK、QQ负载监控-v1.31-独立规则开关与单线程趋势.html/SHA256SUMS，docs-html镜像。备份work/qq-watchdog-before-v1.31含v1.30勿覆盖。update_v131.py一次性；build_v131.py、run_v131_ui_qa.py、run_v131_rule_export_qa.py（-e mode rule-export无需QQ原包）、audit_v131.py、install_v131.py、prepare_v131_delivery.py一次性、verify_v131_final.py。证据v1.31-jvm-result/tests、ui-tests/102PNG、bridge-tests/实际导出、final-evidence/源码/安装/签名/实时。生产无测试/QQ原包，PS1 ASCII。

## 历史：v1.30 监控布局与共用图表（2026-10-02）

- 用户要求范围独立于合计规则、单线程规则独立，精简概览/明细并共用图表。MonitorSettingsView管理草稿，独立总监控/监控范围（CPU0—7+共用采样）/合计阈值时长/ThreadRuleSettingsView/共用GIF处理。WatchPanel统一save，原配置字段/范围/即时总开关保持；外部总开关更新不覆盖草稿。
- DashboardView缩至三卡：合计大数字/趋势与连续超限时长；ThreadRuleStatusView TOP3紧凑行；CoreMetrics将QQ逐核%条与MHz读数按核心合并（宽屏小字体双列，窄屏/大字体单列，频率不参与阈值）。重复TOP4及独立频率图移除。单线程无效时不显示旧进度或说明图例；主/保护线程排除保持。
- MonitoringDetailView复用同一ThreadRuleStatusView组件TOP12完整行和PID/TID；全核心进程/热点排行、完整线程、最近8事件集中折叠诊断，展开可见；原日志按钮保留，处理表/恢复GIF不改。57个原生产Java逐字节不变，只有DashboardView/CpuCharts/ThreadRuleStatusView/WatchPanel/ModuleInfo五个变，新增两个布局组件；后台判定/计时/控制/日志/导出字节相同。
- 444JVM、84原生UI/4847状态/无障碍/布局检查通过（360/300/260dp与1/1.3/1.6字体）；完整设置分区/草稿保存、共享组件、诊断展开验证，关键PNG目视。离屏仅模拟负载，测试包已卸载，无GUI操作；不重复执行无改动的桥接/导出测试。
- 原签名已装v1.30/vc31，APK 189446B SHA256 FCF48157DBF5842E7C9DDC5DB186D3216E51A5A6A644F59E44C20B5FF6EE858F；QQ新SESSION+采集恢复，保存文件原文保持：cpu=285 / duration=3 / interval=2 / cores=255 / action=stop_task / gifRule=true / threadRule=true / threadCpu=80 / threadDuration=10 / threadHandle=true。本机单线程自动处理已开启，升级保留，未保存新配置才默认仅记录；故障机实际停止/降载仍待验证，未发布GitHub/云盘。
- 交付outputs/QQ负载监控.apk、版本APK、QQ负载监控-v1.30-监控布局与共用图表.html/SHA256SUMS，docs-html镜像。备份work/qq-watchdog-before-v1.30含v1.29勿覆盖。update_v130_ui.py一次性勿重跑；build_v130.py、run_v130_ui_qa.py、audit_v130.py、install_v130.py、prepare_v130_delivery.py（一次性）、verify_v130_final.py。证据v1.30-jvm-result/tests、ui-tests/84PNG、final-evidence/源码/安装/签名/实时。生产无测试类/QQ原包，PS1 ASCII。

## 历史：v1.29 统一开关与单线程日志表格（2026-10-02）

- 用户要求开启类按钮统一为v1.28样式，问日志/表格是否补单线程。共用StateSwitchView固定Canvas轨道+Checkable文字/颜色/无障碍，ThreadRuleSettingsView移除内嵌RuleToggle；总监控、GIF、单线程检测/自动处理及MainActivity精确采集统一。总监控即时生效，其他配置统一保存；保存/导出/恢复/跳转仍按钮，核心多选。说明页更新双规则。
- CollectorSwitchView注入Control/代际回调/操作防重复，onResume查询及5秒刷新/onPause停刷新。RootControl.status只读su stat设备+inode和/proc/locks，完整0—EOF POSIX写锁认定运行；结尾标志/256KiB/5秒保护，错误未知，不乐观启动/停止。原start/stop不变，未改CoreCollector。
- ThreadRuleReport共享触发时元数据；SAMPLE每次补单线程配置/模式、准确启动身份/name/highMs/requiredMs/ready，最多8条明确截断；THREAD_LIMIT补模式、共享采样、合计设置及累计、窗口/session。TaskBridge仅历史参数和事件文本变，Intent请求起协议/接收/处理字节相同，前缀资格/去重也相同。表格不可变参数保留两套累计/要求/模式，清单不导出。
- 444JVM（421+23状态/元数据）、66原生UI/2779检查（39+统一开关6+采集状态18+表格3），360/300/260dp及1/1.3/1.6字体已目视。实际RootControl状态probe确认当前运行锁；3Android生产协议record/auto/AB和真实Downloads导出字段核验通过，Future取消/返回是真，负载/观察是隔离夹具；测试包/导出/临时APK清理。50原源文件不变，8更新+4新增。
- 原签名已装v1.29/vc30，APK 185350B SHA256 FF5B62465580CEB95BEE3F946C3187EF7BAF07C255FF64A0830465790BBE1985；实际QQ新SESSION、采集恢复及原285/3/2/255文本保留。单线程本机已保存80/10/自动处理开启，安装前后原文保持（默认新配置才为仅记录）；用户GUI未操作，故障机停止/降载待验证，未发布GitHub/云盘。
- 交付outputs/QQ负载监控.apk、版本APK、QQ负载监控-v1.29-统一开关与单线程日志表格.html/SHA256SUMS，docs-html镜像。备份work/qq-watchdog-before-v1.29（v1.28勿覆盖）；证据v1.29-jvm-result/tests、ui-tests/66PNG/真实状态、bridge-tests/实际隔离导出、final-evidence/源码/安装/签名/实时。backup_v129.py/extract_v129_switch.py一次性勿重跑；build_v129.py、run_v129_ui_qa.py、run_v129_rule_export_qa.py（bridge -e mode rule-export，不推QQ原包）、install_v129.py、audit_v129.py、prepare_v129_delivery.py、verify_v129_final.py。生产仅src，测试类/QQ原包不进APK。

## 历史：v1.28 单线程图表与开关（2026-10-02）

- 用户反馈单线程在概览/明细体现不足、开启关闭不明确，已补实际原生UI。DashboardView合计折线下挂ThreadRuleStatusView前3，WatchPanel明细首卡前12；所选核心CPU条/阈值红线、PID/TID、连续超限进度、已保存阈值/时长/核心/统一采样和超限/达时长数量。达到时长仅代表检测条件，明确仅记录/待处理复核，实际结果仍见处理页。不是将不同线程的最高CPU混成趋势。
- 新纯Java ThreadRuleDisplay解释当前已保存Settings和同配置的不可变Core/单线程结果；总监控关/规则关/无效/过期/前后台未知/等待新配置采样/基线，分别显示且不放旧进度。候选以完整启动身份+当前CPU匹配，不把复用TID时长套新线程；主/保护/无效CPU排除。
- ThreadRuleSettingsView固定尺寸Canvas轨道的Checkable TextView开关，避免QQ主题；文字+颜色+滑块及accessibility switch/checked/stateDescription。关闭检测时自动处理显示未生效、灰色关闭不可操作，保留内部模式重新启用时恢复；已保存和未保存修改明确，统一保存，外部总开关变化提示暂停。原核心选择/合计/计时/控制逻辑未改。
- 52个后端相关原源码与v1.27逐字节相同，仅DashboardView/WatchPanel/ThreadRuleSettingsView/ModuleInfo四文件变更，两新显示文件；manifest1.28/vc29。421JVM（397+24显示状态边界），39组实际Android离屏/1788控件与布局检查；360/300/260dp配1/1.3/1.6字体，record/auto/off/draft、旧配置/总暂停/过期/无效/前后台未知及完整概览；关键图已目视。夹具CPU为模拟，仅UI控件是真，未制造QQ异常。测试APK隔离卸载。
- 已原签名装v1.28/vc29，APK 181254B SHA256 6D4F951B3376D5ECD57E8E3E60B9BCC1EEBBF3627837208A08BE6C630DB473AF，实际设备APK一致、QQ重载+CORE_READY、原285/3/2/255配置原文保持；按安装重载当下前台状态恢复QQ，未操作用户GUI。新单线程默认80/10/仅记录保持，故障机停止/降载仍待验证。未发布GitHub/云盘。
- 交付outputs/QQ负载监控.apk、版本APK、QQ负载监控-v1.28-单线程图表与开关说明.html及SHA256SUMS；docs-html镜像。备份work/qq-watchdog-before-v1.28（v1.27勿覆盖）。证据v1.28-jvm-result/tests、ui-tests/39PNG/verification、final-evidence/安装/签名/源码对比/实时；脚本backup_v128.py（一次性）、build_v128.py、run_v128_ui_qa.py、install_v128.py、audit_v128.py、prepare_v128_delivery.py、verify_v128_final.py。ui-test.ps1支持-e mode thread-rule只跑新UI；生产只编译src，源码测试/QQ原包未进生产。

## 历史：v1.27 单线程持续超限规则（2026-10-02）

- 用户确认按推荐开发：合计＋可选单线程规则、共用CPU0—7选择/统一采样，不做逐核八套配置。默认开启单线程检测、80%/10秒可设、仅记录；threadHandle用户单独开启，旧cpu/duration/interval/cores/action配置保留。新字段threadRule/threadCpu/threadDuration/threadHandle，256B持久化容量检查通过。没有强杀或整池停止。
- ThreadLoadMonitor按PID/pidStart/TID/tidStart计时，使用同一CoreTracker selected时间窗口；回落/缺失/无效/前后台未知/间断/session/配置/身份变化重置，有界1024。DualLoadPolicy OR合并同目标来源，最多16高负载目标/轮，共用pending＋10秒冷却；合计条件已达不等待单线程更长时长。默认记录首轮达时长后一次THREAD_LIMIT，后续SAMPLE续记。
- TaskBridge V5附带完整新配置、sources、两套时长/CPU、report/perform/observeThread；旧V4接收端不会接受。单线程处理开启时每轮高负载向目标进程发送不处理的业务观察；ThreadTaskAttribution弱引用有界512，以同一活动Entry或同一GifDrawable在连续有效窗口的确切证据累计，缺窗口/换任务/换对象/改配置重新开始。GIF每窗口要求原dominant与所选贡献证据，只接单一owner；普通Entry需覆盖本轮窗口。只有线程负载和业务计时均达标才单线程处理；前一任务计时不套新业务，执行前expectedTask/expectedGifOwner复核。合计动作保持原窗口规则。
- ProcessingHistory新增不可变rule/ruleParameters并在状态/诊断/证据更新保留；StatusTable显示合计/单线程/合计＋单线程及两套设置。WatchPanel独立ThreadRuleSettingsView，统一保存；明细显示数量/模式，热点榜标全核心参考。THREAD_LIMIT/SAMPLE/SETTINGS进入原日志，清单不导出；B-only GIF复采目标线程所选负载。原生GIF锁等待问题未改，未知Handler/原生接口仍不支持。
- 397JVM（344+53），37Android（28+8仅记录/独立取消/前后台/切换/双源去重/设置变更/同GIF/换GIF＋UI），实际Downloads导出通过；负载窗口与观察事件为隔离夹具，真实Future执行/取消/返回、GIF模拟接口回执、Android控件为真，不在QQ制造故障。UI离屏360dp/1x、300dp/1.3x、260dp/1.6x已目视无重叠。26关键采集/身份/业务/存储文件逐字节与v1.26相同；HookGateway唯一框架调用，PS1 ASCII，生产无测试或QQ类。
- 已装2026-10-02T21:06:29 v1.27/vc28，QQ PID 11201，CORE_READY pid=11182；原签名与设备APK哈希一致，原285%/合计3秒/间隔2秒/cores255原文保留，新默认80/10/仅记录在真实日志确认。安装期间用户当前应用保留，不继续操作GUI。APK 177,158B SHA256 AECD9347EB261AF3B4D19581227C236AE63C951190817BF1F6DF91F4FB51CE85。故障机真实停止与降载仍待验证。
- 交付outputs/QQ负载监控.apk、版本APK、QQ负载监控-v1.27-单线程持续超限规则.html/SHA256SUMS；docs-html镜像。备份work/qq-watchdog-before-v1.27（不要覆盖）；证据v1.27-jvm-result/tests、bridge-tests/3PNG、final-evidence/dual-rule-audit/安装/签名/实时。update_v127_bridge.py/add_v127_android_tests.py/prepare_v127_tests.py为一次性不要盲重跑；build_v127.py/run_v127_bridge_qa.py/install_v127.py/audit_v127.py/prepare_v127_delivery.py/verify_v127_final.py。未重发GitHub/云盘；QQ925原包保持只读不上传。

## 历史：v1.26 线程池归属与QQ9.2.25核对（2026-10-02）

- 用户问如何推进每类异常任务停止，授权推进，确认故障QQ9.2.25/11820并给路径QQ.apl.1；实际找到D:\deepseek\qq-watchdog\QQ.apk.1，396489596B SHA256 697AA3600822E9994EA2836A2E85F806209FA7E87D4B47A9207E943154FA1E02。只读核对，不替换本机QQ9.2.85，不上传原包/DEX/设备日志/密钥。未发布GitHub/云盘。
- 新ExecutorRegistry按对象身份弱引用有界（池128/提交512/worker1024），避免业务equals/hashCode/toString。成功execute/schedule仅submission-observed；beforeExecute绑定真实executor/当前Thread/TID/实际Runnable，afterExecute结束准确binding。准确执行与最后完成分开；TID复用不套旧Thread，stale end不结束新任务；不关闭池/中断worker。
- TaskServices组合同一registry，TaskExecutions Scope/Event保存当时池String，嵌套继承，混合/不完整不合并推定；MonitorReport SAMPLE加池/worker数量，原EXEC与处理表展开加executor证据，原日志路径/导出沿用。没有新增独立单线程阈值，原仅QQ、核心合计、阈值/时长/前后台/GIF/Future保持，33关键源文件逐字节与v1.25一致。
- 实际QQ925静态DEX：SafeRunnable.d唯一GifDrawable，run委托RenderTask.e→GifInfoHandle.w→native renderFrame，再schedule自身；GifDrawable.stop取消当前Future/移除消息，再经GifInfoHandle锁调用saveRemainder，可能等原生渲染返回。现有公开start/stop/isRunning/recycle反射接口与该版匹配，Android实际原APK child-first加载验证通过（不初始化QQ对象/JNI）。不能声称真实故障机GIF已停止，两个无Java映射的业务仍未知。
- 344JVM（原319+25归属）/28Android（原26+实际pool执行生命周期+真实QQ925类加载）和原Downloads导出通过。Hook后端及GIF运行是隔离夹具；实际Java pool/worker为真，不实际Hook故障QQ。生产无测试类、QQ类或原APK，PS1 ASCII，只有HookGateway调用框架hook。
- 已装2026-10-02T20:25:18 v1.26/vc27 QQ PID 17292，CORE_READY pid=17352，原签名/实际APK哈希一致，配置原文保留（285%/duration3/interval2/cores255）；安装只按重载时QQ是否前台恢复，不继续操作GUI。本机QQ实际观察55个池和execution-confirmed ThreadNormalPool/ScheduledThreadPoolExecutor任务；故障机真停止/降载仍待验证。APK 168,966B SHA256 EA312DB57BD26259674C477F2105BBE4B3624736FD44CF3AE2F2D3BD287C5EB7。
- 交付outputs/QQ负载监控.apk、版本APK、QQ负载监控-v1.26-线程池归属与QQ9.2.25核对.html/SHA256SUMS，docs-html镜像。备份work/qq-watchdog-before-v1.26；证据v1.26-jvm-result/tests、bridge-tests、qq-9.2.25-reference、final-evidence（pool-attribution-audit/安装/签名/实时）。脚本inspect_qq925.py/summarize_qq925_api.py、build_v126.py/run_v126_bridge_qa.py/install_v126.py/audit_v126.py/prepare_v126_delivery.py/verify_v126_final.py；backup_v126.py不要覆盖重跑。下一步用户只需故障机装新模块后自然复现、从原按钮导出日志。

## 历史：v1.25 模块架构整理（2026-10-02）

- 用户澄清是代码架构，授权“可以的/继续”。按五部分整理，保留既有仅QQ、所选核心合计、阈值/持续时长可设、前后台同规则、GIF专项/Future协作处理、原表格/日志；本轮未发布GitHub/云盘。
- WatchModule 1016→22行，仅QQ过滤/运行容器创建，构造与启动异常捕获。WatchRuntime统一启动，main才启动日志/面板/单份MonitorEngine；QQ子进程只装任务观察/控制。HookGateway按Method去重，只有真实句柄到手才登记；关闭释放，失败句柄保留可重试。启动失败清理采样/面板/控制维护循环；暂停GIF租约存在时延后释放保护Hook，关闭监控不卸载诊断。
- SettingsCodec/SettingsStore/WatchSettings隔离解析与原子持久化，保留256B/旧interval/开关/白名单清理和保存失败EVENT。QqLoadSampler/MonitorEngine/LoadSnapshot与MonitorState/MonitorReport拆开；Engine注入面板可见/提示回调。LoadPolicy纯Java资格/目标/原因。所有WatchModule静态消费者移除。
- TaskServices组合旧tasks/executions/history/gif，TaskRuleRegistry不可变顺序GIF→Future；HandlingRequest/Decision明确输入/结果，专项拒绝不回退取消其他任务。TaskObservationHooks/GifHookAdapter/GifHostAdapter独立观察/QQ接口/保护Hook；TaskBridge V4与身份/UID/配置/时效/确认/复采语义保持，补充detach和maintenance身份检查防清理后复活。
- 319项JVM（旧295+24架构），26Android生产协议/实际Downloads（旧23+真实原子配置/运行容器与Hook所有权/控制清理重绑）通过。AndroidHook后端/GIF对象使用隔离夹具，未实际Hook QQ造故障。21关键采集/判定/证据/UI/日志/启动文件与v1.24字节一致；只HookGateway调用框架；规则无Android/Xposed依赖；PS1 ASCII。UI视觉沿用，不重复离屏渲染。
- 已装2026-10-02T18:30:43 v1.25/vc26，QQ PID 23401，CORE_READY pid=23416，原签名与已装APK哈希一致，用户配置原文保留。安装按重载时QQ是否前台恢复，不继续操作手机GUI。APK 164,870B SHA256 `4B076B7430A1EC0EA201DEF65FFF8ADE98671D487B5518D11A1E472F71FB0159`。故障机真实GIF停止/降载、跨进程业务停止、长期功耗仍待验证。
- 交付outputs/QQ负载监控.apk、版本APK、QQ负载监控-v1.25-模块架构与验证.html/SHA256SUMS，docs-html镜像；备份work/qq-watchdog-before-v1.25。证据v1.25-jvm-result/tests、bridge-tests、final-evidence/architecture-audit与安装/签名/实时核对。脚本refactor_v125.py/complete_v125.py只用于本次抽取不可盲目重跑；finish_v125_build.py/run_v125_bridge_qa.py/audit_v125_sources.py/install_v125.py/prepare_v125_delivery.py/verify_v125_final.py。

## 历史：v1.24 GIF对象专项处理测试版（2026-10-02）

- 用户分析13:24故障日志后问能否制定规则中断，授权“那你开发吧”。仅QQ、CPU0—7可选、阈值/持续时长可设、前后台同规则保持。使用RenderTask/GifDrawable业务停止，保留QQ进程/工作线程；不能声称任意线程强杀或泄露已解决。
- 新GifTaskRule反射按声明类型找到唯一GifDrawable所有者；严格公开start/stop/isRunning/recycle类型/归属校验，start/recycle保护hook就绪才使用。弱引用观察512、每次最多3个、每进程最多32暂停对象；stop前设租约，拦截同对象start/run，其他GIF不受影响。active=0及!isRunning才成功；5秒仍运行继续观察；手动、关闭或配置变化解除，不可见/已释放/仍在渲染不主动重播。负载下降不自动恢复。
- TaskExecutions按准确TID/JavaThread、完整本轮窗口统计实际RenderTask.run业务CPU，扣嵌套，仅最多3个弱引用业务对象；至少占该线程全核CPU50%，扣除全部未选核心CPU后的所选贡献保守下限仍需达到该线程所选负载50%才专项；不足明确GIF_SELECTED_ATTRIBUTION_INSUFFICIENT。CoreTracker.Detail.allCpu只作归属参考，阈值仍仅所选核心。TaskBridge V4复核GIF开关；允许短任务已完成后的同owner暂停，不取消换成其他业务的Future。新增恢复广播/GIF_RULE/GIF_CHECK，复采窗口必须在暂停确认后完整起止，旧/跨动作窗口排除；原Future取消保持。
- WatchPanel设置GIF开关默认true（保存后写gifRule），处理页恢复按钮；StatusTable颜色明确待确认/仍运行/暂停/解除/失败。持续时长0—3600保留。生产build仅src，测试夹具不入APK；build.ps1清理先验证绝对mod/build路径；PS1 ASCII。
- 295JVM（新增69GIF）、23Android生产协议/下载实际导出（原13+10GIF，含旧/混合复采拒绝）通过，协议及业务证据为隔离UID/GIF接口夹具手工模拟hook；没有制造QQ解码故障。7概览/3核心选择及300dp/1.3字体表格原生渲染通过。只读核对本机QQ9.2.85(v13860)真实RenderTask/SafeRunnable/GifDrawableDEX公开停止路径，未知故障QQ9.2.25接口运行时不匹配拒绝。
- 已装2026-10-02T15:08:56 v1.24/vc25，QQ PID 27940，CORE_READY pid=27948，原签名/已装APK哈希一致；用户当前285%/cores255/duration3/interval2保留。按安装时前台状态重载QQ，用户期间自行切换应用，未继续操作手机UI。APK 160,774B SHA256 `152770FCDA25A9F9775C30BF708C889A0F211371D8D8A9A0B2F6B3C477418404`。故障机实际GIF停止/降载及长期功耗仍待验证。
- 交付outputs/QQ负载监控.apk、版本APK、QQ负载监控-v1.24-GIF专项处理与验证.html/SHA256SUMS，docs-html镜像；备份work/qq-watchdog-before-v1.24；证据v1.24-jvm-result/tests、bridge-tests、ui-check、gif-reference（真实QQAPK/DEX只读不提交）、final-evidence。脚本run_v124_bridge_qa.py/check_v124_ui.py/install_v124.py/prepare_v124_delivery.py。GitHub/云盘本轮未重发，不上传真实日志、私钥或QQAPK。

## 历史：v1.23 恢复超限持续时长（2026-10-02）

- 用户要求“需要加回来呀，自己设置超限多久”，覆盖之前固定0秒立即处理的决定。UI可设0—3600秒；0秒默认首轮有效超限尝试处理，前后台同阈值/时长；按有效采样窗口累积，回落/无效/长间断/关闭/设置变化重新计时。
- WatchPanel恢复duration输入/保存；WatchModule读取duration，不再固定0。CpuLoadMonitor.Sample.handlingReady区分一次提示与持续处理资格：达到时长后后续有效超限仍可重试。TaskBridge.consider接收同一Sample，核对配置和CPU，不到时长不请求；目标复核duration/highMs。CANCEL/RESULT升级V3防旧接收端忽略新增条件，EXEC查询不变。
- DashboardView超限时追加累计/设置时长；日志配置和处理原因保留时长。226项JVM（原206+20），13个Android生产协议/实际导出（原10+前后台时长2项+排队改设置拒绝1项），原生渲染检查通过。已装QQ GUI输入5、保存、关闭重开读回5，SETTINGS日志确认为5；恢复0及原配置。GUI验证脚本兼容ADB CR输出，读取两份滚动日志避免5秒证据已轮换导致误报。
- 本机v1.23/vc24已装 2026-10-02T01:03:37，QQ PID 16617，CORE_READY pid=16660，原签名/已装APK哈希一致；最终200%/cores255/duration0/interval1保留。APK 156,678B SHA256 `6040545376D13DFEFC10800E39D827855D3A5198F6EE9CAE6709F6509FF7F5C2`。
- 交付outputs/QQ负载监控.apk、版本APK、QQ负载监控-v1.23-超限持续时长说明.html/SHA256SUMS，docs-html镜像；备份work/qq-watchdog-before-v1.23，证据v1.23-jvm-tests及jvm-result、bridge-tests（实际测试APK哈希确认V3）、ui-check、final-evidence；脚本check_v123_settings.py/finish_v123_settings.py/install_v123.py/prepare_v123_delivery.py。故障机任务无专用停止接口的限制保持；GitHub/云盘本轮未重发。

## 历史：v1.22 身份复核、Handler诊断、采样对齐（2026-10-02）

- 用户23:06导出有3条失败：MSF目标被错误当作退出，root确认PID/TID启动身份未变；HRContextQueueHandlerThread事后等待、无活动任务。23:04 CPU650ms/trace406.58ms无loss，相邻窗口相反偏差，推断读取时序失配。用户授权“那你继续做完”。原日志不必另加导出功能。
- TaskBridge.localIdentity仅目标PID读/proc/self及自身task/stat，读失败与ENOENT/ESRCH退出、PID/TID身份变化分开。主consider/logExecutions不提前读子进程；target验证uid与身份，reply/query回执nonce+PID/TID启动身份及时效。缺身份不套用EXEC；失败明确IDENTITY/DIAG，不冒充已退出。
- WatchModule观察工作线程Handler.dispatchMessage，缓存mCallback字段；仅类名/what/路线，不读obj/data。动态挂实际run/handleMessage（含Callback返回false后的Handler fallback）；沿用256方法上限。TaskExecutions.DispatchTask弱引用、有界历史、嵌套CPU扣除；活动消息无接口或已返回分别HANDLER_NO_CANCEL_INTERFACE/HANDLER_TASK_NOT_ACTIVE。标准FutureTask协作取消原策略保留；不通用interrupt Handler/强停线程。
- CoreTimeWindow在discover后前后读进程CPU夹取同一份不可变调度计数，procCpuNs/Lower为保守下限、Upper为上限；cpuReadBracketNs/tracePointElapsedMs/aligned=bracketed补证据。下限仍超过trace×1.25+80ms则暂停；loss/回退/generation/预热保持。阈值仍只用所选核心真实调度计数，不增加等待/平滑。无法保证消除全部异步trace延迟。
- 验证206项JVM（原183+14窗口+9Handler），10个Android生产协议案例（原6+3Handler+1真实同UID第二进程）与实际Downloads导出，隔离CPU0/1/2采集7个有效样本，未选CPU2排除至少96.8%。fixtures手工模拟hook事件，实际QQ另确认Handler.dispatchMessage=ready、MSF回传和observed:run/handleMessage/入口栈，不能当作故障机任务已停止。
- 已装 2026-10-02 00:00:03 vc23/v1.22；QQ PID 26992，CORE_READY pid=27060；配置cpu=200/cores=255/interval=1/action=stop_task保留，QQ前台恢复。原签名48F60554...、已装APK哈希一致；APK 156,678B SHA256 `4D024A3F07547AC08C58203F6A0F9C4E57F2BD5D0881259BF9B45D9B3744BCA1`。
- 交付outputs/QQ负载监控.apk、版本APK、QQ负载监控-v1.22-修复与验证说明.html与SHA256SUMS；备份work/qq-watchdog-before-v1.22；证据v1.22-jvm-tests/v1.22-bridge-tests/v1.22-core-device/v1.22-final-evidence。install_v122.py初次安装成功后host Base64无padding解析失败，finish_v122_install.py仅修复验证（未重复安装/重启）；最终receipt与live-verification记录完整。临时测试包/手机临时APK已清理。GitHub/云盘本轮未重发。

## 历史：v1.21 采集异常/恢复日志（2026-10-01）

- 用户问截图v1.18“调度事件不完整，暂停处理”的原因，授权补日志。原基本采样继续；完整性失败可能为丢事件、进程/trace时间不一致或计数回退，截图不能区分。
- CoreCollector记录既有完整性判定的具体分支；CPU/trace/上限/差值(ns)、逐核3类丢事件和增量、window/scan/lastEventAge、回退身份。原25%+80ms判定、256KiB每核缓冲、预热、generation与处理策略保持。
- CoreSamplingDiagnostics纯Java有界保留64事件/32KiB正文；状态/原因变化当轮，连续异常5秒补充。QQCORE3追加D/E证据，CoreSnapshot仍读QQCORE2。Forwarder在SAMPLE节流前补记原始wall时间；同消费会话去重，跨QQ重启可能重放，collector/event可识别；轮换明确CORE_GAP。QQ冻结仍会延迟写入。
- WatchLog.recordAt复用异步队列与既有滚动/导出；CORE_STATE记录读取端有效性变化，SAMPLE/当前快照含当轮诊断。原处理清单不导出，无聊天内容。升级需停止再启动精确采集器；本机已自动完成。
- 验证183项JVM（原157+26），14项Android生产日志/下载检查（原11+3），实际隔离CPU0/1/2采集7个有效样本/证据。模拟短暂异常用于验证链路，不冒充真实故障。测试包和临时APK已清理；v1.20表格布局保持，不重复渲染/任务控制实验。
- 已装 2026-10-01T22:56:32，QQ PID 22349，CORE_READY pid=22395；vc22/v1.21，实际包哈希和原签名一致，设置/QQ前台保留。故障机19:48原因仍待新日志。APK 152,582B SHA256 `D56DE41BFC78831BF40817A43FFBCF2943AF88EDE73733E6EBDCF524F9E6C102`。
- 交付outputs/QQ负载监控.apk、版本APK、QQ负载监控-v1.21-采集异常日志说明.html，3文件SHA256校验。备份work/qq-watchdog-before-v1.21/；证据v1.21-jvm-tests、v1.21-device-log-check、v1.21-core-device、v1.21-final-evidence；脚本check_v121_sampling_log.py/install_v121.py/prepare_v121_delivery.py。GitHub/云盘未在本轮重发。

## 历史：v1.20 处理表层次优化（2026-10-01）

- 用户「稍微优化下，表格每一行记录不够层次分明」。仅改StatusTableView和版本1.20/vc21；监控、任务控制与日志沿用v1.19，无需重跑业务控制实验。
- 每记录独立边框/14dp间距；时间/真实状态色标在顶部；完整线程名加粗，PID/TID弱化；QQ合计粉色、线程自身蓝色大号负载；前后台/核心/阈值、原因、最近执行全核参考分区。展开证据在同一记录内，身份/DIAG/EXEC分段，栈逐行。保留100条会话清单不导出。
- 复用既有原生RenderCheck，7组概览/表格和3组核心选择渲染；300dp与1.3放大字体目视无重叠/截字。模拟数据截图明确标注，未在真实异常记录上实测。测试包和临时APK已清理。
- 已装 2026-10-01T22:20:53：QQ PID 31311，CORE_READY pid=31394，前台恢复，cpu=200/cores=255/interval=1/action=stop_task保留。原签名通过，安装包SHA256一致。
- APK 148,486 B，SHA256 `3AE9814017840F41AD88549A03B3F162598CC44B0525A9705454FABB3751F275`；交付outputs/QQ负载监控.apk、版本命名APK、QQ负载监控-v1.20-表格层次优化.html与预览PNG；SHA256SUMS四个当前交付文件校验。备份work/qq-watchdog-before-v1.20/；证据v1.20-ui-check和v1.20-final-evidence；脚本install_v120.py、check_v120_ui.py、prepare_v120_delivery.py。安装脚本expected version已修为1.20，第一次旧版本检查错误后已恢复采集并完成安装。GitHub/云盘未在本轮重发。

## 历史：v1.19 执行诊断与表格（2026-10-01）

- 用户授权「你补吧」，最新「继续，同时更新下模块内表格的显示，现在信息不全」。故障日志19:12 v1.18重复pool-45四线程，事后栈在ScheduledExecutor等待且NO_ACTIVE_TASK；需要执行时证据。当前没有扩展取消策略，不宣称故障任务已停止。
- TaskExecutions.java：执行入口/真实run或call，标准FutureTask/RunnableAdapter解包，公开调度接口记录周期；弱引用任务身份、512实例/4096事件/1024线程，最近10秒；保护已知定时实例避免一次性任务挤掉。CPU与次数区分包装/业务，包装CPU嵌套去重，全核心参考且不参与核心阈值。完整落入窗口才计窗口CPU，不分摊未结束/跨界。栈每进程3秒、每实例60秒，限频/未覆盖/截断明确。动态方法256上限。不要推断无记录即无异常。
- WatchModule.java：既有FutureTask+TPE取消注册原样；额外ScheduledFutureTask.run执行观察、动态业务run/call、TPE.execute/前置任务准备、4个公开schedule接口记录参数。入口状态与原导出说明已补。仅主进程日志；每快照查询3个热点工作线程、每线程最近CPU最多3个任务。已安装真实QQ观察到了business方法、CPU/次数、固定调度和执行栈。
- TaskBridge.java：同UID NOT_EXPORTED执行诊断query/reply，nonce、PID/TID启动身份、8秒时效、有界16请求和16控制队列。诊断查询不触发处理行；既有取消回执分离DIAG与EXEC有界extras，明确未确认状态。处理结果绑定当时阈值/核心；ProcessingHistory不可变更新保留完整名、执行摘要/正文和失败证据。
- StatusTableView默认显示完整映射名或内核名、PID/TID、QQ合计/自身、原阈值/核心/前后台、具体原因与最近全核执行摘要。展开两类诊断，最近100条会话清单仍不导出。原生TextView自然换行；时间和5字结果合理分行。300dp放大字体检查通过。当前QQ内真实有异常的表格尚未实测；演示PNG为隔离渲染数据。
- 验证157项JVM（旧119+38执行/表格）；6个Android生产协议案例+实际Downloads导出；7组概览/表格+3组核心选择。实际重复定时任务回到等待仍可回传执行证据，未知任务不受影响、忽略取消不算成功。临时测试包/导出/临时APK已清理。
- 装机 2026-10-01T21:57:22：vc20/v1.19，QQ PID 18206，CORE_READY pid=18260，QQ前台恢复；cpu=200/cores=255/interval=1/action=stop_task保留。原签名校验通过，实际安装SHA256一致。
- APK 144,390 B，SHA256 `546CF7DB2A77542722F7D69D4CDAAC5E0D37B4876E3958B8E744B62198269BCB`；交付outputs/QQ负载监控.apk、版本命名APK与QQ负载监控-v1.19-执行诊断与表格说明.html。备份work/qq-watchdog-before-v1.19/；证据work/v1.19-jvm-tests、v1.19-bridge-tests、v1.19-ui-check、v1.19-final-evidence；工作脚本prepare_v119_delivery.py/install_v119.py。当前GitHub/云盘仍为之前发布版，未在本轮重发。

## 0.1. v1.18 失败诊断（2026-10-01）

- 用户提供故障机日志 `QQWatchdog_20261001_173914_785_cbaf3eb0.txt` 与处理表截图，反馈没有停止成功，随后问如何处理、是否需要补日志。确认测试阈值90%、CPU0–7；处理失败源于缺少覆盖窗口的活动任务或任务开始晚于窗口。原导出没有这些目标细节，不能判定未知目标就是原生线程。
- v1.18/vc19 补到现有监控日志的 DIAG：PID/TID、QQ合计与目标自身负载、逐核贡献、前后台/阈值/核心、失败代码、任务类/捕获入口/generation/窗口时间差、准确映射的 Java 线程状态与最多24层栈。处理状态表仍只在内存，不导出清单。未读取聊天正文、任务对象内容或原生用户栈。
- `TaskDiagnostics.java`：1024个有界弱引用 JavaThread/TID 映射，Thread.run/Looper.loop 生命周期 hook 和既有任务 begin 提供来源；绝不以同名匹配TID，生命周期映射不进入取消任务注册。每目标身份60秒、每进程3秒栈采集限频，4096字符上限，诊断仅走控制线程；没有映射或空栈明确未知。
- `TaskBridge.java`：失败证据由同UID目标回复，主进程核对待处理请求与PID后写入已有WatchLog。取消策略沿用v1.17，保留整个窗口的归属保护，未新增普通Runnable/原生线程强停。表头明确「QQ合计」。
- 验证：119项 JVM（旧101+18诊断）；隔离 UID 的5个实际生产协议案例与 Downloads 实际导出检查通过，临时文件/测试包已清理。原签名验证通过，实际安装包SHA256一致。
- 已安装至当前本机 Xiaomi25102RKBEC，时间2026-10-01 18:53:53，QQ PID21460，采集器CORE_READY PID21474；载入Task/Thread.run/Looper.loop hook，QQ版本9.2.85/vc13860。设置 cpu=200/cores=255/interval=1/action=stop_task 保留。安装及重载期间酷安保持前台。
- 故障机日志设备为 Xiaomi2407FRK8EC，与本机不同；本轮未解决故障机任务停止。新版需在故障机复现后导出既有日志，再决定业务专用停止适配或是否还需原生栈。
- APK132,102 B，SHA256 `AABA77390F29F3F6EFAF3D660A448D5E63AF70D360025E81DE95030B36E4447E`。交付 `outputs/QQ负载监控.apk`、`outputs/QQ负载监控-v1.18-诊断说明.html`（docs-html镜像）；备份 `work/qq-watchdog-before-v1.18/`，证据 `work/v1.18-jvm-tests/`、`work/v1.18-bridge-tests/`、`work/v1.18-final-evidence/`。GitHub/云盘现有v1.17未在本轮重新发布。

## 0.0. v1.17 核心选择与图标修复（2026-10-01）

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
