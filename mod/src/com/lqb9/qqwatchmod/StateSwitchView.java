package com.lqb9.qqwatchmod;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Checkable;
import android.widget.TextView;

/** Shared fixed-size switch; QQ's themed drawables cannot change its geometry. */
final class StateSwitchView extends TextView implements Checkable {
    private boolean checked;
    private String unavailable="当前不可操作";
    private final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF track=new RectF();
    StateSwitchView(Context c){super(c);setTextSize(13);setIncludeFontPadding(false);setLineSpacing(dp(3),1);
        setGravity(Gravity.CENTER_VERTICAL);setPadding(dp(12),dp(12),dp(66),dp(12));setMinHeight(dp(64));setFocusable(true);setChecked(false);}
    private int dp(float n){return DashboardView.dp(getContext(),n);}
    void show(String title,String state,boolean checked,boolean enabled){
        setText(title+"\n"+state);unavailable=state;setEnabled(enabled);setChecked(checked);
    }
    @Override public boolean isChecked(){return checked;}
    @Override public void toggle(){setChecked(!checked);}
    @Override public void setChecked(boolean value){checked=value;setSelected(value);setTextColor(isEnabled() && checked?0xFF1B9073:CpuCharts.SUB);
        GradientDrawable bg=new GradientDrawable();bg.setCornerRadius(dp(10));bg.setColor(isEnabled() && checked?0xFFEAF7F2:0xFFF0F2F6);bg.setStroke(dp(1),isEnabled() && checked?0xFF85C7B7:0xFFDFE3EB);setBackground(bg);
        setContentDescription(getText());if(android.os.Build.VERSION.SDK_INT>=30)setStateDescription(isEnabled()?(checked?"开启":"关闭"):unavailable);invalidate();}
    @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);float x=getWidth()-dp(52),y=(getHeight()-dp(24))/2f;
        track.set(x,y,x+dp(40),y+dp(24));ink.setColor(isEnabled() && checked?0xFF1B9073:0xFFB5BDCB);canvas.drawRoundRect(track,dp(12),dp(12),ink);
        ink.setColor(0xFFFFFFFF);canvas.drawCircle(x+dp(checked?28:12),y+dp(12),dp(8),ink);}
    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(info);info.setClassName("android.widget.Switch");info.setCheckable(true);info.setChecked(checked);}
}
