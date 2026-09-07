package com.nearexpiry.manager.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.nearexpiry.manager.presentation.MainActivity

/**
 * Handles tapping the "Install Now" action on the download-complete
 * notification. Opens the app straight to the Settings screen (same as the
 * "Update Now" flow) with the downloaded version name attached; Settings
 * then hands the already-downloaded APK to the system installer immediately
 * — no extra tap needed once the app is open.
 */
class UpdateInstallReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_INSTALL = "com.nearexpiry.manager.ACTION_INSTALL_UPDATE"
        const val ACTION_DISMISS = "com.nearexpiry.manager.ACTION_DISMISS_UPDATE"
        const val EXTRA_VERSION_NAME = "version_name"
        private const val COMPLETE_NOTIF_ID = 802_200
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_DISMISS) {
            NotificationManagerCompat.from(context).cancel(COMPLETE_NOTIF_ID)
            return
        }
        if (intent.action != ACTION_INSTALL) return
        val versionName = intent.getStringExtra(EXTRA_VERSION_NAME) ?: return
        NotificationManagerCompat.from(context).cancel(COMPLETE_NOTIF_ID)

        // "Install Later" only removes the notification. The cached APK remains
        // available from Settings so it never needs to be downloaded again.
        val launch = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("install_version_name", versionName)
        }
        runCatching { context.startActivity(launch) }
    }
}
