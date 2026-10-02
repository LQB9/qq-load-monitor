package com.lqb9.qqwatchmod;

import java.lang.ref.WeakReference;
import java.util.*;

/** Target-process streaming business confirmation. No raw long-lived task/event retention. */
final class ThreadTaskAttribution {
    static final int MAX_TARGETS=512;
    static final class Binding {
        final Object task,owner;final String detail;
        Binding(Object task,Object owner,String detail){this.task=task;this.owner=owner;this.detail=detail;}
        Object identity(){return owner!=null?owner:task;}
    }
    static Binding identify(TaskRegistry tasks,TaskExecutions executions,int tid,long start,long end,double selected,double all,CpuLoadMonitor.Settings settings){
        if(settings.gifRule){
            TaskExecutions.GifEvidence e=executions.gifEvidence(tid,start,end);
            if(e.dominant(all)){
                if(!e.dominatesSelected(selected,all))return new Binding(null,null,"GIF所选核心贡献不足");
                Object owner=null;
                try {for(Object render:e.renders){Object value=new GifHostAdapter(render.getClass()).owner(render);
                    if(value==null || owner!=null && owner!=value)return new Binding(null,null,"窗口包含多个GIF对象，持续归属未确认");owner=value;}}
                catch(Exception failure){return new Binding(null,null,"GIF接口或所有者未确认");}
                return new Binding(null,owner,"同一GIF对象的窗口业务已确认");
            }
        }
        TaskRegistry.Entry t=tasks.current(tid,Long.MAX_VALUE);
        if(t==null || t.startNs>start)return new Binding(null,null,"无覆盖本轮窗口的同一活动任务");
        return new Binding(t,null,"同一活动任务覆盖本轮窗口");
    }
    private static final class State {
        final WeakReference<Object> business;final boolean gif;final CpuLoadMonitor.Settings settings;final String session;
        long end,ns;
        State(Binding b,CpuLoadMonitor.Settings s,String session){business=new WeakReference<>(b.identity());gif=b.owner!=null;settings=s;this.session=session;}
    }
    private final Map<String,State> states=new LinkedHashMap<>();
    synchronized long observe(String key,String session,Binding binding,long start,long end,CpuLoadMonitor.Settings settings){
        if(binding.identity()==null || start<=0 || end-start<500000000L || end-start>settings.intervalSeconds*3000000000L){states.remove(key);return 0;}
        State s=states.get(key);
        if(s!=null && s.end==end && s.business.get()==binding.identity() && s.settings.sameAs(settings) && s.session.equals(session))return s.ns/1000000L;
        if(s==null || s.end!=start || s.business.get()!=binding.identity() || s.gif!=(binding.owner!=null)
                || !s.settings.sameAs(settings) || !s.session.equals(session)){s=new State(binding,settings,session);states.put(key,s);}
        s.end=end;s.ns+=end-start;
        while(states.size()>MAX_TARGETS)states.remove(states.keySet().iterator().next());
        return s.ns/1000000L;
    }
    synchronized int size(){return states.size();}
}
