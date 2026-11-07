package com.bigme.screensaver;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.time.LocalDate;
import java.util.Locale;

/**
 * 熄屏后由 ScreenOffService 拉起的全屏屏保：底图 + 日期（不含时分秒）。
 * 屏幕点亮（SCREEN_ON）时自动退出，回到正常界面。
 */
public class SaverActivity extends Activity {

    private BroadcastReceiver screenOnReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (android.os.Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(false);
        }
        setContentView(buildView());
    }

    @Override
    protected void onResume() {
        super.onResume();
        screenOnReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                    finish();
                }
            }
        };
        registerReceiver(screenOnReceiver, new IntentFilter(Intent.ACTION_SCREEN_ON));
    }

    @Override
    protected void onPause() {
        if (screenOnReceiver != null) {
            unregisterReceiver(screenOnReceiver);
            screenOnReceiver = null;
        }
        super.onPause();
    }

    private View buildView() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.WHITE);

        File f = new File(ScreenSaverDream.BG_PATH);
        if (f.exists()) {
            ImageView bg = new ImageView(this);
            bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Bitmap bmp = BitmapFactory.decodeFile(f.getAbsolutePath());
            if (bmp != null) {
                bg.setImageBitmap(bmp);
                root.addView(bg, new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));
            }
        }

        LocalDate now = LocalDate.now();
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);

        TextView solar = new TextView(this);
        solar.setTextColor(Color.BLACK);
        solar.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        solar.setTextSize(TypedValue.COMPLEX_UNIT_SP, 46f);
        solar.setGravity(Gravity.CENTER);
        solar.setText(String.format(Locale.CHINA, "%d.%02d  %02d  %s",
                now.getYear(), now.getMonthValue(), now.getDayOfMonth(), weekday(now)));

        TextView lunar = new TextView(this);
        lunar.setTextColor(Color.BLACK);
        lunar.setTypeface(Typeface.SANS_SERIF);
        lunar.setTextSize(TypedValue.COMPLEX_UNIT_SP, 34f);
        lunar.setGravity(Gravity.CENTER);
        lunar.setPadding(0, 20, 0, 0);
        lunar.setText(LunarDate.format(now));

        col.addView(solar);
        col.addView(lunar);
        root.addView(col, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));
        return root;
    }

    private static String weekday(LocalDate d) {
        switch (d.getDayOfWeek()) {
            case MONDAY: return "星期一";
            case TUESDAY: return "星期二";
            case WEDNESDAY: return "星期三";
            case THURSDAY: return "星期四";
            case FRIDAY: return "星期五";
            case SATURDAY: return "星期六";
            default: return "星期日";
        }
    }
}
