package com.tencent.libra.extension.gif;

public abstract class SafeRunnable implements Runnable {
    final GifDrawable drawable;
    protected SafeRunnable(GifDrawable owner) {drawable=owner;}
    public final void run() {execute();}
    protected abstract void execute();
}
