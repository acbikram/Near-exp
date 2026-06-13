package com.nearexpiry.manager.notifications

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nearexpiry.manager.data.local.database.ExpiryDatabase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

/**
 * Runs once per day at ~8 AM local time.
 *
 * Scans all items in the database and posts:
 *  • Soft notification  — items expiring exactly 15 days from today
 *  • Soft notification  — items expiring exactly 7 days from today
 *  • Hard notification  — items expiring exactly 3 days from today
 *
 * Items already deleted from the database are never notified (they're gone).
 * Quantity shown is always the current/latest value from the database.
 */
@HiltWorker
class ExpiryNotificationWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val database: ExpiryDatabase
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "expiry_notification_daily"
        private val DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE

        /**
         * Schedules (or re-schedules) the daily worker.
         * Uses KEEP policy so an existing schedule survives app restarts.
         */
        fun schedule(context: Context) {
            val initialDelayMs = millisUntilNextEightAm()
            val request = PeriodicWorkRequestBuilder<ExpiryNotificationWorker>(
                repeatInterval = 24,
                repeatIntervalTimeUnit = TimeUnit.HOURS
            )
                .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP, // don't reset the 8 AM offset on every launch
                request
            )
        }

        /** Milliseconds until the next 8:00 AM in the device's local time zone. */
        private fun millisUntilNextEightAm(): Long {
            val now = LocalDateTime.now()
            val eightAmToday = now.toLocalDate().atTime(8, 0)
            val target = if (now.isBefore(eightAmToday)) eightAmToday else eightAmToday.plusDays(1)
            return ChronoUnit.MILLIS.between(now, target).coerceAtLeast(0L)
        }
    }

    override suspend fun doWork(): Result {
        // Skip silently if POST_NOTIFICATIONS permission hasn't been granted yet
        // (Android 13+). The channel still exists; the user can grant it later.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                appContext,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return Result.success()
        }

        NotificationHelper.createChannels(appContext)

        val today = LocalDate.now()
        val dao   = database.expiryItemDao()
        val items = dao.getAllItemsOnce()

        for (item in items) {
            val expiryDate = runCatching { LocalDate.parse(item.expiryDate, DATE_FMT) }
                .getOrNull() ?: continue  // skip malformed dates

            val daysLeft = ChronoUnit.DAYS.between(today, expiryDate)

            when (daysLeft.toInt()) {
                15   -> NotificationHelper.postSoftNotification(appContext, item, 15)
                7    -> NotificationHelper.postSoftNotification(appContext, item, 7)
                3    -> NotificationHelper.postHardNotification(appContext, item)
            }
        }

        return Result.success()
    }
}
