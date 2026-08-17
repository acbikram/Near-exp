package com.nearexpiry.manager.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.WorkManager
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Owns the local-time expiry-notification sequence. Three distinct exact alarms
 * separate the workday risk tiers instead of delivering every alert at once:
 * 8:00 AM for items expiring today, 8:15 AM for 3-day items, and 8:30 AM for
 * 7-day items. The final stage re-arms the following day's sequence.
 */
object DailyExpiryAlarmScheduler {
    private const val LEGACY_REQUEST_CODE = 8_001
    private const val REQUEST_CODE_TODAY = 8_010
    private const val REQUEST_CODE_THREE_DAYS = 8_011
    private const val REQUEST_CODE_SEVEN_DAYS = 8_012
    private const val FIFTEEN_MINUTES_MILLIS = 15 * 60 * 1_000L

    // Kept for cancelling schedules created by v2.70 and earlier.
    const val ACTION_DAILY_EXPIRY_ALARM = "com.nearexpiry.manager.ACTION_DAILY_EXPIRY_ALARM"
    const val ACTION_EXPIRY_TIER_ALARM = "com.nearexpiry.manager.ACTION_EXPIRY_TIER_ALARM"
    const val EXTRA_DAYS_LEFT = "com.nearexpiry.manager.EXTRA_EXPIRY_DAYS_LEFT"

    const val TODAY_TIER = 0
    const val THREE_DAY_TIER = 3
    const val SEVEN_DAY_TIER = 7

    /** Schedules the next 8:00 / 8:15 / 8:30 AM local-time sequence. */
    fun schedule(context: Context) {
        val appContext = context.applicationContext
        // v2.55 and older used periodic work; v2.70 and older used one exact
        // alarm that delivered every tier at 8:00 AM. Cancel both first.
        WorkManager.getInstance(appContext).cancelUniqueWork(ExpiryNotificationWorker.WORK_NAME)
        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
        cancelLegacyAlarm(alarmManager, appContext)
        cancelTierAlarms(alarmManager, appContext)

        val now = LocalDateTime.now()
        val todayAtEight = now.toLocalDate().atTime(8, 0)
        val todayAtEightFifteen = todayAtEight.plusMinutes(15)
        val todayAtEightThirty = todayAtEight.plusMinutes(30)

        when {
            now.isBefore(todayAtEight) -> {
                scheduleAlarm(alarmManager, toMillis(todayAtEight), tierPendingIntent(appContext, TODAY_TIER))
                scheduleAlarm(alarmManager, toMillis(todayAtEightFifteen), tierPendingIntent(appContext, THREE_DAY_TIER))
                scheduleAlarm(alarmManager, toMillis(todayAtEightThirty), tierPendingIntent(appContext, SEVEN_DAY_TIER))
            }
            now.isBefore(todayAtEightFifteen) -> {
                // Today's 8:00 wave has already fired; retain the upcoming two.
                scheduleAlarm(alarmManager, toMillis(todayAtEightFifteen), tierPendingIntent(appContext, THREE_DAY_TIER))
                scheduleAlarm(alarmManager, toMillis(todayAtEightThirty), tierPendingIntent(appContext, SEVEN_DAY_TIER))
            }
            now.isBefore(todayAtEightThirty) -> {
                // Keep the final quiet seven-day reminder when the app is opened
                // between the first two alert stages.
                scheduleAlarm(alarmManager, toMillis(todayAtEightThirty), tierPendingIntent(appContext, SEVEN_DAY_TIER))
            }
            else -> {
                val tomorrowAtEight = todayAtEight.plusDays(1)
                scheduleAlarm(alarmManager, toMillis(tomorrowAtEight), tierPendingIntent(appContext, TODAY_TIER))
                scheduleAlarm(alarmManager, toMillis(tomorrowAtEight.plusMinutes(15)), tierPendingIntent(appContext, THREE_DAY_TIER))
                scheduleAlarm(alarmManager, toMillis(tomorrowAtEight.plusMinutes(30)), tierPendingIntent(appContext, SEVEN_DAY_TIER))
            }
        }
    }

    /** Whether Android will currently permit precise alarms. */
    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.applicationContext.getSystemService(AlarmManager::class.java)
        return alarmManager?.canScheduleExactAlarms() == true
    }

    private fun scheduleAlarm(
        alarmManager: AlarmManager,
        triggerAtMillis: Long,
        operation: PendingIntent
    ) {
        try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms() -> {
                    // Android 12+ needs user-granted exact-alarm access. Keep a
                    // best-effort wakeup until the user enables that access.
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        operation
                    )
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        operation
                    )
                }
                else -> alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
            }
        } catch (_: SecurityException) {
            // Defensive fallback for OEMs that misreport exact-alarm access.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
        }
    }

    private fun cancelLegacyAlarm(alarmManager: AlarmManager, context: Context) {
        alarmManager.cancel(
            PendingIntent.getBroadcast(
                context,
                LEGACY_REQUEST_CODE,
                Intent(context, DailyExpiryAlarmReceiver::class.java).apply {
                    action = ACTION_DAILY_EXPIRY_ALARM
                },
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            ) ?: return
        )
    }

    private fun cancelTierAlarms(alarmManager: AlarmManager, context: Context) {
        listOf(TODAY_TIER, THREE_DAY_TIER, SEVEN_DAY_TIER).forEach { tier ->
            alarmManager.cancel(tierPendingIntent(context, tier))
        }
    }

    private fun tierPendingIntent(context: Context, daysLeft: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCodeFor(daysLeft),
            Intent(context, DailyExpiryAlarmReceiver::class.java).apply {
                action = ACTION_EXPIRY_TIER_ALARM
                putExtra(EXTRA_DAYS_LEFT, daysLeft)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun requestCodeFor(daysLeft: Int): Int = when (daysLeft) {
        TODAY_TIER -> REQUEST_CODE_TODAY
        THREE_DAY_TIER -> REQUEST_CODE_THREE_DAYS
        SEVEN_DAY_TIER -> REQUEST_CODE_SEVEN_DAYS
        else -> error("Unsupported expiry notification tier: $daysLeft")
    }

    internal fun nextEightAmMillis(now: LocalDateTime = LocalDateTime.now()): Long {
        val todayAtEight = now.toLocalDate().atTime(8, 0)
        val target = if (now.isBefore(todayAtEight)) todayAtEight else todayAtEight.plusDays(1)
        return toMillis(target)
    }

    private fun toMillis(dateTime: LocalDateTime): Long =
        dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
