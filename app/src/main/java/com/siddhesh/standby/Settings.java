package com.siddhesh.standby;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/** All user settings, stored in SharedPreferences "standby". */
final class Settings {
    static final String FILE = "standby";

    static final int KEEP_ALWAYS = 0, KEEP_CHARGING = 1, KEEP_SYSTEM = 2;
    static final String[] KEEP_NAMES = {"Always on", "Only while charging", "Use system screen timeout"};

    static final int WP_NONE = 0, WP_BING = 1, WP_PICSUM = 2;
    static final String[] WP_NAMES = {"None (black)", "Bing wallpaper of the day", "Random photo (picsum.photos)"};
    static final int[] WP_MINUTES = {15, 30, 60, 180, 1440};
    static final String[] WP_MINUTE_NAMES = {"Every 15 minutes", "Every 30 minutes", "Every hour", "Every 3 hours", "Once a day"};

    static final String[] CORNER_NAMES = {"Top left", "Top right", "Bottom left", "Bottom right"};

    static final String[] PAGE_NAMES = {"Widgets", "Photos", "Clock"};
    static final int[] DEFAULT_PAGES = {StandByView.PAGE_WIDGETS, StandByView.PAGE_PHOTOS, StandByView.PAGE_CLOCK};
    static final int[] DEFAULT_STACK0 = {Widgets.ANALOG, Widgets.DIGITAL, Widgets.WORLD, Widgets.POMODORO};
    static final int[] DEFAULT_STACK1 = {Widgets.CALENDAR, Widgets.BATTERY, Widgets.DATE, Widgets.NOW_PLAYING};
    static final int[] DEFAULT_FACES = {
            ClockFaces.DIGITAL, ClockFaces.ANALOG, ClockFaces.FLIP, ClockFaces.STACKED, ClockFaces.FLOAT,
            ClockFaces.NEON, ClockFaces.SEGMENT, ClockFaces.RINGS, ClockFaces.WORDS, ClockFaces.WORLD,
            ClockFaces.SOLAR, ClockFaces.POMODORO, ClockFaces.NOW_PLAYING,
    };

    final SharedPreferences sp;

    int[] pages;
    final int[][] stacks = new int[2][];
    int[] faces;
    int widgetAccent;
    final int[] faceAccent = new int[ClockFaces.COUNT];

    boolean nightAuto, showBattery, chargeAnim, nowPlayingPill, hidePhotosLocked, burnIn;
    boolean wallpaperOnFaces, wallpaperOnWidgets, pomoAutoStart, pomoSound;
    int brightness;      // -1 = follow system, else 1..100
    int keepOn, wallpaperSource, wallpaperDim, wallpaperInterval, photoCorner;
    int pomoFocus, pomoShort, pomoLong, pomoRounds;

    Settings(Context c) {
        sp = c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        load();
    }

    void load() {
        pages = parse(sp.getString("pages", null), DEFAULT_PAGES, 3);
        stacks[0] = parse(sp.getString("stackItems0", null), DEFAULT_STACK0, Widgets.COUNT);
        stacks[1] = parse(sp.getString("stackItems1", null), DEFAULT_STACK1, Widgets.COUNT);
        faces = parse(sp.getString("faces", null), DEFAULT_FACES, ClockFaces.COUNT);
        widgetAccent = sp.getInt("widgetAccent", 1);
        for (int i = 0; i < ClockFaces.COUNT; i++) {
            faceAccent[i] = sp.getInt("faceAccent" + i, ClockFaces.DEFAULT_ACCENT[i]);
        }
        nightAuto = sp.getBoolean("nightAuto", true);
        showBattery = sp.getBoolean("showBattery", true);
        chargeAnim = sp.getBoolean("chargeAnim", true);
        nowPlayingPill = sp.getBoolean("nowPlayingPill", true);
        hidePhotosLocked = sp.getBoolean("hidePhotosLocked", true);
        burnIn = sp.getBoolean("burnIn", true);
        wallpaperOnFaces = sp.getBoolean("wpFaces", true);
        wallpaperOnWidgets = sp.getBoolean("wpWidgets", false);
        pomoAutoStart = sp.getBoolean("pomoAutoStart", true);
        pomoSound = sp.getBoolean("pomoSound", true);
        brightness = sp.getInt("brightness", -1);
        keepOn = clamp(sp.getInt("keepOn", KEEP_ALWAYS), 0, 2);
        wallpaperSource = clamp(sp.getInt("wpSource", WP_NONE), 0, 2);
        wallpaperDim = clamp(sp.getInt("wpDim", 45), 0, 90);
        wallpaperInterval = clamp(sp.getInt("wpInterval", 2), 0, WP_MINUTES.length - 1);
        photoCorner = clamp(sp.getInt("photoCorner", 0), 0, 3);
        pomoFocus = clamp(sp.getInt("pomoFocus", 25), 1, 120);
        pomoShort = clamp(sp.getInt("pomoShort", 5), 1, 60);
        pomoLong = clamp(sp.getInt("pomoLong", 15), 1, 60);
        pomoRounds = clamp(sp.getInt("pomoRounds", 4), 2, 10);
    }

    void putInt(String k, int v) {
        sp.edit().putInt(k, v).apply();
        load();
    }

    void putBool(String k, boolean v) {
        sp.edit().putBoolean(k, v).apply();
        load();
    }

    void putList(String k, int[] v) {
        sp.edit().putString(k, join(v)).apply();
        load();
    }

    static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** Parses "1,4,2" keeping only unique ids in [0,max). Falls back to def if empty/invalid. */
    static int[] parse(String s, int[] def, int max) {
        if (s == null || s.isEmpty()) return def.clone();
        List<Integer> out = new ArrayList<>();
        for (String part : s.split(",")) {
            try {
                int v = Integer.parseInt(part.trim());
                if (v >= 0 && v < max && !out.contains(v)) out.add(v);
            } catch (NumberFormatException ignored) {
            }
        }
        if (out.isEmpty()) return def.clone();
        int[] r = new int[out.size()];
        for (int i = 0; i < r.length; i++) r[i] = out.get(i);
        return r;
    }

    static String join(int[] v) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < v.length; i++) {
            if (i > 0) b.append(',');
            b.append(v[i]);
        }
        return b.toString();
    }
}
