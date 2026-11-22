package com.siddhesh.standby;

import android.content.Intent;
import android.os.Build;
import android.service.dreams.DreamService;
import android.view.WindowInsets;
import android.view.WindowInsetsController;

/**
 * Screen saver ("Daydream"). When chosen in Settings > Display > Screen saver, Android starts
 * StandBy automatically while the phone is charging — the same trigger as iPhone StandBy.
 */
public class StandByDreamService extends DreamService implements StandByView.Host {

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(true);   // allow swipes and long-press
        setFullscreen(true);
        setScreenBright(true);
        setContentView(new StandByView(this, this));
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        if (Build.VERSION.SDK_INT >= 30 && getWindow() != null) {
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.systemBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        }
    }

    @Override
    public void setBrightness(float level) {
        if (getWindow() == null) return;
        android.view.WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = level < 0 ? android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                : Math.max(0.01f, Math.min(1f, level));
        getWindow().setAttributes(lp);
    }

    @Override
    public void openDreamSettings() {
    }

    @Override
    public void openSettings() {
        // Leave the screen saver and open settings (the user unlocks first if needed).
        Intent i = new Intent(this, SettingsActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (Exception ignored) {
        }
        finish();
    }

    @Override
    public void requestPhotoPermission() {
    }

    @Override
    public void openTodo() {
        launch(new Intent(this, TodoActivity.class));
    }

    @Override
    public void openUrl(String url) {
        if (!Agenda.isSafeJoinUrl(url)) return; // only Teams meeting links
        launch(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE));
    }

    /** Leaves the screen saver and opens something (Android asks to unlock first if needed). */
    private void launch(Intent i) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (Exception ignored) {
        }
        finish();
    }

    @Override
    public void exit() {
        finish();
    }

    @Override
    public boolean isActivity() {
        return false;
    }
}
