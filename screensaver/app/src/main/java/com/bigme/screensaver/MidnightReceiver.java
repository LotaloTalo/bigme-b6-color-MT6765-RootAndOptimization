package com.bigme.screensaver;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.time.LocalDateTime;
import java.time.ZoneId;

/** 每天 00:00 触发一次图片更新，然后重新排下一次。 */
public class MidnightReceiver extends BroadcastReceiver {

    public static final int REQ = 1001;
    private static final String TAG = "BigmeDream";

    @Override
    public void onReceive(Context context, Intent intent) {
        SaverUpdater.update(context);
        schedule(context);
    }

    public static void schedule(Context context) {
        AlarmManager am = context.getSystemService(AlarmManager.class);
        if (am == null) return;
        long next = LocalDateTime.now().toLocalDate().plusDays(1).atStartOfDay()
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        Intent i = new Intent(context, MidnightReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(context, REQ, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        try {
            if (am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi);
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi);
            }
        } catch (Throwable t) {
            Log.w(TAG, "schedule alarm failed", t);
        }
    }
}
