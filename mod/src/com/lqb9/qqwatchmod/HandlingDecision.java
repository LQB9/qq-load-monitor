package com.lqb9.qqwatchmod;

/** A requested cancellation remains distinct from confirmed task completion. */
final class HandlingDecision {
    final String state,detail,code;
    final TaskRegistry.Entry task;
    final GifTaskRule.Result gif;
    HandlingDecision(String state,String detail,String code,TaskRegistry.Entry task) {
        this.state=state;this.detail=detail;this.code=code;this.task=task;gif=null;
    }
    HandlingDecision(GifTaskRule.Result result) {state=result.state;detail=result.detail;code="";task=null;gif=result;}
}
