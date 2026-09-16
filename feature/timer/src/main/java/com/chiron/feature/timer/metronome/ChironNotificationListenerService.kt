package com.chiron.feature.timer.metronome

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService

/**
 * Lightweight notification listener service required by Android's MediaSessionManager
 * to query active media sessions of other apps (Spotify, YouTube Music, podcasts, etc.)
 * so Chiron can forward earbud skip/previous commands.
 */
class ChironNotificationListenerService : NotificationListenerService() {

    companion object {
        fun isNotificationAccessGranted(context: Context): Boolean {
            val packageName = context.packageName
            val flat = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: return false
            val names = flat.split(":")
            for (name in names) {
                val cn = ComponentName.unflattenFromString(name)
                if (cn != null && cn.packageName == packageName) {
                    return true
                }
            }
            return false
        }
    }
}
