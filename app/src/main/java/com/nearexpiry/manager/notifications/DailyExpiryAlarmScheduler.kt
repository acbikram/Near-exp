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
 * Owns the daily local-time trigger for expiry notifications. WorkManager's
 * periodic work is intentionally not used here because Android may batch it by
 * several hours; an alarm is re-armed after every delivery instead.
 */
object DailyExpiryAlarmScheduler {
    private const val REQUEST_CODE = 8_001
    const val ACTION_DAILY_EXPIRY_ALARM = "com.nearexpiry.manager.ACTION_DAILY_EXPIRY_ALARM"

    /** Schedules the next 8:00 AM in the device's current local time zone. */
    fun schedule(context: Context) {
        val appContext = context.applicationContext
        // v2.55 and older used this unique periodic work name. Cancel it first
        // so a stale, system-batched request cannot still post a late duplicate
        // after the exact local-time alarm is introduced.
        WorkManager.getInstance(appContext).cancelUniqueWork(ExpiryNotificationWorker.WORK_NAME)
        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
        val triggerAtMillis = nextEightAmMillis()
        val operation = alarmPendingIntent(appContext)

        // Rebuilding from the current time zone on every app start, boot, time
        // change, and delivery prevents a fixed UTC offset from drifting after
        // travel or daylight-saving transitions.
        alarmManager.cancel(operation)
        try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms() -> {
                    // Android 12+ requires the user-granted exact-alarm access.
                    // Keep a best-effort fallback active until that access is
                    // enabled from Settings, rather than losing alerts entirely.
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
            // Defensive fallback for OEMs that report exact-alarm access but
            // reject the exact request. The Settings action can then restore
            // precise delivery without suppressing notifications in the meantime.
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                operation
            )
        }
    }

    /** Whether Android will currently permit a precise 8:00 AM alarm. */
    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.applicationContext.getSystemService(AlarmManager::class.java)
        return alarmManager?.canScheduleExactAlarms() == true
    }

    private fun alarmPendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, DailyExpiryAlarmReceiver::class.java).apply {
                action = ACTION_DAILY_EXPIRY_ALARM
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    internal fun nextEightAmMillis(now: LocalDateTime = LocalDateTime.now()): Long {
        val todayAtEight = now.toLocalDate().atTime(8, 0)
        val target = if (now.isBefore(todayAtEight)) todayAtEight else todayAtEight.plusDays(1)
        return target.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
}
