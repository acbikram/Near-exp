package com.nearexpiry.manager.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nearexpiry.manager.data.local.database.ExpiryDatabase
import com.nearexpiry.manager.utils.PreferencesManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Executes the expiry check when the local 8:00 AM alarm fires. */
@AndroidEntryPoint
class DailyExpiryAlarmReceiver : BroadcastReceiver() {

    @Inject
    lateinit var database: ExpiryDatabase

    @Inject
    lateinit var preferencesManager: PreferencesManager

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DailyExpiryAlarmScheduler.ACTION_DAILY_EXPIRY_ALARM) return

        // Always re-arm first so a notification/database failure cannot prevent
        // the following day's check.
        DailyExpiryAlarmScheduler.schedule(context)

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                ExpiryNotificationWorker.runDiagnostic(
                    context.applicationContext,
                    database,
                    preferencesManager
                )
            } finally {
                pendingResult.finish()
            }
        }
    }
}

/** Restores the local 8:00 AM schedule after reboot or device clock changes. */
class DailyExpiryScheduleRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        DailyExpiryAlarmScheduler.schedule(context)
    }
}
