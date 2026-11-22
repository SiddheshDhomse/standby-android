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
    static final int ANALOG = 0, DIGITAL = 1, WORLD = 2, CALENDAR = 3, BATTERY = 4, DATE = 5;

    static final String[][] CITIES = {
            {"Cupertino", "America/Los_Angeles"},
            {"New York", "America/New_York"},
            {"London", "Europe/London"},
            {"Tokyo", "Asia/Tokyo"},
    };

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

    void draw(Canvas c, int type, RectF r, Calendar cal, StandByView v) {
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
        }
    }

    private void analog(Canvas c, RectF r, Calendar cal, int accent) {
        float rad = Math.min(r.width(), r.height()) / 2f - 16 * d;
        Ui.analog(c, p, r.centerX(), r.centerY(), rad, cal, accent == 0xFFFFFFFF ? Ui.RED : accent,
                true, false, tmp);
    }

    private void digital(Canvas c, RectF r, Calendar cal, int accent, StandByView v) {
        String t = Ui.time(cal, v.h24);
        p.setTypeface(Ui.BOLD);
        p.setColor(0xFFFFFFFF);
        Ui.fit(p, t, r.width() * 0.84f, r.height() * 0.40f, tmp);
        Ui.textCenter(c, p, t, r.centerX(), r.top + r.height() * 0.46f, tmp);

        String ds = longDate.format(cal.getTime()).toUpperCase(Locale.getDefault());
        p.setTypeface(Ui.MEDIUM);
        p.setColor(accent);
        Ui.fitWidth(p, ds, r.width() * 0.84f, r.height() * 0.075f);
        Ui.textCenter(c, p, ds, r.centerX(), r.top + r.height() * 0.78f, tmp);
    }

    private void world(Canvas c, RectF r, Calendar cal, StandByView v) {
        float pad = 22 * d;
        float rowH = (r.height() - 2 * pad) / CITIES.length;
        long ms = cal.getTimeInMillis();
        TimeZone local = cal.getTimeZone();
        int localDay = cal.get(Calendar.YEAR) * 400 + cal.get(Calendar.DAY_OF_YEAR);
        for (int i = 0; i < CITIES.length; i++) {
            TimeZone tz = TimeZone.getTimeZone(CITIES[i][1]);
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
            Ui.fitWidth(p, CITIES[i][0], (right - left) * 0.5f, rowH * 0.30f);
            c.drawText(CITIES[i][0], left, top + rowH * 0.46f, p);

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

            if (i < CITIES.length - 1) {
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
            drawBolt(c, startX, sy - bolt / 2f, bolt, Ui.GREEN);
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

    private void drawBolt(Canvas c, float x, float y, float s, int color) {
        path.reset();
        path.moveTo(x + 0.60f * s, y);
        path.lineTo(x + 0.08f * s, y + 0.58f * s);
        path.lineTo(x + 0.45f * s, y + 0.58f * s);
        path.lineTo(x + 0.35f * s, y + s);
        path.lineTo(x + 0.92f * s, y + 0.40f * s);
        path.lineTo(x + 0.55f * s, y + 0.40f * s);
        path.close();
        p.setColor(color);
        c.drawPath(path, p);
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
}
