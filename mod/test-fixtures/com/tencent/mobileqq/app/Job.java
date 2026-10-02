package com.tencent.mobileqq.app;

import java.lang.ref.WeakReference;
import com.tencent.mobileqq.qfix.redirect.IPatchRedirector;

/** Minimal delegation fixture. It is excluded from production compilation. */
public class Job extends WeakReference<Runnable> implements Runnable {
    public static IPatchRedirector $redirector_;
    public Runnable mJob;
    public java.util.function.Consumer<Runnable> runObserver;
    public Job(Runnable business){super(business);mJob=business;}
    public void run(){if(mJob!=null){if(runObserver==null)mJob.run();else runObserver.accept(mJob);}}
}
