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
import androidx.work.await
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
 * Result of one diagnostic/production notification-check run, for the
 * "Test Expiry Notification Now" button in Settings. Top-level (not nested
 * in a companion object) so it can be referenced unambiguously from other
 * files as a plain type.
 */
data class NotificationDiagnosticResult(
    val permissionGranted: Boolean,
    val projectName: String,
    val totalItemsInProject: Int,
    val itemsWithUnparsableDates: Int,
    val tierCounts: Map<Int, Int>,   // 0/3/7/15 -> count
    val notificationsPosted: Int,
    val error: String? = null
)

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

    override suspend fun doWork(): Result {
        // Lock in tomorrow's 8 AM run FIRST, before anything else. This is
        // what makes the chain unbreakable: even if the OS kills this job
        // partway through (common on aggressive-battery-management phones
        // like Honor/Huawei) or a database read fails below, tomorrow's slot
        // is already scheduled and the daily notifications keep coming.
        schedule(appContext)
        runDiagnostic(appContext, database, preferencesManager)
        return Result.success()
    }

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

        /**
         * Watchdog: re-arms the daily chain only if it's not currently
         * scheduled or running. Safe to call anytime — does nothing if the
         * chain is already alive, so it can't cause duplicate notifications.
         * Called from [AutoBackupWorker]'s periodic run as a safety net.
         */
        suspend fun ensureScheduled(context: Context) {
            val infos = WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(WORK_NAME)
                .await()
            val alive = infos.any {
                !it.state.isFinished || it.state == androidx.work.WorkInfo.State.ENQUEUED
            }
            if (!alive) schedule(context)
        }

        /**
         * The actual check-and-notify logic, shared by the daily worker and
         * the "Test Expiry Notification Now" button in Settings so the test
         * exercises the exact same code path as production — same permission
         * check, same active project, same tier matching, same posting calls.
         */
        suspend fun runDiagnostic(
            context: Context,
            database: ExpiryDatabase,
            preferencesManager: PreferencesManager
        ): NotificationDiagnosticResult {
            try {
                val permissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ContextCompat.checkSelfPermission(
                        context, android.Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                } else true

                if (!permissionGranted) {
                    return NotificationDiagnosticResult(false, "", 0, 0, emptyMap(), 0)
                }

                NotificationHelper.createChannels(context)

                val today = LocalDate.now()
                val dao = database.expiryItemDao()
                val projectId = preferencesManager.getActiveProjectId()
                val projectName = database.projectDao().getProjectById(projectId)?.name ?: "(unknown)"
                val items = dao.getAllItemsOnce(projectId)

                var unparsable = 0
                val tiers = linkedMapOf(0 to mutableListOf<Long>(), 3 to mutableListOf(), 7 to mutableListOf(), 15 to mutableListOf())
                for (item in items) {
                    val expiryDate = runCatching { LocalDate.parse(item.expiryDate, DATE_FMT) }.getOrNull()
                    if (expiryDate == null) { unparsable++; continue }
                    val daysLeft = ChronoUnit.DAYS.between(today, expiryDate).toInt()
                    tiers[daysLeft]?.add(item.id)
                }

                val order = listOf(0, 3, 7, 15)
                var slot = 0
                var posted = 0
                for (days in order) {
                    val ids = tiers[days]?.takeIf { it.isNotEmpty() } ?: continue
                    val delayMin = slot * 15L
                    slot++
                    TierNotificationWorker.enqueue(context, days, ids, delayMin)
                    posted += ids.size
                }

                return NotificationDiagnosticResult(
                    permissionGranted = true,
                    projectName = projectName,
                    totalItemsInProject = items.size,
                    itemsWithUnparsableDates = unparsable,
                    tierCounts = tiers.mapValues { it.value.size },
                    notificationsPosted = posted
                )
            } catch (e: Exception) {
                return NotificationDiagnosticResult(false, "", 0, 0, emptyMap(), 0, error = e.message ?: e.toString())
            }
        }
    }
}
