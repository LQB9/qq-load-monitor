package com.tencent.libra.extension.gif;

/** Isolated API fixture. Never part of the module APK; not QQ's native decoder. */
public class GifDrawable {
    public volatile boolean playing=true, visible=true, disposed, failStop, ignoreStop;
    public volatile int stops, starts, renders;
    public void stop() {stops++;if(failStop)throw new IllegalStateException("fixture stop failure");if(!ignoreStop)playing=false;}
    public void start() {if(disposed)throw new IllegalStateException("recycled fixture resumed");starts++;playing=true;}
    public boolean isRunning() {return playing;}
    public boolean isVisible() {return visible;}
    public void recycle() {disposed=true;playing=false;}
}
