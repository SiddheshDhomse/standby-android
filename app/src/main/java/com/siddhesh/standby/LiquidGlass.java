package com.siddhesh.standby;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RecordingCanvas;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.os.Build;

/**
 * Minimal "Liquid Glass" look: a dark, slowly flowing background (always moving, so no pixel
 * stays lit in one place) with frosted glass cards that really blur what is behind them
 * (RenderEffect, Android 12+), a fine light-catching edge and a soft sheen that drifts.
 */
final class LiquidGlass {
    static final String[] PALETTE_NAMES = {"Graphite", "Ocean", "Sage", "Sand", "Dusk"};
    /** base, blob A, blob B, blob C — all low luminance (burn-in friendly, office-calm). */
    private static final int[][] PALETTES = {
            {0xFF0E1013, 0xFF343A46, 0xFF262E3B, 0xFF454A55},
            {0xFF060F14, 0xFF0F4456, 0xFF1A3456, 0xFF235F6D},
            {0xFF0B100D, 0xFF2C4438, 0xFF3F5A49, 0xFF22352D},
            {0xFF110E0B, 0xFF4A3A2D, 0xFF5E4838, 0xFF372F29},
            {0xFF0D0B13, 0xFF342845, 0xFF4A3456, 0xFF262A45},
    };

    private final float d;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rf = new RectF();
    private RenderNode bgNode, blurNode;
    private boolean blurReady;
    private int nodeW, nodeH;

    LiquidGlass(float density) {
        d = density;
    }

    static boolean supported() {
        return Build.VERSION.SDK_INT >= 31;
    }

    private void paintBackground(Canvas c, int w, int h, long now, int palette) {
        int[] pal = PALETTES[Math.floorMod(palette, PALETTES.length)];
        c.drawColor(pal[0]);
        float t = (now % 3_600_000L) / 1000f;
        float big = Math.max(w, h);
        // three blobs on slow, different Lissajous paths (periods 50–90 s)
        blob(c, w * (0.30f + 0.22f * (float) Math.sin(t / 47.0)), h * (0.40f + 0.30f * (float) Math.cos(t / 61.0)),
                big * 0.55f, pal[1]);
        blob(c, w * (0.72f + 0.20f * (float) Math.cos(t / 53.0)), h * (0.62f + 0.28f * (float) Math.sin(t / 71.0)),
                big * 0.50f, pal[2]);
        blob(c, w * (0.55f + 0.30f * (float) Math.sin(t / 89.0 + 1.7)), h * (0.20f + 0.25f * (float) Math.cos(t / 43.0 + 0.4)),
                big * 0.38f, pal[3]);
        p.setShader(null);
    }

    private void blob(Canvas c, float x, float y, float r, int color) {
        p.setStyle(Paint.Style.FILL);
        p.setShader(new RadialGradient(x, y, r, new int[]{Ui.alpha(color, 230), Ui.alpha(color, 90), Ui.alpha(color, 0)},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(x, y, r, p);
    }

    /** Draws the moving background and prepares the blurred copy used by glass cards. */
    void drawBackground(Canvas c, int w, int h, long now, int palette) {
        blurReady = false;
        if (supported() && c.isHardwareAccelerated() && w > 0 && h > 0) {
            if (bgNode == null) {
                bgNode = new RenderNode("glass-bg");
                blurNode = new RenderNode("glass-blur");
            }
            if (nodeW != w || nodeH != h) {
                nodeW = w;
                nodeH = h;
                bgNode.setPosition(0, 0, w, h);
                blurNode.setPosition(0, 0, w, h);
                float r = 36 * d;
                blurNode.setRenderEffect(RenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP));
            }
            RecordingCanvas rc = bgNode.beginRecording(w, h);
            paintBackground(rc, w, h, now, palette);
            bgNode.endRecording();
            RecordingCanvas bc = blurNode.beginRecording(w, h);
            bc.drawRenderNode(bgNode);
            blurNode.endRecording();
            c.drawRenderNode(bgNode);
            blurReady = true;
        } else {
            paintBackground(c, w, h, now, palette);
        }
    }

    /** A frosted glass card. Call after drawBackground() in the same coordinate space. */
    void card(Canvas c, RectF r, float radius, long now) {
        path.reset();
        path.addRoundRect(r, radius, radius, Path.Direction.CW);
        c.save();
        c.clipPath(path);
        if (blurReady) c.drawRenderNode(blurNode);
        // frost tint
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x14FFFFFF);
        c.drawRect(r, p);
        // soft top sheen
        p.setShader(new LinearGradient(0, r.top, 0, r.top + r.height() * 0.55f,
                0x1AFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawRect(r, p);
        c.restore();

        // light-catching edge; the bright spot travels slowly around the card
        double a = (now % 40_000L) / 40_000.0 * Math.PI * 2;
        float cx = r.centerX(), cy = r.centerY();
        float dx = (float) Math.cos(a) * r.width() / 2f, dy = (float) Math.sin(a) * r.height() / 2f;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1f, 1.1f * d));
        p.setShader(new LinearGradient(cx - dx, cy - dy, cx + dx, cy + dy,
                new int[]{0x70FFFFFF, 0x14FFFFFF, 0x10FFFFFF, 0x50FFFFFF},
                new float[]{0f, 0.35f, 0.65f, 1f}, Shader.TileMode.CLAMP));
        rf.set(r.left + 0.5f, r.top + 0.5f, r.right - 0.5f, r.bottom - 0.5f);
        c.drawRoundRect(rf, radius, radius, p);
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
    }
}
