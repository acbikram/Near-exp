package com.nearexpiry.manager.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nearexpiry.manager.BuildConfig
import com.nearexpiry.manager.utils.AppUpdater
import java.util.concurrent.TimeUnit

/**
 * Periodically (every ~24h) checks GitHub Releases for a newer app version,
 * even when the app isn't open, and posts the "Update available" notification
 * if one is found. Complements the on-launch check in the app.
 *
 * Not a HiltWorker — it needs no injected deps (AppUpdater and BuildConfig
 * are static), so the default WorkerFactory can build it.
 */
class UpdateCheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val preferencesManager = com.nearexpiry.manager.utils.PreferencesManager(applicationContext)
            val result = AppUpdater.check(
                currentVersionCode = BuildConfig.VERSION_CODE.toLong(),
                currentVersionName = BuildConfig.VERSION_NAME
            )
            if (result is AppUpdater.CheckResult.UpdateAvailable) {
                preferencesManager.setLastNotifiedUpdateVersionCode(result.info.versionCode)
                NotificationHelper.postUpdateAvailableNotification(
                    applicationContext, result.info.versionName
                )
            } else {
                NotificationHelper.cancelUpdateAvailableNotification(applicationContext)
                preferencesManager.setLastNotifiedUpdateVersionCode(0L)
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "update_check_periodic"

        /** Schedules the daily update check (idempotent; keeps existing schedule). */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(24, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
