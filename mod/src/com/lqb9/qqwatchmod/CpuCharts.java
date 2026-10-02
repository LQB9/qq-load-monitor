package com.lqb9.qqwatchmod;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.view.View;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Small native charts, with measured units and text equivalents for accessibility. */
final class CpuCharts {
    static final int INK = 0xFF202432, SUB = 0xFF778094, LINE = 0xFFE9EDF4;
    static final int PINK = 0xFFFF3D7F, BLUE = 0xFF597CF2, RED = 0xFFD94B5B;
    static String percent(double value) { return String.format(Locale.ROOT, "%.1f%%", value); }

    static abstract class Chart extends View {
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final float density, font;
        boolean stale;
        Chart(Context context) {
            super(context);
            density = context.getResources().getDisplayMetrics().density;
            font = Math.min(1.35f, context.getResources().getDisplayMetrics().scaledDensity / density);
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        }
        float d(float n) { return n * density; }
        void text(Canvas c, String s, float x, float y, int color, float size, Paint.Align align) {
            paint.setStyle(Paint.Style.FILL);
            paint.setPathEffect(null);
            paint.setTypeface(Typeface.DEFAULT);
            paint.setColor(color);
            paint.setTextSize(d(size) * font);
            paint.setTextAlign(align);
            c.drawText(s, x, y, paint);
        }
        void line(Canvas c, float x, float y, float x2, float y2, int color) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(color);
            paint.setStrokeWidth(d(1));
            paint.setPathEffect(null);
            c.drawLine(x, y, x2, y2, paint);
        }
        void bar(Canvas c, float left, float top, float right, float bottom, int color) {
            if (right <= left) return;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(color);
            paint.setPathEffect(null);
            c.drawRoundRect(left, top, right, bottom, d(3), d(3), paint);
        }
        String fit(String name, float width, float size) {
            paint.setTextSize(d(size) * font);
            if (paint.measureText(name) <= width) return name;
            int end = name.length();
            while (end > 0 && paint.measureText(name.substring(0, end) + "…") > width) end--;
            return name.substring(0, end) + "…";
        }
        int dataColor(int color) { return stale ? SUB : color; }
    }

    static final class Trend extends Chart {
        List<LoadHistory.Point> points = Collections.emptyList();
        int threshold = 200;
        boolean showLimit=true;
        Trend(Context c) { super(c); }
        void showLimit(boolean show){showLimit=show;invalidate();}
        void update(List<LoadHistory.Point> data, int threshold, boolean stale) {
            this.points = data;
            this.threshold = threshold;
            this.stale = stale;
            StringBuilder description = new StringBuilder("QQ CPU 趋势，单核满载100%，阈值" + threshold + "%。");
            for (LoadHistory.Point p : data) description.append(p.cpu < 0 ? "数据不可用；" : percent(p.cpu) + "；");
            setContentDescription(description);
            invalidate();
        }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            double ceiling = Math.max(100, threshold * 1.2);
            for (LoadHistory.Point p : points) if (p.cpu >= 0) ceiling = Math.max(ceiling, p.cpu * 1.1);
            ceiling = Math.ceil(ceiling / 100) * 100;
            paint.setTextSize(d(9) * font);
            float left = Math.max(d(41), paint.measureText(String.format(Locale.ROOT, "%.0f%%", ceiling)) + d(8));
            float right = getWidth() - d(8), top = d(24), bottom = getHeight() - d(24);
            for (int i = 0; i <= 2; i++) {
                float y = bottom - (bottom - top) * i / 2;
                line(c, left, y, right, y, LINE);
                text(c, String.format(Locale.ROOT, "%.0f%%", ceiling * i / 2), left - d(6), y + d(3), SUB, 9, Paint.Align.RIGHT);
            }
            float limitY = bottom - (float) (threshold / ceiling) * (bottom - top);
            paint.setColor(RED); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(d(1));
            paint.setPathEffect(new DashPathEffect(new float[]{d(4), d(3)}, 0));
            if(showLimit)c.drawLine(left, limitY, right, limitY, paint);
            paint.setPathEffect(null);
            text(c, showLimit?"合计阈值 " + threshold + "%":"合计检测关闭 · 负载参考", right, d(12), showLimit?RED:SUB, 10, Paint.Align.RIGHT);
            if (points.isEmpty()) {
                text(c, "等待有效采样", (left + right) / 2, (top + bottom) / 2, SUB, 12, Paint.Align.CENTER);
                return;
            }
            long first = points.get(0).elapsedMs, last = points.get(points.size() - 1).elapsedMs;
            long span = Math.max(1000, last - first);
            Path path = new Path();
            boolean previous = false, anyValid = false;
            for (LoadHistory.Point p : points) {
                if (p.cpu < 0) { previous = false; continue; }
                anyValid = true;
                float x = points.size() == 1 ? right : left + (right - left) * (p.elapsedMs - first) / span;
                float y = bottom - (float) (p.cpu / ceiling) * (bottom - top);
                if (previous && p.connected) path.lineTo(x, y); else path.moveTo(x, y);
                previous = true;
                paint.setStyle(Paint.Style.FILL); paint.setColor(dataColor(showLimit && p.cpu >= threshold ? RED : PINK));
                c.drawCircle(x, y, d(points.size() < 4 ? 3 : 1.6f), paint);
            }
            paint.setColor(dataColor(PINK)); paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(d(2)); paint.setStrokeJoin(Paint.Join.ROUND);
            c.drawPath(path, paint);
            if (!anyValid) text(c, "当前无有效 CPU 数据", (left + right) / 2, (top + bottom) / 2, SUB, 11, Paint.Align.CENTER);
            text(c, (last - first) / 1000 + " 秒前", left, getHeight() - d(5), SUB, 9, Paint.Align.LEFT);
            text(c, "最近采样", right, getHeight() - d(5), SUB, 9, Paint.Align.RIGHT);
        }
    }

    static final class ThreadTrend extends Chart {
        static final int[] COLORS={PINK,BLUE,0xFF1B9073};
        List<ThreadLoadHistory.Frame> frames=Collections.emptyList();List<CoreTracker.Detail> identities=Collections.emptyList();int threshold=80;
        ThreadTrend(Context c){super(c);}
        void update(List<ThreadLoadHistory.Frame> points,List<CoreTracker.Detail> ids,int threshold,boolean stale){
            frames=points;identities=ids;this.threshold=threshold;this.stale=stale;
            StringBuilder text=new StringBuilder("单线程所选核心负载趋势，阈值 "+threshold+"%。每条曲线按独立启动身份。");
            for(CoreTracker.Detail t:ids){text.append(t.thread.name).append(" PID ").append(t.thread.pid).append(" TID ").append(t.thread.tid).append("：");for(ThreadLoadHistory.Frame f:points){ThreadLoadHistory.Reading r=f.threads.get(t.thread.key());text.append(r==null?"缺失；":percent(r.cpu)+"；");}}
            setContentDescription(text);invalidate();
        }
        @Override protected void onDraw(Canvas c){
            double scale=100;for(CoreTracker.Detail t:identities)for(ThreadLoadHistory.Frame f:frames){ThreadLoadHistory.Reading r=f.threads.get(t.thread.key());if(r!=null)scale=Math.max(scale,Math.ceil(r.cpu/50)*50);}
            float left=d(41),right=getWidth()-d(8),top=d(25),bottom=getHeight()-d(24);
            for(int i=0;i<3;i++){float y=bottom-(bottom-top)*i/2;line(c,left,y,right,y,LINE);text(c,String.format(Locale.ROOT,"%.0f%%",scale*i/2),left-d(6),y+d(3),SUB,9,Paint.Align.RIGHT);}
            paint.setColor(RED);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(d(1));paint.setPathEffect(new DashPathEffect(new float[]{d(4),d(3)},0));float y=bottom-(float)(threshold/scale)*(bottom-top);c.drawLine(left,y,right,y,paint);paint.setPathEffect(null);
            text(c,"单线程阈值 "+threshold+"%",right,d(12),RED,10,Paint.Align.RIGHT);
            if(frames.isEmpty()){text(c,"等待线程趋势采样",(left+right)/2,(top+bottom)/2,SUB,11,Paint.Align.CENTER);return;}
            long first=frames.get(0).elapsedMs,last=frames.get(frames.size()-1).elapsedMs,span=Math.max(1000,last-first);boolean any=false;
            for(int i=0;i<identities.size();i++){
                String key=identities.get(i).thread.key();Path path=new Path();boolean previous=false;
                for(ThreadLoadHistory.Frame f:frames){ThreadLoadHistory.Reading r=f.threads.get(key);if(r==null){previous=false;continue;}any=true;float x=frames.size()==1?right:left+(right-left)*(f.elapsedMs-first)/span;float v=bottom-(float)(r.cpu/scale)*(bottom-top);
                    if(previous && f.connected)path.lineTo(x,v);else path.moveTo(x,v);previous=true;paint.setStyle(Paint.Style.FILL);paint.setColor(dataColor(COLORS[i]));c.drawCircle(x,v,d(2),paint);
                }
                paint.setStyle(Paint.Style.STROKE);paint.setPathEffect(null);paint.setStrokeWidth(d(2));paint.setColor(dataColor(COLORS[i]));c.drawPath(path,paint);
            }
            if(!any)text(c,"当前线程还没有历史点",(left+right)/2,(top+bottom)/2,SUB,10,Paint.Align.CENTER);
            text(c,(last-first)/1000+" 秒前",left,getHeight()-d(5),SUB,9,Paint.Align.LEFT);text(c,"最近采样",right,getHeight()-d(5),SUB,9,Paint.Align.RIGHT);
        }
    }
    static final class Cores extends Chart {
        List<CoreFrequency.Core> cores = Collections.emptyList();
        Cores(Context c) { super(c); }
        void update(List<CoreFrequency.Core> data, boolean stale) {
            boolean sizeChanged = cores.size() != data.size();
            cores = data; this.stale = stale;
            StringBuilder description = new StringBuilder("各核心当前频率，单位MHz。");
            for (CoreFrequency.Core core : data) description.append("核心").append(core.id).append(' ')
                    .append(!core.online ? "离线" : core.khz <= 0 ? "不可读" : core.khz / 1000 + "MHz")
                    .append(core.hardwareReading ? "硬件读数；" : "驱动请求；");
            setContentDescription(description);
            if (sizeChanged) requestLayout();
            invalidate();
        }
        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            setMeasuredDimension(View.MeasureSpec.getSize(widthSpec), (int) d(Math.max(2, (cores.size() + 1) / 2) * 44 + 24));
        }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            long scale = 1000;
            for (CoreFrequency.Core core : cores) if (core.online) scale = Math.max(scale, (core.khz + 999) / 1000);
            scale = ((scale + 499) / 500) * 500;
            float cell = (getWidth() - d(18)) / 2;
            if (cores.isEmpty()) text(c, "等待核心频率采样", getWidth() / 2f, d(48), SUB, 12, Paint.Align.CENTER);
            for (int i = 0; i < cores.size(); i++) {
                CoreFrequency.Core core = cores.get(i);
                float x = i % 2 * (cell + d(18)), y = i / 2 * d(44) + d(14);
                text(c, "核心 " + core.id, x, y, INK, 10.5f, Paint.Align.LEFT);
                String value = !core.online ? "离线" : core.khz <= 0 ? "不可读" : core.khz / 1000 + " MHz";
                text(c, value, x + cell, y, SUB, 10.5f, Paint.Align.RIGHT);
                bar(c, x, y + d(8), x + cell, y + d(14), LINE);
                if (core.online && core.khz > 0) bar(c, x, y + d(8),
                        x + cell * Math.min(1f, core.khz / 1000f / scale), y + d(14), dataColor(BLUE));
            }
            text(c, "共同刻度 0—" + scale + " MHz", 0, getHeight() - d(3), SUB, 9, Paint.Align.LEFT);
        }
    }

    static final class CoreLoad extends Chart {
        double[] loads = {-1,-1,-1,-1,-1,-1,-1,-1}; int mask = 255;
        CoreLoad(Context c) { super(c); }
        void update(double[] loads, int mask, boolean stale) {
            this.loads = loads.clone(); this.mask = mask; this.stale = stale; invalidate();
            setContentDescription("QQ 各核心负载，选择核心 " + CoreSnapshot.selection(mask));
        }
        @Override protected void onMeasure(int w, int h) { setMeasuredDimension(View.MeasureSpec.getSize(w), (int)d(194)); }
        @Override protected void onDraw(Canvas c) {
            float cell = (getWidth() - d(18)) / 2;
            for (int i = 0; i < 8; i++) {
                float x = i % 2 * (cell + d(18)), y = i / 2 * d(44) + d(14);
                boolean selected = (mask & (1 << i)) != 0;
                text(c, (selected ? "● " : "○ ") + "CPU " + i, x, y, selected ? INK : SUB, 10, Paint.Align.LEFT);
                text(c, loads[i] < 0 ? "—" : percent(loads[i]), x + cell, y, dataColor(selected ? PINK : SUB), 10, Paint.Align.RIGHT);
                bar(c, x, y+d(8), x+cell, y+d(14), LINE);
                if (loads[i] >= 0) bar(c, x, y+d(8), x+cell*(float)Math.min(1, loads[i]/100), y+d(14), dataColor(selected ? PINK : 0xFFCBD0DC));
            }
            text(c, "每核 0—100% · ● 计入合计", 0, getHeight()-d(3), SUB, 9, Paint.Align.LEFT);
        }
    }
    /** Load bars and MHz labels share a core row, with independent units and no frequency scaling. */
    static final class CoreMetrics extends Chart {
        double[] loads = {-1,-1,-1,-1,-1,-1,-1,-1};
        List<CoreFrequency.Core> frequencies = Collections.emptyList();
        int mask=255,columns=1;
        CoreMetrics(Context c){super(c);}
        void updateLoads(double[] values,int selected,boolean stale){loads=values.clone();mask=selected;this.stale=stale;describe();invalidate();}
        void updateFrequency(List<CoreFrequency.Core> data,boolean stale){frequencies=data;this.stale=stale;describe();invalidate();}
        CoreFrequency.Core frequency(int id){for(CoreFrequency.Core c:frequencies)if(c.id==id)return c;return null;}
        String frequencyLabel(int id){CoreFrequency.Core c=frequency(id);return c==null?"频率 —":!c.online?"离线":c.khz<=0?"频率不可读":c.khz/1000+" MHz";}
        void describe(){StringBuilder s=new StringBuilder(stale?"上次采样已过期。":"QQ 各核心负载，单核100%。");for(int i=0;i<8;i++){CoreFrequency.Core f=frequency(i);s.append("CPU ").append(i).append((mask&(1<<i))!=0?" 已选择，":" 未选择，").append(loads[i]<0?"负载未知":percent(loads[i])).append("，").append(frequencyLabel(i)).append(f==null?"。":f.hardwareReading?" 硬件读数。":" 驱动请求。");}setContentDescription(s);}
        @Override protected void onMeasure(int width,int height){int w=View.MeasureSpec.getSize(width);columns=w/density>=280 && font<=1.15f?2:1;setMeasuredDimension(w,(int)d((8/columns)*(columns==2?58:36*font)));}
        @Override protected void onDraw(Canvas c){
            float cell=(getWidth()-d(columns==2?18:0))/columns;
            for(int i=0;i<8;i++){
                boolean selected=(mask&(1<<i))!=0;float x=(i%columns)*(cell+d(18)),y=(i/columns)*d(columns==2?58:36*font)+d(14*font);
                text(c,(selected?"● ":"○ ")+"CPU "+i,x,y,selected?INK:SUB,10,Paint.Align.LEFT);
                String load=loads[i]<0?"—":percent(loads[i]);
                if(columns==2){
                    text(c,load,x+cell,y,dataColor(selected?PINK:SUB),10,Paint.Align.RIGHT);
                    bar(c,x,y+d(7),x+cell,y+d(12),LINE);if(loads[i]>=0)bar(c,x,y+d(7),x+cell*(float)Math.min(1,loads[i]/100),y+d(12),dataColor(selected?PINK:0xFFCBD0DC));
                    text(c,frequencyLabel(i),x,y+d(27),SUB,9.5f,Paint.Align.LEFT);
                }else{
                    text(c,load,x+cell*.55f,y,dataColor(selected?PINK:SUB),10,Paint.Align.RIGHT);
                    text(c,frequencyLabel(i),x+cell,y,SUB,9.5f,Paint.Align.RIGHT);
                    bar(c,x,y+d(7),x+cell,y+d(12),LINE);if(loads[i]>=0)bar(c,x,y+d(7),x+cell*(float)Math.min(1,loads[i]/100),y+d(12),dataColor(selected?PINK:0xFFCBD0DC));
                }
            }
        }
    }
    static final class Rank {
        final String name, note;
        final double cpu;
        Rank(String name, String note, double cpu) { this.name = name; this.note = note; this.cpu = cpu; }
    }
    static final class Ranking extends Chart {
        List<Rank> ranks = Collections.emptyList();
        final boolean perThread;
        final int limit;
        Ranking(Context c, boolean perThread, int limit) { super(c); this.perThread = perThread; this.limit = limit; }
        void update(List<Rank> data, boolean stale) {
            int old = Math.min(limit, ranks.size());
            ranks = data; this.stale = stale;
            StringBuilder description = new StringBuilder(perThread ? "线程CPU排行。" : "进程CPU排行。");
            for (Rank r : data) description.append(r.name).append(' ').append(r.cpu < 0 ? "等待采样" : percent(r.cpu))
                    .append(' ').append(r.note).append('；');
            setContentDescription(description);
            if (old != Math.min(limit, data.size())) requestLayout();
            invalidate();
        }
        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            setMeasuredDimension(View.MeasureSpec.getSize(widthSpec), (int) d(Math.max(1, Math.min(limit, ranks.size())) * 54 + 18));
        }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            double scale = 100;
            if (!perThread) for (Rank rank : ranks) scale = Math.max(scale, Math.ceil(rank.cpu / 100) * 100);
            float width = getWidth();
            if (ranks.isEmpty()) text(c, "等待有效采样", width / 2, d(35), SUB, 12, Paint.Align.CENTER);
            for (int i = 0; i < Math.min(limit, ranks.size()); i++) {
                Rank rank = ranks.get(i);
                float y = d(i * 54 + 15);
                text(c, fit(rank.name, width - d(67), 11), 0, y, INK, 11, Paint.Align.LEFT);
                text(c, rank.cpu < 0 ? "—" : percent(rank.cpu), width, y, dataColor(i == 0 ? PINK : SUB), 11, Paint.Align.RIGHT);
                bar(c, 0, y + d(7), width, y + d(13), LINE);
                if (rank.cpu >= 0) bar(c, 0, y + d(7), width * (float) Math.min(1, rank.cpu / scale), y + d(13),
                        dataColor(i == 0 ? PINK : BLUE));
                text(c, fit(rank.note, width, 9), 0, y + d(28), SUB, 9, Paint.Align.LEFT);
            }
            text(c, "共同刻度 0—" + (int) scale + "%", width, getHeight() - d(3), SUB, 9, Paint.Align.RIGHT);
        }
    }
}
