package com.nearexpiry.manager.notifications

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nearexpiry.manager.data.local.database.ExpiryDatabase
import com.nearexpiry.manager.data.local.entity.ExpiryItemEntity
import com.nearexpiry.manager.utils.PreferencesManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** Result of a manual notification diagnostic from Settings. */
data class NotificationDiagnosticResult(
    val permissionGranted: Boolean,
    val projectName: String,
    val totalItemsInProject: Int,
    val itemsWithUnparsableDates: Int,
    val tierCounts: Map<Int, Int>,
    val notificationsPosted: Int,
    val error: String? = null
)

/**
 * Contains the database and channel-routing logic shared by staged exact alarms
 * and the Settings diagnostic action. Exact scheduling is owned by
 * [DailyExpiryAlarmScheduler], not this worker.
 */
@HiltWorker
class ExpiryNotificationWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val database: ExpiryDatabase,
    private val preferencesManager: PreferencesManager
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        // Defensive handling for any stale legacy WorkManager request. Normal
        // production delivery is performed by the exact staged alarms.
        runTier(appContext, database, preferencesManager, DailyExpiryAlarmScheduler.TODAY_TIER)
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "expiry_notification_daily"
        private val DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE
        private val TRACKED_TIERS = listOf(
            DailyExpiryAlarmScheduler.TODAY_TIER,
            DailyExpiryAlarmScheduler.THREE_DAY_TIER,
            DailyExpiryAlarmScheduler.SEVEN_DAY_TIER
        )

        /** Installs the next staged local-time notification sequence. */
        fun schedule(context: Context) {
            DailyExpiryAlarmScheduler.schedule(context)
        }

        /** Watchdog used by automatic backup; rebuilding the alarms is safe. */
        suspend fun ensureScheduled(context: Context) {
            DailyExpiryAlarmScheduler.schedule(context)
        }

        /** Posts only one risk tier for its corresponding exact alarm. */
        suspend fun runTier(
            context: Context,
            database: ExpiryDatabase,
            preferencesManager: PreferencesManager,
            daysLeft: Int
        ): NotificationDiagnosticResult = runNotifications(
            context = context,
            database = database,
            preferencesManager = preferencesManager,
            tiersToPost = setOf(daysLeft)
        )

        /**
         * Manual Settings diagnostic. It intentionally previews all currently
         * supported stages; scheduled daily delivery never calls this method.
         */
        suspend fun runDiagnostic(
            context: Context,
            database: ExpiryDatabase,
            preferencesManager: PreferencesManager
        ): NotificationDiagnosticResult = runNotifications(
            context = context,
            database = database,
            preferencesManager = preferencesManager,
            tiersToPost = TRACKED_TIERS.toSet()
        )

        private suspend fun runNotifications(
            context: Context,
            database: ExpiryDatabase,
            preferencesManager: PreferencesManager,
            tiersToPost: Set<Int>
        ): NotificationDiagnosticResult {
            try {
                val permissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.POST_NOTIFICATIONS
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
                val tiers = TRACKED_TIERS.associateWith { mutableListOf<ExpiryItemEntity>() }
                for (item in items) {
                    val expiryDate = runCatching { LocalDate.parse(item.expiryDate, DATE_FMT) }.getOrNull()
                    if (expiryDate == null) {
                        unparsable++
                        continue
                    }
                    val daysLeft = ChronoUnit.DAYS.between(today, expiryDate).toInt()
                    tiers[daysLeft]?.add(item)
                }

                var posted = 0
                TRACKED_TIERS.forEach { daysLeft ->
                    if (daysLeft !in tiersToPost) return@forEach
                    val tierItems = tiers[daysLeft].orEmpty()
                    tierItems.forEach { item ->
                        NotificationHelper.postItemNotification(context, daysLeft, item, projectName)
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
                return NotificationDiagnosticResult(
                    false,
                    "",
                    0,
                    0,
                    emptyMap(),
                    0,
                    error = e.message ?: e.toString()
                )
            }
        }
    }
}
