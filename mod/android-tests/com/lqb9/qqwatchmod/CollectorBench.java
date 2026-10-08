package com.lqb9.qqwatchmod;

import android.app.Instrumentation;
import android.os.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Identical idle-worker workload remains alive across old/new collector measurements. */
final class CollectorBench {
    static void run(Instrumentation test) {
        Bundle result=new Bundle();AtomicBoolean stop=new AtomicBoolean();List<Thread> threads=new ArrayList<Thread>();
        try {
            File directory=new File(test.getTargetContext().getFilesDir(),"bench");directory.mkdirs();
            new File(directory,"stop").delete();CountDownLatch ready=new CountDownLatch(600);
            for(int i=0;i<600;i++) {
                Thread worker=new Thread(null,() -> {ready.countDown();while(!stop.get())SystemClock.sleep(500);},"qa-idle-"+i,128*1024);
                threads.add(worker);worker.start();
            }
            if(!ready.await(15,TimeUnit.SECONDS))throw new AssertionError("idle workers not ready");
            try(FileWriter writer=new FileWriter(new File(directory,"ready"))) {
                writer.write(android.os.Process.myUid()+" "+android.os.Process.myPid()+"\n");
            }
            long until=SystemClock.elapsedRealtime()+180000;
            while(!new File(directory,"stop").exists() && SystemClock.elapsedRealtime()<until)SystemClock.sleep(200);
            result.putString("stream","PASS identical 600-worker benchmark finished\n");test.finish(-1,result);
        }catch(Throwable failure){result.putString("stream","FAILED "+failure);test.finish(1,result);}
        finally{stop.set(true);for(Thread worker:threads)try{worker.join(1500);}catch(Exception ignored){}}
    }
}
