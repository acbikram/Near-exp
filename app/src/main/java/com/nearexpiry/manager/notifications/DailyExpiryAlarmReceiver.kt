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

/** Delivers one scheduled expiry-notification risk tier. */
@AndroidEntryPoint
class DailyExpiryAlarmReceiver : BroadcastReceiver() {

    @Inject
    lateinit var database: ExpiryDatabase

    @Inject
    lateinit var preferencesManager: PreferencesManager

    override fun onReceive(context: Context, intent: Intent) {
        val tier = when (intent.action) {
            // A defensive migration path for a legacy alarm that was already
            // armed before the staged schedule was installed.
            DailyExpiryAlarmScheduler.ACTION_DAILY_EXPIRY_ALARM -> DailyExpiryAlarmScheduler.TODAY_TIER
            DailyExpiryAlarmScheduler.ACTION_EXPIRY_TIER_ALARM -> intent.getIntExtra(
                DailyExpiryAlarmScheduler.EXTRA_DAYS_LEFT,
                -1
            )
            else -> return
        }
        if (tier !in setOf(
                DailyExpiryAlarmScheduler.TODAY_TIER,
                DailyExpiryAlarmScheduler.THREE_DAY_TIER,
                DailyExpiryAlarmScheduler.SEVEN_DAY_TIER
            )
        ) return

        // The 8:30 AM stage is the final delivery in today's sequence. Re-arm
        // only then so today's 8:15/8:30 alarms are not accidentally cancelled.
        if (tier == DailyExpiryAlarmScheduler.SEVEN_DAY_TIER ||
            intent.action == DailyExpiryAlarmScheduler.ACTION_DAILY_EXPIRY_ALARM
        ) {
            DailyExpiryAlarmScheduler.schedule(context)
        }

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                ExpiryNotificationWorker.runTier(
                    context = context.applicationContext,
                    database = database,
                    preferencesManager = preferencesManager,
                    daysLeft = tier
                )
            } finally {
                pendingResult.finish()
            }
        }
    }
}

/** Restores the local-time sequence after reboot or device clock changes. */
class DailyExpiryScheduleRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        DailyExpiryAlarmScheduler.schedule(context)
    }
}
