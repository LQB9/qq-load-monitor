package com.lqb9.qqwatchmod;

public final class HandlerExecutionsTest {
    private static int checks;
    private static void check(boolean ok,String name){checks++;if(!ok)throw new AssertionError(name);}
    public static void main(String[] args) {
        TaskExecutions executions=new TaskExecutions();Thread thread=Thread.currentThread();Object callback=new Object(),handler=new Object();
        TaskExecutions.DispatchTask dispatch=new TaskExecutions.DispatchTask(callback,"handleMessage","handler=QQHandler messageWhat=42 callbackRoute=Handler.Callback");
        TaskExecutions.Scope scope=executions.begin(dispatch,777,thread,1000000000,100000000,"Handler.dispatchMessage");
        check(executions.activeMessage(777),"active handler tracked without inventing cancellable Future");
        TaskExecutions.Body initial=executions.beginBody(callback,"handleMessage",777,thread,1010000000,110000000);
        executions.endBody(initial,1020000000,120000000);
        TaskExecutions.Body fallback=executions.beginBody(handler,"handleMessage",777,thread,1020000000,120000000);
        check(fallback!=null && fallback.ownsScope,"false Handler.Callback route captures actual fallback handler body");
        executions.endBody(fallback,1040000000,140000000);
        executions.end(scope,1050000000,150000000);
        check(!executions.activeMessage(777) && executions.recentMessage(777,1050000000),"returned message remains evidence while worker waits");
        TaskExecutions.Report report=executions.describe(777,1000000000,1050000000);
        check(report.text.contains("messageWhat=42") && report.text.contains("handler=QQHandler"),"route evidence contains safe message identifier");
        check(report.text.contains("schedule=handler-message") && report.text.contains("observed:handleMessage"),"observed handler method distinguished from guessed route");
        check(report.text.contains("windowCpuMs=30.0") && report.text.contains("windowCpuMs=20.0"),"nested body CPU removed from parent, total remains 50ms");
        check(report.text.contains("[执行入口]") && report.text.contains("stackOwnerTid=777"),"execution stack captured on actual executing thread");
        check(!executions.recentMessage(777,12000000000L),"old handler history expires");
        TaskRegistry registry=new TaskRegistry();TaskRegistry.Entry ordinary=registry.begin(777,thread,dispatch,0,"fixture");
        check(registry.request(ordinary).contains("专用停止接口") && !thread.isInterrupted(),"ordinary handler not blindly interrupted");registry.end(ordinary);
        System.out.println("PASS "+checks+" Handler route and execution checks");
    }
}
