package com.nearexpiry.manager.notifications

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nearexpiry.manager.data.local.database.ExpiryDatabase
import com.nearexpiry.manager.utils.PreferencesManager
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
 * Scans items in the **currently selected project only** and posts:
 *  • Soft notification  — items expiring exactly 15 days from today
 *  • Soft notification  — items expiring exactly 7 days from today
 *  • Hard notification  — items expiring exactly 3 days from today
 *
 * Each notification is labelled with the active project's name. Items in
 * other (non-active) projects are not notified — switching projects changes
 * which inventory gets alerts. Deleted items are never notified; quantity
 * shown is always the current/latest value from the database.
 *
 * SCHEDULING: this is a self-rescheduling ONE-TIME worker, not a periodic
 * one. Each run enqueues the *next* run by computing "the next 8:00 AM" fresh
 * from the actual current time, rather than chaining 24h off the previous
 * scheduled slot. This means a late run (the OS deferring it for battery
 * reasons, as periodic jobs often drift on some phones) never pushes the
 * following day's time later — every day independently re-targets 8:00 AM.
 */
@HiltWorker
class ExpiryNotificationWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val database: ExpiryDatabase,
    private val preferencesManager: PreferencesManager
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "expiry_notification_daily"
        private val DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE

        /**
         * Schedules (or re-schedules) the next run. Called both at app start
         * and at the end of every run, so the chain is self-sustaining and
         * never drifts: each link targets 8:00 AM computed from "now" at
         * enqueue time, not from any previous run's timestamp.
         */
        fun schedule(context: Context) {
            val initialDelayMs = millisUntilNextEightAm()
            val request = OneTimeWorkRequestBuilder<ExpiryNotificationWorker>()
                .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
                .build()

            // REPLACE (not KEEP): re-scheduling must always win, otherwise a
            // stale periodic/one-time schedule from before this fix would
            // never be cleared and the drift would continue.
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
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
            if (!granted) {
                schedule(appContext) // still line up tomorrow's 8 AM run
                return Result.success()
            }
        }

        NotificationHelper.createChannels(appContext)

        val today = LocalDate.now()
        val dao   = database.expiryItemDao()

        // Only the currently selected project gets notifications.
        val projectId = preferencesManager.getActiveProjectId()
        val projectName = database.projectDao().getProjectById(projectId)?.name ?: ""
        val items = dao.getAllItemsOnce(projectId)

        // Bucket items into tiers by exact days-to-expiry.
        val tiers = linkedMapOf(0 to mutableListOf<Long>(), 3 to mutableListOf(), 7 to mutableListOf(), 15 to mutableListOf())
        for (item in items) {
            val expiryDate = runCatching { LocalDate.parse(item.expiryDate, DATE_FMT) }
                .getOrNull() ?: continue
            val daysLeft = ChronoUnit.DAYS.between(today, expiryDate).toInt()
            tiers[daysLeft]?.add(item.id)
        }

        // Fire tiers most-urgent first, staggered 15 min apart so they don't
        // pile into one buzz:  today=0min, 3d=+15, 7d=+30, 15d=+45.
        // Each tier is a separate one-time worker that posts ONE grouped
        // notification. Empty tiers are skipped (and their delay slot is reused
        // by simply not enqueuing them).
        val order = listOf(0, 3, 7, 15)
        var slot = 0
        for (days in order) {
            val ids = tiers[days]?.takeIf { it.isNotEmpty() } ?: continue
            val delayMin = slot * 15L
            slot++
            TierNotificationWorker.enqueue(appContext, days, ids, delayMin)
        }

        // Line up tomorrow's 8 AM run now, computed fresh from the current
        // time — this is what stops any drift from today carrying forward.
        schedule(appContext)

        return Result.success()
    }
}
