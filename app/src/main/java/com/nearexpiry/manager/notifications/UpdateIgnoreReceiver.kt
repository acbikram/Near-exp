package com.nearexpiry.manager.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat

/**
 * Handles the "Ignore" action on the update-available notification. Simply
 * dismisses it; the on-launch and daily [UpdateCheckWorker] checks will
 * surface the update again next time (next launch or next day), matching the
 * existing repeat-notification behaviour.
 */
class UpdateIgnoreReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_IGNORE = "com.nearexpiry.manager.ACTION_IGNORE_UPDATE"
        private const val UPDATE_NOTIF_ID = 800_000
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_IGNORE) {
            NotificationManagerCompat.from(context).cancel(UPDATE_NOTIF_ID)
        }
    }
}
