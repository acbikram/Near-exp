package com.nearexpiry.manager.notifications

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nearexpiry.manager.utils.AutoBackup
import com.nearexpiry.manager.utils.GoogleDriveBackupManager
import com.nearexpiry.manager.utils.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Uploads an already-created local backup after the network is available. This
 * keeps the internal backup safe even when the noon or midnight Drive attempt
 * is offline, and WorkManager runs the queued upload as soon as connectivity
 * returns.
 */
class GoogleDriveBackupUploadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val name = inputData.getString(KEY_BACKUP_NAME) ?: return@withContext Result.failure()
        try {
            val preferences = PreferencesManager(applicationContext)
            if (!preferences.isGoogleDriveBackupEnabled()) return@withContext Result.success()
            val bytes = AutoBackup.readBackup(applicationContext, name) ?: return@withContext Result.failure()
            GoogleDriveBackupManager(applicationContext, preferences).uploadBackup(name, bytes)
            preferences.clearGoogleDrivePendingBackupIfMatches(name)
            Result.success()
        } catch (_: Exception) {
            // OAuth/network/server failures are usually transient. Retain the
            // name for UI feedback while WorkManager retries with backoff.
            PreferencesManager(applicationContext).setGoogleDrivePendingBackupName(name)
            Result.retry()
        }
    }

    companion object {
        private const val KEY_BACKUP_NAME = "backup_name"
        private const val WORK_PREFIX = "google_drive_backup_"
        private const val WORK_TAG = "google_drive_backup_upload"

        fun enqueue(context: Context, backupName: String) {
            val request = OneTimeWorkRequestBuilder<GoogleDriveBackupUploadWorker>()
                .addTag(WORK_TAG)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .setInputData(androidx.work.workDataOf(KEY_BACKUP_NAME to backupName))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_PREFIX + backupName,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun cancelPending(context: Context) {
            WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG)
        }
    }
}
