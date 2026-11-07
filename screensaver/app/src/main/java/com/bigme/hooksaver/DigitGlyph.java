package com.bigme.hooksaver;

import android.graphics.Path;
import android.graphics.RectF;

/**
 * 数字字形（几何路径）：
 * - 8 = 上环（小）+ 下环（大），每个环 = 外弧 + 内弧 + 两端封口，
 *   腰部（两环交点处）**断开**，不是完整圆环，也没有中腰横线。
 * - 其他数字用对应的弧/线组合。
 * - 两位数（1-31）按两列并排。
 */
public final class DigitGlyph {

    private DigitGlyph() {
    }

    public static Path build(String s, float cx, float cy, float h) {
        Path out = new Path();
        if (s == null || s.isEmpty()) return out;
        float w = h * 0.58f;
        float gap = w * 0.32f;
        int n = s.length();
        float total = n * w + (n - 1) * gap;
        float startX = cx - total / 2f + w / 2f;
        for (int i = 0; i < n; i++) {
            float x = startX + i * (w + gap);
            addDigit(out, s.charAt(i), x, cy, h);
        }
        return out;
    }

    /** 单环：外弧 + 内弧 + 两端封口（上方环用上半弧，下方环用下半弧）。 */
    private static void addRing(Path p, float cx, float cy, float rOuter, float ringW, boolean topHalf) {
        float rInner = rOuter - ringW;
        RectF outer = new RectF(cx - rOuter, cy - rOuter, cx + rOuter, cy + rOuter);
        RectF inner = new RectF(cx - rInner, cy - rInner, cx + rInner, cy + rInner);
        if (topHalf) {
            p.addArc(outer, 180f, 180f);   // 上半外弧
            p.addArc(inner, 180f, 180f);   // 上半内弧
            // 左右封口（外弧端点 → 内弧端点）
            p.moveTo(cx - rOuter, cy);
            p.lineTo(cx - rInner, cy);
            p.moveTo(cx + rInner, cy);
            p.lineTo(cx + rOuter, cy);
        } else {
            p.addArc(outer, 0f, 180f);     // 下半外弧
            p.addArc(inner, 0f, 180f);     // 下半内弧
            p.moveTo(cx - rOuter, cy);
            p.lineTo(cx - rInner, cy);
            p.moveTo(cx + rInner, cy);
            p.lineTo(cx + rInner + (rOuter - rInner), cy);
        }
    }

    private static void addDigit(Path p, char d, float cx, float cy, float h) {
        float w = h * 0.58f;
        float l = cx - w / 2f, r = cx + w / 2f;
        float t = cy - h / 2f, b = cy + h / 2f;
        float mid = cy;
        float ringW = h * 0.075f;

        switch (d) {
            case '0':
                // 椭圆环
                p.addOval(new RectF(l, t, r, b), Path.Direction.CW);
                break;
            case '1':
                p.moveTo(cx, t + h * 0.12f);
                p.lineTo(cx, b);
                p.moveTo(cx - w * 0.18f, t + h * 0.24f);
                p.lineTo(cx, t + h * 0.12f);
                break;
            case '2':
                p.moveTo(l, mid - h * 0.12f);
                p.cubicTo(l, t, r, t, r, mid - h * 0.12f);
                p.lineTo(l, b);
                p.lineTo(r, b);
                break;
            case '3':
                p.moveTo(l, t + h * 0.10f);
                p.cubicTo(cx, t - h * 0.04f, r, t + h * 0.16f, r, mid);
                p.cubicTo(r, mid, r, b - h * 0.16f, cx, b);
                p.cubicTo(l, b + h * 0.04f, l, b - h * 0.10f, l, b - h * 0.10f);
                break;
            case '4':
                p.moveTo(r - w * 0.12f, t);
                p.lineTo(l, mid + h * 0.06f);
                p.lineTo(r, mid + h * 0.06f);
                p.moveTo(r - w * 0.12f, t);
                p.lineTo(r - w * 0.12f, b);
                break;
            case '5':
                p.moveTo(r, t);
                p.lineTo(l, t);
                p.lineTo(l, mid);
                p.lineTo(r, mid);
                p.cubicTo(r + w * 0.12f, b - h * 0.20f, cx, b, cx, b);
                p.cubicTo(l, b + h * 0.04f, l, b - h * 0.20f, l, b - h * 0.20f);
                break;
            case '6':
                // 下环 + 上弧
                addRing(p, cx, mid + h * 0.16f, h * 0.24f, ringW, false);
                p.moveTo(r, t + h * 0.16f);
                p.cubicTo(cx, t - h * 0.04f, l, t + h * 0.16f, l, mid);
                break;
            case '7':
                p.moveTo(l, t);
                p.lineTo(r, t);
                p.lineTo(l + w * 0.18f, b);
                break;
            case '8':
                // 上环（小）+ 下环（大），腰部断开
                addRing(p, cx, cy - h * 0.20f, h * 0.185f, ringW, true);
                addRing(p, cx, cy + h * 0.22f, h * 0.235f, ringW, false);
                break;
            case '9':
                // 上环 + 下弧
                addRing(p, cx, mid - h * 0.16f, h * 0.235f, ringW, true);
                p.moveTo(l, b - h * 0.14f);
                p.cubicTo(cx, b + h * 0.04f, r, b - h * 0.18f, r, mid);
                break;
            default:
                break;
        }
    }
}
