package com.lqb9.qqwatchmod;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Independent reusable buffers for the discovery and event-reader threads. */
final class CollectorFileReader {
    private static final ThreadLocal<Buffer> LOCAL = new ThreadLocal<Buffer>() {
        @Override protected Buffer initialValue() { return new Buffer(); }
    };
    private static final class Buffer { byte[] bytes = new byte[2048]; }
    static String read(File file) throws IOException {
        try (FileInputStream input = new FileInputStream(file)) {
            return read(input);
        }
    }
    static String read(InputStream input) throws IOException {
        Buffer buffer = LOCAL.get(); int used = 0;
        for (;;) {
            if (used == buffer.bytes.length) {
                if (used == CoreSnapshot.MAX_BYTES) {
                    if (input.read() != -1) throw new IOException("File too large");
                    break;
                }
                buffer.bytes = Arrays.copyOf(buffer.bytes, Math.min(CoreSnapshot.MAX_BYTES, used * 2));
            }
            int count = input.read(buffer.bytes, used, buffer.bytes.length - used);
            if (count < 0) break;
            if (count == 0) { int b = input.read(); if (b < 0) break; buffer.bytes[used++] = (byte)b; }
            else used += count;
        }
        return new String(buffer.bytes, 0, used, StandardCharsets.UTF_8);
    }
    private CollectorFileReader() {}
}
