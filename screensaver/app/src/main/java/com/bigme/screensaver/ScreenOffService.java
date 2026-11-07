package com.bigme.screensaver;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

/**
 * 前台服务：开机/启动时生成当天屏保图并通过 su 写入大我屏保的 Sandbox，
 * 再安排每天 00:00 的更新。屏保显示本身仍由大我原生 ScreenSaveActivity 负责。
 */
public class ScreenOffService extends Service {

    static final String CHANNEL = "screensaver";

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(
                    CHANNEL, "屏保更新服务", NotificationManager.IMPORTANCE_MIN));
        }
        Notification n = new Notification.Builder(this, CHANNEL)
                .setContentTitle("Bigme Dream")
                .setContentText("每天 00:00 更新屏保图")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .build();
        startForeground(1, n);

        new Thread(() -> SaverUpdater.update(getApplicationContext())).start();
        MidnightReceiver.schedule(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
