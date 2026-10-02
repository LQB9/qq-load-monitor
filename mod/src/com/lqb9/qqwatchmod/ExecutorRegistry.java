package com.lqb9.qqwatchmod;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

/** Observation only. Identity-based, bounded weak references never own or shut down QQ pools. */
final class ExecutorRegistry {
    static final int MAX_POOLS=128,MAX_TASKS=512,MAX_WORKERS=1024;
    private static final class Identity extends WeakReference<Object> {
        final int hash;
        Identity(Object value,ReferenceQueue<Object> queue){super(value,queue);hash=System.identityHashCode(value);}
        public int hashCode(){return hash;}
        public boolean equals(Object other){return this==other || other instanceof Identity && get()!=null && get()==((Identity)other).get();}
    }
    private static final class Pool {
        final long id;final String type;final WeakReference<Object> value;
        Pool(long id,Object value){this.id=id;type=value.getClass().getName();this.value=new WeakReference<>(value);}
        String label(){return "executor#"+id+" class="+type;}
    }
    private static final class Submission {
        final Pool pool;final String source;final boolean ambiguous;
        Submission(Pool pool,String source,boolean ambiguous){this.pool=pool;this.source=source;this.ambiguous=ambiguous;}
    }
    static final class Binding {
        final int tid;final WeakReference<Thread> thread;final WeakReference<Object> task;
        final Pool pool;final String taskClass;boolean active=true;
        Binding(int tid,Thread thread,Object task,Pool pool){this.tid=tid;this.thread=new WeakReference<>(thread);this.task=new WeakReference<>(task);this.pool=pool;taskClass=task.getClass().getName();}
        String evidence(){return pool.label()+" binding=execution-confirmed";}
    }
    private final ReferenceQueue<Object> collected=new ReferenceQueue<>();
    private final Map<Identity,Pool> pools=new LinkedHashMap<>();
    private final Map<Identity,Submission> tasks=new LinkedHashMap<>();
    private final Map<Integer,Binding> workers=new LinkedHashMap<>();
    private long sequence;
    private void prune(){Identity old;while((old=(Identity)collected.poll())!=null){pools.remove(old);tasks.remove(old);}}
    private static <K,V> void bound(Map<K,V> values,int max){while(values.size()>max)values.remove(values.keySet().iterator().next());}
    private Pool pool(Object executor){
        if(!(executor instanceof ThreadPoolExecutor))return null;
        prune();Identity key=new Identity(executor,null);Pool result=pools.get(key);
        if(result==null){result=new Pool(++sequence,executor);pools.put(new Identity(executor,collected),result);bound(pools,MAX_POOLS);}
        return result;
    }
    synchronized void submitted(Object executor,Object task,String source){
        if(task==null)return;Pool pool=pool(executor);if(pool==null)return;
        Submission before=tasks.get(new Identity(task,null));
        tasks.put(new Identity(task,collected),new Submission(pool,source,before!=null && (before.ambiguous || before.pool!=pool)));
        bound(tasks,MAX_TASKS);
    }
    synchronized Binding begin(Object executor,int tid,Thread thread,Object task){
        if(tid<=0 || thread==null || task==null)return null;Pool pool=pool(executor);if(pool==null)return null;
        Binding binding=new Binding(tid,thread,task,pool);workers.put(tid,binding);bound(workers,MAX_WORKERS);return binding;
    }
    synchronized void end(Binding binding){
        if(binding!=null && workers.get(binding.tid)==binding)binding.active=false;
    }
    synchronized String execution(int tid,Thread thread,Object task){
        Binding binding=workers.get(tid);
        return binding!=null && binding.active && binding.thread.get()==thread && binding.task.get()==task
                ?binding.evidence():"线程池执行归属=未确认";
    }
    synchronized String describe(int tid,Thread thread){
        Binding binding=workers.get(tid);
        if(binding==null || thread==null || binding.thread.get()!=thread)return "线程池=未建立准确工作线程映射";
        if(!thread.isAlive())return "线程池=原Java工作线程已退出，历史归属不套用";
        return binding.evidence()+" workerState="+(binding.active?"active":"last-completed")
                +" taskClass="+binding.taskClass+" poolAvailable="+(binding.pool.value.get()!=null);
    }
    synchronized String submission(Object task){
        if(task==null)return "线程池提交归属=未知";prune();Submission value=tasks.get(new Identity(task,null));
        if(value==null)return "线程池提交归属=未观测";
        return value.pool.label()+" binding=submission-observed source="+value.source
                +" ambiguous="+value.ambiguous+"；提交观察不证明实际执行";
    }
    synchronized int poolCount(){prune();return pools.size();}
    synchronized int taskCount(){prune();return tasks.size();}
    synchronized int workerCount(){return workers.size();}
}
