package com.siddhesh.standby;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;

import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/** The widgets shown in the two side-by-side stacks of the Widgets view. */
final class Widgets {
    static final int ANALOG = 0, DIGITAL = 1, WORLD = 2, CALENDAR = 3, BATTERY = 4, DATE = 5,
            NOW_PLAYING = 6, POMODORO = 7, WEATHER = 8, UP_NEXT = 9, TODO = 10, COUNT = 11;
    static final String[] NAMES = {"Analog clock", "Digital clock", "World clock", "Calendar",
            "Battery", "Date & next alarm", "Now Playing (Spotify)", "Pomodoro timer", "Weather",
            "Up Next (Teams / calendar)", "To-do list"};

    /** Now Playing controls per stack: [stack][prev, play, next]. */
    final RectF[][] npHits = {{new RectF(), new RectF(), new RectF()}, {new RectF(), new RectF(), new RectF()}};



    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect tmp = new Rect();
    private final RectF rf = new RectF();
    private final Path path = new Path();
    private final float d;
    private final SimpleDateFormat longDate = new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault());
    private final SimpleDateFormat weekday = new SimpleDateFormat("EEEE", Locale.getDefault());
    private final SimpleDateFormat monthYear = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
    private final SimpleDateFormat monthName = new SimpleDateFormat("MMMM", Locale.getDefault());

    Widgets(float density) {
        d = density;
    }

    void draw(Canvas c, int type, RectF r, Calendar cal, StandByView v, int stack, long now) {
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Paint.Align.LEFT);
        int accent = v.widgetAccentColor();
        switch (type) {
            case ANALOG: analog(c, r, cal, accent); break;
            case DIGITAL: digital(c, r, cal, accent, v); break;
            case WORLD: world(c, r, cal, v); break;
            case CALENDAR: calendar(c, r, cal, accent); break;
            case BATTERY: battery(c, r, accent, v); break;
            case DATE: date(c, r, cal, accent, v); break;
            case NOW_PLAYING: nowPlaying(c, r, v, stack); break;
            case POMODORO: v.pomodoro.drawWidget(c, r, now, v.settings); break;
            case WEATHER: v.weather.drawWidget(c, r, v.settings); break;
            case UP_NEXT: v.agenda.drawWidget(c, r, accent == 0xFFFFFFFF ? Ui.RED : accent, v.h24,
                    v.hideDetails(), stack); break;
            case TODO: v.todo.drawWidget(c, r, accent == 0xFFFFFFFF ? Ui.RED : accent, v.hideDetails(), stack); break;
        }
    }

    private void analog(Canvas c, RectF r, Calendar cal, int accent) {
        float rad = Math.min(r.width(), r.height()) / 2f - 16 * d;
        Ui.analog(c, p, r.centerX(), r.centerY(), rad, cal, accent == 0xFFFFFFFF ? Ui.RED : accent,
                true, false, tmp);
    }

    private void digital(Canvas c, RectF r, Calendar cal, int accent, StandByView v) {
        String t = Ui.time(cal, v.h24);
        p.setTypeface(Ui.LIGHT);
        p.setColor(0xFFFFFFFF);
        p.setLetterSpacing(-0.03f);
        Ui.fit(p, t, r.width() * 0.84f, r.height() * 0.36f, tmp);
        Ui.textCenter(c, p, t, r.centerX(), r.top + r.height() * 0.46f, tmp);
        p.setLetterSpacing(0.14f);
        String ds = longDate.format(cal.getTime()).replace(",", "").toUpperCase(Locale.getDefault());
        p.setTypeface(Ui.MEDIUM);
        p.setColor(0xBFFFFFFF);
        Ui.fitWidth(p, ds, r.width() * 0.84f, r.height() * 0.042f);
        Ui.textCenter(c, p, ds, r.centerX(), r.top + r.height() * 0.70f, tmp);
        p.setLetterSpacing(0f);
    }

    private void world(Canvas c, RectF r, Calendar cal, StandByView v) {
        float pad = 22 * d;
        int[] ids = v.settings.cities;
        int n = Math.min(ids.length, WorldCities.MAX);
        float rowH = (r.height() - 2 * pad) / n;
        long ms = cal.getTimeInMillis();
        TimeZone local = cal.getTimeZone();
        int localDay = cal.get(Calendar.YEAR) * 400 + cal.get(Calendar.DAY_OF_YEAR);
        for (int i = 0; i < n; i++) {
            String cityName = WorldCities.NAMES[ids[i]];
            TimeZone tz = TimeZone.getTimeZone(WorldCities.ZONES[ids[i]]);
            Calendar cc = Calendar.getInstance(tz);
            cc.setTimeInMillis(ms);
            int diffMin = (tz.getOffset(ms) - local.getOffset(ms)) / 60000;
            int day = cc.get(Calendar.YEAR) * 400 + cc.get(Calendar.DAY_OF_YEAR);
            String rel = day == localDay ? "Today" : (day > localDay ? "Tomorrow" : "Yesterday");
            String label = rel + ", " + offset(diffMin);

            float top = r.top + pad + i * rowH;
            float left = r.left + pad, right = r.right - pad;

            p.setTextAlign(Paint.Align.LEFT);
            p.setTypeface(Ui.MEDIUM);
            p.setColor(0xFFFFFFFF);
            Ui.fitWidth(p, cityName, (right - left) * 0.5f, rowH * 0.30f);
            c.drawText(cityName, left, top + rowH * 0.46f, p);

            p.setTypeface(Ui.REGULAR);
            p.setColor(Ui.GRAY);
            Ui.fitWidth(p, label, (right - left) * 0.5f, rowH * 0.19f);
            c.drawText(label, left, top + rowH * 0.76f, p);

            String t = Ui.time(cc, v.h24);
            p.setTextAlign(Paint.Align.RIGHT);
            p.setTypeface(Ui.LIGHT);
            p.setColor(0xFFFFFFFF);
            p.setTextSize(rowH * 0.52f);
            c.drawText(t, right, top + rowH * 0.72f, p);

            if (i < n - 1) {
                p.setColor(Ui.alpha(0xFFFFFFFF, 40));
                p.setStrokeWidth(Math.max(1f, d));
                c.drawLine(left, top + rowH, right, top + rowH, p);
            }
        }
        p.setTextAlign(Paint.Align.LEFT);
    }

    private static String offset(int min) {
        if (min == 0) return "+0HRS";
        String sign = min > 0 ? "+" : "-";
        int a = Math.abs(min);
        int h = a / 60, m = a % 60;
        return sign + h + (m != 0 ? ":" + (m < 10 ? "0" + m : String.valueOf(m)) : "") + "HRS";
    }

    private void calendar(Canvas c, RectF r, Calendar cal, int accent) {
        float pad = 22 * d;
        int title = accent == 0xFFFFFFFF ? Ui.RED : accent;
        String month = monthName.format(cal.getTime()).toUpperCase(Locale.getDefault());
        p.setTypeface(Ui.BOLD);
        p.setColor(title);
        float ts = r.height() * 0.085f;
        Ui.fitWidth(p, month, r.width() - 2 * pad, ts);
        p.setTextAlign(Paint.Align.LEFT);
        c.drawText(month, r.left + pad, r.top + pad + ts * 0.85f, p);

        Calendar m = (Calendar) cal.clone();
        m.set(Calendar.DAY_OF_MONTH, 1);
        int firstDow = cal.getFirstDayOfWeek();
        int startCol = Math.floorMod(m.get(Calendar.DAY_OF_WEEK) - firstDow, 7);
        int days = m.getActualMaximum(Calendar.DAY_OF_MONTH);
        int rows = (startCol + days + 6) / 7;
        int today = cal.get(Calendar.DAY_OF_MONTH);

        float gridTop = r.top + pad + ts * 1.5f;
        float gridBottom = r.bottom - pad * 0.8f;
        float colW = (r.width() - 2 * pad) / 7f;
        float rowH = (gridBottom - gridTop) / (rows + 1);
        float fs = Math.min(rowH * 0.48f, colW * 0.46f);

        String[] wd = DateFormatSymbols.getInstance(Locale.getDefault()).getShortWeekdays();
        p.setTypeface(Ui.BOLD);
        p.setTextSize(fs * 0.85f);
        for (int col = 0; col < 7; col++) {
            int dow = ((firstDow - 1 + col) % 7) + 1;
            String s = wd[dow];
            s = s.isEmpty() ? "" : s.substring(0, 1).toUpperCase(Locale.getDefault());
            p.setColor(Ui.GRAY);
            Ui.textCenter(c, p, s, r.left + pad + colW * (col + 0.5f), gridTop + rowH * 0.5f, tmp);
        }

        p.setTypeface(Ui.MEDIUM);
        p.setTextSize(fs);
        for (int day = 1; day <= days; day++) {
            int idx = startCol + day - 1;
            int row = idx / 7, col = idx % 7;
            float cx = r.left + pad + colW * (col + 0.5f);
            float cy = gridTop + rowH * (row + 1.5f);
            int dow = ((firstDow - 1 + col) % 7) + 1;
            boolean weekend = dow == Calendar.SATURDAY || dow == Calendar.SUNDAY;
            if (day == today) {
                p.setColor(title);
                c.drawCircle(cx, cy, Math.min(rowH, colW) * 0.46f, p);
                p.setColor(0xFFFFFFFF);
                p.setTypeface(Ui.BOLD);
            } else {
                p.setColor(weekend ? Ui.GRAY : 0xFFFFFFFF);
                p.setTypeface(Ui.MEDIUM);
            }
            Ui.textCenter(c, p, String.valueOf(day), cx, cy, tmp);
        }
        p.setTextAlign(Paint.Align.LEFT);
    }

    private void battery(Canvas c, RectF r, int accent, StandByView v) {
        int pct = v.batteryPct;
        boolean charging = v.charging;
        float cx = r.centerX();
        float rad = Math.min(r.width(), r.height()) * 0.28f;
        float cy = r.top + r.height() * 0.42f;
        float sw = rad * 0.17f;
        int ringColor = charging ? Ui.GREEN : (pct >= 0 && pct <= 20 ? Ui.RED : accent);

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(sw);
        p.setColor(Ui.alpha(ringColor, 60));
        c.drawCircle(cx, cy, rad, p);
        if (pct > 0) {
            p.setColor(ringColor);
            rf.set(cx - rad, cy - rad, cx + rad, cy + rad);
            c.drawArc(rf, -90f, 360f * pct / 100f, false, p);
        }
        p.setStyle(Paint.Style.FILL);

        p.setTypeface(Ui.BOLD);
        p.setColor(0xFFFFFFFF);
        p.setTextSize(rad * 0.56f);
        Ui.textCenter(c, p, pct >= 0 ? pct + "%" : "--", cx, cy, tmp);

        String status;
        if (charging) status = v.full || pct >= 100 ? "Fully charged" : "Charging";
        else status = "On battery";
        p.setTypeface(Ui.MEDIUM);
        p.setTextSize(r.height() * 0.07f);
        float tw = p.measureText(status);
        float sy = r.top + r.height() * 0.80f;
        float bolt = charging ? p.getTextSize() * 0.9f : 0f;
        float startX = cx - (tw + bolt * 1.2f) / 2f;
        if (charging) {
            Icons.bolt(c, p, path, startX, sy - bolt / 2f, bolt, Ui.GREEN);
        }
        p.setColor(0xFFFFFFFF);
        p.setTextAlign(Paint.Align.LEFT);
        Ui.textMid(c, p, status, startX + bolt * 1.2f, sy, tmp);

        long rem = charging ? v.chargeTimeRemaining() : -1;
        if (rem > 0 && pct < 100) {
            long mins = rem / 60000;
            String t = "Full in " + (mins >= 60 ? (mins / 60) + " h " + (mins % 60) + " min" : mins + " min");
            p.setTypeface(Ui.REGULAR);
            p.setColor(Ui.GRAY);
            p.setTextSize(r.height() * 0.055f);
            Ui.textCenter(c, p, t, cx, r.top + r.height() * 0.90f, tmp);
        }
        p.setTextAlign(Paint.Align.LEFT);
    }

    private void date(Canvas c, RectF r, Calendar cal, int accent, StandByView v) {
        float cx = r.centerX();
        int title = accent == 0xFFFFFFFF ? Ui.RED : accent;
        String wd = weekday.format(cal.getTime()).toUpperCase(Locale.getDefault());
        p.setTypeface(Ui.BOLD);
        p.setColor(title);
        Ui.fitWidth(p, wd, r.width() * 0.8f, r.height() * 0.085f);
        Ui.textCenter(c, p, wd, cx, r.top + r.height() * 0.17f, tmp);

        String dn = String.valueOf(cal.get(Calendar.DAY_OF_MONTH));
        p.setTypeface(Ui.LIGHT);
        p.setColor(0xFFFFFFFF);
        Ui.fit(p, dn, r.width() * 0.7f, r.height() * 0.38f, tmp);
        Ui.textCenter(c, p, dn, cx, r.top + r.height() * 0.48f, tmp);

        String my = monthYear.format(cal.getTime());
        p.setTypeface(Ui.MEDIUM);
        p.setColor(Ui.GRAY);
        Ui.fitWidth(p, my, r.width() * 0.8f, r.height() * 0.065f);
        Ui.textCenter(c, p, my, cx, r.top + r.height() * 0.73f, tmp);

        String alarm = v.nextAlarm();
        String line = alarm == null ? "No alarm set" : "⏰ " + alarm;
        p.setTypeface(Ui.REGULAR);
        p.setColor(alarm == null ? Ui.GRAY : 0xFFFFFFFF);
        Ui.fitWidth(p, line, r.width() * 0.8f, r.height() * 0.06f);
        Ui.textCenter(c, p, line, cx, r.top + r.height() * 0.87f, tmp);
        p.setTextAlign(Paint.Align.LEFT);
    }

    private void nowPlaying(Canvas c, RectF r, StandByView v, int stack) {
        NowPlaying np = v.nowPlaying;
        RectF[] hits = npHits[stack];
        for (RectF h : hits) h.setEmpty();
        float padX = Math.max(18 * d, r.width() * 0.056f), padY = Math.max(16 * d, r.height() * 0.058f);
        boolean spotify = NowPlaying.SPOTIFY.equals(np.pkg);
        int brand = spotify || !np.active() ? 0xFF1DB954 : 0xFFFF375F;
        p.setTextAlign(Paint.Align.LEFT);
        p.setStyle(Paint.Style.FILL);

        // soft glow in the album's colours
        if (np.active() && np.artBlur != null) {
            int c1 = 0xFF3A3A3C, c2 = 0xFF3A3A3C;
            try {
                c1 = np.artBlur.getPixel(3, 3) | 0xFF000000;
                c2 = np.artBlur.getPixel(9, 9) | 0xFF000000;
            } catch (Exception ignored) {
            }
            float rad = v.cardRadius();
            c.save();
            path.reset();
            path.addRoundRect(r, rad, rad, Path.Direction.CW);
            c.clipPath(path);
            p.setShader(new android.graphics.RadialGradient(r.left + r.width() * 0.2f, r.top + r.height() * 0.3f,
                    r.width() * 0.6f, Ui.alpha(c1, 72), Ui.alpha(c1, 0), android.graphics.Shader.TileMode.CLAMP));
            c.drawRect(r, p);
            p.setShader(new android.graphics.RadialGradient(r.left + r.width() * 0.85f, r.top + r.height() * 0.85f,
                    r.width() * 0.6f, Ui.alpha(c2, 56), Ui.alpha(c2, 0), android.graphics.Shader.TileMode.CLAMP));
            c.drawRect(r, p);
            p.setShader(null);
            c.restore();
        }

        // header: brand dot + app name
        float hy = r.top + padY + 7 * d;
        p.setColor(brand);
        c.drawCircle(r.left + padX + 4 * d, hy, 4 * d, p);
        p.setTypeface(Ui.BOLD);
        p.setLetterSpacing(0.14f);
        p.setTextSize(12 * d);
        Ui.textMid(c, p, (np.active() ? np.appLabel() : "Now Playing").toUpperCase(Locale.US), r.left + padX + 14 * d, hy, tmp);
        p.setLetterSpacing(0f);

        if (!np.active()) {
            String msg = NowPlaying.hasAccess(v.getContext()) ? "Nothing playing" : "Allow notification access in Settings";
            p.setTypeface(Ui.MEDIUM);
            p.setColor(Ui.GRAY);
            Ui.fitWidth(p, msg, r.width() - 2 * padX, r.height() * 0.06f);
            Ui.textCenter(c, p, msg, r.centerX(), r.centerY(), tmp);
            p.setTextAlign(Paint.Align.LEFT);
            return;
        }

        // art + text, side by side
        float artS = Math.min(r.height() * 0.34f, r.width() * 0.30f);
        float artTop = hy + 16 * d;
        rf.set(r.left + padX, artTop, r.left + padX + artS, artTop + artS);
        if (np.art != null) {
            c.save();
            path.reset();
            path.addRoundRect(rf, artS * 0.12f, artS * 0.12f, Path.Direction.CW);
            c.clipPath(path);
            android.graphics.Matrix m = new android.graphics.Matrix();
            float sc = Math.max(artS / np.art.getWidth(), artS / np.art.getHeight());
            m.setScale(sc, sc);
            m.postTranslate(rf.left + (artS - np.art.getWidth() * sc) / 2f, rf.top + (artS - np.art.getHeight() * sc) / 2f);
            c.drawBitmap(np.art, m, p);
            c.restore();
        } else {
            p.setColor(0xFF2C2C2E);
            c.drawRoundRect(rf, artS * 0.12f, artS * 0.12f, p);
            p.setColor(Ui.GRAY);
            p.setTextSize(artS * 0.4f);
            Ui.textCenter(c, p, "\u266A", rf.centerX(), rf.centerY(), tmp);
            p.setTextAlign(Paint.Align.LEFT);
        }
        float tx = rf.right + 18 * d, tw = r.right - padX - tx;
        float titleSize = Math.min(24 * d, r.height() * 0.066f);
        p.setTypeface(Ui.BOLD);
        p.setColor(0xFFFFFFFF);
        p.setTextSize(titleSize);
        String[] lines = wrap2(np.title == null ? "" : np.title, tw);
        float artistSize = titleSize * 0.68f, albumSize = titleSize * 0.55f;
        boolean hasAlbum = np.album != null && !np.album.isEmpty();
        float block = lines.length * titleSize * 1.18f + 6 * d + artistSize * 1.2f + (hasAlbum ? albumSize * 1.3f : 0);
        float ty = rf.centerY() - block / 2f + titleSize * 0.92f;
        for (String line : lines) {
            c.drawText(line, tx, ty, p);
            ty += titleSize * 1.18f;
        }
        ty += 6 * d - titleSize * 0.18f + artistSize * 0.2f;
        p.setTypeface(Ui.REGULAR);
        p.setTextSize(artistSize);
        p.setColor(0xB8FFFFFF);
        c.drawText(clip(np.artist == null ? "" : np.artist, tw), tx, ty, p);
        if (hasAlbum) {
            ty += albumSize * 1.35f;
            p.setTextSize(albumSize);
            p.setColor(0x73FFFFFF);
            c.drawText(clip(np.album, tw), tx, ty, p);
        }

        // progress with times
        float py = rf.bottom + Math.max(16 * d, r.height() * 0.07f);
        long pos = np.position();
        float fr = np.duration > 0 ? Math.min(1f, pos / (float) np.duration) : 0f;
        float bx0 = r.left + padX, bx1 = r.right - padX;
        float bh = 5 * d;
        rf.set(bx0, py - bh / 2f, bx1, py + bh / 2f);
        p.setColor(0x38FFFFFF);
        c.drawRoundRect(rf, bh / 2f, bh / 2f, p);
        if (fr > 0) {
            rf.set(bx0, py - bh / 2f, bx0 + (bx1 - bx0) * fr, py + bh / 2f);
            p.setColor(0xFFFFFFFF);
            c.drawRoundRect(rf, bh / 2f, bh / 2f, p);
        }
        p.setTypeface(Ui.REGULAR);
        p.setTextSize(12 * d);
        p.setColor(0x99FFFFFF);
        float timeY = py + 18 * d;
        c.drawText(Pomodoro.fmt(pos), bx0, timeY, p);
        if (np.duration > 0) {
            p.setTextAlign(Paint.Align.RIGHT);
            c.drawText("-" + Pomodoro.fmt(Math.max(0, np.duration - pos)), bx1, timeY, p);
            p.setTextAlign(Paint.Align.LEFT);
        }

        // controls: big white play button, prev / next either side
        float playR = Math.min(32 * d, r.height() * 0.09f);
        float cy = Math.max(timeY + 10 * d + playR, (timeY + r.bottom - padY) / 2f + 4 * d);
        cy = Math.min(cy, r.bottom - padY - playR);
        float cx = r.centerX();
        float side = playR + 44 * d + 24 * d;
        p.setColor(0xFFFFFFFF);
        c.drawCircle(cx, cy, playR, p);
        hits[1].set(cx - playR - 6 * d, cy - playR - 6 * d, cx + playR + 6 * d, cy + playR + 6 * d);
        Icons.playPause(c, p, path, new RectF(cx - playR * 0.75f, cy - playR * 0.75f, cx + playR * 0.75f, cy + playR * 0.75f),
                np.playing, 0xFF000000);
        float is = 24 * d;
        hits[0].set(cx - side - is, cy - is, cx - side + is, cy + is);
        hits[2].set(cx + side - is, cy - is, cx + side + is, cy + is);
        Icons.prev(c, p, path, hits[0], 0xFFFFFFFF);
        Icons.next(c, p, path, hits[2], 0xFFFFFFFF);
    }

    /** Up to two lines; the second is ellipsized. */
    private String[] wrap2(String text, float maxW) {
        if (p.measureText(text) <= maxW) return new String[]{text};
        String[] words = text.split(" ");
        StringBuilder first = new StringBuilder();
        int i = 0;
        while (i < words.length) {
            String trial = first.length() == 0 ? words[i] : first + " " + words[i];
            if (p.measureText(trial) > maxW) break;
            first.setLength(0);
            first.append(trial);
            i++;
        }
        if (first.length() == 0) return new String[]{clip(text, maxW)};
        StringBuilder rest = new StringBuilder();
        for (int k = i; k < words.length; k++) {
            if (rest.length() > 0) rest.append(' ');
            rest.append(words[k]);
        }
        return new String[]{first.toString(), clip(rest.toString(), maxW)};
    }

    private String clip(String s, float maxW) {
        if (p.measureText(s) <= maxW) return s;
        int n = s.length();
        while (n > 1 && p.measureText(s, 0, n) + p.measureText("\u2026") > maxW) n--;
        return s.substring(0, n) + "\u2026";
    }
}
