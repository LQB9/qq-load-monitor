package com.lqb9.qqwatchmod;

import java.util.concurrent.*;

public final class TaskExecutionsTest {
    private static int checks;
    static void check(boolean result, String message) { checks++; if (!result) throw new AssertionError(message); }
    static final class Work implements Runnable {
        public void run() {}
        public String toString() { throw new AssertionError("business toString called"); }
    }
    static final class Call implements Callable<Integer> { public Integer call() { return 1; } }
    public static void main(String[] args) throws Exception {
        TaskExecutions x = new TaskExecutions(); Thread thread = Thread.currentThread(); Work work = new Work();
        long start = 1000000000L;
        TaskExecutions.Profile p = x.profile(work,start,"test");
        check(p.info.body.get()==work,"direct payload");
        check(p.info.method.equals("run"),"Runnable method");
        check(p==x.profile(work,start,"other"),"identity stable");
        check(p!=x.profile(new Work(),start,"test"),"different identity");
        FutureTask<Void> wrapped = new FutureTask<Void>(work,null);
        TaskExecutions.Profile wrapper = x.profile(wrapped,start,"test");
        check(wrapper.info.body.get()==work,"RunnableAdapter unwrap");
        check(wrapper.info.method.equals("run"),"unwrap method");
        x.scheduled(wrapped,-40000000);
        check(wrapper.info.schedule().equals("fixed-delay") && wrapper.info.periodNs==-40000000 && wrapper.info.scheduleSource.equals("schedule-API"),"public admission metadata replaces unavailable field");
        Call call = new Call(); FutureTask<Integer> callable = new FutureTask<Integer>(call);
        check(x.profile(callable,start,"test").info.body.get()==call,"Callable unwrap");
        check(x.profile(callable,start,"test").info.method.equals("call"),"Callable method");
        ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(1);
        try {
            Object rate = scheduler.scheduleAtFixedRate(work,1,1,TimeUnit.DAYS);
            Object delay = scheduler.scheduleWithFixedDelay(work,1,1,TimeUnit.DAYS);
            Object once = scheduler.schedule(work,1,TimeUnit.DAYS);
            check(x.profile(rate,start,"test").info.schedule().equals("fixed-rate"),"rate metadata");
            check(x.profile(delay,start,"test").info.schedule().equals("fixed-delay"),"delay metadata");
            check(x.profile(once,start,"test").info.schedule().equals("one-shot"),"one-shot metadata");
            check(x.profile(rate,start,"test").info.body.get()==work,"scheduled business class");
        } finally { scheduler.shutdownNow(); }
        TaskExecutions.Scope s = x.begin(wrapped,7,thread,start,0,"wrapper");
        TaskExecutions.Scope alias = x.begin(wrapped,7,thread,start+1,1,"alias");
        check(alias==s,"same-object alias dedup");
        TaskExecutions.Body body = x.beginBody(work,"run",7,thread,start+10,10);
        check(body!=null,"payload body captured");
        check(x.beginBody(new Work(),"run",7,thread,start+11,11)==null,"foreign body rejected");
        x.endBody(body,start+10000000,5000000); x.end(alias,start+12000000,6000000);
        check(wrapper.completed==0,"alias has not prematurely ended");
        x.end(s,start+15000000,7000000);
        check(wrapper.completed==1 && wrapper.bodyRuns==1,"one completion and body");
        s=x.begin(wrapped,7,thread,start+20000000,10000000,"wrapper");
        body=x.beginBody(work,"run",7,thread,start+20000001,10000001);x.endBody(body,start+25000000,14000000);x.end(s,start+26000000,15000000);
        TaskExecutions.Report r=x.describe(7,start,start+1000000000);
        check(r.text.contains("windowRuns=2"),"window counts");
        check(r.text.contains("lifetimeRuns=2"),"lifetime retains periodic repetitions");
        check(r.text.contains("recentBodyRuns=2"),"business body counts");
        check(r.text.contains("windowBodyRuns=2") && r.text.contains("windowBodyCpuMs="),"business CPU separate from wrapper");
        check(r.text.contains("executionStack=") && r.text.contains("[执行入口]"),"execution stack persists idle");
        check(r.text.contains("全部核心参考"),"scope reference disclaimer");
        check(x.describe(7,start+5000000,start+1000000000).text.contains("windowRuns=1"),"cross-window CPU not allocated");
        Work outer=new Work(),inner=new Work();
        TaskExecutions.Scope a=x.begin(outer,8,thread,start,0,"outer");
        TaskExecutions.Scope b=x.begin(inner,8,thread,start+1,20,"inner");x.end(b,start+2,50);x.end(a,start+3,100);
        check(x.profile(outer,start,"test").cpuNs==70,"nested exclusive CPU");
        check(x.profile(inner,start,"test").cpuNs==30,"nested child CPU");
        body=x.beginBody(call,"call",9,thread,start,0);check(body!=null,"direct Callable body");
        x.endBody(body,start+1000,100);check(x.profile(call,start,"test").bodyRuns==1,"direct call counted");
        check(x.describe(7,start,start+12000000000L).text.contains("近10秒完成任务实例=0"),"history expires");
        StringBuilder longText=new StringBuilder();for(int i=0;i<2000;i++)longText.append("😀");
        String limited=TaskExecutions.limit(longText.toString(),128);
        check(limited.length()<=128 && !Character.isHighSurrogate(limited.charAt(limited.indexOf(" [" )-1)),"UTF16 truncation bound");
        ProcessingHistory history=new ProcessingHistory();
        history.add(new ProcessingHistory.Row("x",1,new CoreSnapshot.Counter(1,2,3,4,"native",new long[8]),15,250,60,200,"QQ 后台超限","等待处理","","","",""));
        history.diagnostic("x","NO_ACTIVE_TASK javaStack=fixture");
        history.evidence("x","full-worker-name","task summary","execution evidence");history.update("x","无法处理","reason");
        ProcessingHistory.Row row=history.find("x");
        check(row.threshold==200 && row.fullName.equals("full-worker-name") && row.executionText.equals("execution evidence"),"table update preserves original context");
        check(row.diagnostic.contains("NO_ACTIVE_TASK"),"table preserves failure stack evidence");
        for(int i=0;i<TaskExecutions.MAX_PROFILES+20;i++)x.profile(new Work(),start,"cache-pressure");
        check(x.profile(wrapped,start,"test")==wrapper,"periodic profile survives one-shot cache pressure");
        s=x.begin(wrapped,11,thread,start+5000000000L,0,"active-fixture");
        body=x.beginBody(work,"run",11,thread,start+5000000001L,1);
        check(x.describe(11,start+5000000000L,start+5500000000L).text.contains("activeExecutionStack="),"running task entry evidence available before return");
        x.endBody(body,start+6000000000L,100000);x.end(s,start+6000000001L,100001);
        // A new Java thread using a previously mapped Linux TID must not inherit old task events.
        CountDownLatch ready=new CountDownLatch(1),release=new CountDownLatch(1);
        Thread newer=new Thread(() -> {TaskExecutions.Scope n=x.begin(new Work(),7,Thread.currentThread(),start+1000000000,0,"new-owner");x.end(n,start+1000000001,1);ready.countDown();try{release.await();}catch(InterruptedException ignored){}},"new-owner");
        newer.start();ready.await();
        check(!x.describe(7,start,start+2000000000).text.contains("task#"+wrapper.id+" taskClass="),"TID reuse history isolated");
        release.countDown();newer.join();
        check(x.describe(7,start,start+2000000000).summary.contains("退出"),"exited owner explicit");
        for(int i=0;i<TaskExecutions.MAX_EVENTS+10;i++) {s=x.begin(work,10,thread,start+i,0,"flood");x.end(s,start+i+1,1);}
        check(!x.describe(10,start,start+1000000000).text.contains("historyDropped=0"),"bounded history reports loss");
        System.out.println("PASS "+checks+" execution-time diagnostics and table checks");
    }
}
