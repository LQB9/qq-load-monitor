package com.lqb9.qqwatchmod;

import java.util.*;

/** Independent identity-keyed streaks, using the SAME selected-core sampling windows. */
final class ThreadLoadMonitor {
    static final int MAX_THREADS=1024;
    static final class Candidate {
        final CoreTracker.Detail hot;final long startNs,highMs;final boolean ready,trigger;
        Candidate(CoreTracker.Detail hot,long start,long ms,boolean ready,boolean trigger){this.hot=hot;startNs=start;highMs=ms;this.ready=ready;this.trigger=trigger;}
    }
    static final class Result {
        static final Result EMPTY=new Result(Collections.<Candidate>emptyList(),"等待有效采样");
        final List<Candidate> high;final String state;
        Result(List<Candidate> high,String state){this.high=Collections.unmodifiableList(new ArrayList<>(high));this.state=state;}
        int readyCount(){int n=0;for(Candidate c:high)if(c.ready)n++;return n;}
    }
    private static final class Streak {long start,end,ns;boolean reported;}
    private final Map<String,Streak> streaks=new LinkedHashMap<>();
    private CpuLoadMonitor.Settings previous;private String session="";
    private long lastEnd;
    void reset(){streaks.clear();previous=null;session="";lastEnd=0;}
    Result sample(CoreTracker.Result data,CpuLoadMonitor.Settings settings,long nowMs,long guardEndMs){
        if(!settings.enabled || !settings.threadRule){reset();return new Result(Collections.<Candidate>emptyList(),"已关闭");}
        if(data==null)return Result.EMPTY; // duplicate snapshots are handled without advancing or resetting clocks
        if(!Double.isFinite(data.cpu) || data.cpu<0 || data.snapshot==null || !data.snapshot.valid || data.snapshot.foreground<0 || nowMs<guardEndMs
                || data.snapshot.elapsedMs>nowMs+1000 || nowMs-data.snapshot.elapsedMs>Math.max(3000L,settings.intervalSeconds*3000L)
                || data.windowStartNs<=0 || data.snapshot.monoNs-data.windowStartNs<500000000L
                || data.snapshot.monoNs-data.windowStartNs>settings.intervalSeconds*3000000000L){
            reset();return new Result(Collections.<Candidate>emptyList(),"数据无效或前后台未确认，重新计时");
        }
        if(!settings.sameAs(previous) || !session.equals(data.snapshot.session))streaks.clear();
        previous=settings;session=data.snapshot.session;
        if(data.snapshot.monoNs==lastEnd)return Result.EMPTY;
        lastEnd=data.snapshot.monoNs;
        Set<String> seen=new HashSet<>();List<Candidate> result=new ArrayList<>();
        long end=data.snapshot.monoNs,start=data.windowStartNs;
        for(CoreTracker.Detail hot:data.threads){
            if(hot.thread.tid==hot.thread.pid || LoadPolicy.protectedName(hot.thread.name) || !Double.isFinite(hot.cpu)
                    || hot.cpu<=settings.threadThreshold || hot.cpu>110)continue;
            String key=hot.thread.key();if(!seen.add(key))continue;
            Streak s=streaks.get(key);
            if(s==null || s.end!=start){s=new Streak();s.start=start;streaks.put(key,s);}
            s.ns+=end-start;s.end=end;
            boolean ready=s.ns>=settings.threadDuration*1000000000L,trigger=ready && !s.reported;
            if(trigger)s.reported=true;
            result.add(new Candidate(hot,s.start,s.ns/1000000L,ready,trigger));
            while(streaks.size()>MAX_THREADS)streaks.remove(streaks.keySet().iterator().next());
        }
        streaks.keySet().retainAll(seen);
        return new Result(result,"逐线程独立计时");
    }
    int size(){return streaks.size();}
}
