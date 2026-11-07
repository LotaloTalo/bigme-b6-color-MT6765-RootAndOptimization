package com.bigme.screensaver;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * 墨水屏屏保：底图 + 公历日期（不含时分秒）+ 农历。
 * 不做秒级刷新；每次熄屏触发时重新计算日期，并在跨天 00:00 后重绘一次。
 */
public class ScreenSaverDream extends DreamService {

    /** 自选底图路径：用户把这文件放到设备上即可替换底图。 */
    public static final String BG_PATH = "/sdcard/Pictures/bigme_screensaver.jpg";

    private TextView solarView;
    private TextView lunarView;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable midnightTask;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setFullscreen(true);
        setInteractive(false);
        setContentView(buildView());
        refresh();
        scheduleMidnight();
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        refresh();
    }

    @Override
    public void onDetachedFromWindow() {
        if (midnightTask != null) {
            handler.removeCallbacks(midnightTask);
            midnightTask = null;
        }
        super.onDetachedFromWindow();
    }

    private View buildView() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.WHITE);

        ImageView bg = new ImageView(this);
        bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        File f = new File(BG_PATH);
        if (f.exists()) {
            Bitmap bmp = BitmapFactory.decodeFile(f.getAbsolutePath());
            if (bmp != null) {
                bg.setImageBitmap(bmp);
            }
        }
        root.addView(bg, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);

        solarView = new TextView(this);
        solarView.setTextColor(Color.BLACK);
        solarView.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        solarView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 46f);
        solarView.setGravity(Gravity.CENTER);

        lunarView = new TextView(this);
        lunarView.setTextColor(Color.BLACK);
        lunarView.setTypeface(Typeface.SANS_SERIF);
        lunarView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 34f);
        lunarView.setGravity(Gravity.CENTER);
        lunarView.setPadding(0, 20, 0, 0);

        col.addView(solarView);
        col.addView(lunarView);

        root.addView(col, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));
        return root;
    }

    private void refresh() {
        LocalDate now = LocalDate.now();
        solarView.setText(String.format(Locale.CHINA, "%d.%02d  %02d  %s",
                now.getYear(), now.getMonthValue(), now.getDayOfMonth(), weekday(now)));
        lunarView.setText(LunarDate.format(now));
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

    /** 每天 00:00 后重绘一次，满足"跨天刷新一次"，其余时间不动屏幕。 */
    private void scheduleMidnight() {
        if (midnightTask != null) handler.removeCallbacks(midnightTask);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime next = now.toLocalDate().plusDays(1).atStartOfDay();
        long delay = Math.max(1000L, Duration.between(now, next).toMillis() + 1000L);
        midnightTask = () -> {
            refresh();
            scheduleMidnight();
        };
        handler.postDelayed(midnightTask, delay);
    }
}
