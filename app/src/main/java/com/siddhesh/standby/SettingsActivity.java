package com.siddhesh.standby;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/** All StandBy settings, built in code (no XML) with a dark One UI-like look. */
public class SettingsActivity extends Activity {

    private static final int BG = 0xFF000000, CARD = 0xFF1C1C1E, TEXT = 0xFFFFFFFF, SUB = 0xFF8E8E93,
            ACCENT = 0xFF0A84FF;

    private Settings s;
    private LinearLayout root;
    private LinearLayout card;
    private ScrollView scroll;
    private float d;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        s = new Settings(this);
        d = getResources().getDisplayMetrics().density;
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        scroll.setFitsSystemWindows(true);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, dp(48));
        scroll.addView(root);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        s.load();
        build();
    }

    private int dp(float v) {
        return Math.round(v * d);
    }

    // ------------------------------------------------------------ building blocks

    private void title(String t) {
        TextView tv = new TextView(this);
        tv.setText(t);
        tv.setTextColor(TEXT);
        tv.setTextSize(30);
        tv.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        tv.setPadding(dp(8), dp(24), dp(8), dp(12));
        root.addView(tv);
    }

    private void section(String t) {
        TextView tv = new TextView(this);
        tv.setText(t);
        tv.setTextColor(SUB);
        tv.setTextSize(14);
        tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        tv.setPadding(dp(14), dp(22), dp(14), dp(8));
        root.addView(tv);
        card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(dp(22));
        card.setBackground(bg);
        card.setPadding(dp(4), dp(4), dp(4), dp(4));
        root.addView(card, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private TextView text(String t, float size, int color) {
        TextView tv = new TextView(this);
        tv.setText(t);
        tv.setTextSize(size);
        tv.setTextColor(color);
        return tv;
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.addView(r, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return r;
    }

    private void note(String t) {
        LinearLayout r = row();
        r.addView(text(t, 13, SUB));
    }

    private void toggle(String label, String sub, boolean value, String key) {
        toggle(label, sub, value, checked -> s.putBool(key, checked));
    }

    private void toggle(String label, String sub, boolean value, java.util.function.Consumer<Boolean> onSet) {
        LinearLayout r = row();
        Switch sw = new Switch(this);
        sw.setText(label);
        sw.setTextColor(TEXT);
        sw.setTextSize(16);
        sw.setChecked(value);
        sw.setOnCheckedChangeListener((b, checked) -> onSet.accept(checked));
        r.addView(sw, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        if (sub != null) r.addView(text(sub, 13, SUB));
    }

    private void choice(String label, String[] options, int current, IntConsumer onPick) {
        LinearLayout r = row();
        r.addView(text(label, 16, TEXT));
        r.addView(text(options[Math.max(0, Math.min(options.length - 1, current))], 14, ACCENT));
        r.setClickable(true);
        r.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle(label)
                .setSingleChoiceItems(options, current, (dlg, which) -> {
                    onPick.accept(which);
                    dlg.dismiss();
                    build();
                })
                .setNegativeButton("Cancel", null)
                .show());
    }

    private void slider(String label, int min, int max, int value, String suffix, IntConsumer onSet) {
        LinearLayout r = row();
        TextView tv = text(label + ": " + value + suffix, 16, TEXT);
        r.addView(tv);
        SeekBar sb = new SeekBar(this);
        sb.setMin(min);
        sb.setMax(max);
        sb.setProgress(value);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                tv.setText(label + ": " + progress + suffix);
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                onSet.accept(bar.getProgress());
            }
        });
        r.addView(sb, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void button(String label, Runnable action) {
        LinearLayout r = row();
        TextView tv = text(label, 16, ACCENT);
        r.addView(tv);
        r.setClickable(true);
        r.setOnClickListener(v -> action.run());
    }

    /**
     * Ordered, toggleable list: enabled items in order (with up/down), then disabled ones.
     * At least one item always stays enabled.
     */
    private void orderList(String label, String[] names, int[] enabled, String key) {
        LinearLayout r = row();
        r.addView(text(label, 16, TEXT));
        r.addView(text("Tick to show, arrows to change the order", 13, SUB));
        List<Integer> on = new ArrayList<>();
        for (int v : enabled) on.add(v);
        List<Integer> all = new ArrayList<>(on);
        for (int i = 0; i < names.length; i++) if (!all.contains(i)) all.add(i);

        for (int id : all) {
            boolean isOn = on.contains(id);
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setGravity(Gravity.CENTER_VERTICAL);
            CheckBox cb = new CheckBox(this);
            cb.setText(names[id]);
            cb.setTextColor(isOn ? TEXT : SUB);
            cb.setTextSize(15);
            cb.setChecked(isOn);
            cb.setOnCheckedChangeListener((b, checked) -> {
                List<Integer> nl = new ArrayList<>(on);
                if (checked && !nl.contains(id)) {
                    if (key.equals("cities") && nl.size() >= WorldCities.MAX) {
                        b.setChecked(false);
                        android.widget.Toast.makeText(this, "Up to " + WorldCities.MAX + " cities",
                                android.widget.Toast.LENGTH_SHORT).show();
                        return;
                    }
                    nl.add(id);
                }
                if (!checked) {
                    if (nl.size() <= 1) {
                        b.setChecked(true);
                        return;
                    }
                    nl.remove(Integer.valueOf(id));
                }
                s.putList(key, toArray(nl));
                build();
            });
            line.addView(cb, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            if (isOn) {
                int idx = on.indexOf(id);
                line.addView(arrow("↑", idx > 0, () -> {
                    List<Integer> nl = new ArrayList<>(on);
                    nl.remove(idx);
                    nl.add(idx - 1, id);
                    s.putList(key, toArray(nl));
                    build();
                }));
                line.addView(arrow("↓", idx < on.size() - 1, () -> {
                    List<Integer> nl = new ArrayList<>(on);
                    nl.remove(idx);
                    nl.add(idx + 1, id);
                    s.putList(key, toArray(nl));
                    build();
                }));
            }
            r.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    private View arrow(String label, boolean enabled, Runnable action) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(18);
        b.setTextColor(enabled ? TEXT : 0x33FFFFFF);
        b.setBackgroundColor(Color.TRANSPARENT);
        b.setEnabled(enabled);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(dp(12), 0, dp(12), 0);
        b.setOnClickListener(v -> action.run());
        return b;
    }

    private static int[] toArray(List<Integer> l) {
        int[] r = new int[l.size()];
        for (int i = 0; i < r.length; i++) r[i] = l.get(i);
        return r;
    }

    private void open(Intent i) {
        try {
            startActivity(i);
        } catch (Exception e) {
            try {
                startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));
            } catch (Exception ignored) {
            }
        }
    }

    // ------------------------------------------------------------ screen

    private void build() {
        int y = scroll.getScrollY();
        root.removeAllViews();
        title("StandBy settings");

        section("LIQUID GLASS");
        toggle("Liquid Glass look", "Frosted glass over a slow, flowing background. Also a Quick Settings tile.",
                s.glass, "glass");
        choice("Glass colour", LiquidGlass.PALETTE_NAMES, s.glassPalette, v -> s.putInt("glassPalette", v));
        if (!LiquidGlass.supported()) note("Real blur needs Android 12 or newer; you'll get a simpler frosted look.");
        note("Quick Settings: swipe down twice \u2192 \u270E (edit) \u2192 drag \u201CLiquid Glass\u201D "
                + "and \u201CStandBy\u201D into your tiles.");

        section("SCREEN");
        choice("Keep screen on", Settings.KEEP_NAMES, s.keepOn, v -> s.putInt("keepOn", v));
        toggle("Use system brightness", "Turn off to set StandBy's own brightness", s.brightness < 0, checked -> {
            s.putInt("brightness", checked ? -1 : 60);
            build();
        });
        if (s.brightness >= 0) {
            slider("Brightness", 1, 100, s.brightness, "%", v -> s.putInt("brightness", v));
        }
        toggle("Double-tap to dim", "Double-tap an empty spot to dim or brighten", s.doubleTapDim, "doubleTapDim");
        toggle("Night mode (auto)", "In a dark room everything turns dim red", s.nightAuto, "nightAuto");
        toggle("Show battery level", "Battery icon and % on the clock view", s.showBattery, "showBattery");
        toggle("Charging animation", "Plays when you plug in the charger", s.chargeAnim, "chargeAnim");

        section("BURN-IN PROTECTION");
        toggle("Pixel drift", "Everything slowly moves up to ~24 px, on a new path every hour", s.burnIn, "burnIn");
        toggle("Softer whites", "Draws white at 86% \u2014 barely visible, much less OLED wear", s.softWhites, "softWhites");
        toggle("Auto-dim when idle", null, s.autoDim, "autoDim");
        if (s.autoDim) slider("Dim after", 1, 30, s.autoDimMinutes, " min without a touch", v -> s.putInt("autoDimMinutes", v));
        toggle("Fade extras", "Battery badge and mini-player hide after 30 s; touch to show", s.fadeExtras, "fadeExtras");
        choice("Rotate clock faces", Settings.ROTATE_NAMES, s.faceRotate, v -> s.putInt("faceRotate", v));
        toggle("Bedside mode at night", "Outline-only clock, about 3% of pixels lit. Touch to wake for a minute.",
                s.bedside, "bedside");
        if (s.bedside) {
            choice("Bedside from", Settings.hourNames(), s.bedStart, v -> s.putInt("bedStart", v));
            choice("Bedside until", Settings.hourNames(), s.bedEnd, v -> s.putInt("bedEnd", v));
            choice("Bedside colour", Settings.INK_NAMES, s.bedInk, v -> s.putInt("bedInk", v));
        }
        toggle("Sleep window", "Screen goes fully dark during these hours \u2014 the strongest protection",
                s.sleepWindow, "sleepWindow");
        if (s.sleepWindow) {
            choice("Dark from", Settings.hourNames(), s.sleepStart, v -> s.putInt("sleepStart", v));
            choice("Dark until", Settings.hourNames(), s.sleepEnd, v -> s.putInt("sleepEnd", v));
        }

        section("VIEWS & PLACEMENT");
        orderList("Views (swipe left/right)", Settings.PAGE_NAMES, s.pages, "pages");
        orderList("Left widget stack", Widgets.NAMES, s.stacks[0], "stackItems0");
        orderList("Right widget stack", Widgets.NAMES, s.stacks[1], "stackItems1");
        button("\u21C4  Swap left and right stacks", () -> {
            int[] a = s.stacks[0], b = s.stacks[1];
            s.putList("stackItems0", b);
            s.putList("stackItems1", a);
            build();
        });
        orderList("Clock faces (swipe up/down)", ClockFaces.NAMES, s.faces, "faces");

        section("CALENDAR & TEAMS");
        boolean cal = Agenda.hasPermission(this);
        if (!cal) {
            button("Allow calendar access", () -> requestPermissions(
                    new String[]{android.Manifest.permission.READ_CALENDAR}, 2));
        } else {
            String which = s.calendarIds.isEmpty() ? "all calendars" : "selected calendars";
            button("Choose calendars (now: " + which + ")", this::pickCalendars);
        }
        toggle("Hide details when locked", "Shows \u201CTeams meeting\u201D and task counts instead of titles",
                s.hideDetailsLocked, "hideDetailsLocked");
        note("Teams meetings live in your work Outlook calendar. Install Outlook, sign in with your work account, "
                + "then Outlook \u2192 Settings \u2192 your account \u2192 turn on \u201CSync calendars\u201D. "
                + "Pick that calendar above. Some companies block this \u2014 then it can't be shown. "
                + "StandBy only reads events; nothing leaves your phone. \u201CJoin Teams\u201D appears 15 min before.");

        section("TO-DO");
        button("Edit to-do list", () -> startActivity(new Intent(this, TodoActivity.class)));
        note("Tasks are stored only on this phone. Tap a task in the widget to tick it off.");

        section("WEATHER");
        LinearLayout wr = row();
        wr.addView(text("City", 16, TEXT));
        wr.addView(text(s.weatherCity.isEmpty() ? "Not set \u2014 tap to choose" : s.weatherCity, 14, ACCENT));
        wr.setClickable(true);
        wr.setOnClickListener(v -> askCity());
        note("From Open-Meteo: free, no account, no location permission. Only the city's coordinates are sent.");

        section("WORLD CLOCK");
        orderList("Cities (up to " + WorldCities.MAX + ")", WorldCities.NAMES, s.cities, "cities");

        section("NOW PLAYING");
        boolean access = NowPlaying.hasAccess(this);
        LinearLayout st = row();
        st.addView(text(access ? "\u2713 Notification access allowed" : "Notification access needed", 16,
                access ? 0xFF30D158 : 0xFFFF9F0A));
        st.addView(text("Needed to see the current song from Spotify, YouTube Music or any player. "
                + "StandBy ignores all notification contents.", 13, SUB));
        if (!access) {
            button("Allow notification access", () ->
                    open(new Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
            button("Open App info", () -> open(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", getPackageName(), null))));
        }
        toggle("Switch to Now Playing automatically", "When music starts; switches back 30 s after it stops",
                s.autoNowPlaying, "autoNowPlaying");
        toggle("Mini player on clock faces", "Shows the playing song at the bottom", s.nowPlayingPill, "nowPlayingPill");

        section("POMODORO");
        slider("Focus", 5, 90, s.pomoFocus, " min", v -> s.putInt("pomoFocus", v));
        slider("Short break", 1, 30, s.pomoShort, " min", v -> s.putInt("pomoShort", v));
        slider("Long break", 5, 60, s.pomoLong, " min", v -> s.putInt("pomoLong", v));
        slider("Sessions before a long break", 2, 8, s.pomoRounds, "", v -> s.putInt("pomoRounds", v));
        toggle("Start the next session automatically", null, s.pomoAutoStart, "pomoAutoStart");
        toggle("Sound when a session ends", "It always vibrates", s.pomoSound, "pomoSound");
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            button("Allow Pomodoro alerts (notifications)", () -> requestPermissions(
                    new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 3));
        } else {
            note("\u2713 Alerts work even when StandBy is closed or the screen is off.");
        }

        section("WALLPAPER (WHEN LIQUID GLASS IS OFF)");
        choice("Wallpaper from the internet", Settings.WP_NAMES, s.wallpaperSource, v -> {
            s.putInt("wpSource", v);
            Wallpaper.requestNew(this);
        });
        if (s.wallpaperSource != Settings.WP_NONE) {
            choice("Change wallpaper", Settings.WP_MINUTE_NAMES, s.wallpaperInterval, v -> s.putInt("wpInterval", v));
            slider("Darken wallpaper", 0, 90, s.wallpaperDim, "%", v -> s.putInt("wpDim", v));
            toggle("Behind clock faces", null, s.wallpaperOnFaces, "wpFaces");
            toggle("Behind widgets", null, s.wallpaperOnWidgets, "wpWidgets");
        }

        section("START AUTOMATICALLY");
        note("Choose StandBy as your Screen saver and set it to start while charging.");
        button("Open Screen saver settings", () -> open(new Intent(android.provider.Settings.ACTION_DREAM_SETTINGS)));

        section("ABOUT");
        note("StandBy for Android 1.2 \u00B7 no ads, no tracking. Internet is used only for weather and wallpapers.");
        button("Show the welcome tips again", () -> {
            s.putBool("helpSeen", false);
            finish();
        });

        scroll.post(() -> scroll.scrollTo(0, y));
    }

    private void pickCalendars() {
        java.util.List<Agenda.Cal> cals = Agenda.listCalendars(this);
        if (cals.isEmpty()) {
            android.widget.Toast.makeText(this, "No calendars found on this phone", android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        String[] names = new String[cals.size()];
        boolean[] checked = new boolean[cals.size()];
        java.util.Set<String> sel = new java.util.HashSet<>(java.util.Arrays.asList(s.calendarIds.split(",")));
        for (int i = 0; i < cals.size(); i++) {
            Agenda.Cal c = cals.get(i);
            names[i] = c.name + (c.account != null && !c.account.equals(c.name) ? "  (" + c.account + ")" : "");
            checked[i] = s.calendarIds.isEmpty() || sel.contains(String.valueOf(c.id));
        }
        new AlertDialog.Builder(this)
                .setTitle("Show events from")
                .setMultiChoiceItems(names, checked, (dlg, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton("Save", (dlg, which) -> {
                    StringBuilder b = new StringBuilder();
                    int n = 0;
                    for (int i = 0; i < cals.size(); i++) {
                        if (checked[i]) {
                            if (n++ > 0) b.append(',');
                            b.append(cals.get(i).id);
                        }
                    }
                    // all ticked (or none) = show everything
                    s.putString("calendarIds", n == cals.size() || n == 0 ? "" : b.toString());
                    build();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void askCity() {
        android.widget.EditText in = new android.widget.EditText(this);
        in.setSingleLine(true);
        in.setHint("e.g. Pune");
        in.setText(s.weatherCity.contains(",") ? s.weatherCity.substring(0, s.weatherCity.indexOf(',')) : s.weatherCity);
        in.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(60)});
        new AlertDialog.Builder(this)
                .setTitle("Weather city")
                .setView(in)
                .setPositiveButton("Save", (dlg, which) -> {
                    String q = in.getText().toString().trim();
                    if (q.isEmpty()) return;
                    new Thread(() -> {
                        String[] r = Weather.geocode(q);
                        runOnUiThread(() -> {
                            if (r == null) {
                                android.widget.Toast.makeText(this, "Couldn't find \u201C" + q + "\u201D (or no internet)",
                                        android.widget.Toast.LENGTH_LONG).show();
                                return;
                            }
                            s.sp.edit().putString("weatherCity", r[0])
                                    .putFloat("weatherLat", Float.parseFloat(r[1]))
                                    .putFloat("weatherLon", Float.parseFloat(r[2])).apply();
                            s.load();
                            build();
                        });
                    }, "geocode").start();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        build();
    }
}
