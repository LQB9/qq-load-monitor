package com.lqb9.qqwatchmod;

/** Business action; transport and delayed confirmation stay in TaskBridge. */
interface TaskHandlingRule {
    String name();
    HandlingDecision apply(HandlingRequest request);
}
