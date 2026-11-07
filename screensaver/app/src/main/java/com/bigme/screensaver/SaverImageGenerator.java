package com.bigme.screensaver;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Locale;

/**
 * 生成屏保图片：底图（assets/background.jpg）+ 三行日期文字（白字黑描边），
 * 布局与 Python 版一致：YYYY年MM月 / DD（正中）/ 农历（干支年月日）。
 */
public final class SaverImageGenerator {

    private SaverImageGenerator() {
    }

    public static Bitmap loadBackground(Context ctx) {
        try {
            return BitmapFactory.decodeStream(ctx.getAssets().open("background.jpg"));
        } catch (IOException e) {
            return null;
        }
    }

    public static Bitmap generate(Context ctx, int w, int h, LocalDate date) {
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        c.drawColor(Color.WHITE);

        Bitmap bg = loadBackground(ctx);
        if (bg != null) {
            float scale = Math.max((float) w / bg.getWidth(), (float) h / bg.getHeight());
            int bw = Math.max(1, (int) (bg.getWidth() * scale));
            int bh = Math.max(1, (int) (bg.getHeight() * scale));
            Bitmap scaled = Bitmap.createScaledBitmap(bg, bw, bh, true);
            int left = (bw - w) / 2;
            int top = (bh - h) / 2;
            c.drawBitmap(scaled, new Rect(left, top, left + w, top + h),
                    new Rect(0, 0, w, h), null);
            scaled.recycle();
        }

        String ym = String.format(Locale.CHINA, "%d年 %02d月", date.getYear(), date.getMonthValue());
        String dd = String.format(Locale.CHINA, "%02d", date.getDayOfMonth());
        String lunar = LunarDate.format(date);

        float base = Math.min(w, h) / 824f; // 以 824 宽为基准缩放
        drawText(c, ym, 58f * 1.6f * base, w / 2f, h / 2f - 220f * 1.6f * base, 4f * base);
        drawText(c, dd, 210f * 1.6f * base, w / 2f, h / 2f, 5f * base);
        drawText(c, lunar, 52f * 1.6f * base, w / 2f, h / 2f + 220f * 1.6f * base, 4f * base);
        return out;
    }

    private static void drawText(Canvas c, String text, float size, float cx, float cy, float stroke) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        p.setTextSize(size);
        p.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics fm = p.getFontMetrics();
        // 让文字以 cy 为垂直中心
        float baseline = cy - (fm.ascent + fm.descent) / 2f;

        // 黑色描边
        Paint s = new Paint(p);
        s.setStyle(Paint.Style.STROKE);
        s.setStrokeWidth(stroke);
        s.setColor(Color.BLACK);
        c.drawText(text, cx, baseline, s);

        // 白色填充
        Paint f = new Paint(p);
        f.setStyle(Paint.Style.FILL);
        f.setColor(Color.WHITE);
        c.drawText(text, cx, baseline, f);
    }
}
