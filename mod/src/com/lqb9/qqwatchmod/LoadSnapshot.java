package com.lqb9.qqwatchmod;


/** Immutable sample published to UI and diagnostics; no hook entry dependency. */
final class LoadSnapshot {
        final CpuLoadMonitor.Sample sample;
        final QqCpuTracker.Snapshot processes;
        final ThreadCpuTracker.Snapshot threads;
        final java.util.List<CoreFrequency.Core> frequencies;
        final java.util.List<LoadHistory.Point> history;
        final CoreTracker.Result core;
        final ThreadLoadMonitor.Result single;
        final java.util.List<ThreadLoadHistory.Frame> threadHistory;
        LoadSnapshot(CpuLoadMonitor.Sample sample, QqCpuTracker.Snapshot processes,
                   ThreadCpuTracker.Snapshot threads, java.util.List<CoreFrequency.Core> frequencies) {
            this(sample, processes, threads, frequencies, null);
        }
        LoadSnapshot(CpuLoadMonitor.Sample sample, QqCpuTracker.Snapshot processes,
                   ThreadCpuTracker.Snapshot threads, java.util.List<CoreFrequency.Core> frequencies, CoreTracker.Result core) {
            this(sample,processes,threads,frequencies,core,ThreadLoadMonitor.Result.EMPTY);
        }
        LoadSnapshot(CpuLoadMonitor.Sample sample,QqCpuTracker.Snapshot processes,ThreadCpuTracker.Snapshot threads,
                     java.util.List<CoreFrequency.Core> frequencies,CoreTracker.Result core,ThreadLoadMonitor.Result single) {
            this(sample,processes,threads,frequencies,core,single,java.util.Collections.<ThreadLoadHistory.Frame>emptyList());
        }
        LoadSnapshot(CpuLoadMonitor.Sample sample,QqCpuTracker.Snapshot processes,ThreadCpuTracker.Snapshot threads,
                     java.util.List<CoreFrequency.Core> frequencies,CoreTracker.Result core,ThreadLoadMonitor.Result single,
                     java.util.List<ThreadLoadHistory.Frame> threadHistory) {
            this.sample = sample;
            this.processes = processes;
            this.threads = threads;
            this.frequencies = frequencies;
            this.history = MonitorState.loadHistory.snapshot();
            this.core = core;
            this.single=single;
            this.threadHistory=threadHistory;
        }
    }

