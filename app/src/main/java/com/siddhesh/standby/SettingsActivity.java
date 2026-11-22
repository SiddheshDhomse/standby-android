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
                if (checked && !nl.contains(id)) nl.add(id);
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

        section("SCREEN");
        choice("Keep screen on", Settings.KEEP_NAMES, s.keepOn, v -> s.putInt("keepOn", v));
        note("“Always on” keeps the display awake whenever StandBy is open, even on battery.");
        toggle("Use system brightness", "Turn off to set StandBy's own brightness", s.brightness < 0, checked -> {
            s.putInt("brightness", checked ? -1 : 60);
            build();
        });
        if (s.brightness >= 0) {
            slider("Brightness", 1, 100, s.brightness, "%", v -> s.putInt("brightness", v));
        }
        toggle("Night mode (auto)", "In a dark room everything turns dim red", s.nightAuto, "nightAuto");
        toggle("Burn-in protection", "Moves the picture a few pixels every minute", s.burnIn, "burnIn");
        toggle("Show battery level", "Battery icon and % on the clock and photo views", s.showBattery, "showBattery");
        toggle("Charging animation", "Plays when you plug in the charger", s.chargeAnim, "chargeAnim");

        section("VIEWS & PLACEMENT");
        orderList("Views (swipe left/right)", Settings.PAGE_NAMES, s.pages, "pages");
        orderList("Left widget stack", Widgets.NAMES, s.stacks[0], "stackItems0");
        orderList("Right widget stack", Widgets.NAMES, s.stacks[1], "stackItems1");
        button("⇄  Swap left and right stacks", () -> {
            int[] a = s.stacks[0], b = s.stacks[1];
            s.putList("stackItems0", b);
            s.putList("stackItems1", a);
            build();
        });
        orderList("Clock faces (swipe up/down)", ClockFaces.NAMES, s.faces, "faces");
        choice("Clock position on photos", Settings.CORNER_NAMES, s.photoCorner, v -> s.putInt("photoCorner", v));

        section("WALLPAPER");
        choice("Wallpaper from the internet", Settings.WP_NAMES, s.wallpaperSource, v -> {
            s.putInt("wpSource", v);
            Wallpaper.requestNew(this);
        });
        if (s.wallpaperSource != Settings.WP_NONE) {
            choice("Change wallpaper", Settings.WP_MINUTE_NAMES, s.wallpaperInterval, v -> s.putInt("wpInterval", v));
            slider("Darken wallpaper", 0, 90, s.wallpaperDim, "%", v -> s.putInt("wpDim", v));
            toggle("Behind clock faces", null, s.wallpaperOnFaces, "wpFaces");
            toggle("Behind widgets", null, s.wallpaperOnWidgets, "wpWidgets");
            button("Get a new wallpaper now", () -> {
                Wallpaper.requestNew(this);
                android.widget.Toast.makeText(this, "A new wallpaper will load in a moment",
                        android.widget.Toast.LENGTH_SHORT).show();
            });
        }
        note("Images are downloaded over HTTPS only from bing.com or picsum.photos. Nothing about you is sent.");

        section("NOW PLAYING (SPOTIFY)");
        boolean access = NowPlaying.hasAccess(this);
        LinearLayout st = row();
        st.addView(text(access ? "✓ Notification access allowed" : "Notification access needed", 16,
                access ? 0xFF30D158 : 0xFFFF9F0A));
        st.addView(text("Android only lets apps see the current song through this permission. "
                + "StandBy ignores all notification contents.", 13, SUB));
        if (!access) {
            button("Allow notification access", () ->
                    open(new Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
            note("Switch greyed out? Android blocks this for apps installed from a browser: "
                    + "open App info below → ⋮ (top-right) → “Allow restricted settings”, then try again.");
            button("Open App info", () -> open(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", getPackageName(), null))));
        }
        toggle("Mini player on clock faces", "Shows the playing song at the bottom", s.nowPlayingPill, "nowPlayingPill");

        section("POMODORO");
        slider("Focus", 5, 90, s.pomoFocus, " min", v -> s.putInt("pomoFocus", v));
        slider("Short break", 1, 30, s.pomoShort, " min", v -> s.putInt("pomoShort", v));
        slider("Long break", 5, 60, s.pomoLong, " min", v -> s.putInt("pomoLong", v));
        slider("Sessions before a long break", 2, 8, s.pomoRounds, "", v -> s.putInt("pomoRounds", v));
        toggle("Start the next session automatically", null, s.pomoAutoStart, "pomoAutoStart");
        toggle("Sound when a session ends", "It always vibrates", s.pomoSound, "pomoSound");

        section("PRIVACY & PERMISSIONS");
        toggle("Hide photos on the lock screen", "Anyone could see your gallery while the phone is locked",
                s.hidePhotosLocked, "hidePhotosLocked");
        boolean photos = PhotoSlideshow.hasPermission(this);
        if (!photos) {
            button("Allow access to photos", () -> requestPermissions(PhotoSlideshow.permissions(), 1));
        } else {
            note("✓ Photo access allowed. You can remove it in App info → Permissions.");
        }

        section("START AUTOMATICALLY");
        note("Choose StandBy as your Screen saver and set it to start while charging. "
                + "It then opens by itself whenever the phone charges and the screen would turn off.");
        button("Open Screen saver settings", () -> open(new Intent(android.provider.Settings.ACTION_DREAM_SETTINGS)));

        section("ABOUT");
        note("StandBy for Android 1.1 · no ads, no tracking. Internet is used only for wallpapers.");
        button("Show the welcome tips again", () -> {
            s.putBool("helpSeen", false);
            finish();
        });

        scroll.post(() -> scroll.scrollTo(0, y));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        build();
    }
}
