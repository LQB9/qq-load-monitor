package com.lqb9.qqwatchmod;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public final class LogTest {
    private static int checks;
    private static void check(boolean condition, String name) {
        checks++; if (!condition) throw new AssertionError(name);
    }
    private static String snapshot(RollingLog log) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(); log.writeSnapshot(output);
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }
    public static void main(String[] args) throws Exception {
        File root = Files.createTempDirectory("qqwatch-log-check-").toFile();
        try {
            RollingLog text = new RollingLog(new File(root, "utf8"), 512, 3);
            text.append(1000, "EVENT", "线程中文🙂\nCPU=200%\r\\path");
            String first = snapshot(text);
            check(first.contains("线程中文🙂\\nCPU=200%\\r\\\\path"), "UTF-8 and escaped line breaks");
            check(first.split("\n").length == 1, "one event cannot forge a second record");
            RollingLog reopened = new RollingLog(new File(root, "utf8"), 512, 3);
            reopened.append(2000, "SESSION", "restarted");
            check(snapshot(reopened).contains("线程中文") && snapshot(reopened).contains("restarted"), "restart keeps records");

            RollingLog rotated = new RollingLog(new File(root, "rotation"), 256, 3);
            for (int i = 0; i < 100; i++) rotated.append(i * 1000, "EVENT", String.format("record-%03d", i));
            String history = snapshot(rotated);
            check(!history.contains("record-000") && history.contains("record-099"), "rotation removes oldest and keeps newest");
            File[] parts = new File(root, "rotation").listFiles(); long bytes = 0; boolean bounded = true;
            for (File part : parts) { bytes += part.length(); bounded &= part.length() <= 256; }
            check(parts.length <= 3 && bounded && bytes <= 768, "rotation enforces total size bound");
            String[] lines = history.split("\n"); int previous = -1; boolean ordered = true;
            for (String line : lines) {
                int index = Integer.parseInt(line.substring(line.lastIndexOf("record-") + 7));
                ordered &= index > previous; previous = index;
            }
            check(ordered, "export order is oldest to newest across rotated files");

            RollingLog huge = new RollingLog(new File(root, "oversize"), 512, 2);
            StringBuilder emoji = new StringBuilder(); for (int i = 0; i < 10000; i++) emoji.append("🙂中文");
            huge.append(1000, "EVENT", emoji.toString());
            byte[] raw = Files.readAllBytes(new File(root, "oversize/watchdog.0.log").toPath());
            String decoded = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(raw)).toString();
            check(raw.length <= 512 && decoded.contains("[truncated]"), "oversize record stays bounded and valid Unicode");

            final RollingLog concurrent = new RollingLog(new File(root, "concurrent"), 65536, 2);
            final AtomicReference<Throwable> failure = new AtomicReference<Throwable>(); List<Thread> workers = new ArrayList<Thread>();
            for (int worker = 0; worker < 4; worker++) {
                final int id = worker;
                Thread thread = new Thread(new Runnable() {
                    public void run() {
                        try { for (int i = 0; i < 100; i++) { concurrent.append(i, "EVENT", "writer=" + id + " record=" + i); snapshot(concurrent); } }
                        catch (Throwable problem) { failure.set(problem); }
                    }
                }); workers.add(thread); thread.start();
            }
            for (Thread worker : workers) worker.join();
            String joined = snapshot(concurrent);
            check(failure.get() == null && joined.split("\n").length == 400, "concurrent append and export lose no records");
            check(joined.contains("writer=0 record=99") && joined.contains("writer=3 record=99"), "concurrent writers complete");

            boolean failed = false;
            try { concurrent.writeSnapshot(new OutputStream() { public void write(int value) throws IOException { throw new IOException("disk full"); } }); }
            catch (IOException expected) { failed = true; }
            check(failed && snapshot(concurrent).equals(joined), "failed export preserves source logs");
            failed = false;
            File notDirectory = new File(root, "blocked"); Files.write(notDirectory.toPath(), new byte[]{1});
            try { new RollingLog(notDirectory, 256, 2).append(1000, "EVENT", "fail"); }
            catch (IOException expected) { failed = true; }
            check(failed, "write failures are reported");
            failed = false;
            try { snapshot(new RollingLog(new File(root, "empty"), 256, 2)); }
            catch (IOException expected) { failed = true; }
            check(failed, "empty export is reported instead of claiming success");
            System.out.println("PASS " + checks + " persistent log checks");
        } finally { delete(root); }
    }
    private static void delete(File file) throws IOException {
        if (file.isDirectory()) for (File child : file.listFiles()) delete(child);
        Files.delete(file.toPath());
    }
}
