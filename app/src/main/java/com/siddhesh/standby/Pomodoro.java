package com.siddhesh.standby;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.os.VibrationEffect;
import android.os.Vibrator;

import java.util.Locale;

/** Pomodoro timer (focus / short break / long break), persisted so it survives restarts. */
final class Pomodoro {
    static final int FOCUS = 0, SHORT = 1, LONG = 2;
    static final String[] MODE_NAMES = {"Focus", "Short break", "Long break"};
    static final int[] MODE_COLORS = {0xFFFF6347, 0xFF30D158, 0xFF64D2FF};

    private final Context ctx;
    private final SharedPreferences sp;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect tmp = new Rect();
    private final RectF rf = new RectF();
    private final Path path = new Path();
    private final float d;

    int mode;
    boolean running;
    long endAt;          // when running
    long remaining;      // when paused
    int done;            // completed focus sessions
    long flashUntil;

    final RectF btnReset = new RectF(), btnToggle = new RectF(), btnSkip = new RectF();

    Pomodoro(Context ctx, float density) {
        this.ctx = ctx;
        this.d = density;
        sp = ctx.getSharedPreferences(Settings.FILE, Context.MODE_PRIVATE);
        reload();
    }

    /** Re-reads state (the alarm receiver may have advanced it while StandBy was closed). */
    void reload() {
        mode = Settings.clamp(sp.getInt("pomo_mode", FOCUS), 0, 2);
        running = sp.getBoolean("pomo_running", false);
        endAt = sp.getLong("pomo_end", 0);
        remaining = sp.getLong("pomo_remaining", -1);
        done = sp.getInt("pomo_done", 0);
    }

    private void save() {
        sp.edit().putInt("pomo_mode", mode).putBoolean("pomo_running", running)
                .putLong("pomo_end", endAt).putLong("pomo_remaining", remaining)
                .putInt("pomo_done", done).apply();
        schedule();
    }

    /** Exact alarm at the end of the running session (works with the screen off). */
    private void schedule() {
        try {
            android.app.AlarmManager am = (android.app.AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            android.content.Intent i = new android.content.Intent(ctx, PomodoroReceiver.class);
            android.app.PendingIntent pi = android.app.PendingIntent.getBroadcast(ctx, 7, i,
                    android.app.PendingIntent.FLAG_IMMUTABLE | android.app.PendingIntent.FLAG_UPDATE_CURRENT);
            am.cancel(pi);
            if (!running) return;
            if (android.os.Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, endAt, pi);
            } else {
                am.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, endAt, pi);
            }
        } catch (Exception ignored) {
        }
    }

    long duration(int m, Settings s) {
        int min = m == FOCUS ? s.pomoFocus : (m == SHORT ? s.pomoShort : s.pomoLong);
        return min * 60_000L;
    }

    long remaining(long now, Settings s) {
        if (running) return Math.max(0, endAt - now);
        if (remaining < 0) return duration(mode, s);
        return remaining;
    }

    void toggle(long now, Settings s) {
        if (running) {
            remaining = Math.max(0, endAt - now);
            running = false;
        } else {
            endAt = now + remaining(now, s);
            running = true;
        }
        save();
    }

    void reset(Settings s) {
        running = false;
        remaining = duration(mode, s);
        save();
    }

    void skip(long now, Settings s) {
        advance(now, s, false);
    }

    private static int today() {
        java.util.Calendar c = java.util.Calendar.getInstance();
        return c.get(java.util.Calendar.YEAR) * 400 + c.get(java.util.Calendar.DAY_OF_YEAR);
    }

    /** Call regularly; returns true when a session just finished. */
    boolean tick(long now, Settings s) {
        int day = today();
        if (sp.getInt("pomo_day", -1) != day) {
            done = 0;
            sp.edit().putInt("pomo_day", day).apply();
            save();
        }
        if (running && now >= endAt) {
            reload(); // another instance (alarm receiver) may already have handled it
            if (running && now >= endAt) {
                advance(now, s, true);
                return true;
            }
        }
        return false;
    }

    private void advance(long now, Settings s, boolean finished) {
        if (mode == FOCUS) {
            if (finished) done++;
            mode = (done > 0 && done % s.pomoRounds == 0 && finished) ? LONG : SHORT;
        } else {
            mode = FOCUS;
        }
        remaining = duration(mode, s);
        running = finished && s.pomoAutoStart;
        if (running) endAt = now + remaining;
        if (finished) {
            flashUntil = now + 4000;
            alert(s);
        }
        save();
    }

    private static final String CH_SOUND = "pomodoro_sound", CH_SILENT = "pomodoro_vibrate";

    private void alert(Settings s) {
        boolean posted = false;
        try {
            android.app.NotificationManager nm =
                    (android.app.NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            boolean allowed = nm != null && (android.os.Build.VERSION.SDK_INT < 33
                    || ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED) && nm.areNotificationsEnabled();
            if (allowed) {
                android.app.NotificationChannel snd = new android.app.NotificationChannel(CH_SOUND,
                        "Pomodoro (sound)", android.app.NotificationManager.IMPORTANCE_HIGH);
                snd.enableVibration(true);
                snd.setVibrationPattern(new long[]{0, 400, 200, 400, 200, 700});
                android.app.NotificationChannel sil = new android.app.NotificationChannel(CH_SILENT,
                        "Pomodoro (vibrate only)", android.app.NotificationManager.IMPORTANCE_HIGH);
                sil.setSound(null, null);
                sil.enableVibration(true);
                sil.setVibrationPattern(new long[]{0, 400, 200, 400, 200, 700});
                nm.createNotificationChannel(snd);
                nm.createNotificationChannel(sil);
                android.content.Intent open = new android.content.Intent(ctx, StandByActivity.class)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                android.app.PendingIntent pi = android.app.PendingIntent.getActivity(ctx, 8, open,
                        android.app.PendingIntent.FLAG_IMMUTABLE | android.app.PendingIntent.FLAG_UPDATE_CURRENT);
                String title = mode == FOCUS ? "Break over \u2014 time to focus"
                        : (mode == LONG ? "Great work! Take a long break" : "Focus done \u2014 take a short break");
                android.app.Notification n = new android.app.Notification.Builder(ctx, s.pomoSound ? CH_SOUND : CH_SILENT)
                        .setSmallIcon(R.drawable.ic_tile_standby)
                        .setContentTitle(title)
                        .setContentText(running ? MODE_NAMES[mode] + " started (" + fmt(duration(mode, s)) + ")"
                                : "Tap to open StandBy and start the next session")
                        .setCategory(android.app.Notification.CATEGORY_ALARM)
                        .setAutoCancel(true)
                        .setContentIntent(pi)
                        .build();
                nm.notify(41, n);
                posted = true;
            }
        } catch (Exception ignored) {
        }
        if (posted) return;
        // fallback when notifications are off
        try {
            Vibrator v = (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator()) {
                v.vibrate(VibrationEffect.createWaveform(new long[]{0, 400, 200, 400, 200, 700}, -1));
            }
        } catch (Exception ignored) {
        }
        if (s.pomoSound) {
            try {
                Ringtone r = RingtoneManager.getRingtone(ctx,
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
                if (r != null) r.play();
            } catch (Exception ignored) {
            }
        }
    }

    static String fmt(long ms) {
        long sec = (ms + 999) / 1000;
        return String.format(Locale.US, "%02d:%02d", sec / 60, sec % 60);
    }

    // ------------------------------------------------------------ drawing

    void drawFace(Canvas c, int w, int h, long now, Settings s) {
        boolean landscape = w >= h;
        long rem = remaining(now, s);
        long total = duration(mode, s);
        float frac = total > 0 ? 1f - rem / (float) total : 0f;
        int color = MODE_COLORS[mode];

        float r = landscape ? h * 0.36f : w * 0.36f;
        float cx = landscape ? w * 0.30f : w / 2f;
        float cy = landscape ? h / 2f : h * 0.36f;

        if (now < flashUntil && (now / 400) % 2 == 0) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(Ui.alpha(color, 60));
            c.drawRect(0, 0, w, h, p);
        }

        ring(c, cx, cy, r, r * 0.09f, frac, color);

        p.setStyle(Paint.Style.FILL);
        p.setTypeface(Ui.LIGHT);
        p.setColor(0xFFFFFFFF);
        String t = fmt(rem);
        Ui.fit(p, t, r * 1.35f, r * 0.55f, tmp);
        Ui.textCenter(c, p, t, cx, cy, tmp);
        p.setTypeface(Ui.MEDIUM);
        p.setColor(color);
        p.setTextSize(r * 0.14f);
        Ui.textCenter(c, p, running ? MODE_NAMES[mode].toUpperCase(Locale.US)
                : (MODE_NAMES[mode] + " · paused").toUpperCase(Locale.US), cx, cy + r * 0.48f, tmp);

        // right side: title, rounds, buttons
        float rx = landscape ? w * 0.58f : w * 0.08f;
        float rw = landscape ? w * 0.36f : w * 0.84f;
        float top = landscape ? h * 0.22f : h * 0.70f;
        p.setTextAlign(Paint.Align.LEFT);
        p.setTypeface(Ui.BOLD);
        p.setColor(0xFFFFFFFF);
        Ui.fitWidth(p, "Pomodoro", rw, landscape ? h * 0.11f : w * 0.08f);
        c.drawText("Pomodoro", rx, top, p);

        p.setTypeface(Ui.REGULAR);
        p.setColor(Ui.GRAY);
        p.setTextSize(landscape ? h * 0.05f : w * 0.04f);
        int round = (done % s.pomoRounds) + (mode == FOCUS ? 1 : 0);
        if (round == 0) round = s.pomoRounds;
        c.drawText("Session " + Math.min(round, s.pomoRounds) + " of " + s.pomoRounds
                + "  ·  " + done + " done today", rx, top + p.getTextSize() * 1.6f, p);

        float dotR = (landscape ? h : w) * 0.018f;
        float dy = top + p.getTextSize() * 3.2f;
        for (int i = 0; i < s.pomoRounds; i++) {
            boolean filled = i < (done % s.pomoRounds) || (done > 0 && done % s.pomoRounds == 0 && mode == LONG);
            p.setStyle(filled ? Paint.Style.FILL : Paint.Style.STROKE);
            p.setStrokeWidth(2 * d);
            p.setColor(filled ? MODE_COLORS[FOCUS] : Ui.GRAY);
            c.drawCircle(rx + dotR + i * dotR * 3f, dy, dotR, p);
        }
        p.setStyle(Paint.Style.FILL);

        float bh = Math.max(44 * d, (landscape ? h : w) * 0.12f);
        float gap = 10 * d;
        float by = landscape ? h * 0.62f : h * 0.86f;
        float bw = (rw - 2 * gap) / 3f;
        btnReset.set(rx, by, rx + bw, by + bh);
        btnToggle.set(rx + bw + gap, by, rx + 2 * bw + gap, by + bh);
        btnSkip.set(rx + 2 * (bw + gap), by, rx + 3 * bw + 2 * gap, by + bh);
        pill(c, btnReset, "Reset", 0xFF3A3A3C, 0xFFFFFFFF);
        pill(c, btnToggle, running ? "Pause" : "Start", color, 0xFF000000);
        pill(c, btnSkip, "Skip", 0xFF3A3A3C, 0xFFFFFFFF);
    }

    void drawWidget(Canvas c, RectF r, long now, Settings s) {
        long rem = remaining(now, s);
        long total = duration(mode, s);
        float frac = total > 0 ? 1f - rem / (float) total : 0f;
        int color = MODE_COLORS[mode];
        float rad = Math.min(r.width(), r.height()) * 0.30f;
        float cx = r.centerX(), cy = r.top + r.height() * 0.42f;
        ring(c, cx, cy, rad, rad * 0.12f, frac, color);
        p.setStyle(Paint.Style.FILL);
        p.setTypeface(Ui.MEDIUM);
        p.setColor(0xFFFFFFFF);
        String t = fmt(rem);
        Ui.fit(p, t, rad * 1.3f, rad * 0.45f, tmp);
        Ui.textCenter(c, p, t, cx, cy, tmp);
        p.setColor(color);
        p.setTypeface(Ui.BOLD);
        Ui.fitWidth(p, MODE_NAMES[mode].toUpperCase(Locale.US), r.width() * 0.8f, r.height() * 0.07f);
        Ui.textCenter(c, p, MODE_NAMES[mode].toUpperCase(Locale.US), cx, r.top + r.height() * 0.80f, tmp);
        p.setTypeface(Ui.REGULAR);
        p.setColor(Ui.GRAY);
        p.setTextSize(r.height() * 0.05f);
        Ui.textCenter(c, p, running ? "Tap to pause" : "Tap to start", cx, r.top + r.height() * 0.90f, tmp);
        p.setTextAlign(Paint.Align.LEFT);
    }

    private void ring(Canvas c, float cx, float cy, float r, float sw, float frac, int color) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(sw);
        p.setColor(Ui.alpha(color, 50));
        c.drawCircle(cx, cy, r, p);
        p.setColor(color);
        rf.set(cx - r, cy - r, cx + r, cy + r);
        // remaining time shown as the coloured part, shrinking clockwise
        float sweep = 360f * (1f - frac);
        if (sweep > 0.5f) c.drawArc(rf, -90f + 360f * frac, sweep, false, p);
        p.setStyle(Paint.Style.FILL);
    }

    private void pill(Canvas c, RectF r, String label, int bg, int fg) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(bg);
        c.drawRoundRect(r, r.height() / 2f, r.height() / 2f, p);
        p.setColor(fg);
        p.setTypeface(Ui.BOLD);
        Ui.fitWidth(p, label, r.width() - 16 * d, r.height() * 0.38f);
        Ui.textCenter(c, p, label, r.centerX(), r.centerY(), tmp);
        p.setTextAlign(Paint.Align.LEFT);
    }

    /** Returns true if the tap hit a control. */
    boolean onTap(float x, float y, long now, Settings s) {
        if (btnToggle.contains(x, y)) { toggle(now, s); return true; }
        if (btnReset.contains(x, y)) { reset(s); return true; }
        if (btnSkip.contains(x, y)) { skip(now, s); return true; }
        return false;
    }
}
