package com.siddhesh.standby;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/** Full-screen clock faces of the Clock view (swipe up/down to switch). */
final class ClockFaces {
    static final int DIGITAL = 0, ANALOG = 1, WORLD = 2, SOLAR = 3, FLOAT = 4, FLIP = 5, STACKED = 6,
            RINGS = 7, WORDS = 8, NEON = 9, SEGMENT = 10, POMODORO = 11, NOW_PLAYING = 12, COUNT = 13;
    static final String[] NAMES = {"Digital", "Analog", "World", "Solar", "Float", "Flip", "Stacked",
            "Rings", "Words", "Neon", "Retro LED", "Pomodoro", "Now Playing"};
    /** Index into Ui.ACCENTS, per face id. */
    static final int[] DEFAULT_ACCENT = {1, 1, 0, 0, 7, 0, 2, 4, 3, 8, 1, 1, 0};

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Rect tmp = new Rect();
    private final RectF rf = new RectF();
    private final Path path = new Path();
    private final Matrix m = new Matrix();
    private final float d;
    private final SimpleDateFormat dateFmt = new SimpleDateFormat("EEEE d MMMM", Locale.getDefault());
    private final SimpleDateFormat shortFmt = new SimpleDateFormat("EEE d", Locale.getDefault());
    private final SimpleDateFormat dayFmt = new SimpleDateFormat("EEEE", Locale.getDefault());
    private final SimpleDateFormat monFmt = new SimpleDateFormat("d MMMM", Locale.getDefault());

    // Now Playing face controls (tap targets)
    final RectF npPrev = new RectF(), npPlay = new RectF(), npNext = new RectF();

    // Flip animation state: [0]=hours card, [1]=minutes card
    private final String[] flipNow = new String[2];
    private final String[] flipOld = new String[2];
    private final long[] flipStart = new long[2];
    private static final long FLIP_MS = 650;

    // Solar sky colours over the day: {hour, top, bottom}
    private static final float[] SKY_H = {0f, 5f, 6.5f, 9f, 15f, 17.5f, 19f, 21f, 24f};
    private static final int[] SKY_TOP = {0xFF05070F, 0xFF0E1430, 0xFF3D5A9E, 0xFF2F80ED, 0xFF1E6FD9,
            0xFF5B4B8A, 0xFF1A1F4D, 0xFF070B1E, 0xFF05070F};
    private static final int[] SKY_BOT = {0xFF121A33, 0xFF3A3266, 0xFFF4A261, 0xFF8EC5FC, 0xFF9ED0FF,
            0xFFF7934C, 0xFFA35C7A, 0xFF1A2140, 0xFF121A33};

    // 7-segment map: a b c d e f g
    private static final boolean[][] SEG = {
            {true, true, true, true, true, true, false},     // 0
            {false, true, true, false, false, false, false}, // 1
            {true, true, false, true, true, false, true},    // 2
            {true, true, true, true, false, false, true},    // 3
            {false, true, true, false, false, true, true},   // 4
            {true, false, true, true, false, true, true},    // 5
            {true, false, true, true, true, true, true},     // 6
            {true, true, true, false, false, false, false},  // 7
            {true, true, true, true, true, true, true},      // 8
            {true, true, true, true, false, true, true},     // 9
    };

    private static final String[] SMALL = {"zero", "one", "two", "three", "four", "five", "six", "seven",
            "eight", "nine", "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen",
            "seventeen", "eighteen", "nineteen"};
    private static final String[] TENS = {"", "", "twenty", "thirty", "forty", "fifty"};

    ClockFaces(float density) {
        d = density;
    }

    /** Faces that draw their own full-screen background (wallpaper is skipped). */
    static boolean ownBackground(int face) {
        return face == SOLAR || face == NOW_PLAYING;
    }

    boolean continuous(int face, long now) {
        if (face == FLOAT) return true;
        if (face == FLIP) return now - flipStart[0] < FLIP_MS || now - flipStart[1] < FLIP_MS;
        return false;
    }

    void draw(Canvas c, int face, int w, int h, Calendar cal, int accent, long now, StandByView v) {
        boolean h24 = v.h24;
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setLetterSpacing(0f);
        p.setTextAlign(Paint.Align.LEFT);
        switch (face) {
            case DIGITAL: digital(c, w, h, cal, accent, h24); break;
            case ANALOG: analog(c, w, h, cal, accent); break;
            case WORLD: world(c, w, h, cal, accent, h24); break;
            case SOLAR: solar(c, w, h, cal, accent, h24); break;
            case FLOAT: floating(c, w, h, cal, accent, now, h24); break;
            case FLIP: flip(c, w, h, cal, accent, now, h24); break;
            case STACKED: stacked(c, w, h, cal, accent, h24); break;
            case RINGS: rings(c, w, h, cal, accent, h24); break;
            case WORDS: words(c, w, h, cal, accent); break;
            case NEON: neon(c, w, h, cal, accent, h24); break;
            case SEGMENT: segment(c, w, h, cal, accent, h24); break;
            case POMODORO: v.pomodoro.drawFace(c, w, h, now, v.settings); break;
            case NOW_PLAYING: nowPlaying(c, w, h, cal, accent, v); break;
        }
        p.setShader(null);
        p.setLetterSpacing(0f);
        p.clearShadowLayer();
        p.setTextAlign(Paint.Align.LEFT);
        p.setStyle(Paint.Style.FILL);
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

    // ------------------------------------------------------------ original five

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
        int hand = accent == 0xFFFFFFFF ? Ui.RED : accent;
        Ui.analog(c, p, cx, cy, r, cal, hand, true, false, tmp);
        String ds = shortFmt.format(cal.getTime()).toUpperCase(Locale.getDefault());
        p.setStyle(Paint.Style.FILL);
        p.setTypeface(Ui.BOLD);
        p.setColor(hand);
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

        String ds = "Local time · " + dateFmt.format(cal.getTime());
        p.setTypeface(Ui.MEDIUM);
        p.setColor(Ui.GRAY);
        Ui.fitWidth(p, ds, leftW * 0.8f, leftH * 0.065f);
        Ui.textCenter(c, p, ds, leftW / 2f, leftH * 0.72f, tmp);

        float rx = landscape ? leftW : 0f;
        float ry = landscape ? 0f : leftH;
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
        int back = accent == 0xFFFFFFFF ? 0xFF8E8E93 : Ui.lerp(accent, 0xFFFFFFFF, 0.55f);
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

    // ------------------------------------------------------------ new faces

    private void flip(Canvas c, int w, int h, Calendar cal, int accent, long now, boolean h24) {
        String t = Ui.time(cal, h24);
        int colon = t.indexOf(':');
        String[] parts = {t.substring(0, colon), t.substring(colon + 1)};
        if (parts[0].length() == 1) parts[0] = " " + parts[0];
        for (int i = 0; i < 2; i++) {
            if (!parts[i].equals(flipNow[i])) {
                flipOld[i] = flipNow[i] == null ? parts[i] : flipNow[i];
                flipNow[i] = parts[i];
                flipStart[i] = flipNow[i].equals(flipOld[i]) ? 0 : now;
            }
        }
        boolean landscape = w >= h;
        float gap = (landscape ? w : h) * 0.04f;
        float cardW = landscape ? (w * 0.86f - gap) / 2f : w * 0.7f;
        float cardH = landscape ? Math.min(h * 0.72f, cardW * 1.05f) : (h * 0.8f - gap) / 2f;
        for (int i = 0; i < 2; i++) {
            float left, top;
            if (landscape) {
                left = (w - (2 * cardW + gap)) / 2f + i * (cardW + gap);
                top = (h - cardH) / 2f + h * 0.04f;
            } else {
                left = (w - cardW) / 2f;
                top = (h - (2 * cardH + gap)) / 2f + i * (cardH + gap);
            }
            rf.set(left, top, left + cardW, top + cardH);
            float prog = flipStart[i] == 0 ? 1f : Math.min(1f, (now - flipStart[i]) / (float) FLIP_MS);
            flipCard(c, new RectF(rf), flipNow[i], flipOld[i], prog, accent);
        }
        if (!h24) {
            p.setTypeface(Ui.BOLD);
            p.setColor(Ui.GRAY);
            p.setTextSize(Math.min(w, h) * 0.045f);
            p.setTextAlign(Paint.Align.LEFT);
            c.drawText(Ui.amPm(cal), landscape ? w * 0.07f + 4 * d : w * 0.15f, landscape ? h * 0.12f : h * 0.07f, p);
        }
        dateLine(c, w, h, cal, Ui.GRAY, landscape ? h * 0.08f : h * 0.05f);
    }

    private void flipCard(Canvas c, RectF r, String now, String old, float prog, int accent) {
        float mid = r.centerY();
        float rad = r.width() * 0.08f;
        int text = accent;
        // background halves: new top, old bottom
        half(c, r, prog >= 1f ? now : now, true, rad, text, 0);
        half(c, r, prog >= 1f ? now : old, false, rad, text, 0);
        if (prog < 1f) {
            c.save();
            if (prog < 0.5f) {
                c.scale(1f, 1f - prog * 2f, r.centerX(), mid);
                half(c, r, old, true, rad, text, (int) (prog * 2f * 120));
            } else {
                c.scale(1f, prog * 2f - 1f, r.centerX(), mid);
                half(c, r, now, false, rad, text, (int) ((1f - prog) * 2f * 120));
            }
            c.restore();
        }
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF000000);
        c.drawRect(r.left, mid - 1.5f * d, r.right, mid + 1.5f * d, p);
    }

    private void half(Canvas c, RectF r, String s, boolean top, float rad, int text, int shade) {
        float mid = r.centerY();
        c.save();
        if (top) c.clipRect(r.left, r.top, r.right, mid);
        else c.clipRect(r.left, mid, r.right, r.bottom);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF1C1C1E);
        c.drawRoundRect(r, rad, rad, p);
        p.setTypeface(Ui.BOLD);
        p.setColor(text);
        Ui.fit(p, "00", r.width() * 0.82f, r.height() * 0.62f, tmp);
        Ui.textCenter(c, p, s, r.centerX(), mid, tmp);
        if (shade > 0) {
            p.setColor(Ui.alpha(0xFF000000, shade));
            c.drawRect(r, p);
        }
        c.restore();
    }

    private void stacked(Canvas c, int w, int h, Calendar cal, int accent, boolean h24) {
        String t = Ui.time(cal, h24);
        int colon = t.indexOf(':');
        String hh = t.substring(0, colon), mm = t.substring(colon + 1);
        if (hh.length() == 1) hh = "0" + hh;
        boolean landscape = w >= h;
        float areaW = landscape ? w * 0.58f : w * 0.9f;
        float lineH = landscape ? h * 0.40f : h * 0.30f;
        float x = landscape ? w * 0.07f : w * 0.05f;
        p.setTypeface(Ui.BLACK);
        Ui.fit(p, "00", areaW, lineH, tmp);
        p.getTextBounds("00", 0, 2, tmp);
        float capH = tmp.height();
        float top = landscape ? (h - (2 * capH + h * 0.05f)) / 2f : h * 0.12f;
        p.setTextAlign(Paint.Align.LEFT);
        p.setColor(accent);
        c.drawText(hh, x, top + capH, p);
        p.setColor(0xFFFFFFFF);
        c.drawText(mm, x, top + 2 * capH + h * 0.05f, p);

        float rx = landscape ? w * 0.68f : w * 0.06f;
        float ry = landscape ? h * 0.40f : h * 0.80f;
        String day = dayFmt.format(cal.getTime()).toUpperCase(Locale.getDefault());
        String mon = monFmt.format(cal.getTime());
        p.setTypeface(Ui.BOLD);
        p.setColor(accent);
        Ui.fitWidth(p, day, landscape ? w * 0.28f : w * 0.85f, Math.min(w, h) * 0.08f);
        c.drawText(day, rx, ry, p);
        p.setTypeface(Ui.LIGHT);
        p.setColor(0xFFFFFFFF);
        Ui.fitWidth(p, mon, landscape ? w * 0.28f : w * 0.85f, Math.min(w, h) * 0.075f);
        c.drawText(mon, rx, ry + Math.min(w, h) * 0.11f, p);
        if (!h24) {
            p.setColor(Ui.GRAY);
            p.setTypeface(Ui.MEDIUM);
            p.setTextSize(Math.min(w, h) * 0.06f);
            c.drawText(Ui.amPm(cal), rx, ry + Math.min(w, h) * 0.22f, p);
        }
    }

    private void rings(Canvas c, int w, int h, Calendar cal, int accent, boolean h24) {
        float r = Math.min(w, h) * 0.43f;
        float cx = w / 2f, cy = h / 2f;
        float sw = r * 0.13f;
        int[] colors = accent == 0xFFFFFFFF
                ? new int[]{0xFFFA114F, 0xFF92E82A, 0xFF1EEAEF}
                : new int[]{accent, Ui.lerp(accent, 0xFFFFFFFF, 0.35f), Ui.lerp(accent, 0xFF000000, 0.35f)};
        int hr = cal.get(Calendar.HOUR), mi = cal.get(Calendar.MINUTE), se = cal.get(Calendar.SECOND);
        float[] fr = {(hr + mi / 60f) / 12f, (mi + se / 60f) / 60f, se / 60f};
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(sw);
        for (int i = 0; i < 3; i++) {
            float rr = r - i * sw * 1.25f;
            p.setColor(Ui.alpha(colors[i], 55));
            c.drawCircle(cx, cy, rr, p);
            p.setColor(colors[i]);
            rf.set(cx - rr, cy - rr, cx + rr, cy + rr);
            c.drawArc(rf, -90f, Math.max(1f, 360f * fr[i]), false, p);
        }
        p.setStyle(Paint.Style.FILL);
        float inner = r - 2 * sw * 1.25f - sw;
        String t = Ui.time(cal, h24);
        p.setTypeface(Ui.BOLD);
        p.setColor(0xFFFFFFFF);
        Ui.fit(p, t, inner * 1.5f, inner * 0.55f, tmp);
        Ui.textCenter(c, p, t, cx, cy, tmp);
        String ds = shortFmt.format(cal.getTime()).toUpperCase(Locale.getDefault());
        p.setTypeface(Ui.MEDIUM);
        p.setColor(Ui.GRAY);
        p.setTextSize(inner * 0.16f);
        Ui.textCenter(c, p, ds, cx, cy + inner * 0.48f, tmp);
        if (w > h * 1.3f) {
            // legend on the side
            String[] labels = {"Hours", "Minutes", "Seconds"};
            p.setTextAlign(Paint.Align.LEFT);
            p.setTextSize(h * 0.045f);
            for (int i = 0; i < 3; i++) {
                float y = h * 0.4f + i * h * 0.09f;
                p.setColor(colors[i]);
                c.drawCircle(w * 0.80f, y, h * 0.015f, p);
                p.setColor(0xFFE5E5EA);
                Ui.textMid(c, p, labels[i], w * 0.80f + h * 0.035f, y, tmp);
            }
        }
    }

    private static String numberWords(int n) {
        if (n < 20) return SMALL[n];
        return TENS[n / 10] + (n % 10 != 0 ? "-" + SMALL[n % 10] : "");
    }

    private void words(Canvas c, int w, int h, Calendar cal, int accent) {
        int hr = cal.get(Calendar.HOUR);
        if (hr == 0) hr = 12;
        int mi = cal.get(Calendar.MINUTE);
        String l1 = "It's";
        String l2 = SMALL[hr].substring(0, 1).toUpperCase(Locale.US) + SMALL[hr].substring(1);
        String l3 = mi == 0 ? "o'clock" : (mi < 10 ? "oh " + SMALL[mi] : numberWords(mi));
        float x = w * 0.08f;
        float maxW = w * 0.84f;
        p.setTextAlign(Paint.Align.LEFT);
        p.setTypeface(Ui.LIGHT);
        p.setColor(Ui.GRAY);
        p.setTextSize(h * 0.10f);
        c.drawText(l1, x, h * 0.22f, p);
        p.setTypeface(Ui.BLACK);
        p.setColor(accent);
        Ui.fitWidth(p, l2, maxW, h * 0.30f);
        c.drawText(l2, x, h * 0.52f, p);
        p.setTypeface(Ui.LIGHT);
        p.setColor(0xFFFFFFFF);
        Ui.fitWidth(p, l3, maxW, h * 0.22f);
        c.drawText(l3, x, h * 0.78f, p);
        String ds = dateFmt.format(cal.getTime());
        p.setTypeface(Ui.MEDIUM);
        p.setColor(Ui.GRAY);
        p.setTextSize(h * 0.045f);
        c.drawText(ds + (cal.get(Calendar.AM_PM) == Calendar.AM ? " · morning" :
                (cal.get(Calendar.HOUR_OF_DAY) < 17 ? " · afternoon" : " · evening")), x, h * 0.92f, p);
    }

    private void neon(Canvas c, int w, int h, Calendar cal, int accent, boolean h24) {
        String t = Ui.time(cal, h24);
        p.setTypeface(Ui.LIGHT);
        float size = Ui.fit(p, t, w * 0.84f, h * 0.55f, tmp);
        float cy = h * 0.54f;
        p.setTextAlign(Paint.Align.CENTER);
        p.getTextBounds(t, 0, t.length(), tmp);
        float baseline = cy - tmp.exactCenterY();
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeJoin(Paint.Join.ROUND);
        // outer glow
        p.setStrokeWidth(size * 0.035f);
        p.setColor(Ui.alpha(accent, 150));
        p.setShadowLayer(size * 0.12f, 0, 0, accent);
        c.drawText(t, w / 2f, baseline, p);
        // tube
        p.setShadowLayer(size * 0.04f, 0, 0, accent);
        p.setStrokeWidth(size * 0.018f);
        p.setColor(Ui.lerp(accent, 0xFFFFFFFF, 0.6f));
        c.drawText(t, w / 2f, baseline, p);
        p.clearShadowLayer();
        p.setStyle(Paint.Style.FILL);

        String ds = dateFmt.format(cal.getTime()).toUpperCase(Locale.getDefault());
        p.setTypeface(Ui.LIGHT);
        p.setLetterSpacing(0.25f);
        p.setColor(Ui.lerp(accent, 0xFFFFFFFF, 0.5f));
        p.setShadowLayer(8 * d, 0, 0, accent);
        Ui.fitWidth(p, ds, w * 0.8f, h * 0.05f);
        Ui.textCenter(c, p, ds, w / 2f, h * 0.12f, tmp);
        p.clearShadowLayer();
        p.setLetterSpacing(0f);
    }

    private void segment(Canvas c, int w, int h, Calendar cal, int accent, boolean h24) {
        String t = Ui.time(cal, h24);
        if (t.length() == 4) t = " " + t; // "9:41" -> " 9:41"
        float digitH = h * 0.55f;
        float digitW = digitH * 0.52f;
        float gap = digitW * 0.28f;
        float colonW = digitW * 0.45f;
        float total = 4 * digitW + 3 * gap + colonW + gap;
        if (total > w * 0.92f) {
            float k = w * 0.92f / total;
            digitH *= k; digitW *= k; gap *= k; colonW *= k; total *= k;
        }
        float x = (w - total) / 2f;
        float y = (h - digitH) / 2f + h * 0.04f;
        int ghost = Ui.alpha(accent, 28);
        for (int i = 0; i < t.length(); i++) {
            char ch = t.charAt(i);
            if (ch == ':') {
                boolean on = cal.get(Calendar.SECOND) % 2 == 0;
                p.setColor(on ? accent : ghost);
                float r = digitW * 0.09f;
                c.drawCircle(x + colonW / 2f, y + digitH * 0.32f, r, p);
                c.drawCircle(x + colonW / 2f, y + digitH * 0.68f, r, p);
                x += colonW + gap;
            } else {
                int dgt = ch == ' ' ? -1 : ch - '0';
                sevenSeg(c, x, y, digitW, digitH, dgt, accent, ghost);
                x += digitW + gap;
            }
        }
        if (!h24) {
            p.setTypeface(Ui.BOLD);
            p.setColor(accent);
            p.setTextSize(h * 0.06f);
            p.setTextAlign(Paint.Align.RIGHT);
            c.drawText(Ui.amPm(cal), (w + total) / 2f, y - h * 0.04f, p);
            p.setTextAlign(Paint.Align.LEFT);
        }
        dateLine(c, w, h, cal, Ui.alpha(accent, 170), h * 0.1f);
    }

    private void sevenSeg(Canvas c, float x, float y, float w, float h, int digit, int on, int off) {
        float t = w * 0.17f;          // thickness
        float g = t * 0.18f;          // gap between segments
        float half = h / 2f;
        float r = t / 2f;
        // a b c d e f g
        float[][] segs = {
                {x + t / 2 + g, y, x + w - t / 2 - g, y + t},                           // a
                {x + w - t, y + t / 2 + g, x + w, y + half - g},                         // b
                {x + w - t, y + half + g, x + w, y + h - t / 2 - g},                     // c
                {x + t / 2 + g, y + h - t, x + w - t / 2 - g, y + h},                     // d
                {x, y + half + g, x + t, y + h - t / 2 - g},                             // e
                {x, y + t / 2 + g, x + t, y + half - g},                                 // f
                {x + t / 2 + g, y + half - t / 2, x + w - t / 2 - g, y + half + t / 2},  // g
        };
        for (int i = 0; i < 7; i++) {
            boolean lit = digit >= 0 && SEG[digit][i];
            p.setColor(lit ? on : off);
            rf.set(segs[i][0], segs[i][1], segs[i][2], segs[i][3]);
            c.drawRoundRect(rf, r, r, p);
        }
    }

    private void nowPlaying(Canvas c, int w, int h, Calendar cal, int accent, StandByView v) {
        NowPlaying np = v.nowPlaying;
        npPrev.setEmpty();
        npPlay.setEmpty();
        npNext.setEmpty();
        if (!np.active()) {
            c.drawColor(0xFF000000);
            String t = Ui.time(cal, v.h24);
            p.setTypeface(Ui.LIGHT);
            p.setColor(0xFFFFFFFF);
            Ui.fit(p, t, w * 0.5f, h * 0.3f, tmp);
            Ui.textCenter(c, p, t, w / 2f, h * 0.38f, tmp);
            String msg = NowPlaying.hasAccess(v.getContext())
                    ? "Nothing playing — start a song in Spotify"
                    : "Allow Notification access in StandBy Settings to show Spotify";
            p.setTypeface(Ui.REGULAR);
            p.setColor(Ui.GRAY);
            Ui.fitWidth(p, msg, w * 0.8f, h * 0.05f);
            Ui.textCenter(c, p, msg, w / 2f, h * 0.68f, tmp);
            return;
        }
        // blurred album art background
        if (np.artBlur != null) {
            drawCover(c, np.artBlur, 0, 0, w, h);
            c.drawColor(0x99000000);
        } else {
            c.drawColor(0xFF101012);
        }
        boolean landscape = w >= h;
        float artS = landscape ? h * 0.62f : w * 0.7f;
        float ax = landscape ? w * 0.07f : (w - artS) / 2f;
        float ay = landscape ? (h - artS) / 2f : h * 0.08f;
        rf.set(ax, ay, ax + artS, ay + artS);
        if (np.art != null) {
            c.save();
            path.reset();
            path.addRoundRect(rf, artS * 0.06f, artS * 0.06f, Path.Direction.CW);
            c.clipPath(path);
            drawCover(c, np.art, rf.left, rf.top, rf.width(), rf.height());
            c.restore();
        } else {
            p.setColor(0xFF2C2C2E);
            c.drawRoundRect(rf, artS * 0.06f, artS * 0.06f, p);
            p.setColor(Ui.GRAY);
            p.setTextSize(artS * 0.4f);
            Ui.textCenter(c, p, "♪", rf.centerX(), rf.centerY(), tmp);
        }

        float tx = landscape ? ax + artS + w * 0.05f : w * 0.08f;
        float tw = landscape ? w - tx - w * 0.06f : w * 0.84f;
        float ty = landscape ? ay + artS * 0.12f : ay + artS + h * 0.07f;
        p.setTextAlign(Paint.Align.LEFT);
        p.setTypeface(Ui.MEDIUM);
        p.setColor(NowPlaying.SPOTIFY.equals(np.pkg) ? 0xFF1DB954 : Ui.GRAY);
        p.setTextSize(Math.min(w, h) * 0.04f);
        c.drawText(np.appLabel().toUpperCase(Locale.US), tx, ty, p);

        p.setTypeface(Ui.BOLD);
        p.setColor(0xFFFFFFFF);
        String title = np.title == null ? "" : np.title;
        Ui.fitWidth(p, title, tw, Math.min(w, h) * 0.085f);
        c.drawText(ellipsize(title, tw), tx, ty + Math.min(w, h) * 0.12f, p);
        p.setTypeface(Ui.REGULAR);
        p.setColor(0xCCFFFFFF);
        String artist = np.artist == null ? "" : np.artist;
        p.setTextSize(Math.min(w, h) * 0.055f);
        c.drawText(ellipsize(artist, tw), tx, ty + Math.min(w, h) * 0.20f, p);

        // progress
        float py = ty + Math.min(w, h) * 0.30f;
        long pos = np.position();
        float fr = np.duration > 0 ? pos / (float) np.duration : 0f;
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(5 * d);
        p.setColor(0x44FFFFFF);
        c.drawLine(tx, py, tx + tw, py, p);
        p.setColor(0xFFFFFFFF);
        if (fr > 0) c.drawLine(tx, py, tx + tw * fr, py, p);
        p.setStyle(Paint.Style.FILL);
        p.setTypeface(Ui.REGULAR);
        p.setColor(0xAAFFFFFF);
        p.setTextSize(Math.min(w, h) * 0.035f);
        c.drawText(Pomodoro.fmt(pos), tx, py + Math.min(w, h) * 0.065f, p);
        p.setTextAlign(Paint.Align.RIGHT);
        if (np.duration > 0) c.drawText("-" + Pomodoro.fmt(Math.max(0, np.duration - pos)), tx + tw, py + Math.min(w, h) * 0.065f, p);
        p.setTextAlign(Paint.Align.LEFT);

        // controls
        float cs = Math.min(w, h) * 0.13f;
        float cy = py + Math.min(w, h) * 0.20f;
        float ccx = tx + tw / 2f;
        npPrev.set(ccx - cs * 2.2f - cs / 2, cy - cs / 2, ccx - cs * 2.2f + cs / 2, cy + cs / 2);
        npPlay.set(ccx - cs / 2, cy - cs / 2, ccx + cs / 2, cy + cs / 2);
        npNext.set(ccx + cs * 2.2f - cs / 2, cy - cs / 2, ccx + cs * 2.2f + cs / 2, cy + cs / 2);
        Icons.prev(c, p, path, npPrev, 0xFFFFFFFF);
        Icons.playPause(c, p, path, npPlay, np.playing, 0xFFFFFFFF);
        Icons.next(c, p, path, npNext, 0xFFFFFFFF);

        // small clock top-right
        p.setTypeface(Ui.MEDIUM);
        p.setColor(0xCCFFFFFF);
        p.setTextSize(Math.min(w, h) * 0.06f);
        p.setTextAlign(Paint.Align.RIGHT);
        c.drawText(Ui.time(cal, v.h24), w - w * 0.04f, h * 0.12f, p);
        p.setTextAlign(Paint.Align.LEFT);
    }

    private String ellipsize(String s, float maxW) {
        if (p.measureText(s) <= maxW) return s;
        String e = "…";
        int n = s.length();
        while (n > 1 && p.measureText(s, 0, n) + p.measureText(e) > maxW) n--;
        return s.substring(0, n) + e;
    }

    private void drawCover(Canvas c, Bitmap b, float x, float y, float w, float h) {
        float bw = b.getWidth(), bh = b.getHeight();
        float s = Math.max(w / bw, h / bh);
        m.setScale(s, s);
        m.postTranslate(x + (w - bw * s) / 2f, y + (h - bh * s) / 2f);
        bp.setAlpha(255);
        c.drawBitmap(b, m, bp);
    }
}
