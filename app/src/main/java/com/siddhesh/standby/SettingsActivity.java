package com.siddhesh.standby;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Settings, redesigned: a home screen with search, quick toggles and grouped categories, and one
 * focused page per category. Built in code (no XML), dark, One UI / iOS style.
 */
public class SettingsActivity extends Activity {

    private static final int BG = 0xFF000000, CARD = 0xFF1C1C1E, CARD2 = 0xFF2C2C2E, TEXT = 0xFFFFFFFF,
            SUB = 0xFF8E8E93, ACCENT = 0xFF0A84FF, DIV = 0xFF2C2C2E;
    private static final int REQ_PICK_IMAGE = 11;

    private static final String HOME = "home", GLASS = "glass", FACES = "faces", SCREEN = "screen",
            CAL = "calendar", TODO = "todo", WEATHER = "weather", WORLD = "world", MUSIC = "music",
            POMO = "pomodoro", PRIVACY = "privacy", AUTO = "autostart", ABOUT = "about";

    private Settings s;
    private float d;
    private String page = HOME;
    private String facesTab = "faces";
    private String query = "";

    private LinearLayout header;
    private FrameLayout previewHolder;
    private ScrollView scroll;
    private LinearLayout root;
    private LinearLayout homeList;
    private LinearLayout card;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        s = new Settings(this);
        d = getResources().getDisplayMetrics().density;
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        if (savedInstanceState != null) page = savedInstanceState.getString("page", HOME);

        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackgroundColor(BG);
        outer.setFitsSystemWindows(true);

        header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(16), dp(6), dp(16), dp(4));
        outer.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        previewHolder = new FrameLayout(this);
        previewHolder.setPadding(dp(16), dp(4), dp(16), dp(8));
        outer.addView(previewHolder, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        scroll = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(4), dp(16), dp(48));
        scroll.addView(root);
        outer.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(outer);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putString("page", page);
    }

    @Override
    protected void onResume() {
        super.onResume();
        s.load();
        build();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (!HOME.equals(page)) {
            go(HOME);
        } else {
            super.onBackPressed();
        }
    }

    private void go(String p) {
        page = p;
        build();
        scroll.scrollTo(0, 0);
    }

    private int dp(float v) {
        return Math.round(v * d);
    }

    // ================================================================ building blocks

    private TextView text(String t, float size, int color) {
        TextView tv = new TextView(this);
        tv.setText(t);
        tv.setTextSize(size);
        tv.setTextColor(color);
        return tv;
    }

    private GradientDrawable rounded(int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private void buildHeader(String title, boolean back) {
        header.removeAllViews();
        if (back) {
            TextView b = text("‹  Settings", 17, ACCENT);
            b.setPadding(0, dp(10), dp(20), dp(10));
            b.setOnClickListener(v -> go(HOME));
            b.setContentDescription("Back to settings");
            header.addView(b);
        }
        TextView t = text(title, back ? 28 : 34, TEXT);
        t.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        t.setPadding(dp(4), back ? 0 : dp(14), 0, dp(6));
        header.addView(t);
    }

    private void section(String t) {
        if (t != null) {
            TextView tv = text(t, 13, SUB);
            tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            tv.setLetterSpacing(0.06f);
            tv.setPadding(dp(14), dp(18), dp(14), dp(6));
            root.addView(tv);
        }
        card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(rounded(CARD, 18));
        card.setPadding(0, dp(2), 0, dp(2));
        LinearLayout.LayoutParams lp = fullWidth();
        if (t == null) lp.topMargin = dp(12);
        root.addView(card, lp);
    }

    private LinearLayout row() {
        if (card.getChildCount() > 0) {
            View div = new View(this);
            div.setBackgroundColor(DIV);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1);
            lp.leftMargin = dp(16);
            card.addView(div, lp);
        }
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setPadding(dp(16), dp(12), dp(16), dp(12));
        card.addView(r, fullWidth());
        return r;
    }

    private void note(String t) {
        TextView tv = text(t, 13, SUB);
        tv.setLineSpacing(0, 1.15f);
        tv.setPadding(dp(14), dp(8), dp(14), dp(4));
        root.addView(tv);
    }

    private void toggle(String label, String sub, boolean value, String key) {
        toggle(label, sub, value, checked -> s.putBool(key, checked));
    }

    private void toggle(String label, String sub, boolean value, Consumer<Boolean> onSet) {
        LinearLayout r = row();
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(text(label, 16, TEXT));
        if (sub != null) texts.addView(text(sub, 12.5f, SUB));
        line.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Switch sw = new Switch(this);
        sw.setChecked(value);
        sw.setContentDescription(label);
        sw.setOnCheckedChangeListener((b, checked) -> onSet.accept(checked));
        line.addView(sw);
        r.addView(line, fullWidth());
        r.setOnClickListener(v -> sw.toggle());
    }

    private void valueRow(String label, String value, Runnable onClick) {
        LinearLayout r = row();
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.addView(text(label, 16, TEXT), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView v = text(value, 15, ACCENT);
        line.addView(v);
        r.addView(line, fullWidth());
        r.setOnClickListener(x -> onClick.run());
    }

    private void choice(String label, String[] options, int current, IntConsumer onPick) {
        valueRow(label, options[Math.max(0, Math.min(options.length - 1, current))], () ->
                new AlertDialog.Builder(this)
                        .setTitle(label)
                        .setSingleChoiceItems(options, current, (dlg, which) -> {
                            onPick.accept(which);
                            dlg.dismiss();
                            build();
                        })
                        .setNegativeButton("Cancel", null)
                        .show());
    }

    /** Slider; values are saved live so previews and StandBy update while you drag. */
    private void slider(String label, int min, int max, int value, java.util.function.IntFunction<String> fmt,
                        IntConsumer onSet) {
        LinearLayout r = row();
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.addView(text(label, 16, TEXT), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView val = text(fmt.apply(value), 15, SUB);
        line.addView(val);
        r.addView(line, fullWidth());
        SeekBar sb = new SeekBar(this);
        sb.setMin(min);
        sb.setMax(max);
        sb.setProgress(value);
        sb.setContentDescription(label);
        sb.setProgressTintList(ColorStateList.valueOf(ACCENT));
        sb.setThumbTintList(ColorStateList.valueOf(0xFFFFFFFF));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                val.setText(fmt.apply(progress));
                if (fromUser) onSet.accept(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
            }
        });
        LinearLayout.LayoutParams lp = fullWidth();
        lp.topMargin = dp(4);
        r.addView(sb, lp);
    }

    private static java.util.function.IntFunction<String> pct() {
        return v -> v + "%";
    }

    private void segmented(String label, String[] options, int selected, IntConsumer onPick) {
        LinearLayout r = row();
        if (label != null) {
            TextView t = text(label, 16, TEXT);
            t.setPadding(0, 0, 0, dp(8));
            r.addView(t);
        }
        LinearLayout seg = new LinearLayout(this);
        seg.setOrientation(LinearLayout.HORIZONTAL);
        seg.setBackground(rounded(CARD2, 12));
        seg.setPadding(dp(3), dp(3), dp(3), dp(3));
        for (int i = 0; i < options.length; i++) {
            final int idx = i;
            TextView b = text(options[i], 14, TEXT);
            b.setGravity(Gravity.CENTER);
            b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            b.setMinHeight(dp(38));
            if (i == selected) b.setBackground(rounded(0xFF636366, 9));
            b.setContentDescription(options[i] + (i == selected ? ", selected" : ""));
            b.setOnClickListener(v -> {
                onPick.accept(idx);
                build();
            });
            seg.addView(b, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        r.addView(seg, fullWidth());
    }

    private void swatches(String label, int[][] colors, String[] names, int selected, IntConsumer onPick) {
        LinearLayout r = row();
        TextView t = text(label, 16, TEXT);
        t.setPadding(0, 0, 0, dp(10));
        r.addView(t);
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < colors.length; i++) {
            final int idx = i;
            View sw = new View(this);
            GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors[i]);
            g.setShape(GradientDrawable.OVAL);
            g.setStroke(dp(2.5f), i == selected ? 0xFFFFFFFF : 0x00000000);
            sw.setBackground(g);
            sw.setContentDescription(names[i] + (i == selected ? ", selected" : ""));
            sw.setOnClickListener(v -> {
                onPick.accept(idx);
                build();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(44), dp(44));
            lp.rightMargin = dp(12);
            line.addView(sw, lp);
        }
        r.addView(line);
    }

    private void button(String label, int color, Runnable action) {
        LinearLayout r = row();
        TextView tv = text(label, 16, color);
        r.addView(tv);
        r.setOnClickListener(v -> action.run());
    }

    private void button(String label, Runnable action) {
        button(label, ACCENT, action);
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

    /** Ordered list with a switch per item and up/down arrows for the shown ones. */
    private void orderList(String[] names, int[] enabled, String key, int max) {
        List<Integer> on = new ArrayList<>();
        for (int v : enabled) on.add(v);
        List<Integer> all = new ArrayList<>(on);
        for (int i = 0; i < names.length; i++) if (!all.contains(i)) all.add(i);
        section(null);
        for (int id : all) {
            boolean isOn = on.contains(id);
            LinearLayout r = row();
            r.setPadding(dp(16), dp(4), dp(8), dp(4));
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setGravity(Gravity.CENTER_VERTICAL);
            line.setMinimumHeight(dp(48));
            line.addView(text(names[id], 16, isOn ? TEXT : SUB),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            if (isOn) {
                int idx = on.indexOf(id);
                line.addView(arrow("↑", "Move " + names[id] + " up", idx > 0, () -> {
                    List<Integer> nl = new ArrayList<>(on);
                    nl.remove(idx);
                    nl.add(idx - 1, id);
                    s.putList(key, toArray(nl));
                    build();
                }));
                line.addView(arrow("↓", "Move " + names[id] + " down", idx < on.size() - 1, () -> {
                    List<Integer> nl = new ArrayList<>(on);
                    nl.remove(idx);
                    nl.add(idx + 1, id);
                    s.putList(key, toArray(nl));
                    build();
                }));
            }
            Switch sw = new Switch(this);
            sw.setChecked(isOn);
            sw.setContentDescription("Show " + names[id]);
            sw.setOnCheckedChangeListener((b, checked) -> {
                List<Integer> nl = new ArrayList<>(on);
                if (checked && !nl.contains(id)) {
                    if (nl.size() >= max) {
                        b.setChecked(false);
                        Toast.makeText(this, "Up to " + max, Toast.LENGTH_SHORT).show();
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
            line.addView(sw);
            r.addView(line, fullWidth());
        }
    }

    private View arrow(String label, String desc, boolean enabled, Runnable action) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(18);
        b.setTextColor(enabled ? TEXT : 0x33FFFFFF);
        b.setBackgroundColor(Color.TRANSPARENT);
        b.setEnabled(enabled);
        b.setContentDescription(desc);
        b.setMinWidth(0);
        b.setMinimumWidth(dp(40));
        b.setPadding(dp(8), 0, dp(8), 0);
        b.setOnClickListener(v -> action.run());
        return b;
    }

    private static int[] toArray(List<Integer> l) {
        int[] r = new int[l.size()];
        for (int i = 0; i < r.length; i++) r[i] = l.get(i);
        return r;
    }

    // ================================================================ pages

    private void build() {
        int y = scroll.getScrollY();
        root.removeAllViews();
        previewHolder.removeAllViews();
        previewHolder.setVisibility(View.GONE);
        switch (page) {
            case GLASS: buildGlass(); break;
            case FACES: buildFaces(); break;
            case SCREEN: buildScreen(); break;
            case CAL: buildCalendar(); break;
            case TODO: buildTodo(); break;
            case WEATHER: buildWeather(); break;
            case WORLD: buildWorld(); break;
            case MUSIC: buildMusic(); break;
            case POMO: buildPomodoro(); break;
            case PRIVACY: buildPrivacy(); break;
            case AUTO: buildAuto(); break;
            case ABOUT: buildAbout(); break;
            default: buildHome(); break;
        }
        if (!HOME.equals(page)) scroll.post(() -> scroll.scrollTo(0, y));
    }

    // ---------------------------------------------------------------- home

    private static final class Entry {
        final String label, value, page;
        final int color, icon;

        Entry(String label, String value, int color, int icon, String page) {
            this.label = label;
            this.value = value;
            this.color = color;
            this.icon = icon;
            this.page = page;
        }
    }

    private String hours(int a, int b) {
        return Settings.hourName(a) + " – " + Settings.hourName(b);
    }

    private List<Object> entries() {
        List<Object> g = new ArrayList<>();
        String bg = s.bgSource == Settings.BG_FLOW ? LiquidGlass.PALETTE_NAMES[s.glassPalette] + " flow"
                : (s.bgSource == Settings.BG_WALLPAPER ? Settings.WP_NAMES[s.wallSrc] : "Black");
        g.add("LOOK");
        g.add(new Entry("Glass & wallpaper", (s.glass ? "Liquid Glass" : "Solid cards") + " · " + bg
                + (s.bgBlur > 0 ? " · blur " + s.bgBlur + "%" : ""), 0xFF0A84FF, R.drawable.ic_set_glass, GLASS));
        g.add(new Entry("Clock faces & widgets", s.faces.length + " faces · " + s.stacks[0].length + " + "
                + s.stacks[1].length + " widgets", 0xFFFF9F0A, R.drawable.ic_set_clock, FACES));
        g.add("DISPLAY");
        g.add(new Entry("Screen & burn-in", Settings.KEEP_NAMES[s.keepOn] + " · "
                + (s.brightness < 0 ? "system brightness" : s.brightness + "% brightness")
                + (s.bedside ? " · bedside " + hours(s.bedStart, s.bedEnd) : ""), 0xFF30D158, R.drawable.ic_set_screen, SCREEN));
        g.add("YOUR DAY");
        g.add(new Entry("Calendar & Teams", (Agenda.hasPermission(this) ? (s.calendarIds.isEmpty() ? "All calendars" : "Selected calendars")
                : "Calendar access needed") + (s.calendarTodayOnly ? " · only today" : ""), 0xFF5B5FC7, R.drawable.ic_set_calendar, CAL));
        int open = new Todo(this, d).openCount();
        g.add(new Entry("To-do", open == 0 ? "No open tasks" : open + (open == 1 ? " task left" : " tasks left"),
                0xFFFF453A, R.drawable.ic_set_check, TODO));
        g.add(new Entry("Weather", s.weatherCity.isEmpty() ? "Set your city" : s.weatherCity, 0xFF64D2FF, R.drawable.ic_set_cloud, WEATHER));
        StringBuilder cities = new StringBuilder();
        for (int i = 0; i < s.cities.length; i++) {
            if (i > 0) cities.append(", ");
            cities.append(WorldCities.NAMES[s.cities[i]]);
        }
        g.add(new Entry("World clock", cities.toString(), 0xFF8E8E93, R.drawable.ic_set_globe, WORLD));
        g.add("MEDIA & FOCUS");
        g.add(new Entry("Now Playing", (NowPlaying.hasAccess(this) ? "Allowed" : "Access needed")
                + (s.autoNowPlaying ? " · switches when music starts" : ""), 0xFF1DB954, R.drawable.ic_set_music, MUSIC));
        g.add(new Entry("Pomodoro", s.pomoFocus + " / " + s.pomoShort + " / " + s.pomoLong + " min · "
                + s.pomoRounds + " sessions", 0xFFFF6347, R.drawable.ic_set_timer, POMO));
        g.add("SYSTEM");
        g.add(new Entry("Permissions & privacy", "Calendar " + (Agenda.hasPermission(this) ? "✓" : "–")
                + " · Music " + (NowPlaying.hasAccess(this) ? "✓" : "–")
                + " · Photos " + (PhotoSlideshow.hasPermission(this) ? "✓" : "–"), 0xFF636366, R.drawable.ic_set_shield, PRIVACY));
        g.add(new Entry("Start automatically", "Screen saver while charging", 0xFF30D158, R.drawable.ic_set_bolt, AUTO));
        g.add(new Entry("About", "Version " + versionName() + " · check for updates", 0xFF48484A, R.drawable.ic_set_info, ABOUT));
        return g;
    }

    private void buildHome() {
        buildHeader("Settings", false);

        LinearLayout searchBox = new LinearLayout(this);
        searchBox.setOrientation(LinearLayout.HORIZONTAL);
        searchBox.setGravity(Gravity.CENTER_VERTICAL);
        searchBox.setBackground(rounded(CARD, 14));
        searchBox.setPadding(dp(12), 0, dp(12), 0);
        ImageView si = new ImageView(this);
        si.setImageResource(R.drawable.ic_set_search);
        si.setImageTintList(ColorStateList.valueOf(SUB));
        searchBox.addView(si, new LinearLayout.LayoutParams(dp(18), dp(18)));
        EditText search = new EditText(this);
        search.setHint("Search settings");
        search.setText(query);
        search.setTextColor(TEXT);
        search.setHintTextColor(SUB);
        search.setBackground(null);
        search.setSingleLine(true);
        search.setTextSize(16);
        search.setPadding(dp(10), dp(12), dp(4), dp(12));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence c, int a, int b, int e) { }
            @Override public void onTextChanged(CharSequence c, int a, int b, int e) { }
            @Override
            public void afterTextChanged(Editable e) {
                query = e.toString();
                fillHomeList();
            }
        });
        searchBox.addView(search, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams slp = fullWidth();
        slp.bottomMargin = dp(12);
        root.addView(searchBox, slp);

        // quick toggles
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout r1 = new LinearLayout(this), r2 = new LinearLayout(this);
        r1.setOrientation(LinearLayout.HORIZONTAL);
        r2.setOrientation(LinearLayout.HORIZONTAL);
        r1.addView(tile("Liquid Glass", s.glass ? "On" : "Off", s.glass, 0xFF64D2FF, R.drawable.ic_set_glass,
                () -> s.putBool("glass", !s.glass)), tileLp(true));
        r1.addView(tile("Always on", s.keepOn == Settings.KEEP_ALWAYS ? "Screen stays on" : Settings.KEEP_NAMES[s.keepOn],
                s.keepOn == Settings.KEEP_ALWAYS, 0xFFFFD60A, R.drawable.ic_set_awake,
                () -> s.putInt("keepOn", s.keepOn == Settings.KEEP_ALWAYS ? Settings.KEEP_CHARGING : Settings.KEEP_ALWAYS)), tileLp(false));
        r2.addView(tile("Night mode", s.nightAuto ? "Auto (dark room)" : "Off", s.nightAuto, 0xFFFF6961, R.drawable.ic_set_moon,
                () -> s.putBool("nightAuto", !s.nightAuto)), tileLp(true));
        r2.addView(tile("Bedside", s.bedside ? hours(s.bedStart, s.bedEnd) : "Off", s.bedside, 0xFFBF5AF2, R.drawable.ic_set_bed,
                () -> s.putBool("bedside", !s.bedside)), tileLp(false));
        grid.addView(r1, fullWidth());
        LinearLayout.LayoutParams r2lp = fullWidth();
        r2lp.topMargin = dp(10);
        grid.addView(r2, r2lp);
        root.addView(grid, fullWidth());

        homeList = new LinearLayout(this);
        homeList.setOrientation(LinearLayout.VERTICAL);
        root.addView(homeList, fullWidth());
        fillHomeList();
    }

    private LinearLayout.LayoutParams tileLp(boolean left) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(78), 1f);
        if (left) lp.rightMargin = dp(5);
        else lp.leftMargin = dp(5);
        return lp;
    }

    private View tile(String label, String state, boolean on, int color, int icon, Runnable toggle) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setBackground(rounded(on ? color : CARD, 20));
        t.setPadding(dp(14), dp(10), dp(12), dp(10));
        int fg = on ? 0xFF000000 : TEXT;
        ImageView iv = new ImageView(this);
        iv.setImageResource(icon);
        iv.setImageTintList(ColorStateList.valueOf(fg));
        t.addView(iv, new LinearLayout.LayoutParams(dp(22), dp(22)));
        View spacer = new View(this);
        t.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1f));
        TextView l = text(label, 15, fg);
        l.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.addView(l);
        TextView st = text(state, 12, on ? 0xB3000000 : SUB);
        st.setSingleLine(true);
        t.addView(st);
        t.setContentDescription(label + ", " + (on ? "on" : "off"));
        t.setOnClickListener(v -> {
            toggle.run();
            build();
        });
        return t;
    }

    private void fillHomeList() {
        if (homeList == null) return;
        homeList.removeAllViews();
        String q = query.trim().toLowerCase(Locale.getDefault());
        List<Object> all = entries();
        String pendingTitle = null;
        LinearLayout group = null;
        int shown = 0;
        for (Object o : all) {
            if (o instanceof String) {
                pendingTitle = (String) o;
                group = null;
                continue;
            }
            Entry e = (Entry) o;
            if (!q.isEmpty() && !(e.label + " " + e.value + " " + keywords(e.page)).toLowerCase(Locale.getDefault()).contains(q)) continue;
            if (group == null) {
                TextView tv = text(pendingTitle, 13, SUB);
                tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                tv.setLetterSpacing(0.06f);
                tv.setPadding(dp(14), dp(18), dp(14), dp(6));
                homeList.addView(tv);
                group = new LinearLayout(this);
                group.setOrientation(LinearLayout.VERTICAL);
                group.setBackground(rounded(CARD, 18));
                homeList.addView(group, fullWidth());
            } else {
                View div = new View(this);
                div.setBackgroundColor(DIV);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1);
                lp.leftMargin = dp(58);
                group.addView(div, lp);
            }
            group.addView(entryRow(e), fullWidth());
            shown++;
        }
        if (shown == 0) {
            TextView tv = text("No settings match “" + query + "”", 15, SUB);
            tv.setPadding(dp(14), dp(24), dp(14), 0);
            homeList.addView(tv);
        }
    }

    /** Extra words so search finds things inside pages. */
    private static String keywords(String page) {
        switch (page) {
            case GLASS: return "blur wallpaper background refraction frost rim tint bing photo image palette darken liquid";
            case FACES: return "faces widgets stack order layout placement swap views";
            case SCREEN: return "brightness always on keep awake dim double tap burn drift bedside night sleep dark hours battery charging";
            case CAL: return "teams outlook meetings events today";
            case MUSIC: return "spotify youtube music song mini player";
            case POMO: return "focus break timer alerts";
            case PRIVACY: return "permissions lock screen hide";
            default: return "";
        }
    }

    private View entryRow(Entry e) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(14), dp(10), dp(14), dp(10));
        FrameLayout ic = new FrameLayout(this);
        ic.setBackground(rounded(e.color, 8));
        ImageView iv = new ImageView(this);
        iv.setImageResource(e.icon);
        FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER);
        ic.addView(iv, ilp);
        r.addView(ic, new LinearLayout.LayoutParams(dp(30), dp(30)));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(14), 0, dp(8), 0);
        texts.addView(text(e.label, 16, TEXT));
        TextView v = text(e.value, 13, SUB);
        v.setSingleLine(true);
        v.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(v);
        r.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        r.addView(text("›", 22, 0xFF5A5A5F));
        r.setContentDescription(e.label + ". " + e.value);
        r.setOnClickListener(x -> go(e.page));
        return r;
    }

    // ---------------------------------------------------------------- glass & wallpaper

    private static final int[][] PALETTE_SWATCHES = {
            {0xFF4A5A78, 0xFF2E5A88, 0xFF6B4E71}, {0xFF0E6E8C, 0xFF1F3F8F, 0xFF2FA39A},
            {0xFF3F7A5E, 0xFF6A8F4E, 0xFF2C5A6B}, {0xFF8A5A3B, 0xFFB0844F, 0xFF5A3E5A},
            {0xFF5B3B8C, 0xFFA0466E, 0xFF2D4D8F},
    };

    private void buildGlass() {
        buildHeader("Glass & wallpaper", true);
        GlassPreview pv = new GlassPreview(this, s);
        previewHolder.addView(pv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(170)));
        previewHolder.setVisibility(View.VISIBLE);

        section("BACKGROUND");
        segmented(null, new String[]{"Flowing", "Wallpaper", "Black"}, s.bgSource, v -> s.putInt("bgSource", v));
        if (s.bgSource == Settings.BG_FLOW) {
            swatches("Colours", PALETTE_SWATCHES, LiquidGlass.PALETTE_NAMES, s.glassPalette, v -> s.putInt("glassPalette", v));
        } else if (s.bgSource == Settings.BG_WALLPAPER) {
            segmented("Wallpaper", new String[]{"Bing daily", "Random", "My image"}, s.wallSrc - 1, v -> {
                s.putInt("wallSrc", v + 1);
                if (v + 1 == Settings.WP_OWN && !Wallpaper.ownFile(this).exists()) pickImage();
                else Wallpaper.requestNew(this);
            });
            if (s.wallSrc == Settings.WP_OWN) {
                button(Wallpaper.ownFile(this).exists() ? "Choose a different image" : "Choose an image", this::pickImage);
            } else {
                choice("Change wallpaper", Settings.WP_MINUTE_NAMES, s.wallpaperInterval, v -> s.putInt("wpInterval", v));
                button("Get a new wallpaper now", () -> {
                    Wallpaper.requestNew(this);
                    Toast.makeText(this, "A new wallpaper will load in a moment", Toast.LENGTH_SHORT).show();
                });
            }
        }
        if (s.bgSource != Settings.BG_BLACK) {
            slider("Background blur", 0, 100, s.bgBlur, pct(), v -> s.putInt("bgBlur", v));
            slider("Darken", 0, 90, s.wallpaperDim, pct(), v -> s.putInt("wpDim", v));
        }

        section("LIQUID GLASS");
        toggle("Liquid Glass panels", "Also a Quick Settings tile", s.glass, checked -> {
            s.putBool("glass", checked);
            build();
        });
        if (s.glass) {
            slider("Glass blur", 0, 100, s.glassBlur, pct(), v -> s.putInt("glassBlur", v));
            if (Build.VERSION.SDK_INT >= 33) {
                slider("Edge bend (refraction)", 0, 100, s.glassRefr, pct(), v -> s.putInt("glassRefr", v));
            }
            slider("Frost", 0, 100, s.glassFrost, pct(), v -> s.putInt("glassFrost", v));
            slider("Rim light", 0, 100, s.glassRim, pct(), v -> s.putInt("glassRim", v));
            slider("Corner roundness", 8, 44, s.glassRadius, v -> v + " dp", v -> s.putInt("glassRadius", v));
            int[][] tints = {{0xFFFFFFFF, 0xFF8E8E93}, {0xFF64AAFF, 0xFF64AAFF}, {0xFFFFAA5A, 0xFFFFAA5A},
                    {0xFFFF78AA, 0xFFFF78AA}, {0xFF6EE6BE, 0xFF6EE6BE}};
            swatches("Tint", tints, Settings.TINT_NAMES, s.glassTint, v -> s.putInt("glassTint", v));
        }
        if (s.bgSource == Settings.BG_WALLPAPER) {
            note("Tip: a still wallpaper doesn't move, so keep Pixel drift on (Screen & burn-in) and let the "
                    + "wallpaper change regularly.");
        }
        note("Wallpapers are downloaded over HTTPS only from bing.com or picsum.photos. Your own image is "
                + "copied into StandBy and re-saved without location data.");
        section(null);
        button("Reset look to defaults", 0xFFFF453A, () -> {
            s.resetLook();
            build();
        });
    }

    @SuppressWarnings("deprecation")
    private void pickImage() {
        Intent i;
        if (Build.VERSION.SDK_INT >= 33) {
            i = new Intent(MediaStore.ACTION_PICK_IMAGES); // system photo picker: no permission needed
        } else {
            i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*");
        }
        try {
            startActivityForResult(i, REQ_PICK_IMAGE);
        } catch (Exception e) {
            Toast.makeText(this, "No image picker available", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK_IMAGE || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        Toast.makeText(this, "Setting wallpaper…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            boolean ok = Wallpaper.saveOwn(this, uri);
            runOnUiThread(() -> {
                if (ok) {
                    s.sp.edit().putInt("bgSource", Settings.BG_WALLPAPER).putInt("wallSrc", Settings.WP_OWN).apply();
                    s.load();
                    build();
                } else {
                    Toast.makeText(this, "Couldn't use that image", Toast.LENGTH_LONG).show();
                }
            });
        }, "own-wallpaper").start();
    }

    // ---------------------------------------------------------------- faces & widgets

    private void buildFaces() {
        buildHeader("Faces & widgets", true);
        section(null);
        int sel = "faces".equals(facesTab) ? 0 : ("left".equals(facesTab) ? 1 : 2);
        segmented(null, new String[]{"Clock faces", "Left stack", "Right stack"}, sel,
                v -> facesTab = v == 0 ? "faces" : (v == 1 ? "left" : "right"));
        if ("faces".equals(facesTab)) {
            note(s.faces.length + " faces shown · swipe up/down on StandBy in this order. Long-press on StandBy "
                    + "to change a face's colour.");
            orderList(ClockFaces.NAMES, s.faces, "faces", ClockFaces.COUNT);
        } else if ("left".equals(facesTab)) {
            note("Left card · swipe up/down on it to change widget.");
            orderList(Widgets.NAMES, s.stacks[0], "stackItems0", Widgets.COUNT);
        } else {
            note("Right card · swipe up/down on it to change widget.");
            orderList(Widgets.NAMES, s.stacks[1], "stackItems1", Widgets.COUNT);
        }
        if (!"faces".equals(facesTab)) {
            section(null);
            button("⇄  Swap left and right stacks", () -> {
                int[] a = s.stacks[0], b = s.stacks[1];
                s.putList("stackItems0", b);
                s.putList("stackItems1", a);
                build();
            });
        }
        TextView vt = text("VIEWS (SWIPE LEFT / RIGHT)", 13, SUB);
        vt.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        vt.setLetterSpacing(0.06f);
        vt.setPadding(dp(14), dp(22), dp(14), 0);
        root.addView(vt);
        orderList(Settings.PAGE_NAMES, s.pages, "pages", 3);
    }

    // ---------------------------------------------------------------- screen & burn-in

    private void buildScreen() {
        buildHeader("Screen & burn-in", true);
        section("SCREEN");
        segmented("Keep screen on", new String[]{"Always", "Charging", "System"}, s.keepOn, v -> s.putInt("keepOn", v));
        slider("Brightness", 0, 100, Math.max(0, s.brightness), v -> v == 0 ? "System" : v + "%",
                v -> s.putInt("brightness", v == 0 ? -1 : v));
        toggle("Double-tap to dim", "Tap twice on an empty spot", s.doubleTapDim, "doubleTapDim");
        toggle("Battery level", "Icon and % in the corner", s.showBattery, "showBattery");
        toggle("Charging animation", "When you plug in", s.chargeAnim, "chargeAnim");

        section("BURN-IN PROTECTION");
        toggle("Pixel drift", "Moves everything up to ~24 px, new path every hour", s.burnIn, "burnIn");
        toggle("Softer whites", "86% white — less wear, hard to notice", s.softWhites, "softWhites");
        toggle("Fade extras", "Battery badge and mini-player hide after 30 s", s.fadeExtras, "fadeExtras");
        slider("Auto-dim after", 0, 30, s.autoDim ? s.autoDimMinutes : 0, v -> v == 0 ? "Never" : v + " min", v -> {
            if (v == 0) {
                s.putBool("autoDim", false);
            } else {
                s.sp.edit().putBoolean("autoDim", true).putInt("autoDimMinutes", v).apply();
                s.load();
            }
        });
        choice("Rotate clock faces", Settings.ROTATE_NAMES, s.faceRotate, v -> s.putInt("faceRotate", v));

        section("NIGHT");
        toggle("Night mode", "Everything turns dim red in a dark room", s.nightAuto, "nightAuto");
        toggle("Bedside clock", "Outline digits, about 3% of pixels lit", s.bedside, checked -> {
            s.putBool("bedside", checked);
            build();
        });
        if (s.bedside) {
            choice("Bedside from", Settings.hourNames(), s.bedStart, v -> s.putInt("bedStart", v));
            choice("Bedside until", Settings.hourNames(), s.bedEnd, v -> s.putInt("bedEnd", v));
            choice("Bedside colour", Settings.INK_NAMES, s.bedInk, v -> s.putInt("bedInk", v));
        }
        toggle("Dark hours", "Screen fully off — the strongest protection", s.sleepWindow, checked -> {
            s.putBool("sleepWindow", checked);
            build();
        });
        if (s.sleepWindow) {
            choice("Dark from", Settings.hourNames(), s.sleepStart, v -> s.putInt("sleepStart", v));
            choice("Dark until", Settings.hourNames(), s.sleepEnd, v -> s.putInt("sleepEnd", v));
        }
        note("Touching the screen during Bedside or dark hours wakes StandBy for a minute.");
    }

    // ---------------------------------------------------------------- your day

    private void buildCalendar() {
        buildHeader("Calendar & Teams", true);
        section(null);
        if (!Agenda.hasPermission(this)) {
            button("Allow calendar access", () -> requestPermissions(
                    new String[]{android.Manifest.permission.READ_CALENDAR}, 2));
        } else {
            valueRow("Calendars", s.calendarIds.isEmpty() ? "All" : "Selected", this::pickCalendars);
        }
        toggle("Only today's events", "Hide tomorrow; the widget title says TODAY", s.calendarTodayOnly, "calTodayOnly");
        toggle("Hide details when locked", "Shows “Teams meeting” instead of titles", s.hideDetailsLocked, "hideDetailsLocked");
        note("Teams meetings live in your work Outlook calendar. In the Outlook app: Settings → your account → "
                + "turn on “Sync calendars”, then pick that calendar above. Some companies block this. "
                + "StandBy only reads events; nothing leaves your phone. “Join Teams” appears 15 minutes before.");
    }

    private void buildTodo() {
        buildHeader("To-do", true);
        section(null);
        button("Open to-do list", () -> startActivity(new Intent(this, TodoActivity.class)));
        toggle("Hide tasks when locked", "Shows only how many are left", s.hideDetailsLocked, "hideDetailsLocked");
        note("Tasks are stored only on this phone. Tap a task on the widget to tick it off; tap + to add one.");
    }

    private void buildWeather() {
        buildHeader("Weather", true);
        section(null);
        valueRow("City", s.weatherCity.isEmpty() ? "Choose" : s.weatherCity, this::askCity);
        note("From Open-Meteo: free, no account, no location permission. Only the city's rounded coordinates are sent.");
    }

    private void buildWorld() {
        buildHeader("World clock", true);
        note("Pick up to " + WorldCities.MAX + " cities for the World clock widget and face.");
        orderList(WorldCities.NAMES, s.cities, "cities", WorldCities.MAX);
    }

    // ---------------------------------------------------------------- media & focus

    private void buildMusic() {
        buildHeader("Now Playing", true);
        boolean access = NowPlaying.hasAccess(this);
        section(null);
        LinearLayout st = row();
        st.addView(text(access ? "✓ Notification access allowed" : "Notification access needed", 16,
                access ? 0xFF30D158 : 0xFFFF9F0A));
        st.addView(text("Needed to see the current song from Spotify, YouTube Music or any player. "
                + "StandBy ignores all notification contents.", 12.5f, SUB));
        if (!access) {
            button("Allow notification access", () ->
                    open(new Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
            button("Open App info", () -> open(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", getPackageName(), null))));
        }
        section(null);
        toggle("Switch to Now Playing when music starts", "Only after a pause — not on every song change. "
                + "Swipe away and it leaves you alone.", s.autoNowPlaying, "autoNowPlaying");
        toggle("Mini player on clock faces", "The playing song at the bottom", s.nowPlayingPill, "nowPlayingPill");
        if (!access) note("Switch greyed out? App info → ⋮ → “Allow restricted settings”, then try again.");
    }

    private void buildPomodoro() {
        buildHeader("Pomodoro", true);
        section("TIMES");
        slider("Focus", 5, 90, s.pomoFocus, v -> v + " min", v -> s.putInt("pomoFocus", v));
        slider("Short break", 1, 30, s.pomoShort, v -> v + " min", v -> s.putInt("pomoShort", v));
        slider("Long break", 5, 60, s.pomoLong, v -> v + " min", v -> s.putInt("pomoLong", v));
        slider("Sessions before a long break", 2, 8, s.pomoRounds, String::valueOf, v -> s.putInt("pomoRounds", v));
        section("ALERTS");
        toggle("Start the next session automatically", null, s.pomoAutoStart, "pomoAutoStart");
        toggle("Sound when a session ends", "It always vibrates", s.pomoSound, "pomoSound");
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            button("Allow alerts (notifications)", () -> requestPermissions(
                    new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 3));
        } else {
            note("✓ Alerts work even when StandBy is closed or the screen is off.");
        }
    }

    // ---------------------------------------------------------------- system

    private void buildPrivacy() {
        buildHeader("Permissions & privacy", true);
        section("ON THE LOCK SCREEN");
        toggle("Hide calendar & to-do details", null, s.hideDetailsLocked, "hideDetailsLocked");
        toggle("Hide photos", "Only matters if the Photos view is on", s.hidePhotosLocked, "hidePhotosLocked");
        section("PERMISSIONS");
        valueRow("Calendar", Agenda.hasPermission(this) ? "Allowed" : "Allow", () -> {
            if (!Agenda.hasPermission(this)) requestPermissions(new String[]{android.Manifest.permission.READ_CALENDAR}, 2);
        });
        valueRow("Notification access (music)", NowPlaying.hasAccess(this) ? "Allowed" : "Allow", () ->
                open(new Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        valueRow("Photos", PhotoSlideshow.hasPermission(this) ? "Allowed" : "Not needed", () -> {
            if (!PhotoSlideshow.hasPermission(this)) requestPermissions(PhotoSlideshow.permissions(), 1);
        });
        button("Open App info", () -> open(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", getPackageName(), null))));
        note("No ads, no analytics, no accounts. Internet is used only for weather and wallpapers, over HTTPS to "
                + "a fixed list of sites. Backups of StandBy's data are turned off.");
    }

    private void buildAuto() {
        buildHeader("Start automatically", true);
        section(null);
        button("Open Screen saver settings", () -> open(new Intent(android.provider.Settings.ACTION_DREAM_SETTINGS)));
        note("Choose StandBy as your Screen saver and set it to start while charging. It then opens by itself "
                + "whenever the phone charges and the screen would turn off. You can also add the “StandBy” "
                + "and “Liquid Glass” Quick Settings tiles.");
    }

    private void buildAbout() {
        buildHeader("About", true);
        section(null);
        valueRow("Version", versionName(), () -> { });
        button("Check for updates", this::checkForUpdates);
        button("Show the welcome tips again", () -> {
            s.putBool("helpSeen", false);
            finish();
        });
        button("Security notes", () -> open(new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://github.com/SiddheshDhomse/standby-android/blob/main/SECURITY.md"))));
        note("Updates install over this version (keeping your settings) as long as they are signed with the same key.");
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    private long versionCode() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).getLongVersionCode();
        } catch (Exception e) {
            return 0;
        }
    }

    private static final String RELEASE_API = "https://api.github.com/repos/SiddheshDhomse/standby-android/releases/latest";
    private static final String APK_URL = "https://github.com/SiddheshDhomse/standby-android/releases/latest/download/StandBy.apk";

    /** Asks GitHub for the latest release; if newer, offers to download it (Android checks the signature on install). */
    private void checkForUpdates() {
        Toast.makeText(this, "Checking\u2026", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            String tag = null;
            try {
                byte[] b = Net.get(RELEASE_API, 512 * 1024, new String[]{"api.github.com"}, "application/json");
                tag = new org.json.JSONObject(new String(b, java.nio.charset.StandardCharsets.UTF_8)).optString("tag_name", null);
            } catch (Exception ignored) {
            }
            final String latest = tag;
            runOnUiThread(() -> {
                if (latest == null) {
                    Toast.makeText(this, "Couldn't reach GitHub", Toast.LENGTH_LONG).show();
                    return;
                }
                long latestBuild = -1;
                try {
                    latestBuild = Long.parseLong(latest.substring(latest.lastIndexOf('.') + 1));
                } catch (Exception ignored) {
                }
                long myBuild = versionCode() - 100;
                if (latestBuild > myBuild) {
                    new AlertDialog.Builder(this)
                            .setTitle("Update available")
                            .setMessage("StandBy " + latest + " is ready (you have " + versionName() + ").\n\n"
                                    + "Download it, then tap the file to install. Your settings are kept.")
                            .setPositiveButton("Download", (dlg, w) -> open(new Intent(Intent.ACTION_VIEW, Uri.parse(APK_URL))))
                            .setNegativeButton("Later", null)
                            .show();
                } else {
                    Toast.makeText(this, "You're up to date (" + versionName() + ")", Toast.LENGTH_LONG).show();
                }
            });
        }, "update-check").start();
    }

    // ---------------------------------------------------------------- dialogs

    private void pickCalendars() {
        List<Agenda.Cal> cals = Agenda.listCalendars(this);
        if (cals.isEmpty()) {
            Toast.makeText(this, "No calendars found on this phone", Toast.LENGTH_LONG).show();
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
                    s.putString("calendarIds", n == cals.size() || n == 0 ? "" : b.toString());
                    build();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void askCity() {
        EditText in = new EditText(this);
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
                                Toast.makeText(this, "Couldn't find “" + q + "” (or no internet)",
                                        Toast.LENGTH_LONG).show();
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
