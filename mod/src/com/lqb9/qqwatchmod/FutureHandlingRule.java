package com.lqb9.qqwatchmod;

/** Existing standard-Future cancellation; Handler/unknown work needs a dedicated stop interface. */
final class FutureHandlingRule implements TaskHandlingRule {
    private final TaskRegistry tasks;
    private final TaskExecutions executions;
    FutureHandlingRule(TaskRegistry tasks,TaskExecutions executions) {this.tasks=tasks;this.executions=executions;}
    public String name(){return "Future协作取消";}
    public HandlingDecision apply(HandlingRequest request) {
        TaskRegistry.Entry task=tasks.current(request.tid,Long.MAX_VALUE);
        if(request.expectedGifOwner!=null || request.expectedTask!=null && request.expectedTask!=task)
            return new HandlingDecision("任务已变化","持续超限对应的业务已切换，未取消新任务","THREAD_BUSINESS_CHANGED",task);
        if(task!=null && task.startNs>request.windowNs)return new HandlingDecision("任务已变化",
                "当前任务晚于采样窗口开始，等待新样本定位","TASK_WINDOW_MISMATCH",task);
        if(task==null) {
            if(executions.activeMessage(request.tid))return new HandlingDecision("无法处理",
                    "正在执行Handler消息，但没有已验证的取消接口；需按具体消息或回调适配停止","HANDLER_NO_CANCEL_INTERFACE",null);
            if(executions.recentMessage(request.tid,request.sampleNs))return new HandlingDecision("无法处理",
                    "已记录Handler消息执行；处理时消息已返回或切换，没有当前可取消的活动任务","HANDLER_TASK_NOT_ACTIVE",null);
            return new HandlingDecision("无法处理","未捕获覆盖采样窗口的任务；尚不能确定是任务切换、hook 未覆盖还是原生执行","NO_ACTIVE_TASK",null);
        }
        String result=tasks.request(task);
        if(!result.startsWith("已请求"))return new HandlingDecision("未处理",result,"CANCEL_NOT_ACCEPTED",task);
        return new HandlingDecision("待确认",result+"；取消标记不等于任务结束","",task);
    }
}
