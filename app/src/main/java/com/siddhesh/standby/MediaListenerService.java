package com.siddhesh.standby;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/**
 * Exists only so the user can grant "Notification access", which Android requires before an app
 * may read media sessions (song title, artist, album art) and control playback.
 *
 * Privacy: notification contents are deliberately ignored — nothing is read, stored or sent.
 */
public class MediaListenerService extends NotificationListenerService {
    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        // intentionally ignored
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        // intentionally ignored
    }
}
