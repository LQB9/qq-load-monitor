package com.lqb9.qqwatchmod;

import android.app.Application;
import android.content.*;
import android.os.*;
import java.io.File;
import java.util.*;
import java.util.concurrent.*;

/** QQ UID-only control and confirmation; no native kill signal. */
final class TaskBridge {
    static final TaskRegistry tasks = TaskServices.tasks;
    static final TaskExecutions executions = TaskServices.executions;
    static final ProcessingHistory history = TaskServices.history;
    static final GifTaskRule gif = TaskServices.gif;
    private static final ThreadTaskAttribution singleAttribution=new ThreadTaskAttribution();
    // Required duration evidence must not be accepted by an older receiver that ignores it.
    private static final String CANCEL = "com.lqb9.qqwatchmod.CANCEL_TASK_V6", RESULT = "com.lqb9.qqwatchmod.TASK_RESULT_V6";
    private static final String GIF_RESTORE = "com.lqb9.qqwatchmod.GIF_RESTORE_V1";
    private static final String EXEC_QUERY = "com.lqb9.qqwatchmod.EXEC_QUERY_V1", EXEC_REPLY = "com.lqb9.qqwatchmod.EXEC_REPLY_V1";
    private static final Map<String, ExecutionQuery> executionPending = new LinkedHashMap<String, ExecutionQuery>();
    private static final class ExecutionQuery {
        final CoreSnapshot.Counter target; final long sent;
        ExecutionQuery(CoreSnapshot.Counter target) { this.target = target; sent = SystemClock.elapsedRealtime(); }
    }
    private static volatile Application app;
    private static BroadcastReceiver receiver;
    private static volatile Runnable maintenance;
    static volatile String diagnosticEnvironment = "QQ 版本等待应用初始化";
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static final Map<String, Long> attempted = new LinkedHashMap<String, Long>();
    private static final Map<String, Long> pending = new LinkedHashMap<String, Long>();
    private static final Map<String,Integer> gifRows = new LinkedHashMap<String,Integer>();
    private static final Map<String,Long> gifVerify = new LinkedHashMap<String,Long>();
    private static final Map<String,Long> gifConfirmedNs = new LinkedHashMap<String,Long>();
    private static final Set<String> gifLoadChecked = new HashSet<String>();
    private static final ExecutorService controls = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<Runnable>(16), r -> { Thread t = new Thread(r, "qqwatch-control"); t.setDaemon(true); return t; });
    static synchronized void attach(Application application) {
        if (app != null) return;
        app = application;
        try {
                android.content.pm.PackageInfo info = application.getPackageManager().getPackageInfo(application.getPackageName(), 0);
                long versionCode = Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
                String adapter=QqTaskAdapter.configure(application.getPackageName(),info.versionName,versionCode);
                diagnosticEnvironment = "QQ=" + info.versionName + " versionCode=" + versionCode + " SDK=" + Build.VERSION.SDK_INT+" "+adapter;
                if(MonitorState.isMain)WatchLog.record("INFO", "诊断环境 " + diagnosticEnvironment + " hooks=" + TaskDiagnostics.hookState);
            } catch (Exception unavailable) {
                QqTaskAdapter.configure("","unknown",0);
                if(MonitorState.isMain)WatchLog.record("INFO", "QQ 版本读取失败:" + unavailable.getClass().getSimpleName());
            }
        if (Build.VERSION.SDK_INT < 33) return;
        IntentFilter filter = new IntentFilter(); filter.addAction(CANCEL); filter.addAction(RESULT);
        filter.addAction(EXEC_QUERY); filter.addAction(EXEC_REPLY); filter.addAction(GIF_RESTORE);
        receiver=new BroadcastReceiver() {
            public void onReceive(Context c, Intent i) {
                if (GIF_RESTORE.equals(i.getAction()) && i.getIntExtra("uid",-1)==android.os.Process.myUid()) {
                    try { controls.execute(() -> restoreGif(true)); } catch(RejectedExecutionException busy) {}
                } else if (EXEC_REPLY.equals(i.getAction())) {
                    if (MonitorState.isMain) acceptExecution(i);
                } else if (EXEC_QUERY.equals(i.getAction()) && i.getIntExtra("pid", 0) == android.os.Process.myPid()) {
                    try { controls.execute(() -> { try { executionReply(i); } catch (Throwable ignored) {} }); }
                    catch (RejectedExecutionException busy) {}
                } else if (RESULT.equals(i.getAction())) {
                    if (MonitorState.isMain) acceptResult(i);
                } else if (CANCEL.equals(i.getAction()) && i.getIntExtra("pid", 0) == android.os.Process.myPid()) {
                    try { controls.execute(() -> {
                        try { handle(i); } catch (Throwable failed) { reply(i.getStringExtra("id"), "处理失败", failed.getClass().getSimpleName()); }
                    }); } catch (RejectedExecutionException busy) { reply(i.getStringExtra("id"), "未处理", "控制队列已满"); }
                }
            }
        };
        application.registerReceiver(receiver,filter,Context.RECEIVER_NOT_EXPORTED);
        maintenance=new Runnable() {
            public void run() {
                if(app==null || maintenance!=this)return;
                try {controls.execute(() -> {restoreGif(false);verifyGif();});} catch(RejectedExecutionException busy) {}
                if(app!=null && maintenance==this)main.postDelayed(this,1000);
            }
        };
        main.postDelayed(maintenance,1000);
    }
    static synchronized void detach() {
        if(maintenance!=null)main.removeCallbacks(maintenance);
        Application context=app;
        if(context!=null && receiver!=null)try {context.unregisterReceiver(receiver);}catch(IllegalArgumentException ignored){}
        receiver=null;maintenance=null;app=null;
    }
    static void restoreGifManually() {
        Application context=app; if(context==null || Build.VERSION.SDK_INT<33)return;
        context.sendBroadcast(new Intent(GIF_RESTORE).setPackage(context.getPackageName()).putExtra("uid",android.os.Process.myUid()));
        WatchLog.record("GIF_RULE","手动请求恢复已暂停GIF；等待目标进程实际回执");
    }
    private static void restoreGif(boolean manual) {
        for(GifTaskRule.Result result:gif.restore(WatchSettings.read(),manual)) {
            synchronized(gifVerify) {gifVerify.remove(result.id);}
            replyGif(result,null);
        }
    }
    private static void verifyGif() {
        Map<String,Long> waiting; synchronized(gifVerify){waiting=new LinkedHashMap<String,Long>(gifVerify);}
        for(Map.Entry<String,Long> entry:waiting.entrySet()) {
            GifTaskRule.Result result=gif.status(entry.getKey());
            if(result.state.equals("GIF待确认") && SystemClock.elapsedRealtime()<entry.getValue())continue;
            if(result.state.equals("GIF待确认")) {
                synchronized(gifVerify){gifVerify.put(entry.getKey(),Long.MAX_VALUE);}
                result=new GifTaskRule.Result(result.id,"GIF仍在运行",result.detail+" 5秒内渲染未返回，未记为停止成功。");
            } else synchronized(gifVerify){gifVerify.remove(entry.getKey());}
            replyGif(result,null);
        }
    }
    static final class IdentityResult {
        final String code, detail;
        IdentityResult(String code, String detail) { this.code = code; this.detail = detail; }
        boolean valid() { return "OK".equals(code); }
    }
    static IdentityResult localIdentity(CoreSnapshot.Counter t) {
        if (t.pid != android.os.Process.myPid())
            return new IdentityResult("TARGET_PROCESS_MISMATCH", "线程身份必须由目标进程自身复核");
        String threadPath = "/proc/self/task/" + t.tid;
        try {
            android.system.Os.stat(threadPath);
            QqCpuTracker.Reading p = QqCpuTracker.Reading.parse(t.pid, "QQ", CoreCollector.read(new File("/proc/self/stat")));
            ThreadCpuTracker.Reading thread = ThreadCpuTracker.Reading.parse(t.pid, t.tid, "QQ",
                    CoreCollector.read(new File(threadPath + "/stat")));
            if (p.startTicks != t.pidStart) return new IdentityResult("PROCESS_IDENTITY_CHANGED",
                    "进程启动身份变化 expected=" + t.pidStart + " actual=" + p.startTicks);
            if (thread.startTicks != t.tidStart) return new IdentityResult("THREAD_IDENTITY_CHANGED",
                    "线程启动身份变化 expected=" + t.tidStart + " actual=" + thread.startTicks);
            return new IdentityResult("OK", "目标进程已复核PID/TID启动身份");
        } catch (Exception failure) {
            try { android.system.Os.stat(threadPath); }
            catch (android.system.ErrnoException status) {
                if (status.errno == android.system.OsConstants.ENOENT || status.errno == android.system.OsConstants.ESRCH)
                    return new IdentityResult("THREAD_EXITED", "目标进程确认线程已退出");
            }
            return new IdentityResult("IDENTITY_READ_FAILED", "线程身份读取失败，未确认退出："
                    + TaskExecutions.limit(failure.getClass().getSimpleName() + ": " + failure.getMessage(), 500));
        }
    }
    static boolean identity(CoreSnapshot.Counter t) { return localIdentity(t).valid(); }
    static synchronized void consider(CoreTracker.Result data,CpuLoadMonitor.Settings settings,CpuLoadMonitor.Sample sample) {
        consider(data,settings,sample,ThreadLoadMonitor.Result.EMPTY);
    }
    static synchronized void consider(CoreTracker.Result data,CpuLoadMonitor.Settings settings,CpuLoadMonitor.Sample sample,ThreadLoadMonitor.Result single) {
        long now = SystemClock.elapsedRealtime();
        Iterator<Map.Entry<String, Long>> expired = pending.entrySet().iterator();
        while (expired.hasNext()) {
            Map.Entry<String, Long> request = expired.next();
            if (now - request.getValue() > 8000) {
                ProcessingHistory.Row row = history.find(request.getKey());
                if (row != null) {
                    history.update(row.id, "结果未知", "未收到目标进程的停止确认；未记为成功");
                    WatchLog.record("DIAG", "code=CONTROL_TIMEOUT pid=" + row.pid + " tid=" + row.tid
                            + " name=" + row.name + " 原因=8秒内未收到目标控制通道确认，未采集目标调用栈");
                }
                expired.remove();
            }
        }
        if(settings.enabled && settings.gifRule && data!=null && data.cpu>=0 && data.snapshot!=null
                && data.snapshot.valid && data.snapshot.uid==android.os.Process.myUid()
                && data.snapshot.elapsedMs<=now && now-data.snapshot.elapsedMs<=Math.max(3000L,settings.intervalSeconds*3000L)) {
            for(String id:gifRows.keySet()) {
                ProcessingHistory.Row row=history.find(id);
                Long confirmed=gifConfirmedNs.get(id);
                // A window spanning the action still includes pre-pause CPU; wait for a complete post-confirmation window.
                if(row==null || confirmed==null || data.windowStartNs<confirmed || data.snapshot.monoNs<=data.windowStartNs
                        || gifLoadChecked.contains(id) || !row.state.equals("GIF已暂停") || row.mask!=settings.coreMask)continue;
                gifLoadChecked.add(id);
                double targetCpu=0;for(CoreTracker.Detail candidate:data.threads)
                    if(candidate.thread.pid==row.pid && candidate.thread.pidStart==row.pidStart
                            && candidate.thread.tid==row.tid && candidate.thread.tidStart==row.tidStart){targetCpu=candidate.cpu;break;}
                boolean singleOnly=row.rule.equals("单线程");double value=singleOnly?targetCpu:data.cpu;
                int limit=singleOnly?settings.threadThreshold:row.threshold;
                String check=(singleOnly?"复采目标线程所选核心=":"复采QQ合计=")+String.format(Locale.ROOT,"%.1f",value)
                        +"% · "+(value<=limit?"已回落至对应阈值内":"仍超限，可能还有其他热点任务");
                history.update(id,row.state,row.detail+"\n"+check);
                WatchLog.record("GIF_CHECK","id="+id+" pid="+row.pid+" "+check+"；整体回落不单独证明因果");
            }
        }
        for(DualLoadPolicy.Target target:DualLoadPolicy.targets(data,settings,sample,single,android.os.Process.myUid()))
            dispatch(data,settings,sample,target,now);
    }
    private static void dispatch(CoreTracker.Result data,CpuLoadMonitor.Settings settings,CpuLoadMonitor.Sample sample,DualLoadPolicy.Target target,long now) {
        CoreTracker.Detail hot=target.hot;CoreSnapshot.Counter t=hot.thread;
        boolean perform=target.action(settings),report=target.report(settings);
        if(!report && (!settings.threadHandle || target.single==null))return;
        // Pool/task observations continue during cooldown, without sending a second stop request.
        if(report && perform){
            boolean inFlight=false;
            for(String id:pending.keySet()){ProcessingHistory.Row row=history.find(id);if(row!=null && row.pid==t.pid && row.tid==t.tid
                    && row.pidStart==t.pidStart && row.tidStart==t.tidStart){inFlight=true;break;}}
            Long last=attempted.get(t.key());
            if(inFlight || last!=null && now-last<10000){report=false;perform=false;}
        }
        if(!report && (target.single==null || !settings.threadHandle))return;
        String id=UUID.randomUUID().toString();
        String reason=target.reason(data,settings,sample);
        String unavailable=LoadPolicy.unavailable(t,data.snapshot.foreground,app!=null && Build.VERSION.SDK_INT>=33);
        if(report){
            if(perform){attempted.put(t.key(),now);while(attempted.size()>200)attempted.remove(attempted.keySet().iterator().next());}
            String rules=(target.total?"合计":"")+(target.total && target.single!=null && target.single.ready?"＋":"")
                    +(target.single!=null && target.single.ready?"单线程":"");
            String thresholds=ThreadRuleReport.parameters(settings,sample,target.single);
            history.add(new ProcessingHistory.Row(id,System.currentTimeMillis(),t,settings.coreMask,data.cpu,hot.cpu,
                    settings.threshold,reason,!perform?"仅记录":unavailable.isEmpty()?"等待处理":"未处理",!perform?rules+"持续超限已记录，未请求处理":unavailable,"","","")
                    .rule(rules,thresholds));
            if(target.total)WatchLog.record("TOTAL_LIMIT","pid="+t.pid+" tid="+t.tid+" pidStart="+t.pidStart+" tidStart="+t.tidStart+" name="+t.name
                    +" qqCpu="+data.cpu+" totalHighMs="+(sample==null?0:sample.highMs)+" totalThreshold="+settings.threshold+" totalDuration="+settings.durationSeconds
                    +" totalRule="+settings.totalRule+" totalHandle="+settings.totalHandle+" totalMode="+ThreadRuleReport.totalMode(settings)
                    +" sources="+rules+" action="+(perform?"复核处理":"仅记录")+" actionSources="+DualLoadPolicy.actionSources(target.sources(),settings)
                    +" selected="+CoreSnapshot.selection(settings.coreMask)+" foreground="+data.snapshot.foreground+" interval="+settings.intervalSeconds
                    +" windowStartNs="+data.windowStartNs+" sampleNs="+data.snapshot.monoNs+" session="+data.snapshot.session);
            if(target.single!=null && target.single.ready){
                String event="pid="+t.pid+" tid="+t.tid+" pidStart="+t.pidStart+" tidStart="+t.tidStart+" name="+t.name
                        +" selected="+CoreSnapshot.selection(settings.coreMask)+" threadCpu="+hot.cpu+"% qqCpu="+data.cpu
                        +"% threadThreshold="+settings.threadThreshold+"% highMs="+target.single.highMs+" duration="+settings.threadDuration
                        +" foreground="+data.snapshot.foreground+" action="+(perform?"复核处理":"仅记录")+" sources="+rules
                        +" threadRule="+settings.threadRule+" threadMode="+ThreadRuleReport.mode(settings)+" interval="+settings.intervalSeconds
                        +" totalHighMs="+(sample==null?0:sample.highMs)+" totalThreshold="+settings.threshold+" totalDuration="+settings.durationSeconds
                        +" totalRule="+settings.totalRule+" totalHandle="+settings.totalHandle+" totalMode="+ThreadRuleReport.totalMode(settings)+" actionSources="+DualLoadPolicy.actionSources(target.sources(),settings)
                        +" windowStartNs="+data.windowStartNs+" sampleNs="+data.snapshot.monoNs+" session="+data.snapshot.session;
                WatchLog.record("THREAD_LIMIT",event);MonitorState.push("单线程持续超限："+t.name+" "+MonitorReport.round1(hot.cpu)+"% · "+(perform?"复核处理":"仅记录"));
            }
            if(perform && !unavailable.isEmpty()){WatchLog.record("DIAG","code=CONTROL_UNAVAILABLE pid="+t.pid+" tid="+t.tid+" 原因="+unavailable);return;}
            if(perform)pending.put(id,now);
        }else if(!unavailable.isEmpty())return;
        if(!perform && (target.single==null || !settings.threadHandle || !unavailable.isEmpty()))return;
        Intent request=new Intent(CANCEL).setPackage(app.getPackageName());
        request.putExtra("id",id).putExtra("pid",t.pid).putExtra("pidStart",t.pidStart).putExtra("tid",t.tid)
                .putExtra("uid",android.os.Process.myUid()).putExtra("tidStart",t.tidStart).putExtra("name",t.name)
                .putExtra("window",data.windowStartNs).putExtra("sampleNs",data.snapshot.monoNs).putExtra("session",data.snapshot.session)
                .putExtra("mask",settings.coreMask).putExtra("threshold",settings.threshold).putExtra("duration",settings.durationSeconds)
                .putExtra("highMs",sample==null?0:sample.highMs).putExtra("gifRule",settings.gifRule)
                .putExtra("totalRule",settings.totalRule).putExtra("totalHandle",settings.totalHandle)
                .putExtra("threadRule",settings.threadRule).putExtra("threadHandle",settings.threadHandle)
                .putExtra("threadThreshold",settings.threadThreshold).putExtra("threadDuration",settings.threadDuration)
                .putExtra("threadHighMs",target.single==null?0:target.single.highMs)
                .putExtra("sources",target.sources()).putExtra("totalSource",target.total)
                .putExtra("observeThread",target.single!=null && settings.threadHandle).putExtra("report",report && perform).putExtra("perform",perform)
                .putExtra("threadAllCpu",hot.allCpu).putExtra("qqCpu",data.cpu).putExtra("threadCpu",hot.cpu)
                .putExtra("foreground",data.snapshot.foreground).putExtra("cores",data.cores);
        app.sendBroadcast(request);
    }
    private static void handle(Intent request) {
        String id = request.getStringExtra("id");
        if (id == null || id.length() > 80) return;
        CpuLoadMonitor.Settings settings = WatchSettings.read();
        if (!settings.enabled || settings.coreMask != request.getIntExtra("mask",0)
                || settings.durationSeconds!=request.getIntExtra("duration",-1) || settings.threshold!=request.getIntExtra("threshold",0)
                || !request.hasExtra("totalRule") || !request.hasExtra("totalHandle")
                || settings.totalRule!=request.getBooleanExtra("totalRule",false)
                || settings.totalHandle!=request.getBooleanExtra("totalHandle",false)
                || settings.gifRule!=request.getBooleanExtra("gifRule",false)
                || settings.threadRule!=request.getBooleanExtra("threadRule",false)
                || settings.threadHandle!=request.getBooleanExtra("threadHandle",false)
                || settings.threadThreshold!=request.getIntExtra("threadThreshold",-1)
                || settings.threadDuration!=request.getIntExtra("threadDuration",-1) || !"stop_task".equals(settings.action)) {
            reply(id,"未处理","配置已变化或监控已关闭");return;
        }
        boolean report=request.getBooleanExtra("report",false),perform=request.getBooleanExtra("perform",false);
        int sources=request.getIntExtra("sources",0);
        if(report && !DualLoadPolicy.qualifies(sources,settings,request.getDoubleExtra("qqCpu",-1),request.getLongExtra("highMs",-1),
                request.getDoubleExtra("threadCpu",-1),request.getLongExtra("threadHighMs",-1))){
            reply(id,"未处理","连续超限时长未达到设置要求或触发证据无效");return;
        }
        if(perform && (!report || DualLoadPolicy.actionSources(sources,settings)==0)){reply(id,"未处理","触发来源未开启自动处理");return;}
        long age = System.nanoTime() - request.getLongExtra("sampleNs", 0);
        if (age < 0 || age > 2500000000L) { reply(id, "未处理", "采样已过期"); return; }
        CoreSnapshot.Counter t = new CoreSnapshot.Counter(request.getIntExtra("pid", 0), request.getLongExtra("pidStart", 0),
                request.getIntExtra("tid", 0), request.getLongExtra("tidStart", 0), request.getStringExtra("name"), new long[8]);
        if (request.getIntExtra("uid", -1) != android.os.Process.myUid()) { reply(id, "未处理", "目标UID不匹配"); return; }
        if (t.tid == t.pid || LoadPolicy.protectedName(t.name)) { reply(id, "未处理", "主线程或系统线程受到保护"); return; }
        IdentityResult identity = localIdentity(t);
        if (!identity.valid()) { replyIdentity(request, identity); return; }
        if(request.getIntExtra("foreground",-1)<0) {reply(id,"未处理","前后台未确认，暂停处理");return;}
        ThreadTaskAttribution.Binding binding=null;long businessMs=0;
        if(request.getBooleanExtra("observeThread",false)) {
            double selected=request.getDoubleExtra("threadCpu",-1),all=request.getDoubleExtra("threadAllCpu",-1);
            if(!settings.threadRule || !settings.threadHandle || !Double.isFinite(selected) || selected<=settings.threadThreshold
                    || selected>110 || !Double.isFinite(all) || all<selected || all>110)return;
            binding=ThreadTaskAttribution.identify(tasks,executions,t.tid,request.getLongExtra("window",0),request.getLongExtra("sampleNs",0),selected,all,settings);
            businessMs=singleAttribution.observe(t.key(),request.getStringExtra("session"),binding,
                    request.getLongExtra("window",0),request.getLongExtra("sampleNs",0),settings);
        }
        if(!report)return;
        if(!perform){reply(id,"仅记录","连续超限已记录，自动处理未开启","",executionReport(request));return;}
        boolean threadOnly=(DualLoadPolicy.actionSources(sources,settings)&DualLoadPolicy.TOTAL)==0;
        if(threadOnly && (binding==null || binding.identity()==null || businessMs<settings.threadDuration*1000L)){
            String detail="单线程负载已达时长，但同一业务持续归属尚未达到要求："+(binding==null?"未建立归属":binding.detail)
                    +"；业务确认="+businessMs+"ms，要求="+settings.threadDuration*1000L+"ms";
            replyDiagnostic(request,"未处理",detail,"THREAD_ATTRIBUTION_INCOMPLETE",null);return;
        }
        HandlingDecision decision=TaskServices.rules.apply(new HandlingRequest(id,t.tid,
                request.getLongExtra("window",0),request.getLongExtra("sampleNs",0),
                request.getDoubleExtra("threadCpu",-1),request.getDoubleExtra("threadAllCpu",-1),settings,
                threadOnly?binding.task:null,threadOnly?binding.owner:null));
        if(decision.gif!=null) {
            if(decision.state.equals("GIF待确认"))synchronized(gifVerify){gifVerify.put(id,SystemClock.elapsedRealtime()+5000);}
            replyGif(decision.gif,executionReport(request));return;
        }
        if(!decision.code.isEmpty()) {
            replyDiagnostic(request,decision.state,decision.detail,decision.code,decision.task);return;
        }
        reply(id,decision.state,decision.detail,"",executionReport(request));
        main.postDelayed(() -> verify(request,t,decision.task,SystemClock.elapsedRealtime()+5000),100);
    }
    private static void verify(Intent request, CoreSnapshot.Counter t, TaskRegistry.Entry task, long deadline) {
        String id = request.getStringExtra("id");
        if (task.ended) { reply(id, "任务已结束", "已观察到原任务返回；线程池工作线程可继续保留"); return; }
        IdentityResult identity = localIdentity(t);
        if (!identity.valid()) {
            replyIdentity(request, identity); return;
        }
        if (SystemClock.elapsedRealtime() >= deadline) {
            // getStackTrace can suspend a target; never run it on the UI thread.
            try { controls.execute(() -> replyDiagnostic(request, "仍在运行", "任务没有响应取消或中断；未强制结束 QQ 线程/进程", "CANCEL_NO_RETURN", task)); }
            catch (RejectedExecutionException busy) { reply(id, "仍在运行", "任务未返回；诊断队列已满，未采集调用栈"); }
            return;
        }
        main.postDelayed(() -> verify(request, t, task, deadline), 200);
    }
    private static void replyIdentity(Intent request, IdentityResult identity) {
        String code = identity.code;
        String state = code.equals("IDENTITY_READ_FAILED") ? "身份未确认"
                : code.equals("THREAD_EXITED") ? "目标已退出" : "任务已变化";
        String evidence = "code=" + code + " pid=" + android.os.Process.myPid() + " tid=" + request.getIntExtra("tid",0)
                + " expectedPidStart=" + request.getLongExtra("pidStart",0) + " expectedTidStart=" + request.getLongExtra("tidStart",0)
                + " identityOwner=target-process QQ合计=" + request.getDoubleExtra("qqCpu",-1) + "% 目标线程="
                + request.getDoubleExtra("threadCpu",-1) + "% 原因=" + identity.detail;
        reply(request.getStringExtra("id"), state, identity.detail, TaskDiagnostics.limit(evidence));
    }
    private static void replyDiagnostic(Intent request, String state, String detail, String code, TaskRegistry.Entry task) {
        int tid = request.getIntExtra("tid", 0);
        String identity = tid + ":" + request.getLongExtra("tidStart", 0);
        boolean capture = tasks.diagnostics.allow(identity, SystemClock.elapsedRealtime());
        StringBuilder evidence = new StringBuilder("code=").append(code).append(" pid=").append(android.os.Process.myPid())
                .append(" tid=").append(tid).append(" name=").append(request.getStringExtra("name"))
                .append(" QQ合计=").append(request.getDoubleExtra("qqCpu", -1)).append("% 目标线程=")
                .append(request.getDoubleExtra("threadCpu", -1)).append("% threshold=").append(request.getIntExtra("threshold", 0))
                .append("% selected=").append(CoreSnapshot.selection(request.getIntExtra("mask", 0)))
                .append(" foreground=").append(request.getIntExtra("foreground", -1)).append(" 原因=").append(detail);
        double[] cores = request.getDoubleArrayExtra("cores");
        if (cores != null && cores.length == 8) for (int i = 0; i < 8; i++)
            evidence.append(" CPU").append(i).append('=').append(String.format(Locale.ROOT, "%.1f", cores[i])).append('%');
        try { evidence.append(" | ").append(tasks.diagnostics.describe(tid, task, request.getLongExtra("window", 0), request.getLongExtra("sampleNs", 0), capture)); }
        catch (Throwable failed) { evidence.append(" | 诊断读取失败:").append(failed.getClass().getSimpleName()); }
        reply(request.getStringExtra("id"), state, detail, TaskDiagnostics.limit(evidence.toString()), executionReport(request));
    }
    private static void reply(String id, String state, String detail) {
        reply(id, state, detail, "");
    }
    private static void reply(String id, String state, String detail, String diagnostic) {
        reply(id, state, detail, diagnostic, null);
    }
    private static void reply(String id, String state, String detail, String diagnostic, TaskExecutions.Report execution) {
        Application context = app; if (context == null) return;
        try {
            Intent result = new Intent(RESULT).setPackage(context.getPackageName())
                .putExtra("id", id).putExtra("pid", android.os.Process.myPid()).putExtra("state", state).putExtra("detail", detail)
                .putExtra("diagnostic", diagnostic);
            if (execution != null) addExecution(result, execution);
            context.sendBroadcast(result);
        }
        catch (Throwable unavailable) {}
    }
    private static void replyGif(GifTaskRule.Result result,TaskExecutions.Report execution) {
        Application context=app; if(context==null)return;
        try {
            Intent reply=new Intent(RESULT).setPackage(context.getPackageName()).putExtra("id",result.id)
                    .putExtra("pid",android.os.Process.myPid()).putExtra("state",result.state)
                    .putExtra("detail",TaskExecutions.limit(result.detail,1000)).putExtra("gif",true);
            if(execution!=null)addExecution(reply,execution);
            context.sendBroadcast(reply);
        }catch(Throwable unavailable) {}
    }
    private static synchronized void acceptResult(Intent i) {
        String id = i.getStringExtra("id"); ProcessingHistory.Row row = history.find(id);
        if (row == null || row.pid != i.getIntExtra("pid", 0)
                || !pending.containsKey(id) && (!i.getBooleanExtra("gif",false) || !gifRows.containsKey(id))) return;
        String state = i.getStringExtra("state"), detail = i.getStringExtra("detail");
        if (state == null || detail == null || detail.length() > 1024) return;
        if(i.getBooleanExtra("gif",false)) {
            if(state.startsWith("GIF") && !state.equals("GIF已解除暂停") && !state.equals("GIF恢复失败")) {
                gifRows.put(id,row.pid);
                if(state.equals("GIF已暂停") && !gifConfirmedNs.containsKey(id))gifConfirmedNs.put(id,System.nanoTime());
                while(gifRows.size()>100) {String old=gifRows.keySet().iterator().next();gifRows.remove(old);gifLoadChecked.remove(old);gifConfirmedNs.remove(old);}
            } else if(state.equals("GIF已解除暂停") || state.equals("GIF恢复失败")) {gifRows.remove(id);gifLoadChecked.remove(id);gifConfirmedNs.remove(id);}
            WatchLog.record("GIF_RULE","id="+id+" pid="+row.pid+" tid="+row.tid+" state="+state+" "+detail);
        }
        String diagnostic = i.getStringExtra("diagnostic");
        String execution = i.getStringExtra("executions"), summary = i.getStringExtra("executionSummary"), name = i.getStringExtra("threadName");
        if (validExecution(execution, summary, name)) {
            history.evidence(id, name, summary, execution);
            WatchLog.record("EXEC", "pid=" + row.pid + " tidStart=" + row.tidStart + " " + execution);
        }
        if (diagnostic != null && !diagnostic.isEmpty() && diagnostic.length() <= TaskDiagnostics.MAX_TEXT) {
            history.diagnostic(id, diagnostic);
            WatchLog.record("DIAG", diagnostic);
        }
        else if (!i.getBooleanExtra("gif",false) && !state.equals("待确认") && !state.equals("任务已结束") && !state.equals("目标已退出") && !state.equals("仅记录"))
            WatchLog.record("DIAG", "code=CONTROL_REJECTED pid=" + row.pid + " tid=" + row.tid + " name=" + row.name + " 原因=" + detail);
        history.update(id, state, detail); if (!state.equals("待确认") && !state.equals("GIF待确认")) pending.remove(id);
        if (state.equals("任务已结束") || state.equals("任务已变化"))
            attempted.remove(row.pid + ":" + row.pidStart + ":" + row.tid + ":" + row.tidStart);
    }

    private static TaskExecutions.Report executionReport(Intent request) {
        int tid = request.getIntExtra("tid", 0);
        TaskExecutions.Report report = executions.describe(tid, request.getLongExtra("window", 0), request.getLongExtra("sampleNs", 0));
        String name = report.threadName.isEmpty() ? tasks.diagnostics.threadName(tid) : report.threadName;
        return new TaskExecutions.Report(TaskExecutions.limit(name,128), report.summary, report.text);
    }
    private static void addExecution(Intent result, TaskExecutions.Report report) {
        result.putExtra("executions", report.text).putExtra("executionSummary", report.summary).putExtra("threadName", report.threadName);
    }
    private static boolean validExecution(String text, String summary, String name) {
        return text != null && !text.isEmpty() && text.length() <= TaskExecutions.MAX_TEXT
                && summary != null && summary.length() <= 400 && name != null && name.length() <= 128;
    }
    // Diagnostic-only query, independent of monitoring switch/threshold/cancellation.
    // Original per-core counters remain the sole source used for triggering actions.
    static synchronized void logExecutions(CoreTracker.Result data) {
        if (app == null || Build.VERSION.SDK_INT < 33 || data == null || data.snapshot == null || data.cpu < 0) return;
        long now = SystemClock.elapsedRealtime();
        Iterator<Map.Entry<String, ExecutionQuery>> old = executionPending.entrySet().iterator();
        while (old.hasNext()) if (now - old.next().getValue().sent > 8000) old.remove();
        int count = 0;
        for (CoreTracker.Detail hot : data.threads) {
            CoreSnapshot.Counter t = hot.thread;
            if (t.tid == t.pid || LoadPolicy.protectedName(t.name)) continue;
            if (++count > 3 || executionPending.size() >= 16) break;
            String id = UUID.randomUUID().toString(); executionPending.put(id,new ExecutionQuery(t));
            app.sendBroadcast(new Intent(EXEC_QUERY).setPackage(app.getPackageName()).putExtra("id",id)
                    .putExtra("uid",android.os.Process.myUid())
                    .putExtra("pid",t.pid).putExtra("pidStart",t.pidStart).putExtra("tid",t.tid).putExtra("tidStart",t.tidStart)
                    .putExtra("name",t.name).putExtra("window",data.windowStartNs).putExtra("sampleNs",data.snapshot.monoNs));
        }
    }
    private static void executionReply(Intent request) {
        String id = request.getStringExtra("id"); if (id == null || id.length() > 80 || app == null) return;
        long age = System.nanoTime()-request.getLongExtra("sampleNs",0); if (age < 0 || age > 8000000000L) return;
        CoreSnapshot.Counter t = new CoreSnapshot.Counter(request.getIntExtra("pid",0),request.getLongExtra("pidStart",0),
                request.getIntExtra("tid",0),request.getLongExtra("tidStart",0),request.getStringExtra("name"),new long[8]);
        if (request.getIntExtra("uid",-1) != android.os.Process.myUid()) return;
        IdentityResult identity = localIdentity(t);
        Intent result = new Intent(EXEC_REPLY).setPackage(app.getPackageName()).putExtra("id",id).putExtra("pid",t.pid).putExtra("tid",t.tid)
                .putExtra("pidStart",t.pidStart).putExtra("tidStart",t.tidStart).putExtra("identityCode",identity.code)
                .putExtra("identityDetail",identity.detail);
        if (identity.valid()) addExecution(result,executionReport(request));
        app.sendBroadcast(result);
    }
    private static synchronized void acceptExecution(Intent result) {
        ExecutionQuery query = executionPending.remove(result.getStringExtra("id"));
        if (query == null || SystemClock.elapsedRealtime()-query.sent > 8000) return;
        CoreSnapshot.Counter t = query.target;
        if (t.pid != result.getIntExtra("pid",0) || t.tid != result.getIntExtra("tid",0)
                || t.pidStart != result.getLongExtra("pidStart",0) || t.tidStart != result.getLongExtra("tidStart",0)) return;
        String identityCode = result.getStringExtra("identityCode");
        if (!"OK".equals(identityCode)) {
            String detail = result.getStringExtra("identityDetail");
            if (identityCode != null && identityCode.matches("[A-Z_]{1,48}") && detail != null && detail.length() <= 600)
                WatchLog.record("IDENTITY", "pid=" + t.pid + " tid=" + t.tid + " code=" + identityCode + " 原因=" + detail);
            return;
        }
        String text = result.getStringExtra("executions"), summary = result.getStringExtra("executionSummary"), name = result.getStringExtra("threadName");
        if (validExecution(text,summary,name)) WatchLog.record("EXEC","pid="+t.pid+" tidStart="+t.tidStart+" javaThread="+name+" "+text);
    }
}
