package com.lqb9.qqwatchmod;

import android.os.SystemClock;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

final class StatReaderCheck {
    private static volatile double sink;
    private static void check(boolean value, String text) { if (!value) throw new AssertionError(text); }
    static void run(File directory) throws Exception {
        File small = new File(directory, "reader-small"), large = new File(directory, "reader-large");
        String text = "QQ 线程 UTF8\n";
        try {
            try (FileOutputStream out = new FileOutputStream(small)) { out.write(text.getBytes(StandardCharsets.UTF_8)); }
            int before = new File("/proc/self/fd").list().length;
            for (int i=0;i<200;i++) check(text.equals(CoreCollector.read(small)), "native reader exact UTF8");
            check(new File("/proc/self/fd").list().length <= before+3, "native reader leaked descriptors");
            byte[] bytes = new byte[CoreSnapshot.MAX_BYTES]; Arrays.fill(bytes,(byte)'x');
            try (FileOutputStream out = new FileOutputStream(large)) { out.write(bytes); }
            check(CoreCollector.read(large).length()==bytes.length, "native exact maximum bound");
            try (FileOutputStream out = new FileOutputStream(large,true)) { out.write(1); }
            boolean rejected=false; try { CoreCollector.read(large); } catch(IOException expected) { rejected=true; }
            check(rejected && text.equals(CoreCollector.read(small)), "native overflow/reset guard");
        } finally { small.delete();large.delete(); }
        AtomicBoolean stop = new AtomicBoolean(), renamed = new AtomicBoolean(); AtomicInteger tid=new AtomicInteger();
        CountDownLatch ready=new CountDownLatch(1), changed=new CountDownLatch(1);
        Thread worker=new Thread(() -> {
            tid.set(android.os.Process.myTid());ready.countDown();
            while(!stop.get()) {
                if(renamed.get() && changed.getCount()>0) { Thread.currentThread().setName("qa-stat-after");changed.countDown(); }
                double value=1;for(int n=0;n<10000;n++)value=Math.sqrt(value+2.1234);sink=value;
            }
        },"qa-stat-before");
        worker.start();
        try(ProcStatReader reader=new ProcStatReader(2)) {
            check(ready.await(3,TimeUnit.SECONDS),"stat worker not ready");
            int pid=android.os.Process.myPid();File task=new File("/proc/"+pid+"/task/"+tid.get()+"/stat");
            reader.beginScan();ProcStat first=ProcStat.parse(reader.read(task),true);
            check(first.name.equals("qa-stat-before") && reader.openCount()==1,"cached task identity");
            renamed.set(true);check(changed.await(3,TimeUnit.SECONDS),"stat worker rename not ready");SystemClock.sleep(150);
            ProcStat second=ProcStat.parse(reader.read(task),true);
            check(second.name.equals("qa-stat-after") && second.cpuTicks>first.cpuTicks && second.startTicks==first.startTicks,
                    "positioned read reused stale seq_file content");
            File main=new File("/proc/"+pid+"/task/"+android.os.Process.myTid()+"/stat");
            reader.read(main);check(reader.openCount()==2,"cache capacity before overflow");
            reader.read(new File("/proc/"+pid+"/stat"));check(reader.openCount()==2,"cache exceeded descriptor capacity");
            stop.set(true);worker.join(3000);check(!worker.isAlive(),"stat worker did not exit");
            // ART can notify Thread.join before the native pthread leaves /proc.
            long exitDeadline=SystemClock.elapsedRealtime()+3000;
            while(task.exists() && SystemClock.elapsedRealtime()<exitDeadline)SystemClock.sleep(10);
            check(!task.exists(),"native task did not leave proc");
            boolean gone=false;try{reader.read(task);}catch(IOException expected){gone=true;}
            check(gone && reader.openCount()==1,"exited task descriptor kept stale values: gone="+gone+" count="+reader.openCount());
            reader.beginScan();reader.finishScan();check(reader.openCount()==0,"scan sweep left unused descriptors");
        } finally {stop.set(true);worker.join(3000);}
    }
}
