package com.lqb9.qqwatchdog;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

/** 开机自启：只有用户点过「启动」（want=true）才拉起来，没点过就不打扰 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        SharedPreferences sp = c.getSharedPreferences("cfg", Context.MODE_PRIVATE);
        if (!sp.getBoolean(Watchdog.K_WANT, false)) return;
        Intent s = new Intent(c, WatchService.class).setAction(WatchService.ACTION_START);
        try {
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(s);
            else c.startService(s);
        } catch (Throwable ignored) {}
    }
}
