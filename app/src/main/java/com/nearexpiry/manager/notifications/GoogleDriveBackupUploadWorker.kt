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
import com.nearexpiry.manager.R
import com.nearexpiry.manager.utils.AutoBackup
import com.nearexpiry.manager.utils.GoogleDriveBackupManager
import com.nearexpiry.manager.utils.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Uploads an already-created local backup. A manual Drive backup attempts an
 * immediate upload first; this worker remains the durable network-constrained
 * fallback for offline and transient Google Drive failures.
 */
class GoogleDriveBackupUploadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val name = inputData.getString(KEY_BACKUP_NAME) ?: return@withContext Result.failure()
        val attempt = attemptUpload(applicationContext, name)
        when {
            attempt.uploaded -> Result.success()
            // User interaction cannot happen from a Worker. The local file and
            // pending name remain intact until the screen launches consent.
            attempt.authorizationRequired -> Result.success()
            else -> Result.retry()
        }
    }

    companion object {
        data class UploadAttempt(
            val uploaded: Boolean,
            val error: String? = null,
            val authorizationRequired: Boolean = false
        )

        private const val KEY_BACKUP_NAME = "backup_name"
        private const val WORK_PREFIX = "google_drive_backup_"
        private const val WORK_TAG = "google_drive_backup_upload"

        /**
         * Runs a single Drive attempt immediately. Transient errors are handed
         * to WorkManager; a missing user grant is retained for screen-side
         * consent resolution and deliberately not retried in the background.
         */
        suspend fun uploadImmediatelyOrQueue(context: Context, backupName: String): UploadAttempt {
            val attempt = attemptUpload(context.applicationContext, backupName)
            if (!attempt.uploaded && !attempt.authorizationRequired) {
                enqueue(context.applicationContext, backupName)
            }
            return attempt
        }

        private suspend fun attemptUpload(context: Context, backupName: String): UploadAttempt =
            withContext(Dispatchers.IO) {
                val preferences = PreferencesManager(context)
                if (!preferences.isGoogleDriveBackupEnabled()) {
                    return@withContext UploadAttempt(uploaded = true)
                }
                try {
                    val bytes = AutoBackup.readBackup(context, backupName)
                        ?: throw IllegalStateException("The local backup file is unavailable")
                    GoogleDriveBackupManager(context, preferences).uploadBackup(backupName, bytes)
                    preferences.clearGoogleDrivePendingBackupIfMatches(backupName)
                    preferences.clearGoogleDriveLastUploadError()
                    preferences.setGoogleDriveConsentRequired(false)
                    preferences.setGoogleDriveLastSuccess(backupName, System.currentTimeMillis())
                    UploadAttempt(uploaded = true)
                } catch (e: GoogleDriveBackupManager.DriveAuthorizationRequiredException) {
                    val error = context.getString(R.string.google_drive_permission_required)
                    preferences.setGoogleDrivePendingBackupName(backupName)
                    preferences.setGoogleDriveLastUploadError(error)
                    preferences.setGoogleDriveConsentRequired(true)
                    UploadAttempt(uploaded = false, error = error, authorizationRequired = true)
                } catch (e: Exception) {
                    val error = e.message?.take(220) ?: "Google Drive upload could not be completed"
                    preferences.setGoogleDrivePendingBackupName(backupName)
                    preferences.setGoogleDriveLastUploadError(error)
                    UploadAttempt(uploaded = false, error = error)
                }
            }

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
