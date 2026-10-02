package com.lqb9.qqwatchmod;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

/** Object-scoped GIF pause. No thread interrupt, native signal, decoder recycle or guessed field name. */
final class GifTaskRule {
    static final String RENDER = "com.tencent.libra.extension.gif.RenderTask";
    static final String DRAWABLE = "com.tencent.libra.extension.gif.GifDrawable";
    static final int MAX_OWNERS = 32;
    static final class Owner {
        final WeakReference<Object> value; final GifHostAdapter adapter;
        int active; volatile boolean recycled; Lease lease;
        Owner(Object value, GifHostAdapter adapter) { this.value = new WeakReference<Object>(value); this.adapter = adapter; }
    }
    static final class Run {
        final Owner owner; final boolean blocked;
        Run(Owner owner, boolean blocked) { this.owner = owner; this.blocked = blocked; }
    }
    static final class Lease {
        final String id; final CpuLoadMonitor.Settings settings; final List<Owner> owners = new ArrayList<Owner>();
        volatile long blocked; volatile boolean failed;
        Lease(String id, CpuLoadMonitor.Settings settings) { this.id=id; this.settings=settings; }
    }
    static final class Result {
        final String id, state, detail;
        Result(String id, String state, String detail) { this.id=id; this.state=state; this.detail=detail; }
    }
    private final Map<Class<?>,GifHostAdapter> adapters = new HashMap<Class<?>,GifHostAdapter>();
    private final List<Owner> owners = new ArrayList<Owner>();
    private final LinkedHashMap<String,Lease> leases = new LinkedHashMap<String,Lease>();
    synchronized GifHostAdapter adapter(Object render) throws Exception {
        if (render == null || !render.getClass().getName().equals(RENDER)) return null;
        GifHostAdapter a=adapters.get(render.getClass());
        if(a==null) { a=new GifHostAdapter(render.getClass()); adapters.put(render.getClass(),a); }
        return a;
    }
    private Owner owner(Object value, GifHostAdapter adapter) {
        for (Iterator<Owner> it=owners.iterator();it.hasNext();) {
            Owner o=it.next(); Object v=o.value.get();
            if(v==value) return o;
            if(v==null && o.lease==null && o.active==0) it.remove();
        }
        // Live observations are bounded independently of the 32 paused objects.
        if(owners.size()>=512) for(Iterator<Owner> it=owners.iterator();it.hasNext();) {
            Owner o=it.next(); if(o.lease==null && o.active==0) {it.remove();break;}
        }
        if(owners.size()>=512) return null;
        Owner o=new Owner(value,adapter); owners.add(o); return o;
    }
    synchronized Run enter(Object render) {
        try {
            GifHostAdapter a=adapter(render); if(a==null || !a.hooksReady) return null;
            Owner o=owner(a.owner(render),a); if(o==null || o.recycled) return null;
            if(o.lease!=null) {o.lease.blocked++;return new Run(o,true);}
            o.active++; return new Run(o,false);
        } catch(Exception unsupported) {return null;}
    }
    synchronized void exit(Run run) { if(run!=null && !run.blocked && run.owner.active>0) run.owner.active--; }
    synchronized boolean blockStart(Object value) {
        for(Owner o:owners) if(o.value.get()==value) return !o.recycled && o.lease!=null;
        return false;
    }
    synchronized void recycled(Object value) {
        for(Owner o:owners) if(o.value.get()==value) {o.recycled=true;return;}
    }
    synchronized int pausedCount() {
        int count=0; for(Owner o:owners) if(o.lease!=null && !o.recycled && o.value.get()!=null) count++;
        return count;
    }
    /** Caller has already validated CPU window, target identity, settings, age and foreground. */
    Result pause(String id,List<Object> renders,CpuLoadMonitor.Settings settings) {
        Lease lease=new Lease(id,settings);
        synchronized(this) {
            if(!settings.enabled || !settings.gifRule || !"stop_task".equals(settings.action))
                return new Result(id,"未处理","GIF专项规则已关闭");
            if(leases.containsKey(id)) return new Result(id,"未处理","重复的GIF处理请求");
            try {
                for(Object render:renders) {
                    GifHostAdapter a=adapter(render);
                    if(a==null || !a.hooksReady) throw new IllegalArgumentException("GIF启动/释放保护入口未确认");
                    Object value=a.owner(render); Owner o=owner(value,a);
                    if(o==null || o.recycled) throw new IllegalArgumentException("GIF对象已释放或观察上限");
                    if(o.lease!=null || lease.owners.contains(o)) continue;
                    if(!a.running(value)) continue;
                    lease.owners.add(o);
                    if(lease.owners.size()==3) break;
                }
            } catch(Exception unsupported) {
                return new Result(id,"未处理","GIF停止接口未匹配："+unsupported.getClass().getSimpleName()+" · "+unsupported.getMessage());
            }
            if(lease.owners.isEmpty()) return new Result(id,"任务已变化","定位到的GIF已停止、已暂停或对象不再可用");
            if(pausedCount()+lease.owners.size()>MAX_OWNERS || leases.size()>=MAX_OWNERS)
                return new Result(id,"未处理","GIF暂停对象达到32个上限，先恢复已有对象");
            leases.put(id,lease);
            for(Owner o:lease.owners) o.lease=lease;
        }
        // Install the object guard before calling the public API, closing restart/reschedule races.
        for(Owner o:lease.owners) try {
            Object value=o.value.get(); if(value!=null && !o.recycled) o.adapter.stop.invoke(value);
        } catch(Exception failed) {lease.failed=true;}
        return status(id);
    }
    Result status(String id) {
        Lease lease;
        synchronized(this) {lease=leases.get(id);}
        if(lease==null) return new Result(id,"GIF已解除暂停","暂停记录已清除");
        int active=0,remaining=0,unreadable=0;
        for(Owner o:lease.owners) {
            Object value=o.value.get();
            synchronized(this) { active+=o.active; }
            if(value!=null && !o.recycled) try {if(o.adapter.running(value))remaining++;}
            catch(Exception failure){unreadable++;}
        }
        String detail="GIF对象="+lease.owners.size()+" · 正在渲染="+active+" · 仍播放="+remaining
                +" · 已拦截后续渲染="+lease.blocked+"；仅暂停对应GIF，工作线程保留。可从处理页恢复。";
        if(lease.failed || unreadable>0) return new Result(id,"GIF处理失败",detail+" 停止接口调用或状态读取失败，未确认停止。");
        return new Result(id,active==0 && remaining==0?"GIF已暂停":"GIF待确认",detail);
    }
    List<Result> restore(CpuLoadMonitor.Settings current,boolean manual) {
        List<Lease> release=new ArrayList<Lease>();
        synchronized(this) {
            for(Lease lease:leases.values()) {
                boolean gone=true;
                for(Owner o:lease.owners) if(!o.recycled && o.value.get()!=null) {gone=false;break;}
                if(manual || gone || !lease.settings.sameAs(current)) release.add(lease);
            }
        }
        List<Result> results=new ArrayList<Result>();
        for(Lease lease:release) {
            int started=0,hidden=0,busy=0,failed=0;
            // Remove all guards before start(), because start may render immediately on this same thread.
            synchronized(this) {
                leases.remove(lease.id);
                for(Owner o:lease.owners) if(o.lease==lease) o.lease=null;
            }
            for(Owner o:lease.owners) {
                Object value=o.value.get(); if(value==null || o.recycled) {hidden++;continue;}
                synchronized(this) {if(o.active>0) {busy++;continue;}}
                try {
                    if(!(Boolean)o.adapter.visible.invoke(value)) {hidden++;continue;}
                    if(o.adapter.running(value))o.adapter.stop.invoke(value);
                    o.adapter.start.invoke(value);
                    if(o.adapter.running(value))started++;else failed++;
                } catch(Exception unavailable) {failed++;}
            }
            results.add(new Result(lease.id,failed>0?"GIF恢复失败":"GIF已解除暂停",
                    (manual?"手动恢复":"监控关闭或设置变化，自动解除暂停")+" · 恢复播放="+started
                    +" · 不可见/已释放="+hidden+" · 渲染未返回="+busy+" · 失败="+failed+"。不可见、已释放或渲染未返回的对象不主动重播。"));
        }
        return results;
    }
}
