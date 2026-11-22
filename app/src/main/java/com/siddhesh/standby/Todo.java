package com.siddhesh.standby;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** A simple local to-do list (stored only on this phone). */
final class Todo {
    static final int MAX_ITEMS = 50;
    static final int MAX_LEN = 120;

    static final class Item {
        String text;
        boolean done;

        Item(String t, boolean d) {
            text = t;
            done = d;
        }
    }

    private final SharedPreferences sp;
    final List<Item> items = new ArrayList<>();

    // widget drawing state
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Rect tmp = new Rect();
    private final float d;
    final RectF[] addHits = {new RectF(), new RectF()};
    final List<List<RectF>> rowHits = new ArrayList<>();
    final List<List<Integer>> rowIndex = new ArrayList<>();

    Todo(Context ctx, float density) {
        sp = ctx.getSharedPreferences(Settings.FILE, Context.MODE_PRIVATE);
        d = density;
        for (int i = 0; i < 2; i++) {
            rowHits.add(new ArrayList<>());
            rowIndex.add(new ArrayList<>());
        }
        load();
    }

    void load() {
        items.clear();
        try {
            JSONArray a = new JSONArray(sp.getString("todos", "[]"));
            for (int i = 0; i < a.length() && i < MAX_ITEMS; i++) {
                JSONObject o = a.getJSONObject(i);
                items.add(new Item(o.optString("t", ""), o.optBoolean("d", false)));
            }
        } catch (Exception ignored) {
        }
    }

    void save() {
        JSONArray a = new JSONArray();
        try {
            for (Item it : items) a.put(new JSONObject().put("t", it.text).put("d", it.done));
        } catch (Exception ignored) {
        }
        sp.edit().putString("todos", a.toString()).apply();
    }

    boolean add(String text) {
        String t = text == null ? "" : text.trim().replaceAll("\\s+", " ");
        if (t.isEmpty() || items.size() >= MAX_ITEMS) return false;
        if (t.length() > MAX_LEN) t = t.substring(0, MAX_LEN);
        items.add(new Item(t, false));
        save();
        return true;
    }

    void toggle(int i) {
        if (i < 0 || i >= items.size()) return;
        items.get(i).done = !items.get(i).done;
        save();
    }

    void remove(int i) {
        if (i < 0 || i >= items.size()) return;
        items.remove(i);
        save();
    }

    void clearDone() {
        for (int i = items.size() - 1; i >= 0; i--) if (items.get(i).done) items.remove(i);
        save();
    }

    int openCount() {
        int n = 0;
        for (Item it : items) if (!it.done) n++;
        return n;
    }

    // ------------------------------------------------------------ widget

    void drawWidget(Canvas c, RectF r, int accent, boolean hideDetails, int stack) {
        List<RectF> hits = rowHits.get(stack);
        List<Integer> idx = rowIndex.get(stack);
        hits.clear();
        idx.clear();
        float pad = 22 * d;
        p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Paint.Align.LEFT);
        p.setTypeface(Ui.BOLD);
        p.setColor(accent);
        p.setTextSize(r.height() * 0.055f);
        float hy = r.top + pad + p.getTextSize();
        c.drawText("TO-DO", r.left + pad, hy, p);
        p.setTypeface(Ui.REGULAR);
        p.setColor(Ui.GRAY);
        int open = openCount();
        c.drawText(open == 0 ? "" : "  " + open + " left", r.left + pad + p.measureText("TO-DO") + 2 * d, hy, p);

        // add button
        float bs = Math.max(40 * d, r.height() * 0.11f);
        addHits[stack].set(r.right - pad - bs, r.top + pad * 0.6f, r.right - pad, r.top + pad * 0.6f + bs);
        p.setColor(0x26FFFFFF);
        c.drawCircle(addHits[stack].centerX(), addHits[stack].centerY(), bs * 0.42f, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(2.2f * d);
        p.setColor(0xFFFFFFFF);
        float ac = bs * 0.16f;
        c.drawLine(addHits[stack].centerX() - ac, addHits[stack].centerY(), addHits[stack].centerX() + ac, addHits[stack].centerY(), p);
        c.drawLine(addHits[stack].centerX(), addHits[stack].centerY() - ac, addHits[stack].centerX(), addHits[stack].centerY() + ac, p);
        p.setStyle(Paint.Style.FILL);

        if (items.isEmpty()) {
            p.setTypeface(Ui.MEDIUM);
            p.setColor(Ui.GRAY);
            Ui.fitWidth(p, "Tap + to add a task", r.width() * 0.8f, r.height() * 0.06f);
            Ui.textCenter(c, p, "Tap + to add a task", r.centerX(), r.centerY(), tmp);
            p.setTextAlign(Paint.Align.LEFT);
            return;
        }
        if (hideDetails) {
            p.setTypeface(Ui.LIGHT);
            p.setColor(0xFFFFFFFF);
            p.setTextSize(r.height() * 0.22f);
            Ui.textCenter(c, p, String.valueOf(open), r.centerX(), r.centerY(), tmp);
            p.setTypeface(Ui.MEDIUM);
            p.setColor(Ui.GRAY);
            p.setTextSize(r.height() * 0.05f);
            Ui.textCenter(c, p, open == 1 ? "task left · unlock to see it" : "tasks left · unlock to see them",
                    r.centerX(), r.centerY() + r.height() * 0.17f, tmp);
            p.setTextAlign(Paint.Align.LEFT);
            return;
        }

        // open items first, then done ones
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) if (!items.get(i).done) order.add(i);
        for (int i = 0; i < items.size(); i++) if (items.get(i).done) order.add(i);

        float top = r.top + pad + r.height() * 0.13f;
        float rowH = Math.max(44 * d, r.height() * 0.12f);
        float textSize = Math.min(r.height() * 0.055f, rowH * 0.42f);
        for (int k = 0; k < order.size(); k++) {
            float y0 = top + k * rowH;
            if (y0 + rowH > r.bottom - pad * 0.4f) {
                int more = order.size() - k;
                p.setTypeface(Ui.REGULAR);
                p.setColor(Ui.GRAY);
                p.setTextSize(textSize * 0.85f);
                c.drawText("+" + more + " more", r.left + pad, y0 + rowH * 0.6f, p);
                break;
            }
            int i = order.get(k);
            Item it = items.get(i);
            float cy = y0 + rowH / 2f;
            float cr = textSize * 0.55f;
            float cx = r.left + pad + cr;
            if (it.done) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(accent);
                c.drawCircle(cx, cy, cr, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(2f * d);
                p.setColor(0xFF000000);
                path.reset();
                path.moveTo(cx - cr * 0.45f, cy);
                path.lineTo(cx - cr * 0.1f, cy + cr * 0.35f);
                path.lineTo(cx + cr * 0.5f, cy - cr * 0.35f);
                c.drawPath(path, p);
            } else {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(1.6f * d);
                p.setColor(Ui.GRAY);
                c.drawCircle(cx, cy, cr, p);
            }
            p.setStyle(Paint.Style.FILL);
            p.setTypeface(Ui.REGULAR);
            p.setTextSize(textSize);
            p.setColor(it.done ? Ui.GRAY : 0xFFFFFFFF);
            p.setStrikeThruText(it.done);
            float tx = cx + cr + 12 * d;
            Ui.textMid(c, p, clip(it.text, r.right - pad - tx), tx, cy, tmp);
            p.setStrikeThruText(false);
            hits.add(new RectF(r.left, y0, r.right, y0 + rowH));
            idx.add(i);
        }
    }

    private String clip(String s, float maxW) {
        if (p.measureText(s) <= maxW) return s;
        int n = s.length();
        while (n > 1 && p.measureText(s, 0, n) + p.measureText("…") > maxW) n--;
        return s.substring(0, n) + "…";
    }

    /** Returns true if the tap was handled; sets wantsAdd when + was pressed. */
    boolean onTap(int stack, float x, float y, boolean[] wantsAdd) {
        if (addHits[stack].contains(x, y)) {
            wantsAdd[0] = true;
            return true;
        }
        List<RectF> hits = rowHits.get(stack);
        for (int k = 0; k < hits.size(); k++) {
            if (hits.get(k).contains(x, y)) {
                toggle(rowIndex.get(stack).get(k));
                return true;
            }
        }
        return false;
    }
}
