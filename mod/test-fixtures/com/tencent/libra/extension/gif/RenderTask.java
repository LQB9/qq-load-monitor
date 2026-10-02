package com.tencent.libra.extension.gif;

public final class RenderTask extends SafeRunnable {
    public Runnable action;
    public RenderTask(GifDrawable owner) {super(owner);}
    protected void execute() {drawable.renders++;if(action!=null)action.run();}
}
