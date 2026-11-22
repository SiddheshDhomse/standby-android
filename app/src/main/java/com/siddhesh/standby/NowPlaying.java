package com.siddhesh.standby;

import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.util.List;

/**
 * Reads the currently playing media (Spotify preferred, any player otherwise) through the
 * system MediaSession API. Android only allows this for apps the user has granted
 * "Notification access"; the listener service itself ignores notification contents.
 */
final class NowPlaying {
    static final String SPOTIFY = "com.spotify.music";

    private final Context ctx;
    private final Runnable onChange;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ComponentName component;
    private MediaSessionManager msm;
    private MediaController controller;
    private boolean started;

    String title, artist, album, pkg;
    Bitmap art, artBlur;
    boolean playing;
    long duration;

    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsListener = this::pick;

    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            readState();
            onChange.run();
        }

        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            readMetadata();
            onChange.run();
        }

        @Override
        public void onSessionDestroyed() {
            refresh();
        }
    };

    NowPlaying(Context ctx, Runnable onChange) {
        this.ctx = ctx;
        this.onChange = onChange;
        this.component = new ComponentName(ctx, MediaListenerService.class);
    }

    static boolean hasAccess(Context c) {
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            return nm != null && nm.isNotificationListenerAccessGranted(
                    new ComponentName(c, MediaListenerService.class));
        } catch (Exception e) {
            return false;
        }
    }

    void start() {
        if (started || !hasAccess(ctx)) return;
        try {
            msm = (MediaSessionManager) ctx.getSystemService(Context.MEDIA_SESSION_SERVICE);
            if (msm == null) return;
            msm.addOnActiveSessionsChangedListener(sessionsListener, component, main);
            started = true;
            refresh();
        } catch (SecurityException e) {
            started = false;
        }
    }

    void stop() {
        if (msm != null && started) {
            try {
                msm.removeOnActiveSessionsChangedListener(sessionsListener);
            } catch (Exception ignored) {
            }
        }
        started = false;
        setController(null);
    }

    void refresh() {
        if (msm == null) return;
        try {
            pick(msm.getActiveSessions(component));
        } catch (SecurityException e) {
            setController(null);
        }
    }

    private void pick(List<MediaController> list) {
        MediaController best = null;
        int bestScore = -1;
        if (list != null) {
            for (MediaController mc : list) {
                PlaybackState st = mc.getPlaybackState();
                boolean isPlaying = st != null && st.getState() == PlaybackState.STATE_PLAYING;
                int score = (isPlaying ? 2 : 0) + (SPOTIFY.equals(mc.getPackageName()) ? 1 : 0);
                if (score > bestScore) {
                    bestScore = score;
                    best = mc;
                }
            }
        }
        setController(best);
        onChange.run();
    }

    private void setController(MediaController mc) {
        if (controller != null) {
            try {
                controller.unregisterCallback(callback);
            } catch (Exception ignored) {
            }
        }
        controller = mc;
        if (mc != null) {
            mc.registerCallback(callback, main);
            pkg = mc.getPackageName();
        } else {
            pkg = null;
        }
        readMetadata();
        readState();
    }

    private void readMetadata() {
        MediaMetadata md = controller == null ? null : controller.getMetadata();
        if (md == null) {
            title = artist = album = null;
            art = artBlur = null;
            duration = 0;
            return;
        }
        title = md.getString(MediaMetadata.METADATA_KEY_TITLE);
        album = md.getString(MediaMetadata.METADATA_KEY_ALBUM);
        artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST);
        if (artist == null) artist = md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
        duration = md.getLong(MediaMetadata.METADATA_KEY_DURATION);
        Bitmap b = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (b == null) b = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if (b != art) {
            art = b;
            artBlur = null;
            if (b != null) {
                try {
                    // tiny copy, drawn scaled-up with filtering = cheap blur for backgrounds
                    artBlur = Bitmap.createScaledBitmap(b, 12, 12, true);
                } catch (Exception ignored) {
                }
            }
        }
    }

    private void readState() {
        PlaybackState st = controller == null ? null : controller.getPlaybackState();
        playing = st != null && st.getState() == PlaybackState.STATE_PLAYING;
    }

    boolean active() {
        return controller != null && title != null;
    }

    long position() {
        PlaybackState st = controller == null ? null : controller.getPlaybackState();
        if (st == null) return 0;
        long pos = st.getPosition();
        if (st.getState() == PlaybackState.STATE_PLAYING) {
            pos += (long) ((SystemClock.elapsedRealtime() - st.getLastPositionUpdateTime())
                    * st.getPlaybackSpeed());
        }
        if (duration > 0) pos = Math.min(pos, duration);
        return Math.max(0, pos);
    }

    String appLabel() {
        if (SPOTIFY.equals(pkg)) return "Spotify";
        return "Now Playing";
    }

    void playPause() {
        if (controller == null) return;
        if (playing) controller.getTransportControls().pause();
        else controller.getTransportControls().play();
    }

    void next() {
        if (controller != null) controller.getTransportControls().skipToNext();
    }

    void previous() {
        if (controller != null) controller.getTransportControls().skipToPrevious();
    }
}
