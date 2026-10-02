package com.lqb9.qqwatchmod;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Ordered registration. A handled or rejected GIF is never cancelled again by the fallback rule. */
final class TaskRuleRegistry {
    private final List<TaskHandlingRule> rules;
    TaskRuleRegistry(TaskRegistry tasks,TaskExecutions executions,GifTaskRule gif) {
        rules=Collections.unmodifiableList(Arrays.asList(new GifHandlingRule(gif,executions),new FutureHandlingRule(tasks,executions)));
    }
    HandlingDecision apply(HandlingRequest request) {
        for(TaskHandlingRule rule:rules){HandlingDecision result=rule.apply(request);if(result!=null)return result;}
        throw new IllegalStateException("No terminal handling rule registered");
    }
    String names() {StringBuilder out=new StringBuilder();for(TaskHandlingRule rule:rules){if(out.length()>0)out.append(" / ");out.append(rule.name());}return out.toString();}
}
