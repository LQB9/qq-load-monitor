package com.lqb9.qqwatchmod;

import java.io.*;
import java.lang.reflect.*;
import static com.lqb9.qqwatchmod.ModuleInfo.*;
import static com.lqb9.qqwatchmod.MonitorState.*;

/** Diagnostic text and export descriptions; no lifecycle or hook installation. */
final class MonitorReport {
    static String round1(double d) {
        return String.valueOf(Math.round(d * 10) / 10.0d);
    }
    static String settingsText(CpuLoadMonitor.Settings settings) {
        return "监控=" + (settings.enabled ? "开启" : "关闭") + " 阈值=" + settings.threshold
                + "% 核心=" + CoreSnapshot.selection(settings.coreMask) + " 超限持续=" + settings.durationSeconds
                + "秒 采样=" + settings.intervalSeconds + "秒 action=" + settings.action + " GIF专项="+(settings.gifRule?"开启":"关闭")
                + " " + ThreadRuleReport.totalSettings(settings) + " " + ThreadRuleReport.settings(settings);
    }
    private static String loadText(double cpu) { return cpu < 0 ? "不可用" : round1(cpu) + "%"; }
    static String snapshotText(LoadSnapshot data) {
        if (data == null) return "等待采样";
        StringBuilder text = new StringBuilder("elapsedMs=").append(data.sample.elapsedMs)
                .append(" QQ所选核心CPU=").append(loadText(data.sample.cpu)).append(" QQ全核心CPU=").append(loadText(data.processes.cpu))
                .append(" 选择核心=").append(CoreSnapshot.selection(data.sample.settings.coreMask)).append(" state=").append(data.sample.state)
                .append(" 超限持续Ms=").append(data.sample.highMs).append(" 进程数=").append(data.processes.processes.size())
                .append(" 扫描线程数=").append(data.threads.scanned).append(" 线程读取错误=").append(data.threads.errors)
                .append(" Java任务捕获次数=").append(TaskServices.tasks.observed())
                .append(" 线程池观察数=").append(TaskServices.executors.poolCount()).append(" 工作线程归属数=").append(TaskServices.executors.workerCount())
                .append(" note=").append(data.processes.note);
        text.append(' ').append(ThreadRuleReport.totalSettings(data.sample.settings));
        text.append(' ').append(ThreadRuleReport.settings(data.sample.settings));
        text.append(" 单线程检测=").append(data.single.state).append(" 单线程超限数=").append(data.single.high.size())
                .append(" 单线程达时长数=").append(data.single.readyCount());
        int highCount=0;
        for(ThreadLoadMonitor.Candidate c:data.single.high){if(++highCount>8)break;
            text.append(" | 单线程 pid=").append(c.hot.thread.pid).append(" tid=").append(c.hot.thread.tid)
                    .append(" pidStart=").append(c.hot.thread.pidStart).append(" tidStart=").append(c.hot.thread.tidStart).append(" name=").append(c.hot.thread.name)
                    .append(" selectedCpu=").append(round1(c.hot.cpu)).append("% highMs=").append(c.highMs)
                    .append(" requiredMs=").append(data.sample.settings.threadDuration*1000L).append(" ready=").append(c.ready);}
        if(data.single.high.size()>8)text.append(" | 单线程详情截断：显示8/").append(data.single.high.size()).append("个超限线程");
        if (data.core != null) {
            text.append(" 核心采集=").append(data.core.note).append(" QQ前后台=")
                    .append(data.core.snapshot == null ? -1 : data.core.snapshot.foreground);
            for (int core=0;core<8;core++) text.append(" | CPU").append(core).append(" QQ占用=").append(loadText(data.core.cores[core]));
            if (data.core.snapshot != null && data.core.snapshot.diagnostics != null) {
                CoreSamplingDiagnostics.Report diagnostic = data.core.snapshot.diagnostics;
                text.append(" | 核心采集诊断 state=").append(diagnostic.state).append(" reason=").append(diagnostic.reason)
                        .append(' ').append(diagnostic.details);
            }
        }
        for (QqCpuTracker.Detail process : data.processes.processes)
            text.append(" | 进程 pid=").append(process.reading.pid).append(' ').append(process.reading.name)
                    .append(" CPU=").append(loadText(process.cpu));
        for (ThreadCpuTracker.Detail thread : data.threads.threads)
            text.append(" | 线程 pid=").append(thread.reading.pid).append(" tid=").append(thread.reading.tid)
                    .append(' ').append(thread.reading.name).append(" CPU=").append(loadText(thread.cpu))
                    .append(" 最后核心=").append(thread.reading.lastCore);
        for (CoreFrequency.Core core : data.frequencies)
            text.append(" | 核心").append(core.id).append(" 频率=")
                    .append(!core.online ? "离线" : core.khz <= 0 ? "不可读" : core.khz / 1000 + "MHz")
                    .append(core.hardwareReading ? "(硬件)" : "(驱动请求)");
        return text.toString();
    }
    static String exportHeader() {
        LoadSnapshot data = latest;
        long ageMs = data == null ? -1 : Math.max(0, android.os.SystemClock.elapsedRealtime() - data.sample.elapsedMs);
        return "QQ负载监控日志 v" + VERSION + "\n导出时间：" + new java.text.SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss Z", java.util.Locale.ROOT).format(new java.util.Date())
                + "\n设备：" + android.os.Build.MANUFACTURER + "/" + android.os.Build.MODEL
                + " Android " + android.os.Build.VERSION.RELEASE + " SDK " + android.os.Build.VERSION.SDK_INT
                + "\n当前进程：" + selfName + " PID=" + android.os.Process.myPid()
                + "\n诊断环境：" + TaskBridge.diagnosticEnvironment + " hooks=" + TaskDiagnostics.hookState
                + "\n设置：" + settingsText(WatchSettings.read()) + "\n当前样本年龄Ms：" + ageMs
                + "\n当前快照：" + snapshotText(data)
                + "\n\n口径：单核100%；汇总QQ同UID进程。最后核心不是完整驻留，频率不是核心占用率。"
                + "\n日志约每5秒记录一次快照（不快于采样），事件即时入队；最多4份×512KiB，重启保留。"
                + "\nCORE_* 为采集异常/恢复证据：保留采集时刻、具体原因、进程/调度CPU时间及差值、逐核丢事件计数。状态变化独立于5秒快照，持续异常每5秒补充。"
                + "\nCPU校验先完成线程扫描，再以前后进程CPU读数夹取同一份调度计数；procCpuNs/procCpuLowerNs为保守下限，procCpuUpperNs为上限，cpuReadBracketNs为读取区间。超过原校验上限仍暂停处理。"
                + "\n采集器暂存最近64个事件，QQ延迟读取可补记；缓存轮换以CORE_GAP标明。CORE_RECOVER表示采集恢复，不表示任务已处理。旧采集器须停止后重新启动才能提供新证据。"
                + "\nDIAG 为失败诊断：目标负载、任务类型、窗口归属、可取得的 Java 栈；处理状态表不导出。原生用户栈未采集。"
                + "\n线程身份由目标进程复核：IDENTITY_READ_FAILED仅表示读取失败，THREAD_EXITED才表示确认退出；IDENTITY为执行查询的身份失败证据。"
                + "\nEXEC 为执行入口诊断：任务实例、业务类/方法、调度类型/周期、执行次数/CPU耗时和入口栈；最近10秒最多3个任务实例。"
                + "\nEXEC 的CPU是全部核心参考，仅统计完整结束的执行，不参与所选核心触发；嵌套入口CPU去重，未覆盖/截断会标明。"
                + "\nHandler消息仅记录handler类、messageWhat和回调入口，不读取obj/data内容；routeObserved区分消息分发与实际业务入口，缺少专用取消接口时不强停消息线程。"
                + "\nGIF_RULE为GIF对象专项暂停/恢复的实际回执；GIF_CHECK按触发来源复采：合计来源参考QQ合计，单线程来源参考目标线程所选核心负载。需要窗口内已观测RenderTask业务CPU及匹配的GifDrawable公开接口；工作线程保留，处理页可恢复。"
                + "\nTOTAL_LIMIT为合计持续超限记录；合计与单线程都有独立检测和自动处理开关。关闭检测不计时不触发，关闭自动处理只记录和提示；另一规则仍独立运行。处理依据仅来自开启自动处理的达标来源。"
                + "\nTHREAD_LIMIT为单线程所选核心持续超限：每个PID/TID启动身份独立计时，与合计共用采样。默认开启检测、80%/10秒可设，仅记录；自动处理需另启用，并在目标进程连续确认同一任务或GIF对象。两规则合并去重，采样无效/回落/配置或身份变化重新计时。"
                + "\n仅记录监控信息，不记录聊天内容。以下历史按记录顺序排列，超出上限的旧记录已轮换。\n\n";
    }
}

