package com.nearexpiry.manager.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.nearexpiry.manager.R
import com.nearexpiry.manager.data.local.entity.ExpiryItemEntity
import com.nearexpiry.manager.presentation.MainActivity

object NotificationHelper {

    // ── Channel IDs ──────────────────────────────────────────────────────────
    const val CHANNEL_SOFT = "near_expiry_soft"
    const val CHANNEL_HARD = "near_expiry_hard"

    // ── Notification ID helpers ───────────────────────────────────────────────
    // Encode (itemId, threshold) into a unique Int for the notification manager.
    fun notifId(itemId: Long, daysLeft: Int): Int =
        ((itemId and 0xFFFFF) * 100 + daysLeft).toInt()

    /**
     * Must be called once on app start (safe to call multiple times).
     *
     * Note: on Android 13+ (API 33), AppCompatDelegate's per-app language
     * setting updates the system-wide LocaleManager, so context.getString()
     * here returns the correct language automatically. On older API levels,
     * background contexts (WorkManager) may fall back to the device's
     * default locale for these channel names/notification text.
     */
    fun createChannels(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Soft channel — importance DEFAULT: shows in shade, no heads-up, gentle sound
        if (nm.getNotificationChannel(CHANNEL_SOFT) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_SOFT,
                    context.getString(R.string.notif_channel_soft_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = context.getString(R.string.notif_channel_soft_desc)
                    vibrationPattern = longArrayOf(0, 200)
                    enableVibration(true)
                }
            )
        }

        // Hard channel — importance HIGH: heads-up display, loud double vibration
        if (nm.getNotificationChannel(CHANNEL_HARD) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_HARD,
                    context.getString(R.string.notif_channel_hard_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.notif_channel_hard_desc)
                    vibrationPattern = longArrayOf(0, 300, 150, 300) // double-pulse
                    enableVibration(true)
                }
            )
        }
    }

    /** Posts one soft notification for a 15-day or 7-day threshold. */
    fun postSoftNotification(context: Context, item: ExpiryItemEntity, daysLeft: Int) {
        val label = displayName(item)
        val qty   = formatQty(context, item.quantity, item.unit)
        val title = context.getString(R.string.notif_soft_title_format, daysLeft)
        val body  = context.getString(R.string.notif_body_format, label, qty, item.expiryDate)

        val notif = baseBuilder(context, CHANNEL_SOFT, title, body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(context)
            .notify(notifId(item.id, daysLeft), notif)
    }

    /** Posts a high-priority heads-up notification for the 3-day threshold. */
    fun postHardNotification(context: Context, item: ExpiryItemEntity) {
        val label = displayName(item)
        val qty   = formatQty(context, item.quantity, item.unit)
        val title = context.getString(R.string.notif_hard_title)
        val body  = context.getString(R.string.notif_body_format, label, qty, item.expiryDate)

        val notif = baseBuilder(context, CHANNEL_HARD, title, body)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            // Force heads-up by setting a full-screen intent (best-effort; Android
            // may still suppress it if the device is in DND or the screen is on).
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVibrate(longArrayOf(0, 300, 150, 300))  // double-pulse
            .setAutoCancel(false)   // stays in tray until user dismisses
            .build()

        NotificationManagerCompat.from(context)
            .notify(notifId(item.id, 3), notif)
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun baseBuilder(
        context: Context,
        channelId: String,
        title: String,
        body: String
    ): NotificationCompat.Builder {
        val tapIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
    }

    private fun displayName(item: ExpiryItemEntity): String =
        item.productName?.takeIf { it.isNotBlank() }
            ?: item.itemCode?.takeIf { it.isNotBlank() }
            ?: item.barcode

    private fun formatQty(context: Context, qty: Double, unit: String?): String {
        val qtyStr = if (qty % 1.0 == 0.0) qty.toInt().toString() else qty.toString()
        return if (!unit.isNullOrBlank())
            context.getString(R.string.qty_unit_format, qtyStr, unit)
        else
            context.getString(R.string.qty_format, qtyStr)
    }
}
