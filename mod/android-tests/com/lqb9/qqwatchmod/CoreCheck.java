package com.lqb9.qqwatchmod;

import android.app.Instrumentation;
import android.os.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Background-only pinned CPU workers, production collector and differencer. */
public final class CoreCheck extends Instrumentation {
    private File directory;
    private final AtomicBoolean burn = new AtomicBoolean(), stop = new AtomicBoolean();
    private static volatile double sink;
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle(); List<Thread> workers = new ArrayList<Thread>();
        try {
            directory = new File(getTargetContext().getFilesDir(), "corecheck");directory.mkdirs();
            new File(directory,"go").delete();
            int[] tids = new int[3]; CountDownLatch ready = new CountDownLatch(3);
            for (int i=0;i<3;i++) {
                final int index=i;
                Thread t=new Thread(() -> {
                    tids[index]=android.os.Process.myTid();ready.countDown();
                    while(!stop.get()) {
                        if (!burn.get()) { SystemClock.sleep(10); continue; }
                        double value=index+1;
                        for(int n=0;n<10000;n++)value=Math.sqrt(value+2.1234);
                        sink=value;
                    }
                },"qa-core-"+i);workers.add(t);t.start();
            }
            if(!ready.await(3,TimeUnit.SECONDS))throw new AssertionError("workers not ready");
            try(FileWriter f=new FileWriter(new File(directory,"ready"))) {
                f.write(android.os.Process.myUid()+" "+android.os.Process.myPid()+" "+tids[0]+" "+tids[1]+" "+tids[2]+"\n");
            }
            long timeout=SystemClock.elapsedRealtime()+60000;
            while(!new File(directory,"go").exists()) {
                if(SystemClock.elapsedRealtime()>timeout)throw new AssertionError("start timeout");SystemClock.sleep(100);
            }
            burn.set(true);SystemClock.sleep(1000);
            CoreTracker selected=new CoreTracker(),all=new CoreTracker();
            int valid=0; double minimumRemoved=10000; double maximumAll=0;
            for(int i=0;i<8;i++) {
                CoreSnapshot snap=CoreSnapshot.decode(CoreCollector.read(new File("/data/local/tmp/qqcore-controlled/watchdog.core")));
                CoreTracker.Result small=selected.sample(snap,3,android.os.Process.myUid(),SystemClock.elapsedRealtime(),1);
                CoreTracker.Result total=all.sample(snap,7,android.os.Process.myUid(),SystemClock.elapsedRealtime(),1);
                if(small!=null && total!=null && small.cpu>=0 && total.cpu>=0) {
                    valid++;maximumAll=Math.max(maximumAll,total.cpu);
                    double contribution=total.cpu-small.cpu;minimumRemoved=Math.min(minimumRemoved,contribution);
                    if(Math.abs(small.cpu-(small.cores[0]+small.cores[1]))>.001)throw new AssertionError("wrong selected sum");
                    if(Math.abs(contribution-small.cores[2])>.001)throw new AssertionError("unselected not removed");
                    for(CoreTracker.Detail hot:small.threads)if(hot.thread.tid==tids[2] && hot.cpu>2)throw new AssertionError("CPU2 worker attributed to CPU0/1");
                }
                SystemClock.sleep(1100);
            }
            if(valid<3 || minimumRemoved<20)throw new AssertionError("not enough pinned samples: "+valid+",removed="+minimumRemoved);
            result.putString("stream","PASS controlled per-core samples="+valid+"; unselected CPU2 minimum removed="+minimumRemoved
                    +"%; total maximum="+maximumAll+"%; other UIDs excluded; QQ untouched\n");finish(-1,result);
        }catch(Throwable e){result.putString("stream","FAILED "+e);finish(1,result);}
        finally{stop.set(true);for(Thread worker:workers)try{worker.join(2000);}catch(Exception ignored){}}
    }
}
