package com.lqb9.qqwatchmod;

import java.lang.reflect.*;

/** Runtime QQ public-interface validation; no guessed obfuscated field name. */
final class GifHostAdapter {
        final Field owner;
        final Method start, stop, running, visible, recycle;
        volatile boolean hooksReady;
        GifHostAdapter(Class<?> type) throws Exception {
            Field found = null;
            for (Class<?> c = type; c != null; c = c.getSuperclass()) for (Field f : c.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers()) && f.getType().getName().equals(GifTaskRule.DRAWABLE)) {
                    if (found != null) throw new IllegalArgumentException("GIF owner field ambiguous");
                    found = f;
                }
            }
            if (found == null) throw new IllegalArgumentException("GIF owner field missing");
            found.setAccessible(true); owner = found;
            Class<?> c = owner.getType();
            start = api(c,"start",void.class); stop = api(c,"stop",void.class);
            running = api(c,"isRunning",boolean.class); recycle = api(c,"recycle",void.class);
            visible = c.getMethod("isVisible");
            if (visible.getReturnType() != boolean.class) throw new IllegalArgumentException("GIF visibility interface changed");
        }
        private static Method api(Class<?> c, String name, Class<?> result) throws Exception {
            Method m = c.getMethod(name);
            if (m.getReturnType() != result || Modifier.isStatic(m.getModifiers()) || m.getDeclaringClass() != c)
                throw new IllegalArgumentException("GIF public interface changed: " + name);
            return m;
        }
        Object owner(Object render) throws Exception {
            Object value = owner.get(render);
            if (value == null || !value.getClass().getName().equals(GifTaskRule.DRAWABLE))
                throw new IllegalArgumentException("GIF owner unavailable or subclass not verified");
            return value;
        }
        boolean running(Object value) throws Exception { return (Boolean)running.invoke(value); }
    }

