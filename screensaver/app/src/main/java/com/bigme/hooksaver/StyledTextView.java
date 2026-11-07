package com.bigme.hooksaver;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Typeface;
import android.widget.TextView;

/**
 * 文字绘制 View（继承 TextView，兼容 DailyTimeView 的 tvDay 引用）：
 * - 纯数字：Arial Regular + 黑-白-黑三层边（3-6-6）+ 中心挖空
 * - 其他（中文）：系统字体 + 黑色粗描边 + 白色填充
 */
public class StyledTextView extends TextView {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float d;
    private final PorterDuffXfermode clearMode = new PorterDuffXfermode(PorterDuff.Mode.CLEAR);

    public StyledTextView(Context context, String text, float textSize, Typeface typeface,
                          int fillColor, int outerColor, float outerWidth,
                          int innerColor, float innerWidth) {
        super(context);
        this.d = context.getResources().getDisplayMetrics().density;
        setText(text);
        boolean digitMode = text != null && text.matches("[0-9]+");

        Typeface tf;
        if (digitMode) {
            tf = Typeface.create("sans-serif", Typeface.BOLD);
            try {
                tf = Typeface.createFromAsset(context.getAssets(), "arial.ttf");
            } catch (Throwable ignore) {
            }
        } else {
            tf = Typeface.create("sans-serif-black", Typeface.BOLD);
        }
        paint.setTextSize(textSize);
        paint.setTypeface(tf);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeCap(Paint.Cap.ROUND);
        setTypeface(tf);
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, textSize);
        setTextColor(Color.TRANSPARENT);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        CharSequence cs = getText();
        if (cs == null || cs.length() == 0) return;
        String text = cs.toString();
        android.util.Log.e("BIGME_DRAW", "text=" + text + " w=" + getWidth() + " h=" + getHeight());
        boolean digitMode = text.matches("[0-9]+");

        float cx = getWidth() / 2f;
        Paint.FontMetrics fm = paint.getFontMetrics();
        float cy = getHeight() / 2f - (fm.ascent + fm.descent) / 2f;

        if (digitMode) {
            float innerBlack = 3f * d;
            float white = 6f * d;
            float outerBlack = 6f * d;

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2f * (outerBlack + white + innerBlack));
            paint.setColor(Color.BLACK);
            canvas.drawText(text, cx, cy, paint);

            paint.setStrokeWidth(2f * (white + innerBlack));
            paint.setColor(Color.WHITE);
            canvas.drawText(text, cx, cy, paint);

            paint.setStrokeWidth(2f * innerBlack);
            paint.setColor(Color.BLACK);
            canvas.drawText(text, cx, cy, paint);

            paint.setStyle(Paint.Style.FILL);
            paint.setXfermode(clearMode);
            canvas.drawText(text, cx, cy, paint);
            paint.setXfermode(null);
        } else {
            float stroke = 4f * d;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(stroke * 2f);
            paint.setColor(Color.BLACK);
            canvas.drawText(text, cx, cy, paint);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.WHITE);
            canvas.drawText(text, cx, cy, paint);
        }
    }
}
