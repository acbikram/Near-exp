package com.nearexpiry.manager.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/**
 * Handles the "Remind me tomorrow" action button on soft (15/7-day) expiry
 * notifications. Dismisses the current notification and schedules a
 * one-time follow-up reminder ~24 hours later via [SnoozedReminderWorker].
 *
 * Registered in AndroidManifest.xml as a non-exported receiver. Doesn't
 * need Hilt injection itself — it only enqueues WorkManager work; the
 * worker is created via the app's HiltWorkerFactory.
 */
class SnoozeActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_SNOOZE = "com.nearexpiry.manager.ACTION_SNOOZE_REMINDER"
        const val EXTRA_ITEM_ID = "extra_item_id"
        const val EXTRA_DAYS_LEFT = "extra_days_left"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SNOOZE) return

        val itemId = intent.getLongExtra(EXTRA_ITEM_ID, -1L)
        val daysLeft = intent.getIntExtra(EXTRA_DAYS_LEFT, -1)
        if (itemId == -1L || daysLeft == -1) return

        // Dismiss the notification the user just snoozed.
        NotificationManagerCompat.from(context).cancel(NotificationHelper.notifId(itemId, daysLeft))

        // Re-check this single item ~24 hours from now and re-notify if it's
        // still relevant (item not deleted, not yet expired).
        val request = OneTimeWorkRequestBuilder<SnoozedReminderWorker>()
            .setInitialDelay(24, TimeUnit.HOURS)
            .setInputData(
                workDataOf(SnoozedReminderWorker.KEY_ITEM_ID to itemId)
            )
            .build()

        WorkManager.getInstance(context).enqueue(request)
    }
}
