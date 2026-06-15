package com.nearexpiry.manager.notifications

import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nearexpiry.manager.data.local.database.ExpiryDatabase
import com.nearexpiry.manager.utils.ExpiryDateUtils
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Fires ~24 hours after the user taps "Remind me tomorrow" on a soft expiry
 * notification (see [SnoozeActionReceiver]).
 *
 * Re-checks the item: if it was deleted (sold/used) in the meantime, or has
 * since expired, no notification is posted — matching the requirement that
 * deleted items never generate notifications and quantity/expiry always
 * reflect the latest edit.
 */
@HiltWorker
class SnoozedReminderWorker @AssistedInject constructor(
    @Assisted appContext: android.content.Context,
    @Assisted params: WorkerParameters,
    private val database: ExpiryDatabase
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_ITEM_ID = "item_id"
        /** Notification id offset used for snoozed reminders, to avoid colliding with the 15/7/3-day ids. */
        private const val SNOOZE_NOTIF_OFFSET = 90
    }

    override suspend fun doWork(): Result {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                applicationContext,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return Result.success()
        }

        val itemId = inputData.getLong(KEY_ITEM_ID, -1L)
        if (itemId == -1L) return Result.success()

        // Item deleted (sold out / put back in store) — nothing to remind about.
        val item = database.expiryItemDao().getItemById(itemId) ?: return Result.success()

        val expiry = ExpiryDateUtils.parseOrNull(item.expiryDate) ?: return Result.success()
        val daysLeft = ChronoUnit.DAYS.between(LocalDate.now(), expiry).toInt()

        // Already expired by the time the reminder fires — the daily worker's
        // own thresholds (or the user already handling it) take over from here.
        if (daysLeft < 0) return Result.success()

        NotificationHelper.createChannels(applicationContext)
        val projectName = database.projectDao().getProjectById(item.projectId)?.name ?: ""
        NotificationHelper.postSnoozedReminder(applicationContext, item, daysLeft, SNOOZE_NOTIF_OFFSET, projectName)

        return Result.success()
    }
}
