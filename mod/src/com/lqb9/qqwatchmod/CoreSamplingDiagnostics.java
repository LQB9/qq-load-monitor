package com.lqb9.qqwatchmod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.nio.charset.StandardCharsets;

/** Bounded collector evidence; diagnostics never change sampling or cancellation decisions. */
final class CoreSamplingDiagnostics {
    static final int MAX_EVENTS = 64, MAX_DETAILS = 4096, MAX_EVENT_TEXT = 8192;
    static final int MAX_HISTORY_BYTES = 32 * 1024;
    static final long DETAIL_INTERVAL_MS = 5000;
    static final class Event {
        final long sequence, wallMs, elapsedMs;
        final String kind, text;
        Event(long sequence, long wallMs, long elapsedMs, String kind, String text) {
            this.sequence = sequence; this.wallMs = wallMs; this.elapsedMs = elapsedMs;
            this.kind = kind; this.text = text;
        }
    }
    static final class Report {
        final String session, state, reason, details;
        final List<Event> events;
        Report(String session, String state, String reason, String details, List<Event> events) {
            this.session = session; this.state = state; this.reason = reason; this.details = details;
            this.events = Collections.unmodifiableList(new ArrayList<Event>(events));
        }
    }
    interface Sink { void record(long wallMs, String category, String message); }

    private final String session;
    private final List<Event> events = new ArrayList<Event>();
    private String previousState = "", previousReason = "";
    private long sequence, lastDetailMs = -1, pauseSinceMs = -1;
    private boolean hasBeenValid;
    CoreSamplingDiagnostics(String session) { this.session = session; }

    Report observe(String state, String reason, String details, long wallMs, long elapsedMs) {
        details = bounded(details, MAX_DETAILS);
        boolean changed = !state.equals(previousState) || !reason.equals(previousReason);
        boolean invalid = !"VALID".equals(state);
        if (invalid && pauseSinceMs < 0) pauseSinceMs = elapsedMs;
        if (changed || (invalid && (lastDetailMs < 0 || elapsedMs - lastDetailMs >= DETAIL_INTERVAL_MS))) {
            String kind;
            if (previousState.isEmpty()) kind = "START";
            else if (!invalid) kind = hasBeenValid ? "RECOVER" : "READY";
            else if ("FAILED".equals(state)) kind = "ERROR";
            else if ("STOPPED".equals(state)) kind = "STOP";
            else if ("VALID".equals(previousState)) kind = "PAUSE";
            else kind = changed ? "REASON_CHANGE" : "DETAIL";
            long duration = pauseSinceMs < 0 ? 0 : Math.max(0, elapsedMs - pauseSinceMs);
            String text = "collector=" + session + " event=" + (sequence + 1) + " elapsedMs=" + elapsedMs
                    + " state=" + state + " reason=" + reason + " previous=" + previousState + "/" + previousReason
                    + " pausedMs=" + duration + " " + details;
            events.add(new Event(++sequence, wallMs, elapsedMs, kind, bounded(text, MAX_EVENT_TEXT)));
            if (events.size() > MAX_EVENTS) events.remove(0);
            while (events.size() > 1 && historyBytes(events) > MAX_HISTORY_BYTES) events.remove(0);
            lastDetailMs = elapsedMs;
        }
        if (!invalid) { hasBeenValid = true; pauseSinceMs = -1; }
        previousState = state; previousReason = reason;
        return new Report(session, state, reason, details, events);
    }

    static int historyBytes(List<Event> events) {
        int bytes = 0;
        for (Event event : events) bytes += event.text.getBytes(StandardCharsets.UTF_8).length + 80;
        return bytes;
    }

    /** Replay retained transitions before SAMPLE throttling, including an already recovered pause. */
    static final class Forwarder {
        private String session = "";
        private long lastSequence;
        void forward(Report report, Sink sink) {
            if (report == null) return;
            if (!session.equals(report.session)) { session = report.session; lastSequence = 0; }
            for (Event event : report.events) {
                if (event.sequence <= lastSequence) continue;
                if (event.sequence > lastSequence + 1) {
                    sink.record(event.wallMs, "CORE_GAP", "collector=" + session + " missedEvents="
                            + (event.sequence - lastSequence - 1) + " firstAvailable=" + event.sequence
                            + " retainedLimit=" + MAX_EVENTS + " 采集证据缓存已轮换，缺失部分不能追溯");
                }
                sink.record(event.wallMs, "CORE_" + event.kind, event.text);
                lastSequence = event.sequence;
            }
        }
    }

    private static String bounded(String text, int limit) {
        if (text == null) return "";
        if (text.length() <= limit) return text;
        int end = limit - 12;
        if (Character.isHighSurrogate(text.charAt(end - 1))) end--;
        return text.substring(0, end) + " [truncated]";
    }
}
