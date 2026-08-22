package com.nearexpiry.manager.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nearexpiry.manager.utils.AutoBackup
import com.nearexpiry.manager.utils.PreferencesManager
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Writes an all-project local JSON backup at the noon and midnight slots. Each
 * successful local snapshot is independently queued for optional Google Drive
 * upload; Drive availability never makes the internal backup fail.
 */
class AutoBackupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val name = AutoBackup.run(applicationContext)
            val preferences = PreferencesManager(applicationContext)
            if (preferences.isGoogleDriveBackupEnabled()) {
                preferences.setGoogleDrivePendingBackupName(name)
                GoogleDriveBackupUploadWorker.enqueue(applicationContext, name)
            }
            // Preserve the existing notification scheduling watchdog.
            ExpiryNotificationWorker.ensureScheduled(applicationContext)
            Result.success()
        } catch (_: Exception) {
            // The app database remains the source of truth. A future scheduled
            // slot will attempt the local backup again without a retry loop.
            Result.success()
        }
    }

    companion object {
        private const val LEGACY_WORK_NAME = "daily_auto_backup"
        private const val NOON_WORK_NAME = "auto_backup_noon"
        private const val MIDNIGHT_WORK_NAME = "auto_backup_midnight"

        /** Schedules recurring backups at the next local noon and midnight. */
        fun schedule(context: Context) {
            val workManager = WorkManager.getInstance(context)
            // Remove the former one-a-day schedule when users upgrade.
            workManager.cancelUniqueWork(LEGACY_WORK_NAME)
            scheduleAt(context, NOON_WORK_NAME, LocalTime.NOON)
            scheduleAt(context, MIDNIGHT_WORK_NAME, LocalTime.MIDNIGHT)
        }

        private fun scheduleAt(context: Context, workName: String, time: LocalTime) {
            val now = LocalDateTime.now()
            var next = now.toLocalDate().atTime(time)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                workName,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
