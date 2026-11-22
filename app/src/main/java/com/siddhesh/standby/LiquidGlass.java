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

    /** Draws the moving background and remembers it for the glass panels drawn after it. */
    void drawBackground(Canvas c, int w, int h, long now, int palette) {
        ready = false;
        used = 0;
        if (supported() && c.isHardwareAccelerated() && w > 0 && h > 0) {
            if (bgNode == null) bgNode = new RenderNode("glass-bg");
            if (nodeW != w || nodeH != h) {
                nodeW = w;
                nodeH = h;
                bgNode.setPosition(0, 0, w, h);
                float r = 28 * d;
                blur = RenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP);
            }
            RecordingCanvas rc = bgNode.beginRecording(w, h);
            paintBackground(rc, w, h, now, palette);
            bgNode.endRecording();
            c.drawRenderNode(bgNode);
            float[] t = new float[2];
            currentTranslation(c, t);
            bgTx = t[0];
            bgTy = t[1];
            ready = true;
        } else {
            paintBackground(c, w, h, now, palette);
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
        radius = Math.min(radius, Math.min(r.width(), r.height()) / 2f);
        path.reset();
        path.addRoundRect(r, radius, radius, Path.Direction.CW);

        // soft shadow underneath lifts the panel off the background
        c.drawPath(path, shadow);

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
            if (lensSupported() && !shaderBroken) {
                try {
                    RuntimeShader s = shaders.get(idx);
                    if (s == null) {
                        s = new RuntimeShader(LENS_AGSL);
                        shaders.set(idx, s);
                    }
                    s.setFloatUniform("box", r.left + dx - nl, r.top + dy - nt, r.right + dx - nl, r.bottom + dy - nt);
                    s.setFloatUniform("radius", radius);
                    s.setFloatUniform("strength", Math.min(18 * d, Math.min(r.width(), r.height()) * 0.12f));
                    s.setFloatUniform("band", Math.min(radius * 1.3f, 34 * d));
                    effect = RenderEffect.createChainEffect(
                            RenderEffect.createRuntimeShaderEffect(s, "content"), blur);
                } catch (Throwable e) {
                    shaderBroken = true; // fall back to blur only
                }
            }
            n.setRenderEffect(effect);
            c.translate(-dx, -dy);
            c.drawRenderNode(n);
            c.translate(dx, dy);
        } else {
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0x33FFFFFF);
            c.drawRect(r, p);
        }
        // thin frost + a soft highlight across the top
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x0AFFFFFF);
        c.drawRect(r, p);
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
        p.setShader(new LinearGradient(cx + lx, cy + ly, cx - lx, cy - ly * 0.2f,
                new int[]{0xE6FFFFFF, 0x40FFFFFF, 0x18FFFFFF, 0x70FFFFFF},
                new float[]{0f, 0.35f, 0.7f, 1f}, Shader.TileMode.CLAMP));
        float in = p.getStrokeWidth() / 2f;
        rf.set(r.left + in, r.top + in, r.right - in, r.bottom - in);
        c.drawRoundRect(rf, radius - in, radius - in, p);
        // inner glow for thickness
        p.setStrokeWidth(Math.max(1f, 3f * d));
        p.setShader(new LinearGradient(0, r.top, 0, r.bottom,
                0x26FFFFFF, 0x08FFFFFF, Shader.TileMode.CLAMP));
        float in2 = 2.8f * d;
        rf.set(r.left + in2, r.top + in2, r.right - in2, r.bottom - in2);
        c.drawRoundRect(rf, Math.max(0, radius - in2), Math.max(0, radius - in2), p);
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
    }
}
