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

    static final int WP_NONE = 0, WP_BING = 1, WP_PICSUM = 2, WP_OWN = 3;
    static final String[] WP_NAMES = {"None (black)", "Bing wallpaper of the day", "Random photo (picsum.photos)", "My own image"};
    static final int BG_FLOW = 0, BG_WALLPAPER = 1, BG_BLACK = 2;
    static final String[] TINT_NAMES = {"No tint", "Cool", "Warm", "Rose", "Mint"};
    static final int[] TINT_COLORS = {0, 0xFF64AAFF, 0xFFFFAA5A, 0xFFFF78AA, 0xFF6EE6BE};
    static final int[] WP_MINUTES = {15, 30, 60, 180, 1440};
    static final String[] WP_MINUTE_NAMES = {"Every 15 minutes", "Every 30 minutes", "Every hour", "Every 3 hours", "Once a day"};

    static final String[] CORNER_NAMES = {"Top left", "Top right", "Bottom left", "Bottom right"};
    static final int[] ROTATE_HOURS = {0, 1, 2, 4};
    static final String[] ROTATE_NAMES = {"Off", "Every hour", "Every 2 hours", "Every 4 hours"};
    static final String[] INK_NAMES = {"Dim red", "Dim grey", "Dim amber"};
    static final int[] INK_COLORS = {0xFFB33A2E, 0xFF8E8E93, 0xFFB07A2E};

    static final String[] PAGE_NAMES = {"Widgets", "Photos", "Clock"};
    static final int[] DEFAULT_PAGES = {StandByView.PAGE_WIDGETS, StandByView.PAGE_CLOCK};
    static final int[] DEFAULT_STACK0 = {Widgets.DIGITAL, Widgets.ANALOG, Widgets.WEATHER, Widgets.WORLD, Widgets.POMODORO};
    static final int[] DEFAULT_STACK1 = {Widgets.UP_NEXT, Widgets.TODO, Widgets.CALENDAR, Widgets.NOW_PLAYING,
            Widgets.BATTERY, Widgets.DATE};
    static final int[] DEFAULT_FACES = {
            ClockFaces.GLASS, ClockFaces.DIGITAL, ClockFaces.ANALOG, ClockFaces.FLIP, ClockFaces.WEATHER,
            ClockFaces.STACKED, ClockFaces.FLOAT, ClockFaces.NEON, ClockFaces.SEGMENT, ClockFaces.RINGS,
            ClockFaces.WORDS, ClockFaces.WORLD, ClockFaces.SOLAR, ClockFaces.POMODORO, ClockFaces.NOW_PLAYING,
            ClockFaces.BEDSIDE,
    };

    final SharedPreferences sp;

    int[] pages;
    final int[][] stacks = new int[2][];
    int[] faces;
    int[] cities;
    int widgetAccent;
    final int[] faceAccent = new int[ClockFaces.COUNT];

    boolean nightAuto, showBattery, chargeAnim, nowPlayingPill, hidePhotosLocked, burnIn;
    boolean wallpaperOnFaces, wallpaperOnWidgets, pomoAutoStart, pomoSound;
    int brightness;      // -1 = follow system, else 1..100
    int keepOn, wallpaperSource, wallpaperDim, wallpaperInterval, photoCorner;
    int pomoFocus, pomoShort, pomoLong, pomoRounds;

    // v1.2
    boolean glass;
    int glassPalette;
    boolean softWhites, autoDim, doubleTapDim, fadeExtras, autoNowPlaying, hideDetailsLocked;
    int autoDimMinutes;
    boolean bedside, sleepWindow;
    int bedStart, bedEnd, bedInk, sleepStart, sleepEnd, faceRotate;
    String weatherCity;
    float weatherLat, weatherLon;
    // v1.3 background & glass
    int bgSource, wallSrc, bgBlur, glassBlur, glassRefr, glassFrost, glassRim, glassRadius, glassTint;
    String calendarIds;  // "" = all calendars
    boolean calendarTodayOnly;

    Settings(Context c) {
        sp = c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        load();
    }

    void load() {
        pages = parse(sp.getString("pages", null), DEFAULT_PAGES, 3);
        stacks[0] = parse(sp.getString("stackItems0", null), DEFAULT_STACK0, Widgets.COUNT);
        stacks[1] = parse(sp.getString("stackItems1", null), DEFAULT_STACK1, Widgets.COUNT);
        faces = parse(sp.getString("faces", null), DEFAULT_FACES, ClockFaces.COUNT);
        cities = parse(sp.getString("cities", null), WorldCities.DEFAULT, WorldCities.NAMES.length);
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
        wallpaperDim = clamp(sp.getInt("wpDim", 30), 0, 90);
        wallpaperInterval = clamp(sp.getInt("wpInterval", 2), 0, WP_MINUTES.length - 1);
        photoCorner = clamp(sp.getInt("photoCorner", 0), 0, 3);
        pomoFocus = clamp(sp.getInt("pomoFocus", 25), 1, 120);
        pomoShort = clamp(sp.getInt("pomoShort", 5), 1, 60);
        pomoLong = clamp(sp.getInt("pomoLong", 15), 1, 60);
        pomoRounds = clamp(sp.getInt("pomoRounds", 4), 2, 10);

        glass = sp.getBoolean("glass", true);
        glassPalette = clamp(sp.getInt("glassPalette", 0), 0, LiquidGlass.PALETTE_NAMES.length - 1);
        softWhites = sp.getBoolean("softWhites", true);
        autoDim = sp.getBoolean("autoDim", true);
        autoDimMinutes = clamp(sp.getInt("autoDimMinutes", 10), 1, 60);
        doubleTapDim = sp.getBoolean("doubleTapDim", true);
        fadeExtras = sp.getBoolean("fadeExtras", true);
        autoNowPlaying = sp.getBoolean("autoNowPlaying", true);
        hideDetailsLocked = sp.getBoolean("hideDetailsLocked", true);
        bedside = sp.getBoolean("bedside", true);
        bedStart = clamp(sp.getInt("bedStart", 23), 0, 23);
        bedEnd = clamp(sp.getInt("bedEnd", 7), 0, 23);
        bedInk = clamp(sp.getInt("bedInk", 0), 0, INK_COLORS.length - 1);
        sleepWindow = sp.getBoolean("sleepWindow", false);
        sleepStart = clamp(sp.getInt("sleepStart", 1), 0, 23);
        sleepEnd = clamp(sp.getInt("sleepEnd", 6), 0, 23);
        faceRotate = clamp(sp.getInt("faceRotate", 0), 0, ROTATE_HOURS.length - 1);
        weatherCity = sp.getString("weatherCity", "");
        weatherLat = sp.getFloat("weatherLat", Float.NaN);
        weatherLon = sp.getFloat("weatherLon", Float.NaN);
        calendarIds = sp.getString("calendarIds", "");
        bgSource = clamp(sp.getInt("bgSource", BG_FLOW), 0, 2);
        int legacy = sp.getInt("wpSource", WP_NONE);
        wallSrc = clamp(sp.getInt("wallSrc", legacy == WP_NONE ? WP_BING : legacy), 1, 3);
        bgBlur = clamp(sp.getInt("bgBlur", 0), 0, 100);
        glassBlur = clamp(sp.getInt("glassBlur", 60), 0, 100);
        glassRefr = clamp(sp.getInt("glassRefr", 50), 0, 100);
        glassFrost = clamp(sp.getInt("glassFrost", 15), 0, 100);
        glassRim = clamp(sp.getInt("glassRim", 60), 0, 100);
        glassRadius = clamp(sp.getInt("glassRadius", 26), 8, 44);
        glassTint = clamp(sp.getInt("glassTint", 0), 0, TINT_COLORS.length - 1);
        calendarTodayOnly = sp.getBoolean("calTodayOnly", false);
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

    void putString(String k, String v) {
        sp.edit().putString(k, v).apply();
        load();
    }

    /** Wallpaper source to download/load right now (WP_NONE unless the background is a wallpaper). */
    int activeWallpaper() {
        return bgSource == BG_WALLPAPER ? wallSrc : WP_NONE;
    }

    void resetLook() {
        sp.edit().remove("bgSource").remove("wallSrc").remove("bgBlur").remove("wpDim").remove("glass")
                .remove("glassPalette").remove("glassBlur").remove("glassRefr").remove("glassFrost")
                .remove("glassRim").remove("glassRadius").remove("glassTint").apply();
        load();
    }

    static String hourName(int h) {
        int hh = h % 12 == 0 ? 12 : h % 12;
        return hh + (h < 12 ? " AM" : " PM");
    }

    static String[] hourNames() {
        String[] r = new String[24];
        for (int i = 0; i < 24; i++) r[i] = hourName(i);
        return r;
    }

    /** True if hour-of-day (fractional) is inside [start, end), wrapping past midnight. */
    static boolean inWindow(float hour, int start, int end) {
        if (start == end) return false;
        if (start < end) return hour >= start && hour < end;
        return hour >= start || hour < end;
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
