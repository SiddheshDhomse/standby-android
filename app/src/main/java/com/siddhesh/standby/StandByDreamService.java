package com.siddhesh.standby;

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
    public void setNight(boolean night) {
        setScreenBright(!night);
    }

    @Override
    public void openDreamSettings() {
    }

    @Override
    public void requestPhotoPermission() {
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
