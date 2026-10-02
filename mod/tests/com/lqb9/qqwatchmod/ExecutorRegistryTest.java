package com.lqb9.qqwatchmod;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Identity, task switching, exact execution attribution and bounded observations. */
public final class ExecutorRegistryTest {
    private static int checks;
    private static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);checks++;}
    static final class Pool extends ThreadPoolExecutor {
        Pool(){super(0,1,1,TimeUnit.SECONDS,new LinkedBlockingQueue<Runnable>());}
        public boolean equals(Object value){throw new AssertionError("QQ equals invoked");}
        public int hashCode(){throw new AssertionError("QQ hashCode invoked");}
        public String toString(){throw new AssertionError("QQ toString invoked");}
    }
    static final class Work implements Runnable {
        public void run(){}
        public boolean equals(Object value){throw new AssertionError("task equals invoked");}
        public int hashCode(){throw new AssertionError("task hashCode invoked");}
        public String toString(){throw new AssertionError("task toString invoked");}
    }
    public static void main(String[] args) throws Exception {
        ExecutorRegistry r=new ExecutorRegistry();Pool a=new Pool(),b=new Pool();Work one=new Work(),two=new Work();Thread thread=Thread.currentThread();
        check(r.submission(one).contains("未观测"),"unknown task has no guessed pool");
        r.submitted(new Object(),one,"execute");r.submitted(a,null,"execute");
        check(r.poolCount()==0 && r.taskCount()==0,"invalid executor/null task ignored");
        r.submitted(a,one,"schedule");
        check(r.submission(one).contains("executor#1") && r.submission(one).contains("submission-observed"),"actual pool object registered without application callbacks");
        check(r.workerCount()==0 && r.execution(1,thread,one).contains("未确认"),"submission is not proof of execution");
        r.submitted(b,two,"execute");
        check(r.poolCount()==2 && r.submission(two).contains("executor#2"),"same-class pool instances remain distinct");
        check(r.begin(a,0,thread,one)==null && r.begin(a,1,null,one)==null && r.begin(a,1,thread,null)==null,"invalid worker identity rejected");
        ExecutorRegistry.Binding old=r.begin(a,1,thread,one);
        check(r.execution(1,thread,one).contains("executor#1"),"beforeExecute proves exact pool and task identity");
        check(r.execution(1,thread,two).contains("未确认"),"different task never borrows active binding");
        check(r.execution(1,new Thread(),one).contains("未确认"),"reused TID needs exact Java thread");
        check(r.describe(1,thread).contains("workerState=active"),"active worker clearly identified");
        r.end(old);
        check(r.describe(1,thread).contains("last-completed"),"completion retained as history, not activity");
        check(r.execution(1,thread,one).contains("未确认"),"finished task is not current cancellable work");
        ExecutorRegistry.Binding fresh=r.begin(b,1,thread,two);r.end(old);
        check(r.execution(1,thread,two).contains("executor#2"),"old afterExecute cannot end replacement task");
        r.end(fresh);r.end(fresh);
        check(r.describe(1,thread).contains("last-completed"),"duplicate end is harmless");
        Thread dead=new Thread();r.begin(a,2,dead,one);
        check(r.describe(2,dead).contains("已退出"),"dead Java thread mapping is not reused");
        check(r.describe(2,thread).contains("未建立"),"different live Java owner rejects stale TID history");
        r.submitted(b,one,"execute");
        check(r.submission(one).contains("ambiguous=true"),"same task submitted to multiple pools explicitly ambiguous");
        r.begin(a,3,thread,one);
        check(r.execution(3,thread,one).contains("executor#1"),"execution proof independent of ambiguous submission");

        ExecutorRegistry actual=new ExecutorRegistry();TaskExecutions executions=new TaskExecutions(actual);
        ExecutorRegistry.Binding first=actual.begin(a,4,thread,one);
        TaskExecutions.Scope s=executions.begin(one,4,thread,2000000000L,0,"fixture");
        executions.end(s,2100000000L,10000000L);actual.end(first);
        actual.begin(b,4,thread,two);
        String text=executions.describe(4,1900000000L,2200000000L).text;
        check(text.contains("business="+Work.class.getName()+".run executor={executor#1"),"finished task keeps its original pool evidence after worker changes");
        check(text.contains("executor#2") && text.contains("workerState=active"),"current worker pool separated from completed task pool");
        s=executions.begin(one,4,thread,2250000000L,0,"fixture");executions.end(s,2300000000L,5000000L);
        check(executions.describe(4,1900000000L,2400000000L).text.contains("多个或不完整"),"unmatched later execution is not falsely merged with original pool");
        ExecutorRegistry nested=new ExecutorRegistry();TaskExecutions nestedExecutions=new TaskExecutions(nested);nested.begin(a,5,thread,one);
        TaskExecutions.Scope parent=nestedExecutions.begin(one,5,thread,2000000000L,0,"wrapper");
        TaskExecutions.Scope child=nestedExecutions.begin(two,5,thread,2010000000L,1000000L,"body");
        check(child.executor.equals(parent.executor) && child.executor.contains("executor#1"),"nested business inherits confirmed outer pool");
        nestedExecutions.end(child,2020000000L,2000000L);nestedExecutions.end(parent,2030000000L,3000000L);

        ExecutorRegistry bounded=new ExecutorRegistry();List<Pool> pools=new ArrayList<>();List<Work> tasks=new ArrayList<>();
        for(int i=0;i<1100;i++){Pool pool=new Pool();Work task=new Work();pools.add(pool);tasks.add(task);bounded.submitted(pool,task,"execute");bounded.begin(pool,i+100,thread,task);}
        check(bounded.poolCount()==ExecutorRegistry.MAX_POOLS,"pool cache bounded");
        check(bounded.taskCount()==ExecutorRegistry.MAX_TASKS,"submission cache bounded");
        check(bounded.workerCount()==ExecutorRegistry.MAX_WORKERS,"worker cache bounded");
        System.out.println("PASS "+checks+" executor identity, switching and attribution checks");
    }
}
