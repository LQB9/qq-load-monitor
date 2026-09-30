package com.lqb9.qqwatchmod;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.*;

/** Native table with expandable identity/reason. No export entry. */
final class StatusTableView extends LinearLayout {
    private long revision = -1; private String expanded = "";
    private List<ProcessingHistory.Row> current = Collections.emptyList();
    StatusTableView(Context c) { super(c); setOrientation(VERTICAL); }
    void update(ProcessingHistory history) {
        if (revision == history.revision()) return;
        revision = history.revision(); current = history.snapshot(); drawRows();
    }
    private void drawRows() {
        removeAllViews();
        addView(DashboardView.label(getContext(), "最近 100 条 · 新记录在上方 · 点击查看详情\n仅在当前 QQ 会话保留，处理清单不导出", 10, CpuCharts.SUB));
        addView(cells("时间", "线程", "合计", "状态", true, false));
        if (current.isEmpty()) addView(DashboardView.label(getContext(), "暂无处理记录。有效采样超过阈值后显示实际结果。", 12, CpuCharts.SUB));
        SimpleDateFormat time = new SimpleDateFormat("HH:mm:ss", Locale.ROOT);
        for (final ProcessingHistory.Row row : current) {
            LinearLayout cells = cells(time.format(new Date(row.timeMs)), row.name + "\nTID " + row.tid,
                    CpuCharts.percent(row.load), row.state, false, row.state.equals("任务已结束"));
            cells.setOnClickListener(v -> { expanded = expanded.equals(row.id) ? "" : row.id; drawRows(); });
            addView(cells);
            if (expanded.equals(row.id)) {
                TextView detail = DashboardView.label(getContext(), row.reason + " · CPU " + CoreSnapshot.selection(row.mask)
                        + "\nPID " + row.pid + " · TID " + row.tid + " · 线程负载 " + CpuCharts.percent(row.threadLoad)
                        + "\n" + row.detail, 11, CpuCharts.SUB);
                detail.setTextIsSelectable(true); detail.setPadding(8, 8, 8, 14); addView(detail);
            }
        }
    }
    private LinearLayout cells(String a, String b, String c, String d, boolean header, boolean success) {
        LinearLayout line = new LinearLayout(getContext());
        line.setPadding(0, DashboardView.dp(getContext(), 12), 0, DashboardView.dp(getContext(), 12));
        line.setBackgroundColor(header ? 0xFFF3F5FA : (getChildCount() % 2 == 0 ? 0xFFFAFBFD : 0xFFFFFFFF));
        String[] texts = {a,b,c,d}; float[] widths = {1.05f,1.85f,1.05f,1.4f};
        for (int i=0;i<4;i++) {
            TextView cell = DashboardView.label(getContext(), texts[i], header ? 10 : 11,
                    i==3 && !header ? success ? 0xFF1B9073 : CpuCharts.PINK : CpuCharts.INK);
            cell.setPadding(DashboardView.dp(getContext(), 3),0,DashboardView.dp(getContext(), 3),0);
            if (header) cell.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            line.addView(cell, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, widths[i]));
        }
        return line;
    }
}
