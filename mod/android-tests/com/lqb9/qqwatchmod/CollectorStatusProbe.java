package com.lqb9.qqwatchmod;
/** Read-only app_process probe of the production collector-status API. Test APK only. */
public final class CollectorStatusProbe {
    public static void main(String[] args)throws Exception{
        java.util.concurrent.CountDownLatch done=new java.util.concurrent.CountDownLatch(1);
        RootControl.status(value->{System.out.println("COLLECTOR_STATUS known="+value.known+" running="+value.running+" reason="+value.reason);done.countDown();});
        if(!done.await(8,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("status timeout");
    }
}
