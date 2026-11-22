package com.siddhesh.standby;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;

import java.util.Calendar;

/** Shared colours, fonts and drawing helpers. */
final class Ui {
    private Ui() {}

    /** Accent colours (iOS system palette). */
    static final int[] ACCENTS = {
            0xFFFFFFFF, // white
            0xFFFF453A, // red
            0xFFFF9F0A, // orange
            0xFFFFD60A, // yellow
            0xFF30D158, // green
            0xFF64D2FF, // teal
            0xFF0A84FF, // blue
            0xFFBF5AF2, // purple
            0xFFFF375F, // pink
    };

    static final int CARD = 0xFF1C1C1E;
    static final int CARD_2 = 0xFF2C2C2E;
    static final int GRAY = 0xFF8E8E93;
    static final int GREEN = 0xFF30D158;
    static final int RED = 0xFFFF453A;

    static final Typeface BLACK = Typeface.create("sans-serif-black", Typeface.NORMAL);
    static final Typeface BOLD = Typeface.create("sans-serif", Typeface.BOLD);
    static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    static final Typeface REGULAR = Typeface.create("sans-serif", Typeface.NORMAL);
    static final Typeface LIGHT = Typeface.create("sans-serif-light", Typeface.NORMAL);

    static int accent(int idx) {
        return ACCENTS[Math.floorMod(idx, ACCENTS.length)];
    }

    static int alpha(int color, int a) {
        a = Math.max(0, Math.min(255, a));
        return (color & 0x00FFFFFF) | (a << 24);
    }

    static int lerp(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int ra = Math.round(aa + (ba - aa) * t);
        int rr = Math.round(ar + (br - ar) * t);
        int rg = Math.round(ag + (bg - ag) * t);
        int rb = Math.round(ab + (bb - ab) * t);
        return (ra << 24) | (rr << 16) | (rg << 8) | rb;
    }

    /** "9:41" or "21:41" — StandBy shows no AM/PM. */
    static String time(Calendar c, boolean h24) {
        int m = c.get(Calendar.MINUTE);
        if (h24) {
            return String.format(java.util.Locale.US, "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), m);
        }
        int h = c.get(Calendar.HOUR);
        if (h == 0) h = 12;
        return String.format(java.util.Locale.US, "%d:%02d", h, m);
    }

    static String amPm(Calendar c) {
        return c.get(Calendar.AM_PM) == Calendar.AM ? "AM" : "PM";
    }

    /** Sets the paint's text size so that {@code s} fits inside maxW x maxH. Returns the size. */
    static float fit(Paint p, String s, float maxW, float maxH, Rect tmp) {
        p.setTextSize(200f);
        p.getTextBounds(s, 0, s.length(), tmp);
        float w = Math.max(1f, p.measureText(s));
        float h = Math.max(1f, tmp.height());
        float size = 200f * Math.min(maxW / w, maxH / h);
        p.setTextSize(size);
        return size;
    }

    /** Fits width only, capped at maxSize. */
    static void fitWidth(Paint p, String s, float maxW, float maxSize) {
        p.setTextSize(maxSize);
        float w = p.measureText(s);
        if (w > maxW && w > 0) p.setTextSize(maxSize * maxW / w);
    }

    /** Draws text horizontally aligned per paint's alignment, vertically centred on cy. */
    static void textMid(Canvas c, Paint p, String s, float x, float cy, Rect tmp) {
        p.getTextBounds(s, 0, s.length(), tmp);
        c.drawText(s, x, cy - tmp.exactCenterY(), p);
    }

    static void textCenter(Canvas c, Paint p, String s, float cx, float cy, Rect tmp) {
        p.setTextAlign(Paint.Align.CENTER);
        textMid(c, p, s, cx, cy, tmp);
    }

    /** Analog clock. */
    static void analog(Canvas c, Paint p, float cx, float cy, float r, Calendar t, int accent,
                       boolean numbers, boolean lightFace, Rect tmp) {
        int fg = lightFace ? 0xFF000000 : 0xFFFFFFFF;
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(lightFace ? 0xFFFFFFFF : 0xFF000000);
        c.drawCircle(cx, cy, r, p);

        // ticks
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.BUTT);
        for (int i = 0; i < 60; i++) {
            boolean major = i % 5 == 0;
            double a = Math.toRadians(i * 6);
            float outer = r * 0.95f;
            float inner = major ? r * 0.85f : r * 0.90f;
            p.setStrokeWidth(major ? r * 0.022f : r * 0.009f);
            p.setColor(alpha(fg, major ? 255 : 110));
            float sx = (float) Math.sin(a), sy = (float) -Math.cos(a);
            c.drawLine(cx + sx * inner, cy + sy * inner, cx + sx * outer, cy + sy * outer, p);
        }

        if (numbers) {
            p.setStyle(Paint.Style.FILL);
            p.setTypeface(MEDIUM);
            p.setColor(fg);
            p.setTextSize(r * 0.17f);
            for (int n = 1; n <= 12; n++) {
                double a = Math.toRadians(n * 30);
                float x = cx + (float) Math.sin(a) * r * 0.69f;
                float y = cy - (float) Math.cos(a) * r * 0.69f;
                textCenter(c, p, String.valueOf(n), x, y, tmp);
            }
        }

        int h = t.get(Calendar.HOUR);
        int m = t.get(Calendar.MINUTE);
        int s = t.get(Calendar.SECOND);
        float hourA = (h + m / 60f + s / 3600f) * 30f;
        float minA = (m + s / 60f) * 6f;
        float secA = s * 6f;

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        hand(c, p, cx, cy, hourA, r * 0.50f, 0f, r * 0.060f, fg);
        hand(c, p, cx, cy, minA, r * 0.80f, 0f, r * 0.042f, fg);
        hand(c, p, cx, cy, secA, r * 0.88f, r * 0.16f, r * 0.016f, accent);

        p.setStyle(Paint.Style.FILL);
        p.setColor(fg);
        c.drawCircle(cx, cy, r * 0.05f, p);
        p.setColor(accent);
        c.drawCircle(cx, cy, r * 0.028f, p);
    }

    private static void hand(Canvas c, Paint p, float cx, float cy, float deg, float len,
                             float tail, float width, int color) {
        double a = Math.toRadians(deg);
        float sx = (float) Math.sin(a), sy = (float) -Math.cos(a);
        p.setColor(color);
        p.setStrokeWidth(width);
        c.drawLine(cx - sx * tail, cy - sy * tail, cx + sx * len, cy + sy * len, p);
    }
}
