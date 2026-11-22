package com.siddhesh.standby;

import android.Manifest;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.CalendarContract;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * "Up Next" — upcoming events from the phone's calendar. Teams meetings come from the work
 * Outlook calendar (Outlook app → Sync calendars). Read-only, nothing leaves the phone.
 */
final class Agenda {
    static final class Event {
        String title, joinUrl;
        long begin, end;
        boolean allDay;
        int color;
    }

    static final class Cal {
        long id;
        String name, account;
        int color;
    }

    // Only real Teams meeting links are ever opened.
    private static final Pattern TEAMS = Pattern.compile(
            "https://(teams\\.microsoft\\.com/l/meetup-join/|teams\\.live\\.com/meet/)[A-Za-z0-9%._~:/?#@!$&'()*+,;=\\-]+");

    private final Context ctx;
    private final Runnable onChange;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect tmp = new Rect();
    private final RectF rf = new RectF();
    private final float d;
    private volatile boolean busy;
    private long lastQuery;
    List<Event> events = new ArrayList<>();
    final RectF[] joinHits = {new RectF(), new RectF()};
    String[] joinUrls = new String[2];

    Agenda(Context ctx, Runnable onChange, float density) {
        this.ctx = ctx;
        this.onChange = onChange;
        this.d = density;
    }

    static boolean hasPermission(Context c) {
        return c.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED;
    }

    void invalidateCache() {
        lastQuery = 0;
    }

    void ensure(Settings s) {
        long now = System.currentTimeMillis();
        if (busy || now - lastQuery < 60_000 || !hasPermission(ctx)) return;
        lastQuery = now;
        busy = true;
        final String ids = s.calendarIds;
        new Thread(() -> {
            List<Event> list = query(ids);
            main.post(() -> {
                busy = false;
                events = list;
                onChange.run();
            });
        }, "agenda").start();
    }

    private List<Event> query(String ids) {
        List<Event> out = new ArrayList<>();
        long now = System.currentTimeMillis();
        Uri.Builder b = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(b, now - 12 * 3600_000L);
        ContentUris.appendId(b, now + 48 * 3600_000L);
        String[] proj = {
                CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.DISPLAY_COLOR,
                CalendarContract.Instances.EVENT_LOCATION, CalendarContract.Instances.DESCRIPTION,
                CalendarContract.Instances.CALENDAR_ID, CalendarContract.Instances.SELF_ATTENDEE_STATUS,
        };
        StringBuilder sel = new StringBuilder(CalendarContract.Instances.END + " > ? AND "
                + CalendarContract.Instances.SELF_ATTENDEE_STATUS + " != "
                + CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED);
        List<String> args = new ArrayList<>();
        args.add(String.valueOf(now));
        if (ids != null && !ids.isEmpty()) {
            sel.append(" AND ").append(CalendarContract.Instances.CALENDAR_ID).append(" IN (");
            String[] parts = ids.split(",");
            int n = 0;
            for (String part : parts) {
                try {
                    long id = Long.parseLong(part.trim());
                    sel.append(n++ == 0 ? "?" : ",?");
                    args.add(String.valueOf(id));
                } catch (NumberFormatException ignored) {
                }
            }
            if (n == 0) sel.append("-1");
            sel.append(")");
        }
        try (Cursor c = ctx.getContentResolver().query(b.build(), proj, sel.toString(),
                args.toArray(new String[0]), CalendarContract.Instances.BEGIN + " ASC")) {
            if (c == null) return out;
            while (c.moveToNext() && out.size() < 8) {
                Event e = new Event();
                e.title = c.getString(0);
                if (e.title == null || e.title.trim().isEmpty()) e.title = "Busy";
                e.begin = c.getLong(1);
                e.end = c.getLong(2);
                e.allDay = c.getInt(3) == 1;
                e.color = c.getInt(4) | 0xFF000000;
                String loc = c.getString(5), desc = c.getString(6);
                e.joinUrl = findTeams(loc);
                if (e.joinUrl == null) e.joinUrl = findTeams(desc);
                out.add(e);
            }
        } catch (Exception ignored) {
        }
        // timed events first, all-day after
        List<Event> timed = new ArrayList<>(), allDay = new ArrayList<>();
        for (Event e : out) (e.allDay ? allDay : timed).add(e);
        timed.addAll(allDay);
        return timed;
    }

    static String findTeams(String s) {
        if (s == null) return null;
        Matcher m = TEAMS.matcher(s);
        return m.find() ? m.group() : null;
    }

    static boolean isSafeJoinUrl(String url) {
        if (url == null) return false;
        Uri u = Uri.parse(url);
        String host = u.getHost();
        return "https".equals(u.getScheme()) && host != null
                && (host.equals("teams.microsoft.com") || host.equals("teams.live.com"));
    }

    static List<Cal> listCalendars(Context c) {
        List<Cal> out = new ArrayList<>();
        if (!hasPermission(c)) return out;
        String[] proj = {CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.ACCOUNT_NAME, CalendarContract.Calendars.CALENDAR_COLOR};
        try (Cursor cur = c.getContentResolver().query(CalendarContract.Calendars.CONTENT_URI, proj,
                CalendarContract.Calendars.VISIBLE + " = 1", null, null)) {
            if (cur == null) return out;
            while (cur.moveToNext()) {
                Cal cal = new Cal();
                cal.id = cur.getLong(0);
                cal.name = cur.getString(1);
                cal.account = cur.getString(2);
                cal.color = cur.getInt(3) | 0xFF000000;
                out.add(cal);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    // ------------------------------------------------------------ drawing

    private String when(Event e, long now, boolean h24) {
        if (e.allDay) return "All day";
        Calendar b = Calendar.getInstance();
        b.setTimeInMillis(e.begin);
        Calendar en = Calendar.getInstance();
        en.setTimeInMillis(e.end);
        String range = Ui.time(b, h24) + (h24 ? "" : " " + Ui.amPm(b)) + " – " + Ui.time(en, h24)
                + (h24 ? "" : " " + Ui.amPm(en));
        if (e.begin <= now) return range + " · now";
        long mins = (e.begin - now) / 60_000;
        if (mins < 60) return range + " · in " + Math.max(1, mins) + " min";
        Calendar today = Calendar.getInstance();
        if (b.get(Calendar.DAY_OF_YEAR) != today.get(Calendar.DAY_OF_YEAR)) {
            return new java.text.SimpleDateFormat("EEE", Locale.getDefault()).format(b.getTime()) + " " + range;
        }
        return range;
    }

    void drawWidget(Canvas c, RectF r, int accent, boolean h24, boolean hideDetails, int stack) {
        joinHits[stack].setEmpty();
        joinUrls[stack] = null;
        float pad = 22 * d;
        long now = System.currentTimeMillis();
        p.setTextAlign(Paint.Align.LEFT);
        p.setStyle(Paint.Style.FILL);
        p.setTypeface(Ui.BOLD);
        p.setColor(accent);
        p.setTextSize(r.height() * 0.055f);
        c.drawText("UP NEXT", r.left + pad, r.top + pad + p.getTextSize(), p);

        if (!hasPermission(ctx)) {
            msg(c, r, "Allow calendar access in StandBy Settings");
            return;
        }
        if (events.isEmpty()) {
            msg(c, r, "Nothing else on your calendar");
            return;
        }
        Event e = events.get(0);
        float y = r.top + pad + r.height() * 0.16f;
        p.setColor(e.color);
        rf.set(r.left + pad, y - r.height() * 0.07f, r.left + pad + 4 * d, y + r.height() * 0.10f);
        c.drawRoundRect(rf, 2 * d, 2 * d, p);
        float tx = r.left + pad + 14 * d, tw = r.width() - 2 * pad - 14 * d;
        p.setTypeface(Ui.BOLD);
        p.setColor(0xFFFFFFFF);
        p.setTextSize(r.height() * 0.075f);
        String title = hideDetails ? (e.joinUrl != null ? "Teams meeting" : "Event") : e.title;
        c.drawText(clip(title, tw), tx, y, p);
        p.setTypeface(Ui.REGULAR);
        p.setColor(Ui.GRAY);
        p.setTextSize(r.height() * 0.05f);
        c.drawText(clip(when(e, now, h24), tw), tx, y + r.height() * 0.085f, p);

        float listTop = y + r.height() * 0.16f;
        if (e.joinUrl != null && !e.allDay && e.begin - now < 15 * 60_000 && e.end > now) {
            float bh = Math.max(40 * d, r.height() * 0.11f);
            rf.set(tx, listTop, tx + Math.min(tw, 150 * d), listTop + bh);
            p.setColor(0xFF5B5FC7); // Teams purple
            c.drawRoundRect(rf, bh / 2f, bh / 2f, p);
            p.setColor(0xFFFFFFFF);
            p.setTypeface(Ui.BOLD);
            p.setTextSize(bh * 0.38f);
            Ui.textCenter(c, p, "Join Teams", rf.centerX(), rf.centerY(), tmp);
            p.setTextAlign(Paint.Align.LEFT);
            joinHits[stack].set(rf);
            joinUrls[stack] = e.joinUrl;
            listTop += bh + 10 * d;
        } else if (e.joinUrl != null) {
            p.setColor(0xFF7B83EB);
            p.setTypeface(Ui.MEDIUM);
            p.setTextSize(r.height() * 0.045f);
            c.drawText("Teams meeting", tx, listTop + r.height() * 0.03f, p);
            listTop += r.height() * 0.07f;
        }

        p.setColor(0xFF2C2C2E);
        c.drawRect(r.left + pad, listTop, r.right - pad, listTop + Math.max(1f, d), p);
        float rowH = r.height() * 0.105f;
        float yy = listTop + rowH * 0.75f;
        for (int i = 1; i < events.size() && yy < r.bottom - pad * 0.6f; i++) {
            Event ev = events.get(i);
            p.setColor(ev.color);
            c.drawCircle(r.left + pad + 4 * d, yy - rowH * 0.18f, 4 * d, p);
            p.setTypeface(Ui.REGULAR);
            p.setColor(0xFFFFFFFF);
            p.setTextSize(r.height() * 0.052f);
            String t = hideDetails ? (ev.joinUrl != null ? "Teams meeting" : "Event") : ev.title;
            Calendar b = Calendar.getInstance();
            b.setTimeInMillis(ev.begin);
            String right = ev.allDay ? "All day" : Ui.time(b, h24) + (h24 ? "" : " " + Ui.amPm(b));
            p.setTextAlign(Paint.Align.RIGHT);
            p.setColor(Ui.GRAY);
            c.drawText(right, r.right - pad, yy, p);
            float rw = p.measureText(right) + 12 * d;
            p.setTextAlign(Paint.Align.LEFT);
            p.setColor(0xFFFFFFFF);
            c.drawText(clip(t, r.width() - 2 * pad - 18 * d - rw), r.left + pad + 16 * d, yy, p);
            yy += rowH;
        }
    }

    private void msg(Canvas c, RectF r, String m) {
        p.setTypeface(Ui.MEDIUM);
        p.setColor(Ui.GRAY);
        Ui.fitWidth(p, m, r.width() * 0.85f, r.height() * 0.06f);
        Ui.textCenter(c, p, m, r.centerX(), r.centerY(), tmp);
        p.setTextAlign(Paint.Align.LEFT);
    }

    private String clip(String s, float maxW) {
        if (s == null) return "";
        if (p.measureText(s) <= maxW) return s;
        int n = s.length();
        while (n > 1 && p.measureText(s, 0, n) + p.measureText("…") > maxW) n--;
        return s.substring(0, n) + "…";
    }
}
