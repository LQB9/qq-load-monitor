package com.lqb9.qqwatchmod;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CoreSamplingDiagnosticsTest {
    private static int checks;
    private static void check(boolean ok, String name) { checks++; if (!ok) throw new AssertionError(name); }
    private static CoreSnapshot snapshot(CoreSamplingDiagnostics.Report report, long elapsed) {
        return new CoreSnapshot("collector-1", 10001, 30, elapsed, elapsed * 1000000, true, 1,
                "running", new long[8], Collections.emptyList(), report);
    }
    private static void rejects(String text, String name) throws Exception {
        try { CoreSnapshot.decode(text); throw new AssertionError(name); } catch (IOException expected) { checks++; }
    }
    public static void main(String[] args) throws Exception {
        CoreSamplingDiagnostics diagnostics = new CoreSamplingDiagnostics("collector");
        diagnostics.observe("VALID", "OK", "lossTotal=0", 100000, 1000);
        diagnostics.observe("INCOMPLETE", "TRACE_LOSS,CPU_TIME_MISMATCH", "lossDelta=7 procCpuNs=900000000 traceRuntimeNs=100000000 中文", 101000, 2000);
        diagnostics.observe("WARMUP", "WARMUP", "lossDelta=0", 102000, 3000);
        CoreSamplingDiagnostics.Report recovered = diagnostics.observe("VALID", "OK", "lossDelta=0", 104000, 5000);
        check(recovered.events.size() == 4, "short pause retained even after recovery before consumer read");
        check(recovered.events.get(1).kind.equals("PAUSE") && recovered.events.get(3).kind.equals("RECOVER"), "pause and actual collector recovery are separate");
        check(recovered.events.get(3).text.contains("pausedMs=3000"), "recovery duration includes warmup");
        List<String> forwarded = new ArrayList<String>(); List<Long> times = new ArrayList<Long>();
        CoreSamplingDiagnostics.Sink sink = (time, kind, text) -> { times.add(time); forwarded.add(kind + " " + text); };
        CoreSamplingDiagnostics.Forwarder forwarder = new CoreSamplingDiagnostics.Forwarder();
        forwarder.forward(recovered, sink);
        check(forwarded.size() == 4 && times.get(1) == 101000, "original collector wall time preserved after delayed import");
        forwarder.forward(recovered, sink);
        check(forwarded.size() == 4, "same snapshot not logged twice");
        CoreSamplingDiagnostics.Report stable = diagnostics.observe("VALID", "OK", "new timing", 105000, 6000);
        forwarder.forward(stable, sink);
        check(forwarded.size() == 4 && stable.details.equals("new timing"), "healthy details update without noisy transitions");
        CoreSnapshot decoded = CoreSnapshot.decode(snapshot(recovered, 5000).encode());
        check(decoded.diagnostics.events.get(1).text.contains("中文") && decoded.diagnostics.reason.equals("OK"), "UTF8 evidence codec roundtrip");
        check(decoded.cores[0] == 0 && decoded.valid && decoded.uid == 10001, "diagnostic codec retains authoritative sampling counters");
        CoreSnapshot legacy = new CoreSnapshot("old",10001,1,1000,1000000000,true,0,"ok",new long[8],Collections.emptyList());
        check(CoreSnapshot.decode(legacy.encode()).diagnostics == null, "old collector snapshots accepted without fabricated evidence");
        String encoded = snapshot(recovered, 5000).encode();
        rejects(encoded.replace("QQCORE3", "QQCORE2"), "new evidence cannot masquerade as old version");
        rejects(encoded.replace("E 2 101000 2000", "E 1 101000 2000"), "repeated event sequence rejected");
        rejects(encoded.replace("E 2 101000 2000", "E 2 101000 9000"), "future diagnostic timestamp rejected");
        rejects(encoded.replaceAll("(?m)^D .*\\n", ""), "new version requires diagnostics");
        rejects(encoded.substring(0, encoded.length()-1), "partial evidence snapshot rejected");

        CoreSamplingDiagnostics repeated = new CoreSamplingDiagnostics("repeat");
        CoreSamplingDiagnostics.Report report = repeated.observe("INCOMPLETE","CPU_TIME_MISMATCH","first",100,0);
        repeated.observe("INCOMPLETE","CPU_TIME_MISMATCH","second",200,1000);
        report = repeated.observe("INCOMPLETE","CPU_TIME_MISMATCH","third",300,4999);
        check(report.events.size() == 1, "continuous identical failure does not flood each sample");
        report = repeated.observe("INCOMPLETE","CPU_TIME_MISMATCH","fourth",400,5000);
        check(report.events.size() == 2 && report.events.get(1).kind.equals("DETAIL"), "persistent failure refreshes evidence every five seconds");
        report = repeated.observe("INCOMPLETE","TRACE_LOSS","lossDelta=1",500,5001);
        check(report.events.size() == 3 && report.events.get(2).kind.equals("REASON_CHANGE"), "new failure reason bypasses repeat rate limit");
        check(recovered.events.size() == 4, "published diagnostic report immutable across later samples");

        CoreSamplingDiagnostics rolled = new CoreSamplingDiagnostics("rolled");
        for (int i=0;i<70;i++) report = rolled.observe(i%2==0?"VALID":"INCOMPLETE",i%2==0?"OK":"TRACE_LOSS","counter="+i,1000+i,i);
        check(report.events.size()==64 && report.events.get(0).sequence==7, "bounded ring keeps newest events");
        List<String> gaps = new ArrayList<String>();
        new CoreSamplingDiagnostics.Forwarder().forward(report,(time,kind,text)->gaps.add(kind+" "+text));
        check(gaps.get(0).contains("CORE_GAP") && gaps.get(0).contains("missedEvents=6"), "evicted transitions explicitly reported");
        check(gaps.size()==65, "all remaining transitions forwarded after gap marker");
        CoreSamplingDiagnostics fresh = new CoreSamplingDiagnostics("new-session");
        forwarder.forward(fresh.observe("WARMUP","WARMUP","restart",999999,9000),sink);
        check(forwarded.size()==5 && forwarded.get(4).contains("new-session"), "new collector session does not collide with old sequence");

        String big = String.join("", Collections.nCopies(2000,"证据😀"));
        CoreSamplingDiagnostics large = new CoreSamplingDiagnostics("large");
        for(int i=0;i<70;i++) report = large.observe(i%2==0?"VALID":"INCOMPLETE",i%2==0?"OK":"TRACE_LOSS",big,2000+i,i);
        check(CoreSamplingDiagnostics.historyBytes(report.events)<=32768 && report.events.size()<64, "byte budget bounds multibyte evidence");
        check(report.details.endsWith("[truncated]"), "long evidence explicitly truncated");
        check(CoreSnapshot.decode(snapshot(report,100).encode()).diagnostics.events.size()==report.events.size(), "bounded large evidence remains decodable");

        java.io.File dir = Files.createTempDirectory("qq-core-log-").toFile();
        RollingLog log = new RollingLog(dir,512*1024,4);
        new CoreSamplingDiagnostics.Forwarder().forward(recovered,(time,kind,text)-> {
            try { log.append(time,kind,text); } catch(IOException failure) { throw new RuntimeException(failure); }
        });
        java.io.ByteArrayOutputStream export = new java.io.ByteArrayOutputStream(); log.writeSnapshot(export);
        String exported = new String(export.toByteArray(), StandardCharsets.UTF_8);
        check(exported.contains("CORE_PAUSE") && exported.contains("CORE_RECOVER") && exported.contains("lossDelta=7"), "existing rolling export carries pause recovery and loss evidence");
        for(java.io.File file:dir.listFiles()) file.delete(); dir.delete();
        System.out.println("PASS "+checks+" collector diagnostic retention and export checks");
    }
}
