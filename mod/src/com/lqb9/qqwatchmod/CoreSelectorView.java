package com.lqb9.qqwatchmod;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.LinearLayout;

/** Fixed-size core selectors, independent of QQ's themed checkbox drawables. */
public final class CoreSelectorView extends LinearLayout {
    private int mask;

    public CoreSelectorView(Context context, int initialMask) {
        super(context);
        mask = initialMask & 255;
        setOrientation(VERTICAL);
        for (int line = 0; line < 4; line++) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(HORIZONTAL);
            LayoutParams rowParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            rowParams.topMargin = dp(8);
            addView(row, rowParams);
            for (int col = 0; col < 2; col++) {
                final int core = line * 2 + col;
                CoreOption option = new CoreOption(context, core);
                LayoutParams params = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1);
                if (col == 0) params.rightMargin = dp(8);
                row.addView(option, params);
                option.setOnClickListener(view -> {
                    mask ^= 1 << core;
                    view.setSelected((mask & (1 << core)) != 0);
                    view.invalidate();
                });
                option.setSelected((mask & (1 << core)) != 0);
            }
        }
    }

    public int getMask() { return mask; }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private final class CoreOption extends View {
        private final int core;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF box = new RectF();
        private final Path tick = new Path();
        private final float textSize;

        CoreOption(Context context, int core) {
            super(context);
            this.core = core;
            textSize = 13 * getResources().getDisplayMetrics().scaledDensity;
            setFocusable(true);
            setContentDescription("CPU " + core);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        }

        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            paint.setTextSize(textSize);
            Paint.FontMetrics metrics = paint.getFontMetrics();
            int height = Math.max(dp(48), (int) Math.ceil(metrics.descent - metrics.ascent) + dp(24));
            setMeasuredDimension(resolveSize(dp(120), widthSpec), resolveSize(height, heightSpec));
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            boolean checked = (mask & (1 << core)) != 0;
            float stroke = Math.max(1, dp(1));
            box.set(stroke, stroke, getWidth() - stroke, getHeight() - stroke);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(checked ? 0xFFFFEDF4 : 0xFFF5F6FA);
            canvas.drawRoundRect(box, dp(10), dp(10), paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(isFocused() ? dp(2) : stroke);
            paint.setColor(checked || isFocused() ? CpuCharts.PINK : 0xFFE3E6ED);
            canvas.drawRoundRect(box, dp(10), dp(10), paint);

            float left = dp(12), top = (getHeight() - dp(18)) / 2f;
            box.set(left, top, left + dp(18), top + dp(18));
            paint.setStyle(checked ? Paint.Style.FILL : Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1.5f));
            paint.setColor(checked ? CpuCharts.PINK : 0xFF9AA2B1);
            canvas.drawRoundRect(box, dp(4), dp(4), paint);
            if (checked) {
                tick.reset();
                tick.moveTo(left + dp(4), top + dp(9));
                tick.lineTo(left + dp(8), top + dp(13));
                tick.lineTo(left + dp(14), top + dp(5));
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(2));
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setStrokeJoin(Paint.Join.ROUND);
                paint.setColor(0xFFFFFFFF);
                canvas.drawPath(tick, paint);
            }
            paint.setStyle(Paint.Style.FILL);
            paint.setTextSize(textSize);
            paint.setColor(checked ? 0xFFCC2865 : CpuCharts.INK);
            Paint.FontMetrics metrics = paint.getFontMetrics();
            canvas.drawText("CPU " + core, left + dp(28),
                    (getHeight() - metrics.ascent - metrics.descent) / 2f, paint);
        }

        @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setClassName("android.widget.CheckBox");
            info.setCheckable(true);
            info.setChecked((mask & (1 << core)) != 0);
        }
    }
}
