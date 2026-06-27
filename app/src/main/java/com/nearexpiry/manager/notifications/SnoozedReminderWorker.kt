package com.nearexpiry.manager.notifications

import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nearexpiry.manager.data.local.database.ExpiryDatabase
import com.nearexpiry.manager.utils.ExpiryDateUtils
import com.nearexpiry.manager.utils.PreferencesManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Fires ~24 hours after the user taps "Remind me tomorrow" on a tier expiry
 * notification (see [SnoozeActionReceiver]).
 *
 * Re-evaluates that whole tier for the active project: items deleted in the
 * meantime are dropped, and only items still within (or past) the tier's
 * window are re-notified. If nothing is left relevant, no notification posts.
 */
@HiltWorker
class SnoozedReminderWorker @AssistedInject constructor(
    @Assisted appContext: android.content.Context,
    @Assisted params: WorkerParameters,
    private val database: ExpiryDatabase,
    private val preferencesManager: PreferencesManager
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_DAYS = "days"
    }

    override suspend fun doWork(): Result {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                applicationContext,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return Result.success()
        }

        val days = inputData.getInt(KEY_DAYS, -1)
        if (days < 0) return Result.success()

        NotificationHelper.createChannels(applicationContext)

        val today = LocalDate.now()
        val projectId = preferencesManager.getActiveProjectId()
        val projectName = database.projectDao().getProjectById(projectId)?.name ?: ""
        val items = database.expiryItemDao().getAllItemsOnce(projectId)

        // Re-collect the items that still belong to this tier. For the "today"
        // (0) tier we also keep already-expired items so the reminder still
        // surfaces them; for forward tiers we match the exact day bucket.
        val tierItems = items.filter { item ->
            val expiry = ExpiryDateUtils.parseOrNull(item.expiryDate) ?: return@filter false
            val left = ChronoUnit.DAYS.between(today, expiry).toInt()
            if (days == 0) left <= 0 else left == days
        }
        if (tierItems.isEmpty()) return Result.success()

        NotificationHelper.postTierNotification(applicationContext, days, tierItems, projectName)
        return Result.success()
    }
}
