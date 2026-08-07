package com.nearexpiry.manager.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.nearexpiry.manager.R
import com.nearexpiry.manager.utils.AppUpdater

/**
 * Downloads an app-update APK in the background, with a visible progress
 * notification, so it keeps going even if the user leaves the Settings
 * screen or backgrounds the app entirely. On success, replaces the progress
 * notification with a "Download complete — tap to install" one; tapping it
 * launches the system installer directly via [UpdateInstallReceiver], with no
 * need to reopen the app. Does NOT auto-install — the user always taps
 * either the notification or the in-app "Install Now" button.
 *
 * Not a HiltWorker — only needs a Context (AppUpdater/NotificationHelper are
 * plain objects), so the default WorkerFactory can build it, same as
 * [AutoBackupWorker].
 */
class UpdateDownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val apkUrl = inputData.getString(KEY_APK_URL) ?: return Result.failure()
        val versionName = inputData.getString(KEY_VERSION_NAME) ?: return Result.failure()

        // The download's own progress notification takes over from here —
        // clear the "Update Available" one immediately so they don't sit
        // side by side.
        NotificationHelper.cancelUpdateAvailableNotification(applicationContext)
        setForeground(foregroundInfo(0))

        var lastNotifiedPercent = -1
        return try {
            AppUpdater.download(applicationContext, apkUrl, versionName) { fraction ->
                val percent = (fraction * 100).toInt()
                if (percent != lastNotifiedPercent) {
                    lastNotifiedPercent = percent
                    setProgressAsync(workDataOf(KEY_PROGRESS_PERCENT to percent))
                    NotificationManagerCompat.from(applicationContext)
                        .notify(PROGRESS_NOTIF_ID, progressNotification(percent))
                }
            }
            NotificationManagerCompat.from(applicationContext).cancel(PROGRESS_NOTIF_ID)
            postCompleteNotification(versionName)
            Result.success(workDataOf(KEY_VERSION_NAME to versionName))
        } catch (e: Exception) {
            NotificationManagerCompat.from(applicationContext).cancel(PROGRESS_NOTIF_ID)
            postErrorNotification(e.message ?: "Download failed")
            Result.failure(workDataOf(KEY_ERROR to (e.message ?: "Download failed")))
        }
    }

    private fun foregroundInfo(percent: Int): ForegroundInfo {
        val notification = progressNotification(percent)
        return ForegroundInfo(
            PROGRESS_NOTIF_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    private fun progressNotification(percent: Int) =
        NotificationCompat.Builder(applicationContext, NotificationHelper.CHANNEL_SOFT)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(applicationContext.getString(R.string.notif_downloading_update_title))
            .setContentText(applicationContext.getString(R.string.downloading_percent_format, percent))
            .setProgress(100, percent, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun postCompleteNotification(versionName: String) {
        val installIntent = Intent(applicationContext, UpdateInstallReceiver::class.java).apply {
            action = UpdateInstallReceiver.ACTION_INSTALL
            putExtra(UpdateInstallReceiver.EXTRA_VERSION_NAME, versionName)
        }
        val installPi = PendingIntent.getBroadcast(
            applicationContext, 802_001, installIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(applicationContext, NotificationHelper.CHANNEL_SOFT)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(applicationContext.getString(R.string.notif_update_downloaded_title))
            .setContentText(applicationContext.getString(R.string.notif_update_downloaded_body_format, versionName))
            .setContentIntent(installPi)
            .setAutoCancel(true)
            .addAction(R.drawable.ic_launcher_foreground, applicationContext.getString(R.string.install_update), installPi)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(COMPLETE_NOTIF_ID, notification)
    }

    private fun postErrorNotification(message: String) {
        val notification = NotificationCompat.Builder(applicationContext, NotificationHelper.CHANNEL_SOFT)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(applicationContext.getString(R.string.notif_update_failed_title))
            .setContentText(message)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(COMPLETE_NOTIF_ID, notification)
    }

    companion object {
        const val WORK_NAME = "update_download"
        const val KEY_APK_URL = "apk_url"
        const val KEY_VERSION_NAME = "version_name"
        const val KEY_PROGRESS_PERCENT = "progress_percent"
        const val KEY_ERROR = "error"
        private const val PROGRESS_NOTIF_ID = 802_100
        private const val COMPLETE_NOTIF_ID = 802_200

        /** Enqueues the background download. Replaces any prior attempt for a
         *  different version; a same-version retry just re-attaches. */
        fun enqueue(context: Context, apkUrl: String, versionName: String) {
            val request = OneTimeWorkRequestBuilder<UpdateDownloadWorker>()
                .setInputData(
                    Data.Builder()
                        .putString(KEY_APK_URL, apkUrl)
                        .putString(KEY_VERSION_NAME, versionName)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME, ExistingWorkPolicy.REPLACE, request
            )
        }
    }
}
