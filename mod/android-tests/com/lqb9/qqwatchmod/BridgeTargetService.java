package com.lqb9.qqwatchmod;

import android.app.*;
import android.content.Intent;
import android.os.*;
import java.io.File;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Isolated same-UID second process. No QQ state or native signals are touched. */
public final class BridgeTargetService extends Service {
    private final AtomicBoolean release = new AtomicBoolean();
    private final ExecutorService pool = Executors.newSingleThreadExecutor(r -> new Thread(r,"qa-cross-process"));
    private final Messenger messenger = new Messenger(new Handler(Looper.getMainLooper()) {
        public void handleMessage(Message request) {
            final Messenger destination = request.replyTo;
            AtomicReference<FutureTask<Void>> reference = new AtomicReference<FutureTask<Void>>();
            FutureTask<Void> future = new FutureTask<Void>(() -> {
                int pid=android.os.Process.myPid(),tid=android.os.Process.myTid();
                long start=System.nanoTime(); TaskRegistry.Entry active=TaskBridge.tasks.begin(tid,Thread.currentThread(),reference.get(),start);
                Bundle data=new Bundle();
                try {
                    QqCpuTracker.Reading process=QqCpuTracker.Reading.parse(pid,"fixture",CoreCollector.read(new File("/proc/self/stat")));
                    ThreadCpuTracker.Reading thread=ThreadCpuTracker.Reading.parse(pid,tid,"fixture",CoreCollector.read(new File("/proc/self/task/"+tid+"/stat")));
                    data.putInt("pid",pid);data.putInt("tid",tid);data.putLong("pidStart",process.startTicks);
                    data.putLong("tidStart",thread.startTicks);data.putLong("taskStart",start);
                    Message response=Message.obtain();response.setData(data);destination.send(response);
                    while(!release.get() && !Thread.currentThread().isInterrupted())Thread.yield();
                } catch(Throwable failure) {
                    data.putString("error",failure.toString());Message response=Message.obtain();response.setData(data);
                    try{destination.send(response);}catch(Exception ignored){}
                } finally {TaskBridge.tasks.end(active);}
                return null;
            });reference.set(future);pool.execute(future);
        }
    });
    @Override public void onCreate(){super.onCreate();TaskBridge.attach((Application)getApplicationContext());}
    @Override public IBinder onBind(Intent intent){return messenger.getBinder();}
    @Override public void onDestroy(){release.set(true);pool.shutdownNow();super.onDestroy();}
}
