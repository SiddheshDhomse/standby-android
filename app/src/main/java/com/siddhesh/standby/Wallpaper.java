package com.siddhesh.standby;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.security.SecureRandom;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HttpsURLConnection;

/**
 * Downloads background wallpapers from Bing (wallpaper of the day) or picsum.photos.
 *
 * Security: HTTPS only (cleartext is also blocked app-wide), fixed host allow-list checked
 * again after redirects, strict size limits, timeouts, image dimensions checked before
 * decoding, and the image is cached in app-private storage.
 */
final class Wallpaper {
    private static final int MAX_JSON = 256 * 1024;
    private static final int MAX_IMAGE = 15 * 1024 * 1024;
    private static final long MAX_PIXELS = 40_000_000L;
    private static final String[] ALLOWED_HOSTS = {"www.bing.com", "picsum.photos", "fastly.picsum.photos"};

    private final Context ctx;
    private final Runnable onChange;
    private final SharedPreferences sp;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService exec = new ThreadPoolExecutor(0, 1, 5, TimeUnit.SECONDS,
            new LinkedBlockingQueue<Runnable>());
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Matrix m = new Matrix();
    private final SecureRandom random = new SecureRandom();

    private Bitmap bmp;
    private int loadedSource = -1;
    private boolean busy;
    private long lastAttempt;
    private int targetSize = 1920;

    Wallpaper(Context ctx, Runnable onChange) {
        this.ctx = ctx;
        this.onChange = onChange;
        this.sp = ctx.getSharedPreferences(Settings.FILE, Context.MODE_PRIVATE);
    }

    void setScreenSize(int w, int h) {
        targetSize = Math.max(w, h);
    }

    private File cacheFile() {
        return new File(ctx.getFilesDir(), "wallpaper.img");
    }

    Bitmap bitmap() {
        return bmp;
    }

    static File ownFile(Context c) {
        return new File(c.getFilesDir(), "own_wallpaper.jpg");
    }

    private long loadedOwnVersion = -1;

    /** Call often (cheap). Loads the cached image or downloads a new one when due. */
    void ensure(int source, int intervalIdx) {
        if (source == Settings.WP_NONE) {
            bmp = null;
            loadedSource = -1;
            return;
        }
        if (busy) return;
        if (source == Settings.WP_OWN) {
            long ver = sp.getLong("own_wp_ver", 0);
            if (loadedSource == Settings.WP_OWN && loadedOwnVersion == ver) return;
            File f = ownFile(ctx);
            if (!f.exists()) {
                bmp = null;
                loadedSource = Settings.WP_OWN;
                loadedOwnVersion = ver;
                return;
            }
            busy = true;
            final int target = targetSize;
            exec.execute(() -> {
                Bitmap b = decodeFile(f, target);
                main.post(() -> {
                    busy = false;
                    bmp = b;
                    loadedSource = Settings.WP_OWN;
                    loadedOwnVersion = ver;
                    onChange.run();
                });
            });
            return;
        }
        long now = System.currentTimeMillis();
        boolean sameSource = sp.getInt("wp_cachedSource", -1) == source;
        long fetchedAt = sp.getLong("wp_fetchedAt", 0);
        long interval = Settings.WP_MINUTES[Math.max(0, Math.min(Settings.WP_MINUTES.length - 1, intervalIdx))] * 60_000L;
        boolean due = !sameSource || now - fetchedAt > interval || fetchedAt > now;

        if (bmp == null || loadedSource != source) {
            if (sameSource && cacheFile().exists()) {
                busy = true;
                final int src = source;
                final int target = targetSize;
                exec.execute(() -> {
                    Bitmap b = decodeFile(cacheFile(), target);
                    main.post(() -> {
                        busy = false;
                        if (b != null) {
                            bmp = b;
                            loadedSource = src;
                            onChange.run();
                        }
                    });
                });
                return;
            }
        }
        if (due && now - lastAttempt > 120_000) {
            lastAttempt = now;
            download(source);
        }
    }

    /** Saves a picked image (re-encoded: strips location/EXIF) as the user's own wallpaper. Blocking. */
    static boolean saveOwn(Context c, android.net.Uri uri) {
        try {
            android.graphics.ImageDecoder.Source src = android.graphics.ImageDecoder.createSource(c.getContentResolver(), uri);
            Bitmap b = android.graphics.ImageDecoder.decodeBitmap(src, (decoder, info, source) -> {
                int w = info.getSize().getWidth(), h = info.getSize().getHeight();
                if ((long) w * h > 200_000_000L) throw new IllegalArgumentException("too large");
                decoder.setTargetSampleSize(sampleSize(w, h, 2400));
                decoder.setAllocator(android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE);
            });
            File tmp = new File(c.getFilesDir(), "own_wallpaper.tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                b.compress(Bitmap.CompressFormat.JPEG, 90, out);
                out.getFD().sync();
            }
            if (!tmp.renameTo(ownFile(c))) {
                tmp.delete();
                return false;
            }
            c.getSharedPreferences(Settings.FILE, Context.MODE_PRIVATE).edit()
                    .putLong("own_wp_ver", System.currentTimeMillis()).apply();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Forces a new image on the next ensure(). */
    static void requestNew(Context c) {
        c.getSharedPreferences(Settings.FILE, Context.MODE_PRIVATE).edit().putLong("wp_fetchedAt", 0).apply();
    }

    private void download(final int source) {
        busy = true;
        final int target = targetSize;
        exec.execute(() -> {
            Bitmap b = null;
            try {
                String url;
                if (source == Settings.WP_BING) {
                    url = bingImageUrl();
                } else {
                    url = "https://picsum.photos/1920/1080?random=" + Math.abs(random.nextInt());
                }
                byte[] data = fetch(url, MAX_IMAGE, true);
                b = decodeBytes(data, target);
                if (b != null) writeCache(data);
            } catch (Exception ignored) {
                // network errors are expected when offline; keep the old wallpaper
            }
            final Bitmap result = b;
            main.post(() -> {
                busy = false;
                if (result != null) {
                    bmp = result;
                    loadedSource = source;
                    sp.edit().putLong("wp_fetchedAt", System.currentTimeMillis())
                            .putInt("wp_cachedSource", source).apply();
                    onChange.run();
                }
            });
        });
    }

    private String bingImageUrl() throws Exception {
        byte[] json = fetch("https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=8&mkt=en-IN",
                MAX_JSON, false);
        JSONArray images = new JSONObject(new String(json, "UTF-8")).getJSONArray("images");
        if (images.length() == 0) throw new IOException("no images");
        JSONObject img = images.getJSONObject(random.nextInt(images.length()));
        String path = img.getString("url");
        // Only accept a site-relative path so the JSON can't send us to another host.
        if (!path.startsWith("/") || path.startsWith("//") || path.contains("\\")) {
            throw new IOException("unexpected image path");
        }
        return "https://www.bing.com" + path;
    }

    private static boolean allowedHost(String host) {
        if (host == null) return false;
        for (String h : ALLOWED_HOSTS) if (h.equalsIgnoreCase(host)) return true;
        return false;
    }

    private static byte[] fetch(String urlStr, int maxBytes, boolean image) throws IOException {
        URL url = new URL(urlStr);
        if (!"https".equals(url.getProtocol()) || !allowedHost(url.getHost())) {
            throw new IOException("blocked url");
        }
        URLConnection uc = url.openConnection();
        if (!(uc instanceof HttpsURLConnection)) throw new IOException("not https");
        HttpsURLConnection c = (HttpsURLConnection) uc;
        c.setConnectTimeout(10_000);
        c.setReadTimeout(20_000);
        c.setInstanceFollowRedirects(true); // Java never follows https -> http redirects
        c.setUseCaches(false);
        c.setRequestProperty("User-Agent", "StandBy-Android/1.1");
        try {
            int code = c.getResponseCode();
            URL finalUrl = c.getURL();
            if (code != 200 || !"https".equals(finalUrl.getProtocol()) || !allowedHost(finalUrl.getHost())) {
                throw new IOException("bad response");
            }
            String type = c.getContentType();
            if (image && (type == null || !type.toLowerCase(java.util.Locale.US).startsWith("image/"))) {
                throw new IOException("not an image");
            }
            long len = c.getContentLengthLong();
            if (len > maxBytes) throw new IOException("too large");
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[16 * 1024];
                int n, total = 0;
                while ((n = in.read(buf)) != -1) {
                    total += n;
                    if (total > maxBytes) throw new IOException("too large");
                    out.write(buf, 0, n);
                }
                return out.toByteArray();
            }
        } finally {
            c.disconnect();
        }
    }

    private static int sampleSize(int w, int h, int target) {
        int s = 1;
        while (Math.max(w, h) / (s * 2) >= target) s *= 2;
        return s;
    }

    private static Bitmap decodeBytes(byte[] data, int target) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, o);
        if (o.outWidth <= 0 || o.outHeight <= 0 || (long) o.outWidth * o.outHeight > MAX_PIXELS) return null;
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = sampleSize(o.outWidth, o.outHeight, target);
        return BitmapFactory.decodeByteArray(data, 0, data.length, o2);
    }

    private static Bitmap decodeFile(File f, int target) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getPath(), o);
            if (o.outWidth <= 0 || o.outHeight <= 0 || (long) o.outWidth * o.outHeight > MAX_PIXELS) return null;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = sampleSize(o.outWidth, o.outHeight, target);
            return BitmapFactory.decodeFile(f.getPath(), o2);
        } catch (Exception e) {
            return null;
        }
    }

    private void writeCache(byte[] data) {
        File tmp = new File(ctx.getFilesDir(), "wallpaper.tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(data);
            out.getFD().sync();
        } catch (IOException e) {
            tmp.delete();
            return;
        }
        if (!tmp.renameTo(cacheFile())) tmp.delete();
    }

    /** Draws the wallpaper centre-cropped, darkened by dimPercent. */
    void draw(Canvas c, int w, int h, int dimPercent) {
        Bitmap b = bmp;
        if (b == null) return;
        float bw = b.getWidth(), bh = b.getHeight();
        float s = Math.max(w / bw, h / bh);
        m.setScale(s, s);
        m.postTranslate((w - bw * s) / 2f, (h - bh * s) / 2f);
        paint.setAlpha(255);
        c.drawBitmap(b, m, paint);
        if (dimPercent > 0) c.drawColor(Ui.alpha(0xFF000000, Math.round(dimPercent * 2.55f)));
    }
}
