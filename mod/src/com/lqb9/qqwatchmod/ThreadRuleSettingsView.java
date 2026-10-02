package com.lqb9.qqwatchmod;
import android.content.Context;
/** Single-thread configuration uses the same rule switches and draft behavior as the total rule. */
final class ThreadRuleSettingsView extends LoadRuleSettingsView {
    ThreadRuleSettingsView(Context c,CpuLoadMonitor.Settings initial){super(c,initial,true);}
}
