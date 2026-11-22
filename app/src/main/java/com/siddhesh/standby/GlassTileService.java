package com.siddhesh.standby;

import android.content.Context;
import android.graphics.drawable.Icon;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** Quick Settings tile: turns the Liquid Glass look on/off. StandBy updates instantly. */
public class GlassTileService extends TileService {

    private boolean isOn() {
        return getSharedPreferences(Settings.FILE, Context.MODE_PRIVATE).getBoolean("glass", true);
    }

    private void refresh() {
        Tile t = getQsTile();
        if (t == null) return;
        boolean on = isOn();
        t.setState(on ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        t.setLabel("Liquid Glass");
        t.setIcon(Icon.createWithResource(this, R.drawable.ic_tile_glass));
        t.updateTile();
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        refresh();
    }

    @Override
    public void onClick() {
        super.onClick();
        getSharedPreferences(Settings.FILE, Context.MODE_PRIVATE).edit().putBoolean("glass", !isOn()).apply();
        refresh();
    }
}
