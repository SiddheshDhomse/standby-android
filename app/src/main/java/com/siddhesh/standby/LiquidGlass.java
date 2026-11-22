package com.siddhesh.standby;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RecordingCanvas;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.os.Build;

import java.util.ArrayList;
import java.util.List;

/**
 * Liquid Glass: a slowly flowing, softly coloured background (always moving, so no pixel stays
 * lit in one place) seen through glass panels that
 *  - blur it (RenderEffect, Android 12+),
 *  - bend it at the edges like a lens, with a hint of colour fringing (AGSL shader, Android 13+),
 *  - brighten/saturate it a little, like real frosted glass,
 *  - catch the light on a bright rim, with a soft shadow underneath.
 */
final class LiquidGlass {
    static final String[] PALETTE_NAMES = {"Graphite", "Ocean", "Sage", "Sand", "Dusk"};
    /** base, blob A, blob B, blob C, small accent blob. Muted, mid-dark tones. */
    private static final int[][] PALETTES = {
            {0xFF0B0D12, 0xFF4A5A78, 0xFF2E5A88, 0xFF6B4E71, 0xFF8AA0B8},
            {0xFF04121A, 0xFF0E6E8C, 0xFF1F3F8F, 0xFF2FA39A, 0xFF7FD4E0},
            {0xFF08110D, 0xFF3F7A5E, 0xFF6A8F4E, 0xFF2C5A6B, 0xFFB7D39A},
            {0xFF120D08, 0xFF8A5A3B, 0xFFB0844F, 0xFF5A3E5A, 0xFFE8C08A},
            {0xFF0B0915, 0xFF5B3B8C, 0xFFA0466E, 0xFF2D4D8F, 0xFFE59AC0},
    };

    private static final String LENS_AGSL =
            "uniform shader content;\n"
            + "uniform float4 box;\n"
            + "uniform float radius;\n"
            + "uniform float strength;\n"
            + "uniform float band;\n"
            + "float sdBox(float2 p, float2 b, float r) {\n"
            + "  float2 q = abs(p) - b + float2(r);\n"
            + "  return length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - r;\n"
            + "}\n"
            + "half4 main(float2 xy) {\n"
            + "  float2 c = (box.xy + box.zw) * 0.5;\n"
            + "  float2 hb = (box.zw - box.xy) * 0.5;\n"
            + "  float2 p = xy - c;\n"
            + "  float d = sdBox(p, hb, radius);\n"
            + "  float t = clamp(1.0 + d / band, 0.0, 1.0);\n"
            + "  float k = t * t * t;\n"
            + "  float gx = sdBox(p + float2(1.0, 0.0), hb, radius) - sdBox(p - float2(1.0, 0.0), hb, radius);\n"
            + "  float gy = sdBox(p + float2(0.0, 1.0), hb, radius) - sdBox(p - float2(0.0, 1.0), hb, radius);\n"
            + "  float2 n = float2(gx, gy);\n"
            + "  float nl = length(n);\n"
            + "  if (nl > 0.0001) { n = n / nl; } else { n = float2(0.0); }\n"
            + "  float2 off = n * k * strength;\n"
            + "  half r = content.eval(xy + off * 1.18).r;\n"
            + "  half g = content.eval(xy + off).g;\n"
            + "  half b = content.eval(xy + off * 0.82).b;\n"
            + "  half3 col = half3(r, g, b);\n"
            + "  half l = dot(col, half3(0.299, 0.587, 0.114));\n"
            + "  col = mix(half3(l), col, half(1.35)) * half(1.18) + half3(0.025);\n"
            + "  return half4(col, 1.0);\n"
            + "}\n";

    private final float d;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rf = new RectF();
    private final Matrix mtx = new Matrix();
    private final float[] mv = new float[9];

    private RenderNode bgNode;
    private RenderEffect blur;
    private boolean ready;
    private int nodeW, nodeH;
    private float bgTx, bgTy;
    private final List<RenderNode> pool = new ArrayList<>();
    private final List<RuntimeShader> shaders = new ArrayList<>();
    private int used;
    private boolean shaderBroken;

    // user-tunable look (Settings › Glass & wallpaper)
    private float glassBlurPx, bgBlurPx = -1f, lensPx;
    private int frostAlpha, rimPct, tintColor;
    private RenderEffect bgBlurEffect;
    private float lastBgBlur = -2f;
    private final Paint bmpPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);

    void configure(Settings s) {
        float gb = s.glassBlur / 100f * 44f * d;
        if (gb != glassBlurPx) {
            glassBlurPx = gb;
            blur = gb > 0.5f ? RenderEffect.createBlurEffect(gb, gb, Shader.TileMode.CLAMP) : null;
        }
        lensPx = s.glassRefr / 100f * 30f * d;
        frostAlpha = Math.round(s.glassFrost / 100f * 90f);
        rimPct = s.glassRim;
        tintColor = Settings.TINT_COLORS[s.glassTint];
        bgBlurPx = s.bgBlur / 100f * 40f * d;
    }

    LiquidGlass(float density) {
        d = density;
        shadow.setStyle(Paint.Style.FILL);
        shadow.setColor(0x02000000);
        shadow.setShadowLayer(22 * density, 0, 8 * density, 0x99000000);
    }

    static boolean supported() {
        return Build.VERSION.SDK_INT >= 31;
    }

    private static boolean lensSupported() {
        return Build.VERSION.SDK_INT >= 33;
    }

    // ------------------------------------------------------------ background

    private void paintBackground(Canvas c, int w, int h, long now, int palette) {
        int[] pal = PALETTES[Math.floorMod(palette, PALETTES.length)];
        c.drawColor(pal[0]);
        float t = (now % 3_600_000L) / 1000f;
        float big = Math.max(w, h);
        blob(c, w * (0.28f + 0.20f * (float) Math.sin(t / 37.0)), h * (0.35f + 0.30f * (float) Math.cos(t / 49.0)),
                big * 0.40f, pal[1], 235);
        blob(c, w * (0.74f + 0.18f * (float) Math.cos(t / 43.0)), h * (0.65f + 0.28f * (float) Math.sin(t / 59.0)),
                big * 0.38f, pal[2], 235);
        blob(c, w * (0.52f + 0.30f * (float) Math.sin(t / 71.0 + 1.7)), h * (0.20f + 0.25f * (float) Math.cos(t / 33.0 + 0.4)),
                big * 0.30f, pal[3], 220);
        blob(c, w * (0.50f + 0.38f * (float) Math.sin(t / 23.0 + 0.9)), h * (0.55f + 0.32f * (float) Math.cos(t / 29.0 + 2.2)),
                big * 0.14f, pal[4], 150);
        p.setShader(null);
    }

    private void blob(Canvas c, float x, float y, float r, int color, int alpha) {
        p.setStyle(Paint.Style.FILL);
        p.setShader(new RadialGradient(x, y, r,
                new int[]{Ui.alpha(color, alpha), Ui.alpha(color, alpha * 2 / 3), Ui.alpha(color, 0)},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(x, y, r, p);
    }

    @SuppressWarnings("deprecation")
    private void currentTranslation(Canvas c, float[] out) {
        c.getMatrix(mtx);
        mtx.getValues(mv);
        out[0] = mv[Matrix.MTRANS_X];
        out[1] = mv[Matrix.MTRANS_Y];
    }

    /** True when the background moves (needs ~25 fps). */
    static boolean animated(Settings s) {
        return s.bgSource == Settings.BG_FLOW;
    }

    private void paintSource(Canvas c, int w, int h, long now, Settings s, android.graphics.Bitmap wall) {
        if (s.bgSource == Settings.BG_WALLPAPER && wall != null) {
            float bw = wall.getWidth(), bh = wall.getHeight();
            float sc = Math.max(w / bw, h / bh);
            mtx.setScale(sc, sc);
            mtx.postTranslate((w - bw * sc) / 2f, (h - bh * sc) / 2f);
            c.drawColor(0xFF000000);
            c.drawBitmap(wall, mtx, bmpPaint);
        } else if (s.bgSource == Settings.BG_BLACK || s.bgSource == Settings.BG_WALLPAPER) {
            c.drawColor(0xFF000000);
        } else {
            paintBackground(c, w, h, now, s.glassPalette);
        }
    }

    /**
     * Draws the page background (flowing colours, wallpaper or black, blurred and darkened as set)
     * and remembers it for the glass panels drawn after it.
     */
    void drawBackground(Canvas c, int w, int h, long now, Settings s, android.graphics.Bitmap wall) {
        configure(s);
        ready = false;
        used = 0;
        int dimA = s.bgSource == Settings.BG_BLACK ? 0 : Math.round(s.wallpaperDim * 2.55f);
        if (supported() && c.isHardwareAccelerated() && w > 0 && h > 0) {
            if (bgNode == null) bgNode = new RenderNode("glass-bg");
            if (nodeW != w || nodeH != h) {
                nodeW = w;
                nodeH = h;
                bgNode.setPosition(0, 0, w, h);
            }
            if (bgBlurPx != lastBgBlur) {
                lastBgBlur = bgBlurPx;
                bgNode.setRenderEffect(bgBlurPx > 0.5f
                        ? RenderEffect.createBlurEffect(bgBlurPx, bgBlurPx, Shader.TileMode.CLAMP) : null);
            }
            RecordingCanvas rc = bgNode.beginRecording(w, h);
            paintSource(rc, w, h, now, s, wall);
            if (dimA > 0) rc.drawColor(Ui.alpha(0xFF000000, dimA));
            bgNode.endRecording();
            c.drawRenderNode(bgNode);
            float[] t = new float[2];
            currentTranslation(c, t);
            bgTx = t[0];
            bgTy = t[1];
            ready = true;
        } else {
            paintSource(c, w, h, now, s, wall);
            if (dimA > 0) c.drawColor(Ui.alpha(0xFF000000, dimA));
        }
    }

    // ------------------------------------------------------------ glass panel

    private RenderNode node(int i) {
        while (pool.size() <= i) {
            RenderNode n = new RenderNode("glass-" + pool.size());
            pool.add(n);
            shaders.add(null);
        }
        return pool.get(i);
    }

    /** A glass panel. Call after drawBackground() during the same frame. */
    void card(Canvas c, RectF r, float radius, long now) {
        card(c, r, radius, now, true);
    }

    /** @param withShadow false inside clipped areas (widget stacks), where a shadow would show as dark corners */
    void card(Canvas c, RectF r, float radius, long now, boolean withShadow) {
        radius = Math.min(radius, Math.min(r.width(), r.height()) / 2f);
        if (rimPct < 0) rimPct = 60;
        path.reset();
        path.addRoundRect(r, radius, radius, Path.Direction.CW);

        // soft shadow underneath lifts the panel off the background
        if (withShadow) c.drawPath(path, shadow);

        c.save();
        c.clipPath(path);
        if (ready) {
            float[] t = new float[2];
            currentTranslation(c, t);
            float dx = t[0] - bgTx, dy = t[1] - bgTy;   // panel space -> background space
            RenderNode n = node(used);
            int idx = used++;
            // only render the area around this panel (plus a margin for blur and lens sampling)
            int m = Math.round(40 * d);
            int nl = Math.max(0, (int) Math.floor(r.left + dx) - m);
            int nt = Math.max(0, (int) Math.floor(r.top + dy) - m);
            int nr = Math.min(nodeW, (int) Math.ceil(r.right + dx) + m);
            int nb = Math.min(nodeH, (int) Math.ceil(r.bottom + dy) + m);
            if (nr - nl < 2 || nb - nt < 2) {
                used--;
                c.restore();
                return;
            }
            n.setPosition(nl, nt, nr, nb);
            RecordingCanvas rc = n.beginRecording(nr - nl, nb - nt);
            rc.translate(-nl, -nt);
            rc.drawRenderNode(bgNode);
            n.endRecording();
            RenderEffect effect = blur;
            if (lensSupported() && !shaderBroken && lensPx > 0.5f) {
                try {
                    RuntimeShader s = shaders.get(idx);
                    if (s == null) {
                        s = new RuntimeShader(LENS_AGSL);
                        shaders.set(idx, s);
                    }
                    s.setFloatUniform("box", r.left + dx - nl, r.top + dy - nt, r.right + dx - nl, r.bottom + dy - nt);
                    s.setFloatUniform("radius", radius);
                    s.setFloatUniform("strength", Math.min(lensPx, Math.min(r.width(), r.height()) * 0.2f));
                    s.setFloatUniform("band", Math.min(radius * 1.3f, 34 * d));
                    RenderEffect lens = RenderEffect.createRuntimeShaderEffect(s, "content");
                    effect = blur == null ? lens : RenderEffect.createChainEffect(lens, blur);
                } catch (Throwable e) {
                    shaderBroken = true; // fall back to blur only
                }
            }
            n.setRenderEffect(effect); // may be null: clear glass
            c.translate(-dx, -dy);
            c.drawRenderNode(n);
            c.translate(dx, dy);
        } else {
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0x33FFFFFF);
            c.drawRect(r, p);
        }
        // frost + optional tint + a soft highlight across the top
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        if (frostAlpha > 0) {
            p.setColor(Ui.alpha(0xFFFFFFFF, frostAlpha));
            c.drawRect(r, p);
        }
        if (tintColor != 0) {
            p.setColor(Ui.alpha(tintColor, 40));
            c.drawRect(r, p);
        }
        p.setShader(new LinearGradient(0, r.top, 0, r.top + r.height() * 0.5f,
                0x22FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawRect(r, p);
        c.restore();

        // rim light: bright where the (slowly moving) light hits, faint elsewhere
        double a = (now % 60_000L) / 60_000.0 * Math.PI * 2;
        float cx = r.centerX(), cy = r.centerY();
        float lx = (float) Math.cos(a) * r.width() / 2f, ly = (float) Math.sin(a) * r.height() / 2f - r.height() / 2f;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1f, 1.4f * d));
        float k = rimPct / 60f;
        p.setShader(new LinearGradient(cx + lx, cy + ly, cx - lx, cy - ly * 0.2f,
                new int[]{Ui.alpha(0xFFFFFFFF, Math.round(230 * k)), Ui.alpha(0xFFFFFFFF, Math.round(64 * k)),
                        Ui.alpha(0xFFFFFFFF, Math.round(24 * k)), Ui.alpha(0xFFFFFFFF, Math.round(112 * k))},
                new float[]{0f, 0.35f, 0.7f, 1f}, Shader.TileMode.CLAMP));
        float in = p.getStrokeWidth() / 2f;
        rf.set(r.left + in, r.top + in, r.right - in, r.bottom - in);
        c.drawRoundRect(rf, radius - in, radius - in, p);
        // inner glow for thickness
        p.setStrokeWidth(Math.max(1f, 3f * d));
        p.setShader(new LinearGradient(0, r.top, 0, r.bottom,
                Ui.alpha(0xFFFFFFFF, Math.round(38 * k)), Ui.alpha(0xFFFFFFFF, Math.round(8 * k)), Shader.TileMode.CLAMP));
        float in2 = 2.8f * d;
        rf.set(r.left + in2, r.top + in2, r.right - in2, r.bottom - in2);
        c.drawRoundRect(rf, Math.max(0, radius - in2), Math.max(0, radius - in2), p);
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
    }
}
