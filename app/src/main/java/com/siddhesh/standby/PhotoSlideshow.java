package com.siddhesh.standby;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ImageDecoder;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Photos view: recent gallery photos, slow zoom + cross-fade, with the time in the corner. */
final class PhotoSlideshow {
    private static final long SHOW_MS = 12000;
    private static final long FADE_MS = 1400;

    private final Context ctx;
    private final StandByView view;
    private final float d;
    // one background worker that exits when idle
    private final ExecutorService exec = new ThreadPoolExecutor(0, 1, 5, TimeUnit.SECONDS,
            new LinkedBlockingQueue<Runnable>());
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Matrix m = new Matrix();
    private final Rect tmp = new Rect();
    private final RectF rf = new RectF();
    private final SimpleDateFormat dateFmt = new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault());

    private List<Uri> uris;
    private boolean listLoading;
    private int next;
    private boolean loading;
    private Bitmap cur, incoming;
    private long curShownAt, fadeStart = -1;
    private int targetSize = 1600;
    RectF permissionButton = new RectF();

    PhotoSlideshow(Context ctx, StandByView view, float density) {
        this.ctx = ctx;
        this.view = view;
        this.d = density;
    }

    static String[] permissions() {
        if (Build.VERSION.SDK_INT >= 34) {
            return new String[]{Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED};
        } else if (Build.VERSION.SDK_INT >= 33) {
            return new String[]{Manifest.permission.READ_MEDIA_IMAGES};
        }
        return new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
    }

    static boolean hasPermission(Context c) {
        for (String perm : permissions()) {
            if (c.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED) return true;
        }
        return false;
    }

    void setScreenSize(int w, int h) {
        targetSize = Math.max(w, h);
    }

    /** Called on start / resume / after the permission dialog. */
    void refresh() {
        if (!hasPermission(ctx) || listLoading) return;
        listLoading = true;
        exec.execute(() -> {
            List<Uri> list = query();
            main.post(() -> {
                listLoading = false;
                uris = list;
                next = 0;
                if (cur == null) loadNext();
                view.invalidate();
            });
        });
    }

    void release() {
        cur = null;
        incoming = null;
        fadeStart = -1;
    }

    boolean animating() {
        return cur != null;
    }

    /** Swipe up/down on the Photos view skips to the next photo. */
    void skip() {
        if (incoming == null && !loading) loadNext();
    }

    private List<Uri> query() {
        List<Uri> out = new ArrayList<>();
        Uri base = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        String[] proj = {MediaStore.Images.Media._ID};
        try (Cursor cs = ctx.getContentResolver().query(base, proj, null, null,
                MediaStore.Images.Media.DATE_ADDED + " DESC")) {
            if (cs != null) {
                int col = cs.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
                while (cs.moveToNext() && out.size() < 300) {
                    out.add(ContentUris.withAppendedId(base, cs.getLong(col)));
                }
            }
        } catch (Exception ignored) {
        }
        Collections.shuffle(out);
        return out;
    }

    private void loadNext() {
        if (loading || uris == null || uris.isEmpty()) return;
        loading = true;
        final Uri u = uris.get(next % uris.size());
        next++;
        final int target = targetSize;
        exec.execute(() -> {
            Bitmap b = decode(u, target);
            main.post(() -> {
                loading = false;
                if (b == null) {
                    // skip unreadable images
                    if (uris != null && next < uris.size() * 2) loadNext();
                    return;
                }
                long now = System.currentTimeMillis();
                if (cur == null) {
                    cur = b;
                    curShownAt = now;
                } else {
                    incoming = b;
                    fadeStart = now;
                }
                view.invalidate();
            });
        });
    }

    private Bitmap decode(Uri u, int target) {
        try {
            ContentResolver cr = ctx.getContentResolver();
            ImageDecoder.Source src = ImageDecoder.createSource(cr, u);
            return ImageDecoder.decodeBitmap(src, (decoder, info, source) -> {
                int sw = info.getSize().getWidth();
                int sh = info.getSize().getHeight();
                int sample = 1;
                while (Math.max(sw, sh) / (sample * 2) >= target) sample *= 2;
                decoder.setTargetSampleSize(sample);
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
            });
        } catch (Throwable t) {
            return null;
        }
    }

    void draw(Canvas c, int w, int h, long now, Calendar cal, boolean h24, boolean hidden, int corner) {
        p.setShader(null);
        p.setAlpha(255);
        if (hidden) {
            // Privacy: don't show the gallery to anyone who picks up the locked phone.
            message(c, w, h, "Photos hidden while locked",
                    "Unlock your phone to see them \u00B7 change this in Settings", false);
            return;
        }
        if (!hasPermission(ctx)) {
            message(c, w, h, "Show your photos in StandBy",
                    view.canAskPermission() ? "Tap to allow access to your photos"
                            : "Open the StandBy app once to allow photo access", true);
            return;
        }
        if (cur == null) {
            boolean empty = uris != null && uris.isEmpty();
            message(c, w, h, empty ? "No photos found" : "Loading photos…",
                    empty ? "Photos in your gallery will appear here" : "", false);
            if (uris == null) refresh();
            return;
        }

        if (fadeStart > 0 && incoming != null) {
            float f = (now - fadeStart) / (float) FADE_MS;
            if (f >= 1f) {
                cur = incoming;
                incoming = null;
                curShownAt = fadeStart;
                fadeStart = -1;
            }
        }
        kenBurns(c, cur, w, h, now - curShownAt, 255);
        if (incoming != null) {
            float f = Math.min(1f, (now - fadeStart) / (float) FADE_MS);
            kenBurns(c, incoming, w, h, now - fadeStart, (int) (f * 255));
        }
        if (incoming == null && !loading && now - curShownAt > SHOW_MS) loadNext();

        float pad = 28 * d;
        boolean right = corner == 1 || corner == 3;
        boolean bottom = corner == 2 || corner == 3;
        // legibility scrim on the clock's side
        p.setAlpha(255);
        if (bottom) {
            p.setShader(new LinearGradient(0, h * 0.5f, 0, h, 0x00000000, 0x99000000, Shader.TileMode.CLAMP));
            c.drawRect(0, h * 0.5f, w, h, p);
        } else {
            p.setShader(new LinearGradient(0, 0, 0, h * 0.5f, 0x99000000, 0x00000000, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h * 0.5f, p);
        }
        p.setShader(null);

        String t = Ui.time(cal, h24);
        String ds = dateFmt.format(cal.getTime());
        p.setTypeface(Ui.BOLD);
        p.setColor(0xFFFFFFFF);
        p.setTextAlign(right ? Paint.Align.RIGHT : Paint.Align.LEFT);
        p.setShadowLayer(10 * d, 0, 2 * d, 0x80000000);
        Ui.fit(p, t, w * 0.4f, h * 0.17f, tmp);
        p.getTextBounds(t, 0, t.length(), tmp);
        float timeH = tmp.height();
        float x = right ? w - pad : pad;
        float dateGap = h * 0.075f;
        float baseline = bottom ? h - pad - dateGap - 4 * d : pad + timeH;
        c.drawText(t, x, baseline, p);
        p.setTypeface(Ui.MEDIUM);
        p.setTextSize(h * 0.045f);
        c.drawText(ds, right ? x - 2 * d : x + 2 * d, baseline + dateGap, p);
        p.clearShadowLayer();
        p.setTextAlign(Paint.Align.LEFT);
    }

    private void kenBurns(Canvas c, Bitmap b, int w, int h, long age, int alpha) {
        float bw = b.getWidth(), bh = b.getHeight();
        float zoom = 1f + 0.08f * Math.min(1f, age / (float) (SHOW_MS + FADE_MS));
        float s = Math.max(w / bw, h / bh) * zoom;
        // keep portrait photos framed toward the top, where faces usually are
        float yAnchor = bh > bw && w > h ? 0.3f : 0.5f;
        m.setScale(s, s);
        m.postTranslate((w - bw * s) / 2f, (h - bh * s) * yAnchor);
        p.setAlpha(alpha);
        c.drawBitmap(b, m, p);
        p.setAlpha(255);
    }

    private void message(Canvas c, int w, int h, String title, String sub, boolean button) {
        float cw = Math.min(w * 0.7f, 560 * d);
        float ch = Math.min(h * 0.6f, 220 * d);
        rf.set((w - cw) / 2f, (h - ch) / 2f, (w + cw) / 2f, (h + ch) / 2f);
        p.setColor(Ui.CARD);
        c.drawRoundRect(rf, 28 * d, 28 * d, p);
        p.setColor(0xFFFFFFFF);
        p.setTypeface(Ui.BOLD);
        Ui.fitWidth(p, title, cw * 0.85f, 24 * d);
        Ui.textCenter(c, p, title, w / 2f, rf.top + ch * 0.32f, tmp);
        if (!sub.isEmpty()) {
            p.setTypeface(Ui.REGULAR);
            p.setColor(button && view.canAskPermission() ? 0xFF0A84FF : Ui.GRAY);
            Ui.fitWidth(p, sub, cw * 0.85f, 17 * d);
            Ui.textCenter(c, p, sub, w / 2f, rf.top + ch * 0.62f, tmp);
        }
        permissionButton.set(button ? rf : new RectF());
        p.setTextAlign(Paint.Align.LEFT);
    }
}
