package com.nearexpiry.manager.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nearexpiry.manager.utils.AutoBackup
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Writes the automatic daily backup (all projects → JSON in the public
 * Documents/Near Expiry Backups folder) every day around 12:00 noon. The
 * system may shift the exact run time slightly for battery; if the phone is
 * off at noon, the backup runs when it's next available.
 *
 * Not a HiltWorker — it reaches the database through the app's singleton, so
 * the default WorkerFactory can build it.
 */
class AutoBackupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            AutoBackup.run(applicationContext)
            Result.success()
        } catch (e: Exception) {
            // Storage hiccup — try again on the next daily slot rather than
            // retry-looping (the data is still safe in the app database).
            Result.success()
        }
    }

    companion object {
        private const val WORK_NAME = "daily_auto_backup"

        /** Schedules the daily run, first occurrence at the next 12:00 noon. */
        fun schedule(context: Context) {
            val now = LocalDateTime.now()
            var next = now.toLocalDate().atTime(LocalTime.NOON)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val initialDelay = Duration.between(now, next)

            val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(initialDelay.toMinutes(), TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
