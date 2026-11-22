package com.siddhesh.standby;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.Locale;

/**
 * Weather from Open-Meteo (free, no account, no API key). Only the city's rounded coordinates
 * are sent; no location permission is used.
 */
final class Weather {
    private static final String[] HOSTS = {"api.open-meteo.com", "geocoding-api.open-meteo.com"};
    private static final long REFRESH_MS = 30 * 60_000L;

    private final Context ctx;
    private final Runnable onChange;
    private final SharedPreferences sp;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Rect tmp = new Rect();
    private final RectF rf = new RectF();
    private final float d;
    private boolean busy;
    private long lastAttempt;

    // parsed data
    boolean valid;
    String city = "";
    int temp, feels, code, hi, lo, rain, humidity;
    boolean isDay = true;
    String sunset = "";
    final int[] hTemp = new int[6];
    final int[] hCode = new int[6];
    final String[] hLabel = new String[6];
    final boolean[] hDay = new boolean[6];
    long updatedAt;

    Weather(Context ctx, Runnable onChange, float density) {
        this.ctx = ctx;
        this.onChange = onChange;
        this.d = density;
        sp = ctx.getSharedPreferences(Settings.FILE, Context.MODE_PRIVATE);
        String cached = sp.getString("wx_json", null);
        if (cached != null) {
            try {
                parse(cached, sp.getString("wx_city", ""));
                updatedAt = sp.getLong("wx_at", 0);
            } catch (Exception ignored) {
                valid = false;
            }
        }
    }

    boolean configured(Settings s) {
        return !Float.isNaN(s.weatherLat) && !Float.isNaN(s.weatherLon);
    }

    /** Cheap; call often. Fetches at most every 30 minutes (retries every 5 when failing). */
    void ensure(Settings s) {
        if (!configured(s) || busy) return;
        long now = System.currentTimeMillis();
        boolean cityChanged = !s.weatherCity.equals(sp.getString("wx_city", ""));
        if (!cityChanged && valid && now - updatedAt < REFRESH_MS && updatedAt <= now) return;
        if (now - lastAttempt < 5 * 60_000L && !cityChanged) return;
        lastAttempt = now;
        busy = true;
        final float lat = s.weatherLat, lon = s.weatherLon;
        final String name = s.weatherCity;
        new Thread(() -> {
            String json = null;
            try {
                String url = String.format(Locale.US,
                        "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f"
                                + "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,is_day"
                                + "&hourly=temperature_2m,weather_code,is_day"
                                + "&daily=temperature_2m_max,temperature_2m_min,sunset,precipitation_probability_max"
                                + "&timezone=auto&forecast_days=2", lat, lon);
                json = new String(Net.get(url, 512 * 1024, HOSTS, "application/json"), StandardCharsets.UTF_8);
            } catch (Exception ignored) {
            }
            final String result = json;
            main.post(() -> {
                busy = false;
                if (result != null) {
                    try {
                        parse(result, name);
                        updatedAt = System.currentTimeMillis();
                        sp.edit().putString("wx_json", result).putString("wx_city", name)
                                .putLong("wx_at", updatedAt).apply();
                        onChange.run();
                    } catch (Exception ignored) {
                    }
                }
            });
        }, "weather").start();
    }

    /** Blocking; call off the main thread. Returns {name, lat, lon} or null. */
    static String[] geocode(String query) {
        try {
            String q = URLEncoder.encode(query.trim(), "UTF-8");
            byte[] b = Net.get("https://geocoding-api.open-meteo.com/v1/search?count=1&language=en&format=json&name=" + q,
                    256 * 1024, HOSTS, "application/json");
            JSONObject o = new JSONObject(new String(b, StandardCharsets.UTF_8));
            JSONArray r = o.optJSONArray("results");
            if (r == null || r.length() == 0) return null;
            JSONObject c = r.getJSONObject(0);
            String name = c.getString("name");
            String country = c.optString("country", "");
            return new String[]{country.isEmpty() ? name : name + ", " + country,
                    String.valueOf(c.getDouble("latitude")), String.valueOf(c.getDouble("longitude"))};
        } catch (Exception e) {
            return null;
        }
    }

    private void parse(String json, String cityName) throws Exception {
        JSONObject o = new JSONObject(json);
        JSONObject cur = o.getJSONObject("current");
        temp = (int) Math.round(cur.getDouble("temperature_2m"));
        feels = (int) Math.round(cur.getDouble("apparent_temperature"));
        humidity = (int) Math.round(cur.getDouble("relative_humidity_2m"));
        code = cur.getInt("weather_code");
        isDay = cur.optInt("is_day", 1) == 1;
        JSONObject daily = o.getJSONObject("daily");
        hi = (int) Math.round(daily.getJSONArray("temperature_2m_max").getDouble(0));
        lo = (int) Math.round(daily.getJSONArray("temperature_2m_min").getDouble(0));
        rain = daily.getJSONArray("precipitation_probability_max").optInt(0, 0);
        String ss = daily.getJSONArray("sunset").optString(0, "");
        sunset = ss.length() >= 16 ? fmtHour(ss.substring(11, 16)) : "";
        JSONObject hourly = o.getJSONObject("hourly");
        JSONArray times = hourly.getJSONArray("time");
        JSONArray ht = hourly.getJSONArray("temperature_2m");
        JSONArray hc = hourly.getJSONArray("weather_code");
        JSONArray hd = hourly.getJSONArray("is_day");
        String nowIso = cur.getString("time"); // local time of the city, "2026-11-22T23:45"
        int start = 0;
        for (int i = 0; i < times.length(); i++) {
            if (times.getString(i).compareTo(nowIso) > 0) { start = i; break; }
        }
        for (int k = 0; k < 6; k++) {
            int i = Math.min(times.length() - 1, start + k);
            hTemp[k] = (int) Math.round(ht.getDouble(i));
            hCode[k] = hc.getInt(i);
            hDay[k] = hd.optInt(i, 1) == 1;
            String t = times.getString(i);
            hLabel[k] = t.length() >= 13 ? fmtHourShort(Integer.parseInt(t.substring(11, 13))) : "";
        }
        city = cityName;
        valid = true;
    }

    private static String fmtHour(String hhmm) {
        int h = Integer.parseInt(hhmm.substring(0, 2));
        String m = hhmm.substring(3, 5);
        int hh = h % 12 == 0 ? 12 : h % 12;
        return hh + ":" + m + (h < 12 ? " AM" : " PM");
    }

    private static String fmtHourShort(int h) {
        int hh = h % 12 == 0 ? 12 : h % 12;
        return hh + (h < 12 ? " AM" : " PM");
    }

    static String describe(int code) {
        if (code == 0) return "Clear";
        if (code <= 2) return "Partly cloudy";
        if (code == 3) return "Cloudy";
        if (code == 45 || code == 48) return "Fog";
        if (code >= 51 && code <= 57) return "Drizzle";
        if (code >= 61 && code <= 67) return "Rain";
        if (code >= 71 && code <= 77) return "Snow";
        if (code >= 80 && code <= 82) return "Showers";
        if (code == 85 || code == 86) return "Snow showers";
        if (code >= 95) return "Thunderstorm";
        return "—";
    }

    // ------------------------------------------------------------ icons

    private final RectF iconRect = new RectF();
    private final Path moonA = new Path(), moonB = new Path();

    /** Draws a weather icon. Leaves the paint in FILL style and touches no shared layout rects. */
    void icon(Canvas c, int code, boolean day, float cx, float cy, float s) {
        try {
            drawIcon(c, code, day, cx, cy, s);
        } finally {
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
        }
    }

    private void crescent(Canvas c, float x, float y, float r, int color) {
        moonA.reset();
        moonA.addCircle(x, y, r, Path.Direction.CW);
        moonB.reset();
        moonB.addCircle(x + r * 0.45f, y - r * 0.3f, r * 0.85f, Path.Direction.CW);
        moonA.op(moonB, Path.Op.DIFFERENCE); // a real crescent: transparent bite, works on any background
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        c.drawPath(moonA, p);
    }

    private void drawIcon(Canvas c, int code, boolean day, float cx, float cy, float s) {
        boolean clear = code == 0, partly = code == 1 || code == 2;
        boolean fog = code == 45 || code == 48;
        boolean rainy = (code >= 51 && code <= 67) || (code >= 80 && code <= 82);
        boolean snowy = (code >= 71 && code <= 77) || code == 85 || code == 86;
        boolean storm = code >= 95;
        p.setShader(null);
        if (clear || partly) {
            float ox = partly ? cx - s * 0.16f : cx, oy = partly ? cy - s * 0.14f : cy;
            float r = s * (partly ? 0.22f : 0.28f);
            if (day) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFFFFD60A);
                c.drawCircle(ox, oy, r, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(s * 0.05f);
                p.setStrokeCap(Paint.Cap.ROUND);
                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI / 4;
                    float x1 = ox + (float) Math.cos(a) * r * 1.35f, y1 = oy + (float) Math.sin(a) * r * 1.35f;
                    float x2 = ox + (float) Math.cos(a) * r * 1.7f, y2 = oy + (float) Math.sin(a) * r * 1.7f;
                    c.drawLine(x1, y1, x2, y2, p);
                }
            } else {
                crescent(c, ox, oy, r, 0xFFE5E5EA);
            }
            if (clear) return;
        }
        if (fog) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeWidth(s * 0.07f);
            p.setColor(0xFFAEAEB2);
            for (int i = 0; i < 3; i++) {
                float y = cy - s * 0.15f + i * s * 0.16f;
                c.drawLine(cx - s * 0.32f + (i % 2) * s * 0.06f, y, cx + s * 0.32f, y, p);
            }
            return;
        }
        // cloud
        float cyc = partly ? cy + s * 0.06f : cy - (rainy || snowy || storm ? s * 0.08f : 0);
        p.setStyle(Paint.Style.FILL);
        p.setColor(rainy || storm ? 0xFFAEAEB2 : 0xFFE5E5EA);
        c.drawCircle(cx - s * 0.16f, cyc + s * 0.04f, s * 0.17f, p);
        c.drawCircle(cx + s * 0.04f, cyc - s * 0.06f, s * 0.22f, p);
        c.drawCircle(cx + s * 0.22f, cyc + s * 0.06f, s * 0.15f, p);
        iconRect.set(cx - s * 0.33f, cyc + s * 0.02f, cx + s * 0.37f, cyc + s * 0.21f);
        c.drawRoundRect(iconRect, s * 0.1f, s * 0.1f, p);
        if (rainy) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeWidth(s * 0.05f);
            p.setColor(0xFF64D2FF);
            for (int i = 0; i < 3; i++) {
                float x = cx - s * 0.16f + i * s * 0.17f;
                c.drawLine(x, cyc + s * 0.3f, x - s * 0.05f, cyc + s * 0.42f, p);
            }
        } else if (snowy) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFFFFFFF);
            for (int i = 0; i < 3; i++) c.drawCircle(cx - s * 0.16f + i * s * 0.17f, cyc + s * 0.36f, s * 0.04f, p);
        } else if (storm) {
            Icons.bolt(c, p, path, cx - s * 0.1f, cyc + s * 0.2f, s * 0.28f, 0xFFFFD60A);
        }
        p.setStyle(Paint.Style.FILL);
    }

    // ------------------------------------------------------------ drawing

    private boolean emptyState(Canvas c, float cx, float cy, float maxW, float size, Settings s) {
        if (valid && configured(s)) return false;
        p.setStyle(Paint.Style.FILL);
        p.setTypeface(Ui.MEDIUM);
        p.setColor(Ui.GRAY);
        String msg = configured(s) ? "Loading weather…" : "Set your city in StandBy Settings";
        Ui.fitWidth(p, msg, maxW, size);
        Ui.textCenter(c, p, msg, cx, cy, tmp);
        p.setTextAlign(Paint.Align.LEFT);
        return true;
    }

    void drawWidget(Canvas c, RectF r, Settings s) {
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setLetterSpacing(0f);
        if (emptyState(c, r.centerX(), r.centerY(), r.width() * 0.85f, r.height() * 0.06f, s)) return;
        float pad = 22 * d;
        p.setTextAlign(Paint.Align.LEFT);
        p.setTypeface(Ui.BOLD);
        p.setColor(0xFFFFFFFF);
        Ui.fitWidth(p, shortCity(), r.width() * 0.6f, r.height() * 0.07f);
        c.drawText(shortCity(), r.left + pad, r.top + pad + p.getTextSize(), p);
        float is = r.height() * 0.28f;
        icon(c, code, isDay, r.right - pad - is * 0.5f, r.top + pad + is * 0.45f, is);
        p.setTypeface(Ui.LIGHT);
        p.setColor(0xFFFFFFFF);
        p.setTextSize(r.height() * 0.30f);
        c.drawText(temp + "°", r.left + pad - 4 * d, r.top + r.height() * 0.50f, p);
        p.setTypeface(Ui.MEDIUM);
        p.setTextSize(r.height() * 0.058f);
        c.drawText(describe(code), r.left + pad, r.top + r.height() * 0.60f, p);
        p.setColor(Ui.GRAY);
        p.setTypeface(Ui.REGULAR);
        c.drawText("H " + hi + "°  L " + lo + "°  ·  Rain " + rain + "%", r.left + pad,
                r.top + r.height() * 0.68f, p);
        // hourly strip
        float y0 = r.top + r.height() * 0.76f;
        float cw = (r.width() - 2 * pad) / 5f;
        for (int k = 0; k < 5; k++) {
            float cx = r.left + pad + cw * (k + 0.5f);
            p.setTextSize(r.height() * 0.045f);
            p.setColor(Ui.GRAY);
            Ui.textCenter(c, p, hLabel[k] == null ? "" : hLabel[k], cx, y0, tmp);
            icon(c, hCode[k], hDay[k], cx, y0 + r.height() * 0.07f, r.height() * 0.08f);
            p.setColor(0xFFFFFFFF);
            p.setTypeface(Ui.MEDIUM);
            p.setTextSize(r.height() * 0.05f);
            Ui.textCenter(c, p, hTemp[k] + "°", cx, y0 + r.height() * 0.15f, tmp);
            p.setTypeface(Ui.REGULAR);
        }
        p.setTextAlign(Paint.Align.LEFT);
    }

    private String shortCity() {
        int comma = city.indexOf(',');
        return comma > 0 ? city.substring(0, comma) : city;
    }

    void drawFace(Canvas c, int w, int h, Calendar cal, int accent, boolean h24, boolean glass, LiquidGlass lg, long now) {
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setLetterSpacing(0f);
        if (emptyState(c, w / 2f, h / 2f, w * 0.8f, h * 0.05f, settingsRef)) return;
        boolean land = w >= h;
        float lx = land ? w * 0.07f : w * 0.08f;
        // left: city, icon, temp, condition
        p.setTextAlign(Paint.Align.LEFT);
        p.setTypeface(Ui.MEDIUM);
        p.setColor(0xFFFFFFFF);
        p.setTextSize(h * 0.06f);
        c.drawText(shortCity(), lx, h * 0.16f, p);
        p.setTypeface(Ui.REGULAR);
        p.setColor(Ui.GRAY);
        p.setTextSize(h * 0.042f);
        String t = Ui.time(cal, h24) + (h24 ? "" : " " + Ui.amPm(cal));
        c.drawText(t, lx, h * 0.23f, p);

        float is = h * 0.24f;
        icon(c, code, isDay, lx + is * 0.5f, h * 0.47f, is);
        p.setTypeface(Ui.LIGHT);
        p.setColor(accent);
        p.setTextSize(h * 0.30f);
        c.drawText(temp + "°", lx + is * 1.1f, h * 0.57f, p);
        p.setTypeface(Ui.MEDIUM);
        p.setColor(0xFFFFFFFF);
        p.setTextSize(h * 0.055f);
        c.drawText(describe(code), lx, h * 0.73f, p);
        p.setTypeface(Ui.REGULAR);
        p.setColor(Ui.GRAY);
        p.setTextSize(h * 0.045f);
        c.drawText("H " + hi + "°  L " + lo + "°  ·  Feels " + feels + "°", lx, h * 0.81f, p);

        if (!land) return;
        // right: hourly card + three small tiles
        float rx = w * 0.46f, rw = w * 0.47f;
        RectF hc = new RectF(rx, h * 0.10f, rx + rw, h * 0.52f);
        cardBg(c, hc, glass, lg, now);
        p.setTypeface(Ui.MEDIUM);
        p.setColor(Ui.GRAY);
        p.setTextSize(h * 0.035f);
        c.drawText("NEXT HOURS", hc.left + 18 * d, hc.top + h * 0.075f, p);
        float cw = (rw - 36 * d) / 6f;
        for (int k = 0; k < 6; k++) {
            float cx = hc.left + 18 * d + cw * (k + 0.5f);
            p.setTypeface(Ui.REGULAR);
            p.setColor(Ui.GRAY);
            p.setTextSize(h * 0.034f);
            Ui.textCenter(c, p, hLabel[k] == null ? "" : hLabel[k], cx, hc.top + h * 0.15f, tmp);
            icon(c, hCode[k], hDay[k], cx, hc.top + h * 0.24f, h * 0.09f);
            p.setTypeface(Ui.MEDIUM);
            p.setColor(0xFFFFFFFF);
            p.setTextSize(h * 0.05f);
            Ui.textCenter(c, p, hTemp[k] + "°", cx, hc.top + h * 0.34f, tmp);
        }
        p.setTextAlign(Paint.Align.LEFT);
        String[][] tiles = {{"SUNSET", sunset}, {"HUMIDITY", humidity + "%"}, {"RAIN", rain + "%"}};
        float tw = (rw - 2 * 12 * d) / 3f;
        for (int i = 0; i < 3; i++) {
            float tx = rx + i * (tw + 12 * d);
            RectF tile = new RectF(tx, h * 0.57f, tx + tw, h * 0.88f);
            cardBg(c, tile, glass, lg, now);
            p.setTypeface(Ui.MEDIUM);
            p.setColor(Ui.GRAY);
            p.setTextSize(h * 0.032f);
            c.drawText(tiles[i][0], tile.left + 14 * d, tile.top + h * 0.07f, p);
            p.setTypeface(Ui.BOLD);
            p.setColor(0xFFFFFFFF);
            Ui.fitWidth(p, tiles[i][1], tw - 28 * d, h * 0.07f);
            c.drawText(tiles[i][1], tile.left + 14 * d, tile.bottom - h * 0.06f, p);
        }
    }

    private Settings settingsRef;

    void setSettings(Settings s) {
        settingsRef = s;
    }

    private void cardBg(Canvas c, RectF r, boolean glass, LiquidGlass lg, long now) {
        if (glass) {
            lg.card(c, new RectF(r), 22 * d, now);
        } else {
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
            p.setColor(Ui.CARD);
            c.drawRoundRect(r, 22 * d, 22 * d, p);
        }
    }
}
