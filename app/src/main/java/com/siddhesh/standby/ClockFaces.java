package com.siddhesh.standby;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/** Full-screen clock faces of the Clock view (swipe up/down to switch). */
final class ClockFaces {
    static final int DIGITAL = 0, ANALOG = 1, WORLD = 2, SOLAR = 3, FLOAT = 4, COUNT = 5;
    static final String[] NAMES = {"Digital", "Analog", "World", "Solar", "Float"};
    /** Index into Ui.ACCENTS. */
    static final int[] DEFAULT_ACCENT = {1, 1, 0, 0, 7};

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect tmp = new Rect();
    private final float d;
    private final SimpleDateFormat dateFmt = new SimpleDateFormat("EEEE d MMMM", Locale.getDefault());
    private final SimpleDateFormat shortFmt = new SimpleDateFormat("EEE d", Locale.getDefault());

    // Sky colours over the day for the Solar face: {hour, top, bottom}
    private static final float[] SKY_H = {0f, 5f, 6.5f, 9f, 15f, 17.5f, 19f, 21f, 24f};
    private static final int[] SKY_TOP = {0xFF05070F, 0xFF0E1430, 0xFF3D5A9E, 0xFF2F80ED, 0xFF1E6FD9,
            0xFF5B4B8A, 0xFF1A1F4D, 0xFF070B1E, 0xFF05070F};
    private static final int[] SKY_BOT = {0xFF121A33, 0xFF3A3266, 0xFFF4A261, 0xFF8EC5FC, 0xFF9ED0FF,
            0xFFF7934C, 0xFFA35C7A, 0xFF1A2140, 0xFF121A33};

    ClockFaces(float density) {
        d = density;
    }

    static boolean continuous(int face) {
        return face == FLOAT;
    }

    void draw(Canvas c, int face, int w, int h, Calendar cal, int accent, long now, boolean h24) {
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setLetterSpacing(0f);
        switch (face) {
            case DIGITAL: digital(c, w, h, cal, accent, h24); break;
            case ANALOG: analog(c, w, h, cal, accent); break;
            case WORLD: world(c, w, h, cal, accent, h24); break;
            case SOLAR: solar(c, w, h, cal, accent, h24); break;
            case FLOAT: floating(c, w, h, cal, accent, now, h24); break;
        }
        p.setShader(null);
        p.setLetterSpacing(0f);
        p.clearShadowLayer();
    }

    private void dateLine(Canvas c, int w, int h, Calendar cal, int color, float y) {
        String ds = dateFmt.format(cal.getTime()).toUpperCase(Locale.getDefault());
        p.setTypeface(Ui.MEDIUM);
        p.setColor(color);
        p.setLetterSpacing(0.08f);
        Ui.fitWidth(p, ds, w * 0.8f, Math.min(w, h) * 0.055f);
        Ui.textCenter(c, p, ds, w / 2f, y, tmp);
        p.setLetterSpacing(0f);
    }

    private void digital(Canvas c, int w, int h, Calendar cal, int accent, boolean h24) {
        dateLine(c, w, h, cal, Ui.alpha(accent, 200), h * 0.12f);
        String t = Ui.time(cal, h24);
        p.setTypeface(Ui.REGULAR);
        p.setColor(accent);
        p.setLetterSpacing(-0.03f);
        Ui.fit(p, t, w * 0.92f, h * 0.62f, tmp);
        Ui.textCenter(c, p, t, w / 2f, h * 0.56f, tmp);
    }

    private void analog(Canvas c, int w, int h, Calendar cal, int accent) {
        float r = Math.min(w, h) * 0.45f;
        float cx = w / 2f, cy = h / 2f;
        Ui.analog(c, p, cx, cy, r, cal, accent == 0xFFFFFFFF ? Ui.RED : accent, true, false, tmp);
        // date window, like a real watch
        String ds = shortFmt.format(cal.getTime()).toUpperCase(Locale.getDefault());
        p.setStyle(Paint.Style.FILL);
        p.setTypeface(Ui.BOLD);
        p.setColor(accent == 0xFFFFFFFF ? Ui.RED : accent);
        p.setTextSize(r * 0.10f);
        Ui.textCenter(c, p, ds, cx, cy + r * 0.40f, tmp);
    }

    private void world(Canvas c, int w, int h, Calendar cal, int accent, boolean h24) {
        boolean landscape = w >= h;
        float leftW = landscape ? w * 0.52f : w;
        float leftH = landscape ? h : h * 0.45f;

        String t = Ui.time(cal, h24);
        p.setTypeface(Ui.LIGHT);
        p.setColor(accent);
        Ui.fit(p, t, leftW * 0.86f, leftH * 0.36f, tmp);
        Ui.textCenter(c, p, t, leftW / 2f, leftH * 0.48f, tmp);

        String ds = dateFmt.format(cal.getTime());
        p.setTypeface(Ui.MEDIUM);
        p.setColor(Ui.GRAY);
        Ui.fitWidth(p, ds, leftW * 0.8f, leftH * 0.065f);
        Ui.textCenter(c, p, "Local time · " + ds, leftW / 2f, leftH * 0.72f, tmp);

        float rx = landscape ? leftW : 0f;
        float ry = landscape ? 0f : leftH;
        float rw = landscape ? w - leftW : w;
        float rh = landscape ? h : h - leftH;
        float pad = 24 * d;
        int n = Widgets.CITIES.length;
        float rowH = (rh - 2 * pad) / n;
        long ms = cal.getTimeInMillis();
        for (int i = 0; i < n; i++) {
            TimeZone tz = TimeZone.getTimeZone(Widgets.CITIES[i][1]);
            Calendar cc = Calendar.getInstance(tz);
            cc.setTimeInMillis(ms);
            float top = ry + pad + i * rowH;
            float cy = top + rowH / 2f;
            float rad = rowH * 0.40f;
            float cx = rx + pad + rad;
            int hour = cc.get(Calendar.HOUR_OF_DAY);
            boolean day = hour >= 6 && hour < 18;
            Ui.analog(c, p, cx, cy, rad, cc, accent == 0xFFFFFFFF ? Ui.RED : accent, false, day, tmp);

            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.LEFT);
            p.setTypeface(Ui.MEDIUM);
            p.setColor(0xFFFFFFFF);
            p.setTextSize(rowH * 0.28f);
            c.drawText(Widgets.CITIES[i][0], cx + rad + 16 * d, cy - rowH * 0.02f, p);
            p.setTypeface(Ui.REGULAR);
            p.setColor(Ui.GRAY);
            p.setTextSize(rowH * 0.2f);
            String sub = Ui.time(cc, h24) + (h24 ? "" : " " + Ui.amPm(cc));
            c.drawText(sub, cx + rad + 16 * d, cy + rowH * 0.26f, p);
        }
        p.setTextAlign(Paint.Align.LEFT);
    }

    private void solar(Canvas c, int w, int h, Calendar cal, int accent, boolean h24) {
        float hour = cal.get(Calendar.HOUR_OF_DAY) + cal.get(Calendar.MINUTE) / 60f
                + cal.get(Calendar.SECOND) / 3600f;
        int seg = 0;
        while (seg < SKY_H.length - 2 && hour >= SKY_H[seg + 1]) seg++;
        float t = (hour - SKY_H[seg]) / (SKY_H[seg + 1] - SKY_H[seg]);
        int top = Ui.lerp(SKY_TOP[seg], SKY_TOP[seg + 1], t);
        int bot = Ui.lerp(SKY_BOT[seg], SKY_BOT[seg + 1], t);
        p.setShader(new LinearGradient(0, 0, 0, h, top, bot, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);

        float horizon = h * 0.80f;
        boolean isDay = hour >= 6f && hour < 18f;
        float f = isDay ? (hour - 6f) / 12f : (((hour - 18f) + 24f) % 24f) / 12f;
        float sx = w * 0.08f + f * w * 0.84f;
        float sy = horizon - (float) Math.sin(f * Math.PI) * h * 0.55f;
        float sr = Math.min(w, h) * 0.07f;

        if (isDay) {
            p.setShader(new RadialGradient(sx, sy, sr * 4f, 0x80FFE29A, 0x00FFE29A, Shader.TileMode.CLAMP));
            c.drawCircle(sx, sy, sr * 4f, p);
            p.setShader(null);
            p.setColor(0xFFFFE7A3);
            c.drawCircle(sx, sy, sr, p);
        } else {
            p.setShader(new RadialGradient(sx, sy, sr * 3f, 0x40E8EEF7, 0x00E8EEF7, Shader.TileMode.CLAMP));
            c.drawCircle(sx, sy, sr * 3f, p);
            p.setShader(null);
            p.setColor(0xFFE8EEF7);
            c.drawCircle(sx, sy, sr * 0.8f, p);
            p.setColor(Ui.alpha(top, 255));
            c.drawCircle(sx + sr * 0.35f, sy - sr * 0.2f, sr * 0.65f, p);
        }

        // ground below horizon
        p.setColor(0x66000000);
        c.drawRect(0, horizon, w, h, p);
        p.setColor(0x55FFFFFF);
        p.setStrokeWidth(Math.max(1f, d));
        c.drawLine(0, horizon, w, horizon, p);

        String ts = Ui.time(cal, h24);
        p.setTypeface(Ui.BOLD);
        p.setColor(accent);
        p.setShadowLayer(12 * d, 0, 2 * d, 0x66000000);
        p.setTextAlign(Paint.Align.LEFT);
        Ui.fit(p, ts, w * 0.5f, h * 0.30f, tmp);
        Ui.textMid(c, p, ts, w * 0.06f, h * 0.30f, tmp);

        String ds = dateFmt.format(cal.getTime());
        p.setTypeface(Ui.MEDIUM);
        p.setColor(0xE6FFFFFF);
        Ui.fitWidth(p, ds, w * 0.5f, h * 0.06f);
        Ui.textMid(c, p, ds, w * 0.065f, h * 0.53f, tmp);
        p.clearShadowLayer();
        p.setTextAlign(Paint.Align.LEFT);
    }

    private void floating(Canvas c, int w, int h, Calendar cal, int accent, long now, boolean h24) {
        String t = Ui.time(cal, h24);
        p.setTypeface(Ui.BLACK);
        Ui.fit(p, t, w * 0.86f, h * 0.58f, tmp);
        p.getTextBounds("0", 0, 1, tmp);
        float capH = tmp.height();
        float baseline = h / 2f + capH / 2f + h * 0.03f;

        float total = 0f;
        float[] widths = new float[t.length()];
        for (int i = 0; i < t.length(); i++) {
            widths[i] = p.measureText(t, i, i + 1);
            total += widths[i];
        }
        float x = (w - total) / 2f;
        float phase = (now % 600000L) / 1000f;
        int back = Ui.lerp(accent, 0xFFFFFFFF, 0.55f);
        if (accent == 0xFFFFFFFF) back = 0xFF8E8E93;
        float off = p.getTextSize() * 0.035f;
        p.setTextAlign(Paint.Align.LEFT);
        for (int i = 0; i < t.length(); i++) {
            String ch = t.substring(i, i + 1);
            float bob = (float) Math.sin(phase * 0.8 + i * 0.9) * h * 0.018f;
            float bob2 = (float) Math.sin(phase * 0.6 + i * 1.3 + 1.5) * h * 0.012f;
            p.setColor(Ui.alpha(back, 200));
            c.drawText(ch, x + off, baseline + bob2 + off, p);
            p.setColor(accent);
            c.drawText(ch, x, baseline + bob, p);
            x += widths[i];
        }
        dateLine(c, w, h, cal, Ui.alpha(accent, 200), h * 0.1f);
    }
}
