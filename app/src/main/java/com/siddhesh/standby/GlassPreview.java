package com.siddhesh.standby;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/** Live mini StandBy used at the top of Settings › Glass & wallpaper. Real glass, real wallpaper. */
final class GlassPreview extends View {
    private final Settings s;
    private final LiquidGlass liquid;
    private final Wallpaper wallpaper;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final Rect tmp = new Rect();
    private final RectF rf = new RectF();
    private final float d;
    private final SimpleDateFormat dateFmt = new SimpleDateFormat("EEEE d MMM", Locale.getDefault());
    private final Runnable tick = this::invalidate;

    GlassPreview(Context c, Settings s) {
        super(c);
        this.s = s;
        d = c.getResources().getDisplayMetrics().density;
        liquid = new LiquidGlass(d);
        wallpaper = new Wallpaper(c, this::postInvalidate);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        wallpaper.setScreenSize(w * 2, h * 2);
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(tick);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        long now = System.currentTimeMillis();
        wallpaper.ensure(s.activeWallpaper(), s.wallpaperInterval);
        float radius = 22 * d;
        clip.reset();
        clip.addRoundRect(new RectF(0, 0, w, h), radius, radius, Path.Direction.CW);
        c.save();
        c.clipPath(clip);
        liquid.drawBackground(c, w, h, now, s, wallpaper.bitmap());

        float gap = 8 * d, m = 10 * d;
        float cw = (w - 2 * m - gap) / 2f;
        RectF a = new RectF(m, m, m + cw, h - m);
        RectF b = new RectF(m + cw + gap, m, w - m, h - m);
        float cr = Math.min(s.glassRadius * d * 0.6f, a.height() / 2f);
        for (RectF r : new RectF[]{a, b}) {
            if (s.glass) {
                liquid.card(c, r, cr, now);
            } else {
                p.setShader(null);
                p.setStyle(Paint.Style.FILL);
                p.setColor(s.bgSource == Settings.BG_BLACK ? Ui.CARD : 0xE61C1C1E);
                c.drawRoundRect(r, cr, cr, p);
            }
        }

        Calendar cal = Calendar.getInstance();
        boolean h24 = android.text.format.DateFormat.is24HourFormat(getContext());
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setTypeface(Ui.LIGHT);
        p.setColor(0xF2FFFFFF);
        String t = Ui.time(cal, h24);
        Ui.fit(p, t, a.width() * 0.8f, a.height() * 0.42f, tmp);
        Ui.textCenter(c, p, t, a.centerX(), a.centerY() - a.height() * 0.05f, tmp);
        p.setTypeface(Ui.MEDIUM);
        p.setTextSize(9 * d);
        p.setLetterSpacing(0.15f);
        p.setColor(0xCCFFFFFF);
        Ui.textCenter(c, p, dateFmt.format(cal.getTime()).toUpperCase(Locale.getDefault()), a.centerX(),
                a.centerY() + a.height() * 0.3f, tmp);
        p.setLetterSpacing(0f);

        float x = b.left + 12 * d;
        p.setTextAlign(Paint.Align.LEFT);
        p.setTypeface(Ui.BOLD);
        p.setTextSize(9 * d);
        p.setColor(0xFFFF6961);
        c.drawText("TODAY", x, b.top + 22 * d, p);
        p.setColor(0xFFFFFFFF);
        p.setTextSize(13 * d);
        c.drawText("Team standup", x, b.top + 42 * d, p);
        p.setTypeface(Ui.REGULAR);
        p.setTextSize(10 * d);
        p.setColor(0xB3FFFFFF);
        c.drawText("10:00 – 10:15 AM", x, b.top + 57 * d, p);
        p.setColor(0x2EFFFFFF);
        c.drawRect(x, b.top + 66 * d, b.right - 12 * d, b.top + 67 * d, p);
        p.setColor(0xD9FFFFFF);
        c.drawText("Design review · 3:00 PM", x, b.top + 82 * d, p);
        c.restore();

        removeCallbacks(tick);
        postDelayed(tick, LiquidGlass.animated(s) ? 40 : 500);
    }
}
