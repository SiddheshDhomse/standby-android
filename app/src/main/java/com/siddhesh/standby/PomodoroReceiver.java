package com.siddhesh.standby;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Fires when a Pomodoro session ends, even if StandBy isn't on screen. Not exported. */
public class PomodoroReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Settings s = new Settings(context);
        Pomodoro p = new Pomodoro(context, context.getResources().getDisplayMetrics().density);
        p.tick(System.currentTimeMillis() + 500, s);
    }
}
