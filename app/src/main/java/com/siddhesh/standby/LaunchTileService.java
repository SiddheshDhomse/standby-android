package com.siddhesh.standby;

import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** Quick Settings tile: opens StandBy. */
public class LaunchTileService extends TileService {

    @Override
    public void onStartListening() {
        super.onStartListening();
        Tile t = getQsTile();
        if (t == null) return;
        t.setState(Tile.STATE_INACTIVE);
        t.setLabel("StandBy");
        t.setIcon(Icon.createWithResource(this, R.drawable.ic_tile_standby));
        t.updateTile();
    }

    @Override
    @SuppressWarnings("deprecation")
    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated") // old API only used below Android 14
    public void onClick() {
        super.onClick();
        Intent i = new Intent(this, StandByActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (Build.VERSION.SDK_INT >= 34) {
            // explicit + immutable: nothing else can redirect or modify it
            PendingIntent pi = PendingIntent.getActivity(this, 0, i,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            startActivityAndCollapse(pi);
        } else {
            startActivityAndCollapse(i);
        }
    }
}
