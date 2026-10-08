package com.lqb9.qqwatchmod;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;

public final class CollectorPerformanceTest {
    private static int checks;
    private static void check(boolean value, String label) { checks++; if (!value) throw new AssertionError(label); }
    private static String stat(String name, long user, long system, long start, int core, String separator) {
        String[] f = new String[50]; Arrays.fill(f, "0"); f[0] = "S";
        f[11] = Long.toString(user); f[12] = Long.toString(system); f[19] = Long.toString(start); f[36] = Integer.toString(core);
        return "123 (" + name + ")" + separator + String.join(separator, f) + "\n";
    }
    private static void parsing() {
        Random random = new Random(133);
        for (int i = 0; i < 300; i++) {
            String name = i % 2 == 0 ? "worker (a) ) b" : "GIF 演示 " + i;
            long u = random.nextInt(1000000), s = random.nextInt(1000000), start = random.nextInt(1000000);
            String text = stat(name, u, s, start, random.nextInt(8), i % 2 == 0 ? " " : "\t\r\n");
            String[] reference = text.substring(text.lastIndexOf(')') + 1).trim().split("\\s+");
            ProcStat value = ProcStat.parse(text, true);
            check(value.name.equals(name) && value.cpuTicks == Long.parseLong(reference[11]) + Long.parseLong(reference[12])
                    && value.startTicks == Long.parseLong(reference[19]) && value.core == Integer.parseInt(reference[36]),
                    "proc tokenization matches independently split fields");
            QqCpuTracker.Reading process = QqCpuTracker.Reading.parse(123, "QQ", text);
            ThreadCpuTracker.Reading thread = ThreadCpuTracker.Reading.parse(123, 124, "QQ", text);
            check(process.cpuTicks == value.cpuTicks && process.startTicks == value.startTicks
                    && thread.cpuTicks == value.cpuTicks && thread.lastCore == value.core && thread.name.equals(name), "public tracker compatibility");
        }
        String[] bad = {"123 S 0", "123 (bad) S 0", stat("bad", -1, 10, 10, 0, " "),
                stat("bad", Long.MAX_VALUE, 1, 10, 0, " "), stat("bad", 1, 0, -1, 0, " "), stat("bad", 1, 0, 10, -1, " "),
                stat("bad", 1, 0, 10, 0, " ").replace(" S ", " S ").replaceFirst(" 1 0 ", " 9223372036854775808 0 ")};
        for (String text : bad) {
            boolean rejected = false; try { ProcStat.parse(text, true); } catch (IllegalArgumentException expected) { rejected = true; }
            check(rejected, "invalid identity or counter rejected");
        }
        ProcStat max = ProcStat.parse(stat("max", Long.MAX_VALUE, 0, Long.MAX_VALUE, 7, " "), true);
        check(max.cpuTicks == Long.MAX_VALUE && max.startTicks == Long.MAX_VALUE, "64-bit maximum is valid without truncation");
        String runtime = " worker-123 [007] d..2 1.0: sched_stat_runtime: comm=worker pid=123 runtime=987654 [ns]";
        RuntimeLine r = RuntimeLine.parse(runtime);
        check(r != null && r.tid == 123 && r.core == 7 && r.runtime == 987654, "runtime fields preserved");
        check(RuntimeLine.parse(runtime.replace("[007]", "[128]")) == null, "invalid event core rejected");
        check(RuntimeLine.parse(runtime.replace("runtime=987654", "runtime=9223372036854775808")) == null, "runtime overflow rejected");
        check(RuntimeLine.parse(runtime.replace("pid=123", "pid=")) == null, "missing event identity rejected");
    }
    private static void files() throws Exception {
        File small = File.createTempFile("qq-collector-small", ".txt"), large = File.createTempFile("qq-collector-large", ".txt");
        try {
            String text = "QQ 线程\nnew (identity) ) end\n";
            Files.write(small.toPath(), text.getBytes(StandardCharsets.UTF_8));
            check(CollectorFileReader.read(small).equals(text), "UTF8 exact file content");
            byte[] data = new byte[CoreSnapshot.MAX_BYTES]; Arrays.fill(data, (byte)'x'); Files.write(large.toPath(), data);
            check(CollectorFileReader.read(large).length() == data.length, "exact maximum bound accepted");
            check(CollectorFileReader.read(small).equals(text), "small read does not expose previous large data");
            Files.write(large.toPath(), new byte[CoreSnapshot.MAX_BYTES + 1]);
            boolean rejected = false; try { CollectorFileReader.read(large); } catch (IOException expected) { rejected = true; }
            check(rejected, "oversized file rejected");
            Files.write(large.toPath(), "other worker data".getBytes(StandardCharsets.UTF_8));
            ExecutorService executor = Executors.newFixedThreadPool(3);
            try {
                List<Future<Boolean>> results = new ArrayList<Future<Boolean>>();
                for (int i = 0; i < 3; i++) {
                    final boolean first = i % 2 == 0;
                    results.add(executor.submit(() -> {
                        for (int n = 0; n < 300; n++) if (!CollectorFileReader.read(first ? small : large).equals(first ? text : "other worker data")) return false;
                        return true;
                    }));
                }
                for (Future<Boolean> f : results) check(f.get(), "parallel readers have independent buffers");
            } finally { executor.shutdownNow(); }
            Files.write(small.toPath(), new byte[0]); check(CollectorFileReader.read(small).isEmpty(), "empty file resets read length");
        } finally { small.delete(); large.delete(); }
    }
    private static void stream() throws Exception {
        StringBuilder text=new StringBuilder();List<String> expected=new ArrayList<String>();
        for(int i=0;i<500;i++) {
            String line=" worker-"+i+" [00"+(i%8)+"] d..2 1.0: sched_stat_runtime: comm=线程 pid="+(100+i)+" runtime="+(7000+i)+" [ns]";
            text.append(line).append('\n');expected.add((100+i)+":"+(i%8)+":"+(7000+i));
            if(i%11==0)text.append("LOST 10 EVENTS\n");
            if(i%17==0)text.append(line.replace("runtime="+(7000+i),"runtime=9223372036854775808")).append('\n');
        }
        text.append(" worker-900 [007] d..2 1.0: sched_stat_runtime: pid=900 runtime=3");expected.add("900:7:3");
        byte[] bytes=text.toString().getBytes(StandardCharsets.UTF_8);
        for(int size:new int[]{1,7,113,8192,65536}) {
            List<String> actual=new ArrayList<String>();
            InputStream input=new ByteArrayInputStream(bytes) {
                @Override public synchronized int read(byte[] dst,int offset,int len) {return super.read(dst,offset,Math.min(size,len));}
            };
            RuntimeStream.read(input,new RuntimeStream.Consumer() {
                public void batch(){}
                public void accept(int tid,int core,long runtime){actual.add(tid+":"+core+":"+runtime);}
            });
            check(expected.equals(actual),"complete trace events across byte split "+size);
        }
        byte[] huge=new byte[70000];Arrays.fill(huge,(byte)'x');
        ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(huge);out.write('\n');out.write(" x-1 [000] d..2 1.0: sched_stat_runtime: pid=1 runtime=2\n".getBytes(StandardCharsets.US_ASCII));
        List<Long> values=new ArrayList<Long>();
        RuntimeStream.read(new ByteArrayInputStream(out.toByteArray()),new RuntimeStream.Consumer(){public void batch(){}public void accept(int tid,int core,long runtime){values.add(runtime);}});
        check(values.equals(Arrays.asList(2L)),"oversized unknown record skipped without dropping following event");
    }
    public static void main(String[] args) throws Exception { parsing(); files(); stream(); System.out.println("PASS " + checks + " collector parser, stream and bounded reader checks"); }
}
