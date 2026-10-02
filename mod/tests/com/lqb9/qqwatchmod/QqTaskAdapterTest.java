package com.lqb9.qqwatchmod;

import com.tencent.mobileqq.app.Job;
import java.util.concurrent.FutureTask;

/** Business identity, window ownership and rejection boundaries, not synthetic load benchmarks. */
public final class QqTaskAdapterTest {
    private static int checks;
    private static void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
    private static void host(){QqTaskAdapter.configure(ModuleInfo.QQ_PACKAGE,"9.3.65",16240);}
    public static void main(String[] args)throws Exception {
        String[] versions={"9.3.35","9.3.50","9.3.55","9.3.60","9.3.65"};long[] codes={15560,15730,15900,16070,16240};
        FutureTask<Void> future=new FutureTask<>(()->null);Job job=new Job(future);
        for(int i=0;i<versions.length;i++){
            QqTaskAdapter.configure(ModuleInfo.QQ_PACKAGE,versions[i],codes[i]);check(QqTaskAdapter.body(job,true)==future,"exact five version binding");
            QqTaskAdapter.configure(ModuleInfo.QQ_PACKAGE,versions[i],codes[i]+1);check(QqTaskAdapter.body(job,true)==null,"same version different build rejected");
        }
        QqTaskAdapter.configure("other.app","9.3.65",16240);check(QqTaskAdapter.body(job,true)==null,"foreign package rejected");
        QqTaskAdapter.configure(ModuleInfo.QQ_PACKAGE,"9.2.85",13860);check(QqTaskAdapter.body(job,true)==null,"unverified Job keeps fallback");
        host();job.mJob=null;check(QqTaskAdapter.body(job,false)==future,"weak submission business diagnostic");check(QqTaskAdapter.body(job,true)==null,"weak reference alone is not execution proof");job.mJob=future;
        Job.$redirector_=new com.tencent.mobileqq.qfix.redirect.IPatchRedirector(){};
        check(!QqTaskAdapter.activeBusiness(job,future),"hotfix blocks execution identity");
        Job.$redirector_=null;
        TaskRegistry registry=new TaskRegistry();Thread worker=Thread.currentThread();
        TaskRegistry.Entry outer=registry.begin(71,worker,job,100,"outer-job");
        TaskRegistry.Entry inner=registry.beginFuture(71,worker,future,200,"FutureTask.run");
        check(inner!=outer && inner.parent==outer && inner.task==future,"observed actual inner Future has separate identity");
        check(registry.current(71,150)==null && registry.current(71,250)==inner,"outer CPU window cannot transfer to inner task");
        TaskRegistry.Entry alias=registry.beginFuture(71,worker,future,201,"FutureTask.runAndReset");check(alias==inner && inner.depth==2,"same inner run alias dedup");
        registry.end(alias);check(registry.current(71,250)==inner && !inner.ended,"alias exit preserves real active Future");
        FutureTask<Void> replaced=new FutureTask<>(()->null);job.mJob=replaced;
        check(registry.request(inner).contains("绑定已变化") && !future.isCancelled() && !replaced.isCancelled(),"field replacement never cancels stale or new task");
        job.mJob=future;Job.$redirector_=new com.tencent.mobileqq.qfix.redirect.IPatchRedirector(){};
        check(registry.request(inner).contains("热补丁") && !future.isCancelled(),"queued action rechecks live hotfix");Job.$redirector_=null;
        check(registry.request(inner).startsWith("已请求") && future.isCancelled() && !inner.ended,"platform cancel remains pending return");
        registry.end(inner);check(inner.ended && registry.current(71,150)==outer && !outer.ended,"inner return restores outer without stopping worker");
        registry.end(inner);check(registry.current(71,150)==outer,"stale inner end cannot end restored job");registry.end(outer);check(outer.ended && registry.current(71,999)==null,"outer cleanup remains balanced");
        FutureTask<Void> next=new FutureTask<>(()->null);job.mJob=next;
        outer=registry.begin(72,worker,job,100,"job");
        FutureTask<Void> unrelated=new FutureTask<>(()->null);
        check(registry.beginFuture(72,worker,unrelated,200,"FutureTask.run")==outer,"unrelated nested Future cannot gain stop authority");registry.end(outer);registry.end(outer);
        Runnable plain=()->{};job=new Job(plain);TaskExecutions.Info info=new TaskExecutions.Info(job);
        check(info.body.get()==plain && info.bodyClass.equals(plain.getClass().getName()) && info.note.contains("Job业务"),"ordinary business is visible without inventing cancel API");
        outer=registry.begin(73,worker,job,100,"plain-job");check(registry.request(outer).contains("没有已验证") && !next.isCancelled(),"ordinary Job has no guessed stop");registry.end(outer);
        Job weakOnly=new Job(next);weakOnly.mJob=null;
        outer=registry.begin(74,worker,weakOnly,100,"job");check(registry.beginFuture(74,worker,next,200,"FutureTask.run")==outer,"weak-only candidate cannot become current business");registry.end(outer);registry.end(outer);
        Job subclass=new Job(next){};check(QqTaskAdapter.body(subclass,true)==null,"unverified subclass rejected");
        FutureTask<Void> opaqueRun=new FutureTask<Void>(()->null){public void run(){super.run();}};Job opaqueJob=new Job(opaqueRun);
        outer=registry.begin(76,worker,opaqueJob,100,"job");check(registry.beginFuture(76,worker,opaqueRun,200,"FutureTask.run")==outer,"custom run may continue after platform return; no nested completion claim");registry.end(outer);registry.end(outer);
        FutureTask<Void> callback=new FutureTask<Void>(()->null){protected void done(){throw new AssertionError("custom callback invoked");}};Job custom=new Job(callback);
        outer=registry.begin(75,worker,custom,100,"job");inner=registry.beginFuture(75,worker,callback,200,"FutureTask.run");
        check(registry.request(inner).contains("自定义取消") && !callback.isCancelled(),"custom Future callbacks still rejected");registry.end(inner);registry.end(outer);
        FutureTask<Void> continuing=new FutureTask<>(()->null);Job steady=new Job(continuing);
        outer=registry.begin(77,worker,steady,1000000000L,"job");
        CpuLoadMonitor.Settings settings=new CpuLoadMonitor.Settings(true,200,0,1,"stop_task",255,false,true,80,2,true);
        ThreadTaskAttribution track=new ThreadTaskAttribution();
        ThreadTaskAttribution.Binding prior=ThreadTaskAttribution.identify(registry,new TaskExecutions(),77,1000000001L,2000000000L,90,90,settings);
        check(prior.identity()==outer && track.observe("k","s",prior,1000000001L,2000000000L,settings)>0,"outer has own business duration");
        inner=registry.beginFuture(77,worker,continuing,2000000000L,"FutureTask.run");
        ThreadTaskAttribution.Binding incomplete=ThreadTaskAttribution.identify(registry,new TaskExecutions(),77,1000000000L,2000000000L,90,90,settings);
        check(incomplete.identity()==null,"old outer window does not cover new Future");
        ThreadTaskAttribution.Binding fresh=ThreadTaskAttribution.identify(registry,new TaskExecutions(),77,2000000000L,3000000000L,90,90,settings);
        check(fresh.identity()==inner && track.observe("k","s",fresh,2000000000L,3000000000L,settings)==1000,"single-thread business duration restarts at inner identity");
        registry.end(inner);registry.end(outer);
        host();System.out.println("PASS "+checks+" QQ five-version Job business/window/cancellation boundaries");
    }
}
