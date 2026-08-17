package com.nearexpiry.manager.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import com.nearexpiry.manager.utils.AppUpdater

/**
 * Handles tapping the "Install Now" action on the download-complete
 * notification. Launches the system installer for the already-downloaded
 * APK directly — no need to reopen the app first.
 *
 * Calls startActivity() synchronously (onReceive already runs on the main
 * thread) rather than hopping through a coroutine, so the install reliably
 * launches even if the OS is quick to reclaim the receiver's process.
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
        val file = AppUpdater.downloadedApk(context, versionName) ?: return
        val apkUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(installIntent) }
    }
}
