package com.lqb9.qqwatchmod;

import java.util.*;

/** Bounded selected-core thread time series. An identity never inherits another thread's line. */
final class ThreadLoadHistory {
    static final int MAX_POINTS=60,MAX_THREADS=64;
    static final class Reading {
        final String key,name;final int pid,tid;final double cpu;
        Reading(CoreTracker.Detail d){key=d.thread.key();name=d.thread.name;pid=d.thread.pid;tid=d.thread.tid;cpu=d.cpu;}
    }
    static final class Frame {
        final long elapsedMs;final boolean connected;final Map<String,Reading> threads;
        Frame(long ms,boolean connected,Map<String,Reading> data){elapsedMs=ms;this.connected=connected;threads=Collections.unmodifiableMap(new LinkedHashMap<>(data));}
    }
    private final List<Frame> frames=new ArrayList<>();
    private CpuLoadMonitor.Settings previous;private String session="";private long lastEnd;private boolean broken=true;
    void clear(){frames.clear();previous=null;session="";lastEnd=0;broken=true;}
    void add(CoreTracker.Result data,CpuLoadMonitor.Settings settings,long nowMs){
        if(!settings.sameAs(previous)){clear();previous=settings;}
        if(!settings.enabled || !settings.threadRule){frames.clear();lastEnd=0;broken=true;return;}
        CoreSnapshot s=data==null?null:data.snapshot;
        if(s!=null && !s.session.equals(session)){frames.clear();lastEnd=0;broken=true;session=s.session;}
        boolean valid=data!=null && s!=null && s.valid && s.foreground>=0 && Double.isFinite(data.cpu) && data.cpu>=0
                && s.elapsedMs<=nowMs+1000 && nowMs-s.elapsedMs<=Math.max(3000L,settings.intervalSeconds*3000L)
                && data.windowStartNs>0 && s.monoNs-data.windowStartNs>=500000000L && s.monoNs-data.windowStartNs<=settings.intervalSeconds*3000000000L;
        if(valid && s.monoNs<=lastEnd)return;
        long ms=valid?s.elapsedMs:nowMs;
        if(!frames.isEmpty() && ms<=frames.get(frames.size()-1).elapsedMs){if(!valid)broken=true;return;}
        Map<String,Reading> values=new LinkedHashMap<>();
        if(valid)for(CoreTracker.Detail d:data.threads){
            if(d.thread.tid==d.thread.pid || LoadPolicy.protectedName(d.thread.name) || !Double.isFinite(d.cpu) || d.cpu<0 || d.cpu>110)continue;
            if(values.size()==MAX_THREADS)break;values.put(d.thread.key(),new Reading(d));
        }
        boolean connect=valid && !broken && data.windowStartNs==lastEnd;
        frames.add(new Frame(ms,connect,values));while(frames.size()>MAX_POINTS)frames.remove(0);
        broken=!valid;if(valid)lastEnd=s.monoNs;
    }
    List<Frame> snapshot(){return Collections.unmodifiableList(new ArrayList<>(frames));}
}
