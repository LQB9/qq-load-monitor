package com.lqb9.qqwatchmod;

/** Immutable input after receiver validation of UID, settings, identity, age and foreground. */
final class HandlingRequest {
    final String id;
    final int tid;
    final long windowNs,sampleNs;
    final double selectedCpu,allCpu;
    final CpuLoadMonitor.Settings settings;
    final Object expectedTask,expectedGifOwner;
    HandlingRequest(String id,int tid,long windowNs,long sampleNs,double selectedCpu,double allCpu,CpuLoadMonitor.Settings settings) {
        this(id,tid,windowNs,sampleNs,selectedCpu,allCpu,settings,null,null);
    }
    HandlingRequest(String id,int tid,long windowNs,long sampleNs,double selectedCpu,double allCpu,CpuLoadMonitor.Settings settings,Object expectedTask,Object expectedGifOwner) {
        this.id=id;this.tid=tid;this.windowNs=windowNs;this.sampleNs=sampleNs;
        this.selectedCpu=selectedCpu;this.allCpu=allCpu;this.settings=settings;
        this.expectedTask=expectedTask;this.expectedGifOwner=expectedGifOwner;
    }
}
