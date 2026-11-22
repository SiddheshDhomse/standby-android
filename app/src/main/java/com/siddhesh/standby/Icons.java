package com.siddhesh.standby;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/** Simple vector icons drawn with paths. */
final class Icons {
    private Icons() {}

    static void playPause(Canvas c, Paint p, Path path, RectF r, boolean playing, int color) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        float s = Math.min(r.width(), r.height());
        float cx = r.centerX(), cy = r.centerY();
        if (playing) {
            float bw = s * 0.18f, bh = s * 0.6f, gap = s * 0.14f;
            c.drawRoundRect(new RectF(cx - gap / 2 - bw, cy - bh / 2, cx - gap / 2, cy + bh / 2), bw * 0.25f, bw * 0.25f, p);
            c.drawRoundRect(new RectF(cx + gap / 2, cy - bh / 2, cx + gap / 2 + bw, cy + bh / 2), bw * 0.25f, bw * 0.25f, p);
        } else {
            float h = s * 0.62f;
            path.reset();
            path.moveTo(cx - h * 0.38f, cy - h / 2);
            path.lineTo(cx + h * 0.52f, cy);
            path.lineTo(cx - h * 0.38f, cy + h / 2);
            path.close();
            c.drawPath(path, p);
        }
    }

    private static void tri(Canvas c, Paint p, Path path, float x, float cy, float h, boolean right) {
        float w = h * 0.8f;
        path.reset();
        if (right) {
            path.moveTo(x, cy - h / 2);
            path.lineTo(x + w, cy);
            path.lineTo(x, cy + h / 2);
        } else {
            path.moveTo(x + w, cy - h / 2);
            path.lineTo(x, cy);
            path.lineTo(x + w, cy + h / 2);
        }
        path.close();
        c.drawPath(path, p);
    }

    static void next(Canvas c, Paint p, Path path, RectF r, int color) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        float h = Math.min(r.width(), r.height()) * 0.42f;
        float w = h * 0.8f;
        float x = r.centerX() - w;
        tri(c, p, path, x, r.centerY(), h, true);
        tri(c, p, path, x + w * 0.9f, r.centerY(), h, true);
        c.drawRect(x + w * 1.9f, r.centerY() - h / 2, x + w * 1.9f + h * 0.12f, r.centerY() + h / 2, p);
    }

    static void prev(Canvas c, Paint p, Path path, RectF r, int color) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        float h = Math.min(r.width(), r.height()) * 0.42f;
        float w = h * 0.8f;
        float x = r.centerX() - w;
        c.drawRect(x - h * 0.12f - w * 0.1f, r.centerY() - h / 2, x - w * 0.1f, r.centerY() + h / 2, p);
        tri(c, p, path, x, r.centerY(), h, false);
        tri(c, p, path, x + w * 0.9f, r.centerY(), h, false);
    }

    static void bolt(Canvas c, Paint p, Path path, float x, float y, float s, int color) {
        path.reset();
        path.moveTo(x + 0.60f * s, y);
        path.lineTo(x + 0.08f * s, y + 0.58f * s);
        path.lineTo(x + 0.45f * s, y + 0.58f * s);
        path.lineTo(x + 0.35f * s, y + s);
        path.lineTo(x + 0.92f * s, y + 0.40f * s);
        path.lineTo(x + 0.55f * s, y + 0.40f * s);
        path.close();
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        c.drawPath(path, p);
    }
}
