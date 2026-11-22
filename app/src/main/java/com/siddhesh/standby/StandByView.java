package com.siddhesh.standby;

import android.app.AlarmManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.BatteryManager;
import android.os.Build;
import android.text.format.DateFormat;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import java.util.Calendar;

/**
 * The whole StandBy experience in one view, shared by the app (Activity) and the
 * screen saver (DreamService).
 *
 * Pages (swipe left/right): Widgets · Photos · Clock.
 * Swipe up/down: change the widget in a stack, the photo, or the clock face.
 * Long-press: customise colours / night mode.
 */
public class StandByView extends View implements SensorEventListener {

    public interface Host {
        void setNight(boolean night);
        void openDreamSettings();
        void requestPhotoPermission();
        void exit();
        boolean isActivity();
    }

    static final int PAGE_WIDGETS = 0, PAGE_PHOTOS = 1, PAGE_CLOCK = 2, PAGES = 3;
    static final int[][] STACKS = {
            {Widgets.ANALOG, Widgets.DIGITAL, Widgets.WORLD},
            {Widgets.CALENDAR, Widgets.BATTERY, Widgets.DATE},
    };

    private static final int DRAG_NONE = 0, DRAG_H = 1, DRAG_V = 2, DRAG_IGNORE = 3;
    private static final int VT_STACK0 = 0, VT_STACK1 = 1, VT_FACE = 2, VT_PHOTO = 3, VT_EXIT = 4;

    private final Host host;
    private final SharedPreferences prefs;
    private final float d;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint layerPaint = new Paint();
    private final Rect tmp = new Rect();
    private final RectF rf = new RectF();
    private final RectF[] cards = {new RectF(), new RectF()};
    private final Widgets widgets;
    private final ClockFaces faces;
    private final PhotoSlideshow photos;
    private final int touchSlop;

    // navigation state (indices are unbounded ints; wrapped with floorMod when drawn)
    private int page;
    private float pagePos;
    private final int[] stackIdx = new int[2];
    private final float[] stackPos = new float[2];
    private final long[] stackDotsUntil = new long[2];
    private int faceIdx;
    private float facePos;
    private long faceDotsUntil;
    private long pageDotsUntil;

    // settings
    private int widgetAccent;
    private final int[] faceAccent = new int[ClockFaces.COUNT];
    private boolean nightAuto;

    // live state, read by Widgets
    boolean h24;
    int batteryPct = -1;
    boolean charging;
    boolean full;
    private boolean night;
    private float lux = -1f;
    private long chargeRemaining = -1;
    private long chargeCheckedAt;
    private String alarmText;
    private long alarmCheckedAt;

    // overlays
    private boolean showCustomize;
    private boolean showHelp;
    private final RectF[] colorHits = new RectF[Ui.ACCENTS.length];
    private final RectF panel = new RectF();
    private final RectF btnNight = new RectF(), btnAuto = new RectF(), btnDone = new RectF();
    private final RectF helpAuto = new RectF(), helpPhotos = new RectF(), helpOk = new RectF();

    // touch
    private int dragMode = DRAG_NONE;
    private int vTarget;
    private float vStartPos;
    private float downX, downY, lastX, lastY, velX, velY;
    private long lastT;
    private boolean longPressFired;
    private long lastFrame;

    private boolean attached;
    private SensorManager sensors;

    private final Runnable tick = this::invalidate;
    private final Runnable longPress = () -> {
        if (dragMode == DRAG_NONE && !showHelp && !showCustomize) {
            longPressFired = true;
            showCustomize = true;
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            invalidate();
        }
    };

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateBattery(intent);
        }
    };

    public StandByView(Context ctx, Host host) {
        super(ctx);
        this.host = host;
        this.d = ctx.getResources().getDisplayMetrics().density;
        this.prefs = ctx.getSharedPreferences("standby", Context.MODE_PRIVATE);
        this.touchSlop = ViewConfiguration.get(ctx).getScaledTouchSlop();
        widgets = new Widgets(d);
        faces = new ClockFaces(d);
        photos = new PhotoSlideshow(ctx, this, d);
        for (int i = 0; i < colorHits.length; i++) colorHits[i] = new RectF();

        page = clamp(prefs.getInt("page", PAGE_CLOCK), 0, PAGES - 1);
        pagePos = page;
        stackIdx[0] = prefs.getInt("stack0", 0);
        stackIdx[1] = prefs.getInt("stack1", 0);
        stackPos[0] = stackIdx[0];
        stackPos[1] = stackIdx[1];
        faceIdx = prefs.getInt("face", ClockFaces.DIGITAL);
        facePos = faceIdx;
        widgetAccent = prefs.getInt("widgetAccent", 1);
        for (int i = 0; i < ClockFaces.COUNT; i++) {
            faceAccent[i] = prefs.getInt("faceAccent" + i, ClockFaces.DEFAULT_ACCENT[i]);
        }
        nightAuto = prefs.getBoolean("nightAuto", true);
        showHelp = host.isActivity() && !prefs.getBoolean("helpSeen", false);

        // Night mode: map everything to its brightness, in red only.
        ColorMatrix cm = new ColorMatrix(new float[]{
                0.33f, 0.59f, 0.11f, 0, 0,
                0, 0, 0, 0, 0,
                0, 0, 0, 0, 0,
                0, 0, 0, 1, 0,
        });
        layerPaint.setColorFilter(new ColorMatrixColorFilter(cm));
        setHapticFeedbackEnabled(true);
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        Context ctx = getContext();
        IntentFilter f = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent sticky;
        if (Build.VERSION.SDK_INT >= 33) {
            sticky = ctx.registerReceiver(batteryReceiver, f, Context.RECEIVER_NOT_EXPORTED);
        } else {
            sticky = ctx.registerReceiver(batteryReceiver, f);
        }
        if (sticky != null) updateBattery(sticky);

        sensors = (SensorManager) ctx.getSystemService(Context.SENSOR_SERVICE);
        if (sensors != null) {
            Sensor light = sensors.getDefaultSensor(Sensor.TYPE_LIGHT);
            if (light != null) sensors.registerListener(this, light, SensorManager.SENSOR_DELAY_NORMAL);
        }
        photos.refresh();
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        attached = false;
        try {
            getContext().unregisterReceiver(batteryReceiver);
        } catch (Exception ignored) {
        }
        if (sensors != null) sensors.unregisterListener(this);
        removeCallbacks(tick);
        removeCallbacks(longPress);
        photos.release();
        super.onDetachedFromWindow();
    }

    /** Called by the Activity when it resumes or a permission result arrives. */
    public void onHostResume() {
        photos.refresh();
        alarmCheckedAt = 0;
        invalidate();
    }

    boolean canAskPermission() {
        return host.isActivity();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        photos.setScreenSize(w, h);
    }

    // ------------------------------------------------------------------ live data

    private void updateBattery(Intent i) {
        int level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        int status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        int plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
        batteryPct = level >= 0 && scale > 0 ? Math.round(level * 100f / scale) : -1;
        charging = plugged != 0;
        full = status == BatteryManager.BATTERY_STATUS_FULL;
        // Like iOS: stay on while charging.
        setKeepScreenOn(charging);
        chargeCheckedAt = 0;
        invalidate();
    }

    long chargeTimeRemaining() {
        long now = System.currentTimeMillis();
        if (now - chargeCheckedAt > 30000) {
            chargeCheckedAt = now;
            chargeRemaining = -1;
            try {
                BatteryManager bm = (BatteryManager) getContext().getSystemService(Context.BATTERY_SERVICE);
                if (bm != null) chargeRemaining = bm.computeChargeTimeRemaining();
            } catch (Exception ignored) {
            }
        }
        return chargeRemaining;
    }

    String nextAlarm() {
        long now = System.currentTimeMillis();
        if (now - alarmCheckedAt > 30000) {
            alarmCheckedAt = now;
            alarmText = null;
            try {
                AlarmManager am = (AlarmManager) getContext().getSystemService(Context.ALARM_SERVICE);
                AlarmManager.AlarmClockInfo info = am == null ? null : am.getNextAlarmClock();
                if (info != null) {
                    Calendar a = Calendar.getInstance();
                    a.setTimeInMillis(info.getTriggerTime());
                    Calendar today = Calendar.getInstance();
                    int dayDiff = (a.get(Calendar.YEAR) * 400 + a.get(Calendar.DAY_OF_YEAR))
                            - (today.get(Calendar.YEAR) * 400 + today.get(Calendar.DAY_OF_YEAR));
                    String t = Ui.time(a, h24) + (h24 ? "" : " " + Ui.amPm(a));
                    if (dayDiff == 1) t = "Tomorrow " + t;
                    else if (dayDiff > 1) t = new java.text.SimpleDateFormat("EEE",
                            java.util.Locale.getDefault()).format(a.getTime()) + " " + t;
                    alarmText = t;
                }
            } catch (Exception ignored) {
            }
        }
        return alarmText;
    }

    int widgetAccentColor() {
        return Ui.accent(widgetAccent);
    }

    @Override
    public void onSensorChanged(SensorEvent e) {
        lux = e.values[0];
        updateNight();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    private void updateNight() {
        boolean n = night;
        if (!nightAuto || lux < 0) n = false;
        else if (!night && lux < 3f) n = true;
        else if (night && lux > 12f) n = false;
        if (n != night) {
            night = n;
            host.setNight(n);
            invalidate();
        }
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void onDraw(Canvas c) {
        long now = System.currentTimeMillis();
        float dt = lastFrame == 0 ? 0.016f : Math.min(0.05f, (now - lastFrame) / 1000f);
        lastFrame = now;
        h24 = DateFormat.is24HourFormat(getContext());

        boolean animating = false;
        if (dragMode != DRAG_H) {
            pagePos = approach(pagePos, page, dt);
            animating |= pagePos != page;
        }
        for (int s = 0; s < 2; s++) {
            if (!(dragMode == DRAG_V && vTarget == s)) {
                stackPos[s] = approach(stackPos[s], stackIdx[s], dt);
                animating |= stackPos[s] != stackIdx[s];
            }
        }
        if (!(dragMode == DRAG_V && vTarget == VT_FACE)) {
            facePos = approach(facePos, faceIdx, dt);
            animating |= facePos != faceIdx;
        }

        int w = getWidth(), h = getHeight();
        Calendar cal = Calendar.getInstance();
        c.drawColor(0xFF000000);

        int save = night ? c.saveLayer(null, layerPaint) : -1;
        for (int p = 0; p < PAGES; p++) {
            float off = (p - pagePos) * w;
            if (Math.abs(off) >= w) continue;
            c.save();
            c.translate(off, 0);
            c.clipRect(0, 0, w, h);
            drawPage(c, p, w, h, cal, now);
            c.restore();
        }
        if (now < pageDotsUntil || dragMode == DRAG_H) drawPageDots(c, w, h);
        if (save != -1) c.restoreToCount(save);

        if (showCustomize) drawCustomize(c, w, h);
        if (showHelp) drawHelp(c, w, h);

        boolean photosVisible = Math.abs(pagePos - PAGE_PHOTOS) < 1f;
        boolean clockVisible = Math.abs(pagePos - PAGE_CLOCK) < 1f;
        boolean continuous = animating || dragMode != DRAG_NONE
                || (photosVisible && photos.animating())
                || (clockVisible && ClockFaces.continuous(Math.floorMod(faceIdx, ClockFaces.COUNT)))
                || now < pageDotsUntil || now < faceDotsUntil
                || now < stackDotsUntil[0] || now < stackDotsUntil[1];
        removeCallbacks(tick);
        if (continuous) {
            postInvalidateOnAnimation();
        } else if (attached) {
            postDelayed(tick, 1000 - (now % 1000) + 5);
        }
    }

    private static float approach(float cur, float target, float dt) {
        float diff = target - cur;
        if (Math.abs(diff) < 0.002f) return target;
        return cur + diff * Math.min(1f, dt * 13f);
    }

    private void burnInShift(Canvas c, Calendar cal) {
        int minute = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
        float s = 5 * d;
        c.translate((float) Math.sin(minute * 0.37) * s, (float) Math.cos(minute * 0.53) * s);
    }

    private void drawPage(Canvas c, int p, int w, int h, Calendar cal, long now) {
        switch (p) {
            case PAGE_WIDGETS:
                burnInShift(c, cal);
                layoutCards(w, h);
                for (int s = 0; s < 2; s++) drawStack(c, s, cal, now);
                break;
            case PAGE_PHOTOS:
                photos.draw(c, w, h, now, cal, h24);
                break;
            case PAGE_CLOCK:
                drawFaces(c, w, h, cal, now);
                break;
        }
    }

    private void layoutCards(int w, int h) {
        float m = 16 * d, gap = 14 * d;
        if (w >= h) {
            float cw = (w - 2 * m - gap) / 2f;
            cards[0].set(m, m, m + cw, h - m);
            cards[1].set(m + cw + gap, m, w - m, h - m);
        } else {
            float ch = (h - 2 * m - gap) / 2f;
            cards[0].set(m, m, w - m, m + ch);
            cards[1].set(m, m + ch + gap, w - m, h - m);
        }
    }

    private void drawStack(Canvas c, int s, Calendar cal, long now) {
        RectF r = cards[s];
        int[] stack = STACKS[s];
        float gap = 14 * d;
        float pitch = r.height() + gap;
        float pos = stackPos[s];
        int base = (int) Math.floor(pos);
        float frac = pos - base;
        float radius = 30 * d;
        c.save();
        c.clipRect(r);
        for (int k = 0; k <= 1; k++) {
            float dy = (k - frac) * pitch;
            if (Math.abs(dy) >= pitch) continue;
            int type = stack[Math.floorMod(base + k, stack.length)];
            c.save();
            c.translate(0, dy);
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Ui.CARD);
            c.drawRoundRect(r, radius, radius, paint);
            widgets.draw(c, type, r, cal, this);
            c.restore();
        }
        c.restore();
        if (now < stackDotsUntil[s] || (dragMode == DRAG_V && vTarget == s)) {
            drawVerticalDots(c, r.right - 10 * d, r.centerY(), stack.length,
                    Math.floorMod(Math.round(pos), stack.length));
        }
    }

    private void drawFaces(Canvas c, int w, int h, Calendar cal, long now) {
        int base = (int) Math.floor(facePos);
        float frac = facePos - base;
        for (int k = 0; k <= 1; k++) {
            float dy = (k - frac) * h;
            if (Math.abs(dy) >= h) continue;
            int face = Math.floorMod(base + k, ClockFaces.COUNT);
            c.save();
            c.translate(0, dy);
            c.clipRect(0, 0, w, h);
            if (face != ClockFaces.SOLAR) burnInShift(c, cal);
            faces.draw(c, face, w, h, cal, Ui.accent(faceAccent[face]), now, h24);
            c.restore();
        }
        if (now < faceDotsUntil || (dragMode == DRAG_V && vTarget == VT_FACE)) {
            drawVerticalDots(c, w - 12 * d, h / 2f, ClockFaces.COUNT,
                    Math.floorMod(Math.round(facePos), ClockFaces.COUNT));
            paint.setTypeface(Ui.MEDIUM);
            paint.setTextSize(13 * d);
            paint.setColor(0xCCFFFFFF);
            paint.setTextAlign(Paint.Align.RIGHT);
            Ui.textMid(c, paint, ClockFaces.NAMES[Math.floorMod(Math.round(facePos), ClockFaces.COUNT)],
                    w - 26 * d, h / 2f, tmp);
            paint.setTextAlign(Paint.Align.LEFT);
        }
    }

    private void drawVerticalDots(Canvas c, float x, float cy, int n, int active) {
        float sp = 11 * d;
        float y0 = cy - sp * (n - 1) / 2f;
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < n; i++) {
            paint.setColor(i == active ? 0xFFFFFFFF : 0x55FFFFFF);
            c.drawCircle(x, y0 + i * sp, 3 * d, paint);
        }
    }

    private void drawPageDots(Canvas c, int w, int h) {
        float sp = 14 * d;
        float y = h - 12 * d;
        float x0 = w / 2f - sp;
        int active = Math.round(pagePos);
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < PAGES; i++) {
            paint.setColor(i == active ? 0xFFFFFFFF : 0x55FFFFFF);
            c.drawCircle(x0 + i * sp, y, 3.5f * d, paint);
        }
    }

    private void pill(Canvas c, RectF r, String label, int bg, int fg) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(bg);
        c.drawRoundRect(r, r.height() / 2f, r.height() / 2f, paint);
        paint.setColor(fg);
        paint.setTypeface(Ui.MEDIUM);
        Ui.fitWidth(paint, label, r.width() - 20 * d, 15 * d);
        Ui.textCenter(c, paint, label, r.centerX(), r.centerY(), tmp);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawCustomize(Canvas c, int w, int h) {
        c.drawColor(0xB3000000);
        int curPage = Math.round(pagePos);
        boolean hasColors = curPage != PAGE_PHOTOS;
        float pw = Math.min(w - 32 * d, 620 * d);
        float ph = hasColors ? 190 * d : 130 * d;
        panel.set((w - pw) / 2f, h - ph - 20 * d, (w + pw) / 2f, h - 20 * d);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Ui.CARD_2);
        c.drawRoundRect(panel, 26 * d, 26 * d, paint);

        String title;
        int selected;
        if (curPage == PAGE_CLOCK) {
            int f = Math.floorMod(faceIdx, ClockFaces.COUNT);
            title = ClockFaces.NAMES[f] + " clock · colour";
            selected = Math.floorMod(faceAccent[f], Ui.ACCENTS.length);
        } else if (curPage == PAGE_WIDGETS) {
            title = "Widgets · colour";
            selected = Math.floorMod(widgetAccent, Ui.ACCENTS.length);
        } else {
            title = "Photos";
            selected = -1;
        }
        paint.setColor(0xFFFFFFFF);
        paint.setTypeface(Ui.BOLD);
        paint.setTextSize(17 * d);
        paint.setTextAlign(Paint.Align.LEFT);
        Ui.textMid(c, paint, title, panel.left + 22 * d, panel.top + 30 * d, tmp);

        float y = panel.top + 52 * d;
        if (hasColors) {
            int n = Ui.ACCENTS.length;
            float r = 15 * d;
            float avail = pw - 44 * d;
            float step = avail / n;
            float cy = y + 26 * d;
            for (int i = 0; i < n; i++) {
                float cx = panel.left + 22 * d + step * (i + 0.5f);
                paint.setColor(Ui.ACCENTS[i]);
                c.drawCircle(cx, cy, r, paint);
                if (i == selected) {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(2.5f * d);
                    paint.setColor(0xFFFFFFFF);
                    c.drawCircle(cx, cy, r + 5 * d, paint);
                    paint.setStyle(Paint.Style.FILL);
                }
                colorHits[i].set(cx - step / 2f, cy - r - 8 * d, cx + step / 2f, cy + r + 8 * d);
            }
            y = cy + r + 22 * d;
        } else {
            for (RectF hr : colorHits) hr.setEmpty();
        }

        float bh = 42 * d;
        float bgap = 10 * d;
        boolean auto = host.isActivity();
        int nb = auto ? 3 : 2;
        float bw = (pw - 44 * d - bgap * (nb - 1)) / nb;
        float bx = panel.left + 22 * d;
        btnNight.set(bx, y, bx + bw, y + bh);
        pill(c, btnNight, "Night mode: " + (nightAuto ? "Auto" : "Off"), 0xFF3A3A3C, 0xFFFFFFFF);
        bx += bw + bgap;
        if (auto) {
            btnAuto.set(bx, y, bx + bw, y + bh);
            pill(c, btnAuto, "Auto-start setup", 0xFF3A3A3C, 0xFFFFFFFF);
            bx += bw + bgap;
        } else {
            btnAuto.setEmpty();
        }
        btnDone.set(bx, y, bx + bw, y + bh);
        pill(c, btnDone, "Done", 0xFFFFFFFF, 0xFF000000);
    }

    private void drawHelp(Canvas c, int w, int h) {
        c.drawColor(0xF2000000);
        float pw = Math.min(w - 40 * d, 640 * d);
        float ph = Math.min(h - 30 * d, 340 * d);
        panel.set((w - pw) / 2f, (h - ph) / 2f, (w + pw) / 2f, (h + ph) / 2f);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Ui.CARD);
        c.drawRoundRect(panel, 28 * d, 28 * d, paint);

        float x = panel.left + 26 * d;
        float y = panel.top + 38 * d;
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setColor(0xFFFFFFFF);
        paint.setTypeface(Ui.BOLD);
        paint.setTextSize(22 * d);
        c.drawText("Welcome to StandBy", x, y, paint);

        String[] lines = {
                "↔  Swipe left / right:  Widgets · Photos · Clock",
                "↕  Swipe up / down:  change a widget, photo or clock style",
                "☝  Long-press:  colours and night mode",
                "🌙  Dark room:  everything turns dim red automatically",
                "⚡  Auto-start:  pick StandBy as your Screen saver so it",
                "     opens by itself whenever the phone is charging",
        };
        paint.setTypeface(Ui.REGULAR);
        float lh = Math.min(26 * d, (ph - 140 * d) / lines.length);
        paint.setTextSize(Math.min(15 * d, lh * 0.62f));
        y += 16 * d;
        for (String l : lines) {
            y += lh;
            paint.setColor(0xFFE5E5EA);
            c.drawText(l, x, y, paint);
        }

        float bh = 42 * d, gap = 10 * d;
        float bw = (pw - 52 * d - 2 * gap) / 3f;
        float by = panel.bottom - bh - 20 * d;
        helpAuto.set(x, by, x + bw, by + bh);
        helpPhotos.set(x + bw + gap, by, x + 2 * bw + gap, by + bh);
        helpOk.set(x + 2 * (bw + gap), by, x + 3 * bw + 2 * gap, by + bh);
        pill(c, helpAuto, "Set up auto-start", 0xFF0A84FF, 0xFFFFFFFF);
        pill(c, helpPhotos, PhotoSlideshow.hasPermission(getContext()) ? "Photos allowed ✓"
                : "Allow photos", 0xFF3A3A3C, 0xFFFFFFFF);
        pill(c, helpOk, "Got it", 0xFFFFFFFF, 0xFF000000);
    }

    // ------------------------------------------------------------------ touch

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        long t = e.getEventTime();
        int w = Math.max(1, getWidth()), h = Math.max(1, getHeight());
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                downX = lastX = x;
                downY = lastY = y;
                lastT = t;
                velX = velY = 0f;
                dragMode = DRAG_NONE;
                longPressFired = false;
                postDelayed(longPress, 550);
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (t > lastT) {
                    float ivx = (x - lastX) / (t - lastT);
                    float ivy = (y - lastY) / (t - lastT);
                    velX = velX * 0.5f + ivx * 0.5f;
                    velY = velY * 0.5f + ivy * 0.5f;
                }
                lastX = x;
                lastY = y;
                lastT = t;
                float dx = x - downX, dy = y - downY;
                if (dragMode == DRAG_NONE && Math.hypot(dx, dy) > touchSlop) {
                    removeCallbacks(longPress);
                    if (showCustomize || showHelp || longPressFired) {
                        dragMode = DRAG_IGNORE;
                    } else if (Math.abs(dx) > Math.abs(dy)) {
                        dragMode = DRAG_H;
                    } else {
                        dragMode = DRAG_V;
                        beginVertical(w, h);
                    }
                }
                if (dragMode == DRAG_H) {
                    float np = page - dx / w;
                    if (np < 0) np *= 0.3f;
                    if (np > PAGES - 1) np = (PAGES - 1) + (np - (PAGES - 1)) * 0.3f;
                    pagePos = np;
                    invalidate();
                } else if (dragMode == DRAG_V) {
                    moveVertical(dy, h);
                    invalidate();
                }
                return true;
            }
            case MotionEvent.ACTION_UP: {
                removeCallbacks(longPress);
                float dx = x - downX, dy = y - downY;
                long now = System.currentTimeMillis();
                if (longPressFired) {
                    // ignore
                } else if (dragMode == DRAG_H) {
                    int np = page;
                    if (dx < -w * 0.15f || velX < -0.6f) np = Math.min(PAGES - 1, page + 1);
                    else if (dx > w * 0.15f || velX > 0.6f) np = Math.max(0, page - 1);
                    setPage(np);
                } else if (dragMode == DRAG_V) {
                    endVertical(dy, h, now);
                } else if (dragMode == DRAG_NONE) {
                    onTap(x, y);
                }
                dragMode = DRAG_NONE;
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_CANCEL: {
                removeCallbacks(longPress);
                dragMode = DRAG_NONE;
                invalidate();
                return true;
            }
        }
        return super.onTouchEvent(e);
    }

    private void beginVertical(int w, int h) {
        // In the screen saver, a swipe up from the bottom edge closes StandBy.
        if (!host.isActivity() && downY > h - 60 * d) {
            vTarget = VT_EXIT;
            return;
        }
        if (page == PAGE_WIDGETS) {
            layoutCards(w, h);
            vTarget = cards[1].contains(downX, downY) ? VT_STACK1
                    : (w >= h ? (downX < w / 2f ? VT_STACK0 : VT_STACK1)
                    : (downY < h / 2f ? VT_STACK0 : VT_STACK1));
            vStartPos = stackIdx[vTarget];
        } else if (page == PAGE_CLOCK) {
            vTarget = VT_FACE;
            vStartPos = faceIdx;
        } else {
            vTarget = VT_PHOTO;
        }
    }

    private void moveVertical(float dy, int h) {
        if (vTarget == VT_STACK0 || vTarget == VT_STACK1) {
            float pitch = cards[vTarget].height() + 14 * d;
            stackPos[vTarget] = vStartPos - dy / Math.max(1f, pitch);
        } else if (vTarget == VT_FACE) {
            facePos = vStartPos - dy / h;
        }
    }

    private void endVertical(float dy, int h, long now) {
        int step = 0;
        float thr = h * 0.12f;
        if (dy < -thr || velY < -0.6f) step = 1;
        else if (dy > thr || velY > 0.6f) step = -1;

        if (vTarget == VT_EXIT) {
            if (dy < -h * 0.2f) host.exit();
            return;
        }
        if (vTarget == VT_STACK0 || vTarget == VT_STACK1) {
            int s = vTarget;
            stackIdx[s] += step;
            stackDotsUntil[s] = now + 1500;
            prefs.edit().putInt("stack" + s, Math.floorMod(stackIdx[s], STACKS[s].length)).apply();
        } else if (vTarget == VT_FACE) {
            faceIdx += step;
            faceDotsUntil = now + 1500;
            prefs.edit().putInt("face", Math.floorMod(faceIdx, ClockFaces.COUNT)).apply();
        } else if (vTarget == VT_PHOTO && step != 0) {
            photos.skip();
        }
    }

    private void setPage(int np) {
        page = clamp(np, 0, PAGES - 1);
        pageDotsUntil = System.currentTimeMillis() + 1500;
        prefs.edit().putInt("page", page).apply();
    }

    private void onTap(float x, float y) {
        if (showHelp) {
            if (helpAuto.contains(x, y)) {
                host.openDreamSettings();
            } else if (helpPhotos.contains(x, y)) {
                if (!PhotoSlideshow.hasPermission(getContext())) host.requestPhotoPermission();
            } else if (helpOk.contains(x, y)) {
                showHelp = false;
                prefs.edit().putBoolean("helpSeen", true).apply();
            }
            return;
        }
        if (showCustomize) {
            int curPage = Math.round(pagePos);
            for (int i = 0; i < colorHits.length; i++) {
                if (colorHits[i].contains(x, y)) {
                    if (curPage == PAGE_CLOCK) {
                        int f = Math.floorMod(faceIdx, ClockFaces.COUNT);
                        faceAccent[f] = i;
                        prefs.edit().putInt("faceAccent" + f, i).apply();
                    } else if (curPage == PAGE_WIDGETS) {
                        widgetAccent = i;
                        prefs.edit().putInt("widgetAccent", i).apply();
                    }
                    return;
                }
            }
            if (btnNight.contains(x, y)) {
                nightAuto = !nightAuto;
                prefs.edit().putBoolean("nightAuto", nightAuto).apply();
                updateNight();
            } else if (btnAuto.contains(x, y)) {
                showCustomize = false;
                showHelp = true;
            } else if (btnDone.contains(x, y) || !panel.contains(x, y)) {
                showCustomize = false;
            }
            return;
        }
        if (page == PAGE_PHOTOS && !PhotoSlideshow.hasPermission(getContext())
                && host.isActivity() && photos.permissionButton.contains(x, y)) {
            host.requestPhotoPermission();
            return;
        }
        pageDotsUntil = System.currentTimeMillis() + 2000;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
