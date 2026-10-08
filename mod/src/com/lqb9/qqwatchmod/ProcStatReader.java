package com.lqb9.qqwatchmod;

import android.system.Os;
import android.system.OsConstants;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Cached task-bound descriptors; pread offset zero regenerates live /proc stat data. */
final class ProcStatReader implements Closeable {
    private static final class OpenStat {
        final FileDescriptor descriptor; long scan;
        OpenStat(FileDescriptor descriptor) { this.descriptor = descriptor; }
    }
    private final Map<String, OpenStat> open = new HashMap<String, OpenStat>();
    private final byte[] buffer = new byte[4096];
    private final int capacity;
    private long scan;
    ProcStatReader() { this(descriptorCapacity()); }
    ProcStatReader(int capacity) { this.capacity = Math.max(0, Math.min(4096, capacity)); }
    synchronized void beginScan() { scan++; }
    synchronized int openCount() { return open.size(); }
    synchronized String read(File file) throws IOException {
        String path = file.getPath(); OpenStat stat = open.get(path);
        if (stat == null) {
            if (open.size() >= capacity) return CoreCollector.read(file);
            try { stat = new OpenStat(Os.open(path, OsConstants.O_RDONLY | OsConstants.O_CLOEXEC, 0)); }
            catch (android.system.ErrnoException error) { throw new IOException("Open stat failed: " + file, error); }
            open.put(path, stat);
        }
        stat.scan = scan;
        try {
            int count;
            for (;;) try { count = Os.pread(stat.descriptor, buffer, 0, buffer.length, 0); break; }
            catch (android.system.ErrnoException error) { if (error.errno != OsConstants.EINTR) throw error; }
            // proc stat is a single newline-terminated record. A large/partial
            // record uses the bounded full reader instead of trusting truncation.
            if (count > 0 && count < buffer.length && buffer[count - 1] == '\n')
                return new String(buffer, 0, count, StandardCharsets.UTF_8);
        } catch (android.system.ErrnoException | IOException error) {
            // A descriptor remains bound to its original task: after exit it
            // cannot be repurposed for a new task that happens to reuse the TID.
        }
        open.remove(path); release(stat);
        return CoreCollector.read(file);
    }
    synchronized void finishScan() {
        Iterator<OpenStat> entries = open.values().iterator();
        while (entries.hasNext()) {
            OpenStat stat = entries.next();
            if (stat.scan != scan) { entries.remove(); release(stat); }
        }
    }
    @Override public synchronized void close() { for (OpenStat stat : open.values()) release(stat); open.clear(); }
    private static void release(OpenStat stat) { try { Os.close(stat.descriptor); } catch (android.system.ErrnoException ignored) {} }
    private static int descriptorCapacity() {
        try {
            for (String line : CoreCollector.read(new File("/proc/self/limits")).split("\n")) {
                if (line.startsWith("Max open files")) {
                    String limit = line.trim().split("\\s+")[3];
                    if ("unlimited".equals(limit)) return 4096;
                    return (int)Math.min(4096, Math.max(0, Long.parseLong(limit) - 64));
                }
            }
        } catch (Exception ignored) {}
        return 0; // Preserve correctness through the full reader when limits are unknown.
    }
}
