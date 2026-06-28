package com.nearexpiry.manager.notifications

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.nearexpiry.manager.data.local.database.ExpiryDatabase
import com.nearexpiry.manager.utils.PreferencesManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Posts ONE grouped notification for a single expiry tier (today / 3 / 7 / 15
 * days), after an optional stagger delay so tiers arrive spread out rather
 * than all at once.
 *
 * Re-reads the items by id at fire time so quantities/names are current and
 * any deleted items are dropped. Scoped to whatever the active project's
 * items were when enqueued (ids are passed in); the project name is looked
 * up fresh for the label.
 */
@HiltWorker
class TierNotificationWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted params: WorkerParameters,
    private val database: ExpiryDatabase,
    private val preferencesManager: PreferencesManager
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_DAYS = "tier_days"
        const val KEY_ITEM_IDS = "tier_item_ids"

        fun enqueue(context: Context, days: Int, itemIds: List<Long>, delayMinutes: Long) {
            val request = OneTimeWorkRequestBuilder<TierNotificationWorker>()
                .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                .setInputData(
                    workDataOf(
                        KEY_DAYS to days,
                        KEY_ITEM_IDS to itemIds.toLongArray()
                    )
                )
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }

    override suspend fun doWork(): Result {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                appContext, android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return Result.success()
        }

        val days = inputData.getInt(KEY_DAYS, -1)
        val ids = inputData.getLongArray(KEY_ITEM_IDS)?.toList().orEmpty()
        if (days < 0 || ids.isEmpty()) return Result.success()

        NotificationHelper.createChannels(appContext)

        val dao = database.expiryItemDao()
        // Re-fetch current rows; drop any that were deleted in the meantime.
        val items = ids.mapNotNull { dao.getItemById(it) }
        if (items.isEmpty()) return Result.success()

        val projectId = items.first().projectId
        val projectName = database.projectDao().getProjectById(projectId)?.name ?: ""

        // Individual notification per item (fired together as this tier's wave).
        items.forEach { item ->
            NotificationHelper.postItemNotification(appContext, days, item, projectName)
        }
        return Result.success()
    }
}
