package com.siddhesh.standby;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

/** Launcher entry: StandBy full-screen in landscape. */
public class StandByActivity extends Activity implements StandByView.Host {

    private StandByView view;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window w = getWindow();
        w.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        WindowManager.LayoutParams lp = w.getAttributes();
        lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        w.setAttributes(lp);
        setShowWhenLocked(true);

        view = new StandByView(this, this);
        setContentView(view);
        hideSystemBars();
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemBars();
        view.onHostResume();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemBars();
    }

    @SuppressWarnings("deprecation")
    private void hideSystemBars() {
        Window w = getWindow();
        if (Build.VERSION.SDK_INT >= 30) {
            w.setDecorFitsSystemWindows(false);
            WindowInsetsController c = w.getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.systemBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            w.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }
    }

    // ---------------------------------------------------------------- Host

    @Override
    public void setBrightness(float level) {
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        float v = level < 0 ? WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE : Math.max(0.01f, Math.min(1f, level));
        if (lp.screenBrightness != v) {
            lp.screenBrightness = v;
            getWindow().setAttributes(lp);
        }
    }

    @Override
    public void openSettings() {
        startActivity(new Intent(this, SettingsActivity.class));
    }

    @Override
    public void openTodo() {
        runUnlocked(() -> startActivity(new Intent(this, TodoActivity.class)));
    }

    @Override
    public void openUrl(String url) {
        if (!Agenda.isSafeJoinUrl(url)) return; // only Teams meeting links
        runUnlocked(() -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        .addCategory(Intent.CATEGORY_BROWSABLE));
            } catch (Exception ignored) {
            }
        });
    }

    /** Asks the user to unlock first when StandBy is showing over the lock screen. */
    private void runUnlocked(Runnable r) {
        android.app.KeyguardManager km = (android.app.KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        if (km != null && km.isKeyguardLocked()) {
            km.requestDismissKeyguard(this, new android.app.KeyguardManager.KeyguardDismissCallback() {
                @Override
                public void onDismissSucceeded() {
                    r.run();
                }
            });
        } else {
            r.run();
        }
    }

    @Override
    public void openDreamSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_DREAM_SETTINGS));
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_DISPLAY_SETTINGS));
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void requestPhotoPermission() {
        int asked = getSharedPreferences("standby", MODE_PRIVATE).getInt("photoAsked", 0);
        boolean rationale = false;
        for (String p : PhotoSlideshow.permissions()) {
            rationale |= shouldShowRequestPermissionRationale(p);
        }
        if (asked >= 2 && !rationale) {
            // Android stops showing the dialog after two refusals; open the app's settings instead.
            try {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", getPackageName(), null)));
            } catch (Exception ignored) {
            }
            return;
        }
        getSharedPreferences("standby", MODE_PRIVATE).edit().putInt("photoAsked", asked + 1).apply();
        requestPermissions(PhotoSlideshow.permissions(), 1);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        view.onHostResume();
    }

    @Override
    public void exit() {
        finish();
    }

    @Override
    public boolean isActivity() {
        return true;
    }
}
