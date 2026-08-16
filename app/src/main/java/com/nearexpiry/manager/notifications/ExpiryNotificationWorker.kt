package com.nearexpiry.manager.notifications

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nearexpiry.manager.data.local.database.ExpiryDatabase
import com.nearexpiry.manager.utils.PreferencesManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

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
 * SCHEDULING: genuinely periodic (24h), matching [AutoBackupWorker]'s proven
 * pattern — Android re-fires it regardless of what happened in a previous
 * run, so a single bad run can never permanently break the chain. Uses
 * ExistingPeriodicWorkPolicy.KEEP, not REPLACE: [schedule] is called on every
 * app launch, and KEEP means those repeated calls leave an already-scheduled
 * job untouched rather than tearing it down and re-creating it. An earlier
 * version used a self-rescheduling one-time chain with REPLACE, which turned
 * out to never fire in practice — likely because constantly cancelling and
 * re-enqueueing on every app open (this app is opened very frequently for
 * scanning) never gave the OS a stable, durable job to actually run.
 */
@HiltWorker
class ExpiryNotificationWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val database: ExpiryDatabase,
    private val preferencesManager: PreferencesManager
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        runDiagnostic(appContext, database, preferencesManager)
        // Never fail: periodic work re-fires in 24h regardless, and a
        // reported "failure" here doesn't help — issues are already
        // swallowed and reported via NotificationDiagnosticResult.error.
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "expiry_notification_daily"
        private val DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE

        /**
         * Installs the next daily local 8:00 AM alarm. The previous
         * WorkManager periodic request is deliberately replaced because Android
         * may batch periodic work by several hours.
         */
        fun schedule(context: Context) {
            DailyExpiryAlarmScheduler.schedule(context)
        }

        /** Watchdog used by automatic backup; rebuilding the alarm is safe. */
        suspend fun ensureScheduled(context: Context) {
            DailyExpiryAlarmScheduler.schedule(context)
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
                val tiers = linkedMapOf(
                    0 to mutableListOf<com.nearexpiry.manager.data.local.entity.ExpiryItemEntity>(),
                    3 to mutableListOf(),
                    7 to mutableListOf(),
                    15 to mutableListOf()
                )
                for (item in items) {
                    val expiryDate = runCatching { LocalDate.parse(item.expiryDate, DATE_FMT) }.getOrNull()
                    if (expiryDate == null) { unparsable++; continue }
                    val daysLeft = ChronoUnit.DAYS.between(today, expiryDate).toInt()
                    tiers[daysLeft]?.add(item)
                }

                // The daily alarm itself fires at local 8:00 AM. Post every
                // eligible tier from this same delivery path rather than
                // handing it to another best-effort worker that Android can
                // defer beyond the requested time.
                val order = listOf(0, 3, 7, 15)
                var posted = 0
                for (days in order) {
                    val tierItems = tiers[days].orEmpty()
                    tierItems.forEach { item ->
                        NotificationHelper.postItemNotification(context, days, item, projectName)
                    }
                    posted += tierItems.size
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
