package com.lqb9.qqwatchmod;


/** Explicit composition of process-local handling and evidence services. */
final class TaskServices {
    static final ExecutorRegistry executors=new ExecutorRegistry();
    static final TaskRegistry tasks=new TaskRegistry();
    static final TaskExecutions executions=new TaskExecutions(executors);
    static final ProcessingHistory history=new ProcessingHistory();
    static final GifTaskRule gif=new GifTaskRule();
    static final TaskRuleRegistry rules=new TaskRuleRegistry(tasks,executions,gif);
    private TaskServices() {}
}
