package com.bigme.screensaver;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

/**
 * 熄屏时冻结番茄进程（cgroup v2）、亮屏时解冻，避免被系统杀死，解锁后能接着看。
 */
public class ScreenFreezeService extends Service {

    private static final String TAG = "BigmeFreeze";
    private static final String CHANNEL = "freeze";

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean screenOn = Intent.ACTION_SCREEN_ON.equals(intent.getAction());
            final boolean freeze = !screenOn;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    freezeTomato(freeze);
                }
            }).start();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(
                    CHANNEL, "冻结服务", NotificationManager.IMPORTANCE_MIN));
        }
        Notification n = new Notification.Builder(this, CHANNEL)
                .setContentTitle("Bigme Freeze")
                .setContentText("熄屏冻结番茄")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .build();
        startForeground(2, n);

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(receiver, f);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void freezeTomato(boolean freeze) {
        String cmd = "pid=$(pidof com.dragon.read); " +
                "if [ -n \"$pid\" ]; then " +
                "uid=$(stat -c %u /proc/$pid); " +
                "if [ -d \"/sys/fs/cgroup/uid_$uid/pid_$pid\" ]; then " +
                "echo " + (freeze ? "1" : "0") + " > /sys/fs/cgroup/uid_$uid/pid_$pid/cgroup.freeze; " +
                "fi; fi";
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
            int code = p.waitFor();
            Log.i(TAG, (freeze ? "freeze" : "unfreeze") + " code=" + code);
        } catch (Throwable t) {
            Log.w(TAG, "freeze err", t);
        }
    }
}
