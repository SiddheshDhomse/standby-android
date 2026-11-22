package com.siddhesh.standby;

import android.app.AlarmManager;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
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
 * Pages (swipe left/right, order set in Settings): Widgets · Photos · Clock.
 * Swipe up/down: change the widget in a stack, the photo, or the clock face.
 * Long-press: colours, night mode, settings. Double-tap: dim / undim.
 */
public class StandByView extends View implements SensorEventListener {

    public interface Host {
        /** 0..1, or -1 for the system brightness. */
        void setBrightness(float level);
        void openDreamSettings();
        void openSettings();
        void openTodo();
        void openUrl(String url);
        void requestPhotoPermission();
        void exit();
        boolean isActivity();
    }

    static final int PAGE_WIDGETS = 0, PAGE_PHOTOS = 1, PAGE_CLOCK = 2;

    private static final int DRAG_NONE = 0, DRAG_H = 1, DRAG_V = 2, DRAG_IGNORE = 3;
    private static final int VT_STACK0 = 0, VT_STACK1 = 1, VT_FACE = 2, VT_PHOTO = 3, VT_EXIT = 4;
    private static final long CHARGE_ANIM_MS = 3200;
    private static final long EXTRAS_VISIBLE_MS = 30_000;
    private static final long WAKE_FROM_NIGHT_MS = 60_000;

    private final Host host;
    final Settings settings;
    final Pomodoro pomodoro;
    final NowPlaying nowPlaying;
    final Weather weather;
    final Agenda agenda;
    final Todo todo;
    final LiquidGlass liquid;
    private final Wallpaper wallpaper;
    private final float d;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint layerPaint = new Paint();
    private final Rect tmp = new Rect();
    private final RectF rf = new RectF();
    private final Path path = new Path();
    private final RectF[] cards = {new RectF(), new RectF()};
    private final Widgets widgets;
    private final ClockFaces faces;
    private final PhotoSlideshow photos;
    private final int touchSlop;

    // navigation state (indices are unbounded ints; wrapped with floorMod when used)
    private int page;            // index into settings.pages
    private float pagePos, pageVel;
    private final int[] stackIdx = new int[2];
    private final float[] stackPos = new float[2];
    private final float[] stackVel = new float[2];
    private final long[] stackDotsUntil = new long[2];
    private int faceIdx;         // index into settings.faces
    private float facePos, faceVel;
    private long faceDotsUntil;
    private long pageDotsUntil;

    // live state, read by Widgets / faces
    boolean h24;
    int batteryPct = -1;
    boolean charging;
    boolean full;
    float driftX, driftY;
    private boolean batteryKnown;
    private boolean night;
    private float lux = -1f;
    private long chargeRemaining = -1;
    private long chargeCheckedAt;
    private String alarmText;
    private long alarmCheckedAt;
    private long chargeAnimStart;
    private long lockCheckedAt;
    private boolean locked;

    // burn-in / dimming
    private long lastTouchAt = System.currentTimeMillis();
    private boolean manualDim;
    private long lastEmptyTapAt;
    private float lastEmptyTapX, lastEmptyTapY;
    private long nightWakeUntil;
    private float lastBrightness = -2f;
    private boolean lastKeepOn;
    private boolean keepOnKnown;
    private long rotateSlot = -1;
    private boolean bedsideActive, sleepActive;

    // auto Now Playing
    private boolean lastPlaying;
    private boolean autoSwitched;
    private int prevPage, prevFace;
    private long stoppedAt;

    // overlays
    private boolean showCustomize;
    private boolean showHelp;
    private final RectF[] colorHits = new RectF[Ui.ACCENTS.length];
    private final RectF panel = new RectF();
    private final RectF btnNight = new RectF(), btnSettings = new RectF(), btnDone = new RectF();
    private final RectF helpAuto = new RectF(), helpSettings = new RectF(), helpOk = new RectF();
    private final RectF pillRect = new RectF();

    // touch
    private int dragMode = DRAG_NONE;
    private int vTarget;
    private float vStartPos;
    private float downX, downY, lastX, lastY, velX, velY;
    private long lastT;
    private boolean longPressFired;
    private boolean swallowTouch;
    private long lastFrame;

    private boolean attached;
    private SensorManager sensors;

    private final Runnable tick = this::invalidate;
    private final Runnable longPress = () -> {
        if (dragMode == DRAG_NONE && !showHelp && !showCustomize && !swallowTouch) {
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

    /** Live updates from Settings, the Quick Settings tile, the alarm receiver and the to-do editor. */
    private final SharedPreferences.OnSharedPreferenceChangeListener prefListener = this::onPrefChanged;

    private void onPrefChanged(SharedPreferences sp, String key) {
        if (key == null) return;
        if (key.startsWith("pomo_")) {
            pomodoro.reload();
        } else if (key.equals("todos")) {
            todo.load();
        } else if (key.equals("pageIndex") || key.equals("faceIndex")
                || (key.startsWith("stack") && !key.startsWith("stackItems"))
                || key.startsWith("wx_") || key.startsWith("wp_")) {
            return;
        } else {
            settings.load();
            clampIndices();
            if (key.equals("calendarIds") || key.equals("calTodayOnly")) agenda.invalidateCache();
            lastBrightness = -2f;
            keepOnKnown = false;
        }
        invalidate();
    }

    public StandByView(Context ctx, Host host) {
        super(ctx);
        this.host = host;
        this.d = ctx.getResources().getDisplayMetrics().density;
        this.touchSlop = ViewConfiguration.get(ctx).getScaledTouchSlop();
        settings = new Settings(ctx);
        pomodoro = new Pomodoro(ctx, d);
        nowPlaying = new NowPlaying(ctx, this::postInvalidate);
        weather = new Weather(ctx, this::postInvalidate, d);
        weather.setSettings(settings);
        agenda = new Agenda(ctx, this::postInvalidate, d);
        todo = new Todo(ctx, d);
        liquid = new LiquidGlass(d);
        wallpaper = new Wallpaper(ctx, this::postInvalidate);
        widgets = new Widgets(d);
        faces = new ClockFaces(d);
        photos = new PhotoSlideshow(ctx, this, d);
        for (int i = 0; i < colorHits.length; i++) colorHits[i] = new RectF();

        SharedPreferences sp = settings.sp;
        page = sp.getInt("pageIndex", settings.pages.length - 1);
        stackIdx[0] = sp.getInt("stack0", 0);
        stackIdx[1] = sp.getInt("stack1", 0);
        faceIdx = sp.getInt("faceIndex", 0);
        clampIndices();
        pagePos = page;
        stackPos[0] = stackIdx[0];
        stackPos[1] = stackIdx[1];
        facePos = faceIdx;
        showHelp = host.isActivity() && !sp.getBoolean("helpSeen", false);
        setHapticFeedbackEnabled(true);
    }

    private void clampIndices() {
        int p = Math.floorMod(page, settings.pages.length);
        if (p != page) { page = p; pagePos = p; pageVel = 0; }
        for (int s = 0; s < 2; s++) {
            int n = settings.stacks[s].length;
            int si = Math.floorMod(stackIdx[s], n);
            if (si != stackIdx[s]) { stackIdx[s] = si; stackPos[s] = si; stackVel[s] = 0; }
        }
        int f = Math.floorMod(faceIdx, settings.faces.length);
        if (f != faceIdx) { faceIdx = f; facePos = f; faceVel = 0; }
        if (pagePos > settings.pages.length - 1) pagePos = settings.pages.length - 1;
    }

    int pageType(int idx) {
        int[] pg = settings.pages;
        return pg[Math.max(0, Math.min(pg.length - 1, idx))];
    }

    int currentFace() {
        return settings.faces[Math.floorMod(faceIdx, settings.faces.length)];
    }

    private int pageIndexOf(int type) {
        for (int i = 0; i < settings.pages.length; i++) if (settings.pages[i] == type) return i;
        return -1;
    }

    private int faceIndexOf(int face) {
        for (int i = 0; i < settings.faces.length; i++) if (settings.faces[i] == face) return i;
        return -1;
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
        settings.sp.registerOnSharedPreferenceChangeListener(prefListener);
        photos.refresh();
        nowPlaying.start();
        lastTouchAt = System.currentTimeMillis();
        applyDisplay(System.currentTimeMillis(), Calendar.getInstance());
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
        settings.sp.unregisterOnSharedPreferenceChangeListener(prefListener);
        nowPlaying.stop();
        removeCallbacks(tick);
        removeCallbacks(longPress);
        photos.release();
        super.onDetachedFromWindow();
    }

    /** Called by the Activity when it resumes (settings may have changed) or a permission result arrives. */
    public void onHostResume() {
        settings.load();
        clampIndices();
        todo.load();
        pomodoro.reload();
        agenda.invalidateCache();
        photos.refresh();
        nowPlaying.stop();
        nowPlaying.start();
        alarmCheckedAt = 0;
        lockCheckedAt = 0;
        lastBrightness = -2f;
        keepOnKnown = false;
        lastTouchAt = System.currentTimeMillis();
        invalidate();
    }

    boolean canAskPermission() {
        return host.isActivity();
    }

    boolean hideDetails() {
        return settings.hideDetailsLocked && isLocked();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        photos.setScreenSize(w, h);
        wallpaper.setScreenSize(w, h);
    }

    /** Keep-screen-on and brightness, recomputed every frame but applied only on change. */
    private void applyDisplay(long now, Calendar cal) {
        boolean keep;
        if (!host.isActivity()) keep = true;
        else if (sleepActive) keep = false; // let the screen turn off in the sleep window
        else if (settings.keepOn == Settings.KEEP_ALWAYS) keep = true;
        else if (settings.keepOn == Settings.KEEP_CHARGING) keep = charging;
        else keep = false;
        if (!keepOnKnown || keep != lastKeepOn) {
            setKeepScreenOn(keep);
            lastKeepOn = keep;
            keepOnKnown = true;
        }

        float b;
        if (sleepActive || night) {
            b = 0.01f;
        } else {
            float base = settings.brightness < 0 ? -1f : settings.brightness / 100f;
            float factor = 1f;
            if (bedsideActive) factor = Math.min(factor, 0.15f);
            if (manualDim) factor = Math.min(factor, 0.3f);
            if (settings.autoDim && now - lastTouchAt > settings.autoDimMinutes * 60_000L) {
                factor = Math.min(factor, 0.35f);
            }
            if (factor < 1f) {
                if (base < 0) base = systemBrightness();
                b = Math.max(0.01f, base * factor);
            } else {
                b = base;
            }
        }
        if (Math.abs(b - lastBrightness) > 0.001f) {
            lastBrightness = b;
            host.setBrightness(b);
        }
    }

    private float systemBrightness() {
        try {
            int v = android.provider.Settings.System.getInt(getContext().getContentResolver(),
                    android.provider.Settings.System.SCREEN_BRIGHTNESS);
            return Math.max(0.05f, Math.min(1f, v / 255f));
        } catch (Exception e) {
            return 0.5f;
        }
    }

    // ------------------------------------------------------------------ live data

    private void updateBattery(Intent i) {
        int level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        int status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        int plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
        boolean wasCharging = charging;
        batteryPct = level >= 0 && scale > 0 ? Math.round(level * 100f / scale) : -1;
        charging = plugged != 0;
        full = status == BatteryManager.BATTERY_STATUS_FULL;
        if (batteryKnown && charging && !wasCharging && settings.chargeAnim) {
            chargeAnimStart = System.currentTimeMillis();
            performHapticFeedback(HapticFeedbackConstants.CONFIRM);
        }
        batteryKnown = true;
        keepOnKnown = false;
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
                // our own Pomodoro alarm is not an "alarm clock", so it never shows here
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

    private boolean isLocked() {
        long now = System.currentTimeMillis();
        if (now - lockCheckedAt > 1000) {
            lockCheckedAt = now;
            KeyguardManager km = (KeyguardManager) getContext().getSystemService(Context.KEYGUARD_SERVICE);
            locked = km != null && km.isKeyguardLocked();
        }
        return locked;
    }

    int widgetAccentColor() {
        return Ui.accent(settings.widgetAccent);
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
        if (!settings.nightAuto || lux < 0) n = false;
        else if (!night && lux < 3f) n = true;
        else if (night && lux > 12f) n = false;
        if (n != night) {
            night = n;
            invalidate();
        }
    }

    // ------------------------------------------------------------------ animation

    /** Critically-damped-ish spring with a little bounce. Returns {pos, vel}. */
    private static float[] spring(float pos, float vel, float target, float dt) {
        final float k = 210f, c = 2f * (float) Math.sqrt(k) * 0.78f;
        int steps = 4;
        float h = dt / steps;
        for (int i = 0; i < steps; i++) {
            float a = k * (target - pos) - c * vel;
            vel += a * h;
            pos += vel * h;
        }
        if (Math.abs(target - pos) < 0.0015f && Math.abs(vel) < 0.01f) {
            pos = target;
            vel = 0f;
        }
        return new float[]{pos, vel};
    }

    private void updateModes(long now, Calendar cal) {
        float hour = cal.get(Calendar.HOUR_OF_DAY) + cal.get(Calendar.MINUTE) / 60f;
        boolean awake = now < nightWakeUntil;
        sleepActive = !awake && settings.sleepWindow && Settings.inWindow(hour, settings.sleepStart, settings.sleepEnd);
        bedsideActive = !awake && !sleepActive && settings.bedside
                && Settings.inWindow(hour, settings.bedStart, settings.bedEnd);

        // burn-in drift: up to ~24 px (9 dp), a fresh path every hour
        if (settings.burnIn) {
            int minute = cal.get(Calendar.MINUTE);
            int hourSeed = cal.get(Calendar.HOUR_OF_DAY) + cal.get(Calendar.DAY_OF_YEAR) * 24;
            double phase = minute / 60.0 * Math.PI * 2;
            double a = hourSeed * 2.399963; // golden angle: each hour starts somewhere new
            float r = 9 * d;
            driftX = (float) (Math.sin(phase + a) * r);
            driftY = (float) (Math.sin(phase * 2 + a * 1.7) * r * 0.6);
        } else {
            driftX = driftY = 0;
        }

        // face rotation
        int hrs = Settings.ROTATE_HOURS[settings.faceRotate];
        if (hrs > 0) {
            long slot = now / (hrs * 3_600_000L);
            if (rotateSlot >= 0 && slot != rotateSlot && dragMode == DRAG_NONE && !autoSwitched) {
                faceIdx++;
                settings.sp.edit().putInt("faceIndex", Math.floorMod(faceIdx, settings.faces.length)).apply();
            }
            rotateSlot = slot;
        } else {
            rotateSlot = -1;
        }

        // Now Playing takes over while music plays, then hands back
        boolean playing = nowPlaying.active() && nowPlaying.playing;
        if (settings.autoNowPlaying && playing && !lastPlaying && !autoSwitched && dragMode == DRAG_NONE) {
            int pi = pageIndexOf(PAGE_CLOCK), fi = faceIndexOf(ClockFaces.NOW_PLAYING);
            if (pi >= 0 && fi >= 0 && !(page == pi && Math.floorMod(faceIdx, settings.faces.length) == fi)) {
                prevPage = page;
                prevFace = faceIdx;
                page = pi;
                faceIdx = fi;
                autoSwitched = true;
            }
        }
        if (autoSwitched) {
            if (playing) {
                stoppedAt = 0;
            } else {
                if (stoppedAt == 0) stoppedAt = now;
                if (now - stoppedAt > 30_000) {
                    page = Math.min(prevPage, settings.pages.length - 1);
                    faceIdx = prevFace;
                    autoSwitched = false;
                    stoppedAt = 0;
                }
            }
        }
        lastPlaying = playing;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void onDraw(Canvas c) {
        long now = System.currentTimeMillis();
        float dt = lastFrame == 0 ? 0.016f : Math.min(0.05f, (now - lastFrame) / 1000f);
        lastFrame = now;
        h24 = DateFormat.is24HourFormat(getContext());
        Calendar cal = Calendar.getInstance();
        pomodoro.tick(now, settings);
        wallpaper.ensure(settings);
        weather.ensure(settings);
        agenda.ensure(settings);
        updateModes(now, cal);
        applyDisplay(now, cal);
        Ui.transparentFaces = settings.glass;

        boolean animating = false;
        if (dragMode != DRAG_H) {
            float[] r = spring(pagePos, pageVel, page, dt);
            pagePos = r[0];
            pageVel = r[1];
            animating |= pagePos != page;
        }
        for (int s = 0; s < 2; s++) {
            if (!(dragMode == DRAG_V && vTarget == s)) {
                float[] r = spring(stackPos[s], stackVel[s], stackIdx[s], dt);
                stackPos[s] = r[0];
                stackVel[s] = r[1];
                animating |= stackPos[s] != stackIdx[s];
            }
        }
        if (!(dragMode == DRAG_V && vTarget == VT_FACE)) {
            float[] r = spring(facePos, faceVel, faceIdx, dt);
            facePos = r[0];
            faceVel = r[1];
            animating |= facePos != faceIdx;
        }

        int w = getWidth(), h = getHeight();
        c.drawColor(0xFF000000);

        if (sleepActive) {
            // nothing lit at all; the screen turns off on its own (app) or stays black (screen saver)
            scheduleNext(false, false, now);
            return;
        }

        boolean useLayer = night || settings.softWhites;
        if (useLayer) {
            ColorMatrix cm;
            if (night) {
                cm = new ColorMatrix(new float[]{
                        0.33f, 0.59f, 0.11f, 0, 0,
                        0, 0, 0, 0, 0,
                        0, 0, 0, 0, 0,
                        0, 0, 0, 1, 0});
            } else {
                float k = 0.86f; // softer whites: slower OLED ageing, hardly visible
                cm = new ColorMatrix(new float[]{
                        k, 0, 0, 0, 0,
                        0, k, 0, 0, 0,
                        0, 0, k, 0, 0,
                        0, 0, 0, 1, 0});
            }
            layerPaint.setColorFilter(new ColorMatrixColorFilter(cm));
        }
        int save = useLayer ? c.saveLayer(null, layerPaint) : -1;

        boolean faceAnim = false;
        boolean glassVisible = false;
        if (bedsideActive) {
            faces.bedside(c, w, h, cal, this);
        } else {
            int n = settings.pages.length;
            for (int p = 0; p < n; p++) {
                float off = (p - pagePos) * w;
                if (Math.abs(off) >= w) continue;
                c.save();
                c.translate(off, 0);
                c.clipRect(0, 0, w, h);
                int type = pageType(p);
                faceAnim |= drawPage(c, type, w, h, cal, now);
                if (type != PAGE_PHOTOS && (settings.glass
                        || (type == PAGE_CLOCK && currentFace() == ClockFaces.GLASS))) glassVisible = true;
                c.restore();
            }
            boolean extras = !settings.fadeExtras || now - lastTouchAt < EXTRAS_VISIBLE_MS;
            int curType = pageType(Math.round(Math.max(0, Math.min(n - 1, pagePos))));
            if (extras && settings.showBattery && curType != PAGE_WIDGETS) drawBatteryBadge(c, w);
            if (extras && curType == PAGE_CLOCK) drawNowPlayingPill(c, w, h);
            else pillRect.setEmpty();
            if (n > 1 && (now < pageDotsUntil || dragMode == DRAG_H)) drawPageDots(c, w, h);
        }
        boolean chargeAnim = now - chargeAnimStart < CHARGE_ANIM_MS;
        if (chargeAnim) drawChargeAnim(c, w, h, now);
        if (save != -1) c.restoreToCount(save);

        if (showCustomize) drawCustomize(c, w, h);
        if (showHelp) drawHelp(c, w, h);

        boolean photosVisible = !bedsideActive && visible(PAGE_PHOTOS);
        boolean fast = animating || dragMode != DRAG_NONE || chargeAnim || faceAnim
                || (photosVisible && photos.animating())
                || now < pageDotsUntil || now < faceDotsUntil
                || now < stackDotsUntil[0] || now < stackDotsUntil[1]
                || now < pomodoro.flashUntil;
        scheduleNext(fast, glassVisible && !bedsideActive, now);
    }

    private void scheduleNext(boolean fast, boolean glass, long now) {
        removeCallbacks(tick);
        if (fast) {
            postInvalidateOnAnimation();
        } else if (glass) {
            postDelayed(tick, 40); // ~25 fps is plenty for the slow liquid background
        } else if (attached) {
            postDelayed(tick, 1000 - (now % 1000) + 5);
        }
    }

    private boolean visible(int type) {
        for (int p = 0; p < settings.pages.length; p++) {
            if (pageType(p) == type && Math.abs(pagePos - p) < 1f) return true;
        }
        return false;
    }

    private void burnInShift(Canvas c) {
        c.translate(driftX, driftY);
    }

    /** Returns true if the page needs continuous animation. */
    private boolean drawPage(Canvas c, int type, int w, int h, Calendar cal, long now) {
        switch (type) {
            case PAGE_WIDGETS: {
                boolean glass = settings.glass;
                boolean wp = !glass && settings.wallpaperOnWidgets && wallpaper.bitmap() != null;
                if (glass) liquid.drawBackground(c, w, h, now, settings.glassPalette);
                else if (wp) wallpaper.draw(c, w, h, settings.wallpaperDim);
                burnInShift(c);
                layoutCards(w, h);
                for (int s = 0; s < 2; s++) drawStack(c, s, cal, now, wp, glass);
                return false;
            }
            case PAGE_PHOTOS: {
                boolean hidden = settings.hidePhotosLocked && isLocked();
                photos.draw(c, w, h, now, cal, h24, hidden, settings.photoCorner);
                return false;
            }
            case PAGE_CLOCK:
                return drawFaces(c, w, h, cal, now);
        }
        return false;
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

    private void drawStack(Canvas c, int s, Calendar cal, long now, boolean overWallpaper, boolean glass) {
        RectF r = cards[s];
        int[] stack = settings.stacks[s];
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
            if (glass) {
                // the card is drawn in translated space; undo the scroll so the blur lines up
                c.save();
                c.translate(0, 0);
                liquid.card(c, new RectF(r), radius, now);
                c.restore();
            } else {
                paint.setShader(null);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(overWallpaper ? 0xB31C1C1E : Ui.CARD);
                c.drawRoundRect(r, radius, radius, paint);
            }
            widgets.draw(c, type, r, cal, this, s, now);
            c.restore();
        }
        c.restore();
        if (stack.length > 1 && (now < stackDotsUntil[s] || (dragMode == DRAG_V && vTarget == s))) {
            drawVerticalDots(c, r.right - 10 * d, r.centerY(), stack.length,
                    Math.floorMod(Math.round(pos), stack.length));
        }
    }

    private boolean drawFaces(Canvas c, int w, int h, Calendar cal, long now) {
        int[] list = settings.faces;
        int base = (int) Math.floor(facePos);
        float frac = facePos - base;
        if (settings.glass) {
            liquid.drawBackground(c, w, h, now, settings.glassPalette);
        } else if (settings.wallpaperOnFaces && wallpaper.bitmap() != null) {
            wallpaper.draw(c, w, h, settings.wallpaperDim);
        }
        boolean anim = false;
        for (int k = 0; k <= 1; k++) {
            float dy = (k - frac) * h;
            if (Math.abs(dy) >= h) continue;
            int face = list[Math.floorMod(base + k, list.length)];
            c.save();
            c.translate(0, dy);
            c.clipRect(0, 0, w, h);
            if (!ClockFaces.ownBackground(face)) burnInShift(c);
            faces.draw(c, face, w, h, cal, Ui.accent(settings.faceAccent[face]), now, this);
            anim |= faces.continuous(face, now);
            c.restore();
        }
        if (list.length > 1 && (now < faceDotsUntil || (dragMode == DRAG_V && vTarget == VT_FACE))) {
            drawVerticalDots(c, w - 12 * d, h / 2f, list.length,
                    Math.floorMod(Math.round(facePos), list.length));
            paint.setTypeface(Ui.MEDIUM);
            paint.setTextSize(13 * d);
            paint.setColor(0xCCFFFFFF);
            paint.setTextAlign(Paint.Align.RIGHT);
            Ui.textMid(c, paint, ClockFaces.NAMES[list[Math.floorMod(Math.round(facePos), list.length)]],
                    w - 26 * d, h / 2f, tmp);
            paint.setTextAlign(Paint.Align.LEFT);
        }
        return anim;
    }

    private void drawVerticalDots(Canvas c, float x, float cy, int n, int active) {
        float sp = Math.min(11 * d, (getHeight() * 0.8f) / Math.max(1, n));
        float y0 = cy - sp * (n - 1) / 2f;
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < n; i++) {
            paint.setColor(i == active ? 0xFFFFFFFF : 0x55FFFFFF);
            c.drawCircle(x, y0 + i * sp, 3 * d, paint);
        }
    }

    private void drawPageDots(Canvas c, int w, int h) {
        int n = settings.pages.length;
        float sp = 14 * d;
        float y = h - 12 * d;
        float x0 = w / 2f - sp * (n - 1) / 2f;
        int active = Math.round(pagePos);
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < n; i++) {
            paint.setColor(i == active ? 0xFFFFFFFF : 0x55FFFFFF);
            c.drawCircle(x0 + i * sp, y, 3.5f * d, paint);
        }
    }

    private void drawBatteryBadge(Canvas c, int w) {
        if (batteryPct < 0) return;
        float bh = 12 * d, bw = 24 * d;
        float right = w - 18 * d, top = 14 * d;
        rf.set(right - bw, top, right, top + bh);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.4f * d);
        paint.setColor(0xB3FFFFFF);
        c.drawRoundRect(rf, 3 * d, 3 * d, paint);
        paint.setStyle(Paint.Style.FILL);
        c.drawRoundRect(new RectF(right + 1.5f * d, top + bh * 0.3f, right + 3.2f * d, top + bh * 0.7f), d, d, paint);
        int col = charging ? Ui.GREEN : (batteryPct <= 20 ? Ui.RED : 0xFFFFFFFF);
        paint.setColor(col);
        float inset = 2.2f * d;
        float fw = (bw - 2 * inset) * Math.max(0.04f, batteryPct / 100f);
        c.drawRoundRect(new RectF(rf.left + inset, rf.top + inset, rf.left + inset + fw, rf.bottom - inset),
                1.5f * d, 1.5f * d, paint);
        paint.setTypeface(Ui.MEDIUM);
        paint.setTextSize(13 * d);
        paint.setColor(0xCCFFFFFF);
        paint.setTextAlign(Paint.Align.RIGHT);
        float tx = rf.left - 6 * d;
        Ui.textMid(c, paint, batteryPct + "%", tx, rf.centerY(), tmp);
        if (charging) {
            float tw = paint.measureText(batteryPct + "%");
            Icons.bolt(c, paint, path, tx - tw - 14 * d, rf.centerY() - 6 * d, 12 * d, Ui.GREEN);
        }
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawNowPlayingPill(Canvas c, int w, int h) {
        pillRect.setEmpty();
        if (!settings.nowPlayingPill || !nowPlaying.active() || !nowPlaying.playing) return;
        if (currentFace() == ClockFaces.NOW_PLAYING) return;
        String text = (nowPlaying.title == null ? "" : nowPlaying.title)
                + (nowPlaying.artist != null ? "  ·  " + nowPlaying.artist : "");
        float ph = 34 * d;
        paint.setTypeface(Ui.MEDIUM);
        paint.setTextSize(13 * d);
        float maxText = Math.min(w * 0.5f, 360 * d);
        float tw = Math.min(paint.measureText(text), maxText);
        float pw = tw + ph + 30 * d;
        float top = h - ph - 22 * d;
        pillRect.set((w - pw) / 2f, top, (w + pw) / 2f, top + ph);
        if (settings.glass) {
            liquid.card(c, new RectF(pillRect), ph / 2f, System.currentTimeMillis());
        } else {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xD91C1C1E);
            c.drawRoundRect(pillRect, ph / 2f, ph / 2f, paint);
        }
        float art = ph - 8 * d;
        rf.set(pillRect.left + 4 * d, pillRect.top + 4 * d, pillRect.left + 4 * d + art, pillRect.top + 4 * d + art);
        if (nowPlaying.art != null) {
            c.save();
            path.reset();
            path.addRoundRect(rf, art / 2f, art / 2f, Path.Direction.CW);
            c.clipPath(path);
            Matrix m = new Matrix();
            float s = Math.max(art / nowPlaying.art.getWidth(), art / nowPlaying.art.getHeight());
            m.setScale(s, s);
            m.postTranslate(rf.left, rf.top);
            c.drawBitmap(nowPlaying.art, m, paint);
            c.restore();
        } else {
            paint.setColor(0xFF1DB954);
            c.drawCircle(rf.centerX(), rf.centerY(), art / 2f, paint);
        }
        paint.setColor(0xFFFFFFFF);
        paint.setTextAlign(Paint.Align.LEFT);
        String shown = text;
        while (shown.length() > 1 && paint.measureText(shown + "…") > maxText) {
            shown = shown.substring(0, shown.length() - 1);
        }
        if (!shown.equals(text)) shown = shown + "…";
        Ui.textMid(c, paint, shown, rf.right + 10 * d, pillRect.centerY(), tmp);
    }

    private void drawChargeAnim(Canvas c, int w, int h, long now) {
        float t = (now - chargeAnimStart) / (float) CHARGE_ANIM_MS;
        float fadeIn = Math.min(1f, t / 0.12f);
        float fadeOut = t > 0.82f ? Math.max(0f, 1f - (t - 0.82f) / 0.18f) : 1f;
        float a = fadeIn * fadeOut;
        c.drawColor(Ui.alpha(0xFF000000, (int) (215 * a)));
        float cx = w / 2f, cy = h / 2f;
        float r = Math.min(w, h) * 0.26f;
        float grow = Math.min(1f, t / 0.55f);
        float ease = 1f - (1f - grow) * (1f - grow) * (1f - grow);
        float pct = Math.max(1, batteryPct) / 100f;
        paint.setStyle(Paint.Style.STROKE);
        for (int i = 0; i < 2; i++) {
            float pt = ((t * 1.6f) + i * 0.5f) % 1f;
            paint.setStrokeWidth(3 * d);
            paint.setColor(Ui.alpha(Ui.GREEN, (int) (120 * (1f - pt) * a)));
            c.drawCircle(cx, cy, r * (1.05f + pt * 0.6f), paint);
        }
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(r * 0.14f);
        paint.setColor(Ui.alpha(Ui.GREEN, (int) (60 * a)));
        c.drawCircle(cx, cy, r, paint);
        paint.setColor(Ui.alpha(Ui.GREEN, (int) (255 * a)));
        rf.set(cx - r, cy - r, cx + r, cy + r);
        c.drawArc(rf, -90f, 360f * pct * ease, false, paint);
        paint.setStyle(Paint.Style.FILL);
        float bs = r * 0.42f;
        Icons.bolt(c, paint, path, cx - bs / 2f, cy - r * 0.55f, bs, Ui.alpha(Ui.GREEN, (int) (255 * a)));
        paint.setTypeface(Ui.BOLD);
        paint.setColor(Ui.alpha(0xFFFFFFFF, (int) (255 * a)));
        paint.setTextSize(r * 0.42f);
        Ui.textCenter(c, paint, Math.round(pct * 100 * ease) + "%", cx, cy + r * 0.18f, tmp);
        paint.setTypeface(Ui.MEDIUM);
        paint.setTextSize(r * 0.13f);
        paint.setColor(Ui.alpha(0xFFE5E5EA, (int) (255 * a)));
        Ui.textCenter(c, paint, "Charging", cx, cy + r * 0.55f, tmp);
        paint.setTextAlign(Paint.Align.LEFT);
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
        int curType = pageType(Math.round(pagePos));
        boolean hasColors = curType != PAGE_PHOTOS;
        float pw = Math.min(w - 32 * d, 620 * d);
        float ph = hasColors ? 190 * d : 130 * d;
        panel.set((w - pw) / 2f, h - ph - 20 * d, (w + pw) / 2f, h - 20 * d);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Ui.CARD_2);
        c.drawRoundRect(panel, 26 * d, 26 * d, paint);

        String title;
        int selected;
        if (curType == PAGE_CLOCK) {
            int f = currentFace();
            title = ClockFaces.NAMES[f] + " · colour";
            selected = Math.floorMod(settings.faceAccent[f], Ui.ACCENTS.length);
        } else if (curType == PAGE_WIDGETS) {
            title = "Widgets · colour";
            selected = Math.floorMod(settings.widgetAccent, Ui.ACCENTS.length);
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
            float step = (pw - 44 * d) / n;
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
        float bw = (pw - 44 * d - bgap * 2) / 3f;
        float bx = panel.left + 22 * d;
        btnNight.set(bx, y, bx + bw, y + bh);
        pill(c, btnNight, "Liquid Glass: " + (settings.glass ? "On" : "Off"), 0xFF3A3A3C, 0xFFFFFFFF);
        bx += bw + bgap;
        btnSettings.set(bx, y, bx + bw, y + bh);
        pill(c, btnSettings, "All settings", 0xFF3A3A3C, 0xFFFFFFFF);
        bx += bw + bgap;
        btnDone.set(bx, y, bx + bw, y + bh);
        pill(c, btnDone, "Done", 0xFFFFFFFF, 0xFF000000);
    }

    private void drawHelp(Canvas c, int w, int h) {
        c.drawColor(0xF2000000);
        float pw = Math.min(w - 40 * d, 660 * d);
        float ph = Math.min(h - 30 * d, 350 * d);
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
                "↔  Swipe left / right:  Widgets · Clock",
                "↕  Swipe up / down:  change a widget or clock face",
                "☝  Long-press:  colours, Liquid Glass and All settings",
                "✦  Double-tap:  dim or brighten",
                "⚙  Quick Settings tiles:  “Liquid Glass” and “StandBy”",
                "⚡  Auto-start:  pick StandBy as your Screen saver (while charging)",
        };
        paint.setTypeface(Ui.REGULAR);
        float lh = Math.min(26 * d, (ph - 140 * d) / lines.length);
        y += 16 * d;
        for (String l : lines) {
            y += lh;
            paint.setColor(0xFFE5E5EA);
            Ui.fitWidth(paint, l, pw - 52 * d, Math.min(15 * d, lh * 0.62f));
            c.drawText(l, x, y, paint);
        }

        float bh = 42 * d, gap = 10 * d;
        float bw = (pw - 52 * d - 2 * gap) / 3f;
        float by = panel.bottom - bh - 20 * d;
        helpAuto.set(x, by, x + bw, by + bh);
        helpSettings.set(x + bw + gap, by, x + 2 * bw + gap, by + bh);
        helpOk.set(x + 2 * (bw + gap), by, x + 3 * bw + 2 * gap, by + bh);
        pill(c, helpAuto, "Set up auto-start", 0xFF0A84FF, 0xFFFFFFFF);
        pill(c, helpSettings, "Open settings", 0xFF3A3A3C, 0xFFFFFFFF);
        pill(c, helpOk, "Got it", 0xFFFFFFFF, 0xFF000000);
    }

    // ------------------------------------------------------------------ touch

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        long t = e.getEventTime();
        long now = System.currentTimeMillis();
        int w = Math.max(1, getWidth()), h = Math.max(1, getHeight());
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                lastTouchAt = now;
                // Bedside / sleep: the first touch just wakes the normal view for a minute.
                swallowTouch = bedsideActive || sleepActive;
                if (swallowTouch) {
                    nightWakeUntil = now + WAKE_FROM_NIGHT_MS;
                    invalidate();
                    return true;
                }
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
                if (swallowTouch) return true;
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
                        autoSwitched = false;
                    } else {
                        dragMode = DRAG_V;
                        autoSwitched = false;
                        beginVertical(w, h);
                    }
                }
                int last = settings.pages.length - 1;
                if (dragMode == DRAG_H) {
                    float np = page - dx / w;
                    if (np < 0) np *= 0.3f;
                    if (np > last) np = last + (np - last) * 0.3f;
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
                if (swallowTouch) {
                    swallowTouch = false;
                    return true;
                }
                float dx = x - downX, dy = y - downY;
                if (longPressFired) {
                    // ignore the release of the long-press
                } else if (dragMode == DRAG_H) {
                    int np = page;
                    int last = settings.pages.length - 1;
                    if (dx < -w * 0.15f || velX < -0.6f) np = Math.min(last, page + 1);
                    else if (dx > w * 0.15f || velX > 0.6f) np = Math.max(0, page - 1);
                    pageVel = -velX * 1000f / w; // carry the fling into the spring
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
                swallowTouch = false;
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
        int type = pageType(page);
        if (type == PAGE_WIDGETS) {
            layoutCards(w, h);
            vTarget = cards[1].contains(downX, downY) ? VT_STACK1
                    : (w >= h ? (downX < w / 2f ? VT_STACK0 : VT_STACK1)
                    : (downY < h / 2f ? VT_STACK0 : VT_STACK1));
            vStartPos = stackIdx[vTarget];
        } else if (type == PAGE_CLOCK) {
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
            float pitch = cards[s].height() + 14 * d;
            stackVel[s] = -velY * 1000f / Math.max(1f, pitch);
            stackDotsUntil[s] = now + 1500;
            settings.sp.edit().putInt("stack" + s, Math.floorMod(stackIdx[s], settings.stacks[s].length)).apply();
        } else if (vTarget == VT_FACE) {
            faceIdx += step;
            faceVel = -velY * 1000f / h;
            faceDotsUntil = now + 1500;
            settings.sp.edit().putInt("faceIndex", Math.floorMod(faceIdx, settings.faces.length)).apply();
        } else if (vTarget == VT_PHOTO && step != 0) {
            photos.skip();
        }
    }

    private void setPage(int np) {
        page = Math.max(0, Math.min(settings.pages.length - 1, np));
        pageDotsUntil = System.currentTimeMillis() + 1500;
        settings.sp.edit().putInt("pageIndex", page).apply();
    }

    private void onTap(float x, float y) {
        long now = System.currentTimeMillis();
        if (showHelp) {
            if (helpAuto.contains(x, y)) {
                host.openDreamSettings();
            } else if (helpSettings.contains(x, y)) {
                showHelp = false;
                settings.sp.edit().putBoolean("helpSeen", true).apply();
                host.openSettings();
            } else if (helpOk.contains(x, y)) {
                showHelp = false;
                settings.sp.edit().putBoolean("helpSeen", true).apply();
            }
            return;
        }
        if (showCustomize) {
            int curType = pageType(Math.round(pagePos));
            for (int i = 0; i < colorHits.length; i++) {
                if (colorHits[i].contains(x, y)) {
                    if (curType == PAGE_CLOCK) {
                        settings.putInt("faceAccent" + currentFace(), i);
                    } else if (curType == PAGE_WIDGETS) {
                        settings.putInt("widgetAccent", i);
                    }
                    return;
                }
            }
            if (btnNight.contains(x, y)) {
                settings.putBool("glass", !settings.glass);
            } else if (btnSettings.contains(x, y)) {
                showCustomize = false;
                host.openSettings();
            } else if (btnDone.contains(x, y) || !panel.contains(x, y)) {
                showCustomize = false;
            }
            return;
        }
        if (now - chargeAnimStart < CHARGE_ANIM_MS) {
            chargeAnimStart = 0;
            return;
        }
        int type = pageType(page);
        if (type == PAGE_CLOCK) {
            if (pillRect.contains(x, y)) {
                int fi = faceIndexOf(ClockFaces.NOW_PLAYING);
                if (fi >= 0) {
                    faceIdx = fi;
                    faceDotsUntil = now + 1500;
                } else {
                    nowPlaying.playPause();
                }
                return;
            }
            int face = currentFace();
            if (face == ClockFaces.POMODORO && pomodoro.onTap(x, y, now, settings)) return;
            if (face == ClockFaces.NOW_PLAYING) {
                if (faces.npPrev.contains(x, y)) { nowPlaying.previous(); return; }
                if (faces.npPlay.contains(x, y)) { nowPlaying.playPause(); return; }
                if (faces.npNext.contains(x, y)) { nowPlaying.next(); return; }
            }
        } else if (type == PAGE_WIDGETS) {
            for (int s = 0; s < 2; s++) {
                if (!cards[s].contains(x, y)) continue;
                int[] stack = settings.stacks[s];
                int wt = stack[Math.floorMod(stackIdx[s], stack.length)];
                if (wt == Widgets.POMODORO) {
                    pomodoro.toggle(now, settings);
                    return;
                }
                if (wt == Widgets.NOW_PLAYING) {
                    RectF[] hits = widgets.npHits[s];
                    if (hits[0].contains(x, y)) { nowPlaying.previous(); return; }
                    if (hits[1].contains(x, y)) { nowPlaying.playPause(); return; }
                    if (hits[2].contains(x, y)) { nowPlaying.next(); return; }
                }
                if (wt == Widgets.UP_NEXT && agenda.joinHits[s].contains(x, y)) {
                    String url = agenda.joinUrls[s];
                    if (Agenda.isSafeJoinUrl(url)) host.openUrl(url);
                    return;
                }
                if (wt == Widgets.TODO) {
                    boolean[] add = {false};
                    if (todo.onTap(s, x, y, add)) {
                        if (add[0]) host.openTodo();
                        else if (hideDetails()) host.openTodo(); // locked: unlock first via the editor
                        invalidate();
                        return;
                    }
                }
            }
        } else if (type == PAGE_PHOTOS) {
            if (!PhotoSlideshow.hasPermission(getContext()) && host.isActivity()
                    && photos.permissionButton.contains(x, y)) {
                host.requestPhotoPermission();
                return;
            }
        }
        // empty tap: double-tap toggles dimming
        if (settings.doubleTapDim && now - lastEmptyTapAt < 350
                && Math.hypot(x - lastEmptyTapX, y - lastEmptyTapY) < 60 * d) {
            manualDim = !manualDim;
            lastEmptyTapAt = 0;
            performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
            return;
        }
        lastEmptyTapAt = now;
        lastEmptyTapX = x;
        lastEmptyTapY = y;
        pageDotsUntil = now + 2000;
    }
}
