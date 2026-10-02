package com.lqb9.qqwatchmod;

import java.io.*;
import java.lang.reflect.*;
import static com.lqb9.qqwatchmod.ModuleInfo.*;
import io.github.libxposed.api.XposedInterface.Chain;
import io.github.libxposed.api.XposedInterface.Hooker;

/** QQ activity/plus-button adapter and enhancer tag handshake; owns no sampling or settings. */
final class PanelEntryHooks {
    private final HookGateway gateway;
    private final java.util.function.Consumer<String> logger;
    private boolean installed;
    private volatile boolean closed;
    PanelEntryHooks(HookGateway gateway,java.util.function.Consumer<String> logger) {this.gateway=gateway;this.logger=logger;}
    private static volatile java.lang.ref.WeakReference<android.view.View> armed =
            new java.lang.ref.WeakReference<android.view.View>(null);
    /** QQ 增强（或别的模块）挂上的长按监听，我们包一层，别把它吃掉 */
    private static volatile android.view.View.OnLongClickListener prevLongClick;

    /**
     * v1.11：两个模块的"会合点" = 同一个 View 上的 tag。
     *   - key 必须是资源 id 形态（View.setTag(int,Object) 要求 key >= 0x02000000）
     *   - 值的类型只能用 **boot classpath 的 java.lang.Runnable**：两个模块各是各的 ClassLoader，
     *     自定义接口跨模块 cast 会 ClassCastException，Runnable/View 这些框架类才是同一个。
     * 谁放了谁的 opener，对方就能直接调起来 —— 比"抢长按监听"稳得多。
     */
    static final int TAG_OPEN_WD = 0x7f0f0002;    // 我（看门狗）放：打开看门狗面板
    static final int TAG_OPEN_ENH = 0x7f0f0001;   // QQ 增强放：打开增强面板

    /** 面板里那个"打开 QQ 增强面板"按钮用的：从 + 的 tag 里取增强模块的 opener */
    static Runnable enhOpener() {
        try {
            android.view.View v = armed.get();
            if (v == null) return null;
            Object o = v.getTag(TAG_OPEN_ENH);
            return (o instanceof Runnable) ? (Runnable) o : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** 我自己的 opener（放在 tag 上给 QQ 增强跳转用） */
    private Runnable wdOpener() {
        return new Runnable() {
            public void run() {
                try {
                    if(!closed)WatchPanel.show(lastCtx.get());
                } catch (Throwable t) {
                    logger.accept(TAG + " opener ERR " + t);
                }
            }
        };
    }

    /** 最近一次 onResume 拿到的 Activity（面板和 opener 都要 Context） */
    private static volatile java.lang.ref.WeakReference<android.content.Context> lastCtx =
            new java.lang.ref.WeakReference<android.content.Context>(null);
    private static volatile boolean backedOff;   // 已经让给 QQ 增强，日志只打一次


    /** 我自己的监听（单例）—— 每次 onResume 靠它判断"现在挂的是不是我的" */
    private static final android.view.View.OnLongClickListener MY_LONG_CLICK =
            new android.view.View.OnLongClickListener() {
                public boolean onLongClick(android.view.View view) {
                    try {
                        WatchPanel.show(view.getContext());
                    } catch (Throwable t) {
                        // 面板炸了也不能把长按吞掉，交回给原来的监听
                        callPrevListener(view);
                    }
                    return true;    // 吃掉，别让 QQ 也处理
                }
            };

    void install() {
        if (installed || closed) return;
        installed=true;
        final String id = "WATCH:Activity#onResume";
        try {
            Class<?> act = Class.forName("android.app.Activity");
            Method m = act.getDeclaredMethod("onResume");
            m.setAccessible(true);
            gateway.install(m,id,new Hooker() {
                public Object intercept(Chain chain) throws Throwable {
                    Object r = chain.proceed();
                    try {
                        Object self = chain.getThisObject();
                        if (self instanceof android.app.Activity) {
                            android.app.Activity a = (android.app.Activity) self;
                            if (a.getClass().getName().indexOf("SplashActivity") >= 0) {
                                arm(a, 0);
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                    return r;
                }
            });
            logger.accept(TAG + " summon hook ok");
        } catch (Throwable t) {
            installed=false;
            logger.accept(TAG + " summon hook FAIL " + t);
        }
    }

    /**
     * 每次 onResume 连查几轮。
     *
     * ⚠️ 为什么不能只查一次：实测 2026-09-30 —— 13:18:55.790 我们挂上了（prevListener=false），
     * QQ 增强在同一次 onResume 的稍后时刻（它自己也 postDelayed 150ms）又挂了一次，
     * `setOnLongClickListener` 是覆盖语义，我们直接被顶掉，长按出来的是它的面板。
     * 所以这里一路盯到 2.5 秒：谁最后挂谁赢，我们查到它挂上之后再接管。
     */
    private static final long[] RETRY_MS = {150L, 350L, 700L, 1500L, 2500L};

    private void arm(final android.app.Activity a, final int attempt) {
        if (closed || attempt >= RETRY_MS.length) return;
        try {
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
                public void run() {
                    if(closed)return;
                    try {
                        android.view.View v = findPlus(a);
                        if (v != null) {
                            lastCtx = new java.lang.ref.WeakReference<android.content.Context>(a);
                            // 我自己的 opener 挂到 tag 上（给 QQ 增强的跳转行调）
                            try { v.setTag(TAG_OPEN_WD, wdOpener()); } catch (Throwable ignored) {}
                            Object enh = null;
                            try { enh = v.getTag(TAG_OPEN_ENH); } catch (Throwable ignored) {}
                            android.view.View.OnLongClickListener cur = readLongClick(v);
                            if (enh instanceof Runnable) {
                                // 装了 QQ 增强 → 长按归它（用户要求"优先 QQ 增强"），我们只留 tag
                                if (cur != MY_LONG_CLICK) {
                                    prevLongClick = (cur == null) ? prevLongClick : cur;
                                }
                                armed = new java.lang.ref.WeakReference<android.view.View>(v);
                                if (!backedOff) {
                                    backedOff = true;
                                    logger.accept(TAG + " long-press left to QQ 增强 (tag found)");
                                }
                            } else if (cur != MY_LONG_CLICK) {
                                armed = new java.lang.ref.WeakReference<android.view.View>(v);
                                // 别人的（QQ 增强的旧版）先存下来，面板里给按钮还回去
                                prevLongClick = (cur == null) ? prevLongClick : cur;
                                v.setOnLongClickListener(MY_LONG_CLICK);
                                logger.accept(TAG + " armed(try=" + attempt + ") on "
                                        + v.getClass().getSimpleName()
                                        + " prevListener=" + (prevLongClick != null));
                            } else {
                                armed = new java.lang.ref.WeakReference<android.view.View>(v);
                            }
                        }
                    } catch (Throwable t) {
                        logger.accept(TAG + " arm ERR " + t);
                    }
                    arm(a, attempt + 1);
                }
            }, RETRY_MS[attempt]);
        } catch (Throwable ignored) {
        }
    }

    static void callPrevListener(android.view.View v) {
        try {
            android.view.View.OnLongClickListener l = prevLongClick;
            if (l != null) l.onLongClick(v);
        } catch (Throwable ignored) {
        }
    }

    static boolean hasPrevListener() {
        return prevLongClick != null;
    }

    private static android.view.View.OnLongClickListener readLongClick(android.view.View v) {
        try {
            Field lf = android.view.View.class.getDeclaredField("mListenerInfo");
            lf.setAccessible(true);
            Object li = lf.get(v);
            if (li == null) return null;
            Field f2 = li.getClass().getDeclaredField("mOnLongClickListener");
            f2.setAccessible(true);
            return (android.view.View.OnLongClickListener) f2.get(li);
        } catch (Throwable t) {
            return null;
        }
    }

    private static android.view.View findPlus(android.app.Activity a) {
        try {
            int rid = a.getResources().getIdentifier("ba3", "id", "com.tencent.mobileqq");
            if (rid != 0) {
                android.view.View v = a.findViewById(rid);
                if (v != null) return v;
            }
        } catch (Throwable ignored) {
        }
        try {
            return walk(a.getWindow().getDecorView(), 0);
        } catch (Throwable t) {
            return null;
        }
    }

    private static android.view.View walk(android.view.View v, int depth) {
        if (v == null || depth > 24) return null;
        try {
            if (v instanceof android.widget.ImageView && v.isClickable()) {
                CharSequence cd = v.getContentDescription();
                String s = (cd == null) ? "" : cd.toString();
                if (s.indexOf("快捷入口") >= 0 || s.indexOf("更多") >= 0) {
                    int[] loc = new int[2];
                    v.getLocationOnScreen(loc);
                    if (loc[1] < 400) return v;     // 只看标题栏那一行
                }
            }
        } catch (Throwable ignored) {
        }
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                android.view.View r = walk(g.getChildAt(i), depth + 1);
                if (r != null) return r;
            }
        }
        return null;
    }
    static void showLoadAlert(final String message) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
            public void run() {
                try {
                    android.content.Context context = lastCtx.get();
                    if (context == null) {
                        Method app = Class.forName("android.app.ActivityThread")
                                .getDeclaredMethod("currentApplication");
                        context = (android.content.Context) app.invoke(null);
                    }
                    if (context != null) android.widget.Toast.makeText(context.getApplicationContext(),
                            message, android.widget.Toast.LENGTH_LONG).show();
                } catch (Throwable ignored) {}
            }
        });
    }
    void close() {
        closed=true;
        android.view.View view=armed.get();
        if(view!=null) {
            try {if(readLongClick(view)==MY_LONG_CLICK)view.setOnLongClickListener(prevLongClick);}
            catch(Throwable ignored){}
            try {view.setTag(TAG_OPEN_WD,null);}catch(Throwable ignored){}
        }
        armed=new java.lang.ref.WeakReference<android.view.View>(null);
        lastCtx=new java.lang.ref.WeakReference<android.content.Context>(null);
        prevLongClick=null;
    }
}

