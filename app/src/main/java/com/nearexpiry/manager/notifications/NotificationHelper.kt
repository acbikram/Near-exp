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
    // Per-tier grouped notifications use a fixed id per (threshold) so each tier
    // is a single notification. Offset by project to avoid cross-project clashes
    // is unnecessary since only the active project notifies at a time.
    fun notifId(itemId: Long, daysLeft: Int): Int =
        ((itemId and 0xFFFFF) * 100 + daysLeft).toInt()

    /**
     * Posts ONE notification for a single [item] at the given [daysLeft] tier
     * (0 today, 3, 7, or 15). Most-urgent tiers (0 and 3 days) use the
     * hard/high-priority channel; 7 and 15 days use the soft channel. A
     * "Remind me tomorrow" action is added for the 3/7/15-day tiers (not for
     * the already-expiring "today" tier).
     */
    fun postItemNotification(
        context: Context,
        daysLeft: Int,
        item: ExpiryItemEntity,
        projectName: String
    ) {
        val isHard = daysLeft <= 3
        val channel = if (isHard) CHANNEL_HARD else CHANNEL_SOFT

        val name = displayName(item)
        val qty = formatQty(context, item.quantity, item.unit)
        val titleBase = when (daysLeft) {
            0    -> context.getString(R.string.notif_item_today_format, name)
            else -> context.getString(R.string.notif_item_days_format, name, daysLeft)
        }
        val title = withProject(titleBase, projectName)
        val body = context.getString(R.string.notif_item_body_format, qty, item.expiryDate)

        val builder = baseBuilder(context, channel, title, body)
            .setPriority(if (isHard) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_DEFAULT)

        if (isHard) {
            builder.setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVibrate(longArrayOf(0, 300, 150, 300))
        }

        // "Remind me tomorrow" for 3/7/15-day items (not the already-expiring today tier).
        if (daysLeft >= 3) {
            builder.addAction(snoozeItemAction(context, item.id, daysLeft))
        }

        NotificationManagerCompat.from(context).notify(notifId(item.id, daysLeft), builder.build())
    }

    /** Prefixes a notification title with the project name when present. */
    private fun withProject(title: String, projectName: String): String =
        if (projectName.isBlank()) title else "[$projectName] $title"

    /** Notifies that a newer app version is available; tapping opens the update screen. */
    fun postUpdateAvailableNotification(context: Context, versionName: String) {
        createChannels(context)
        val title = context.getString(R.string.notif_update_title)
        val body = context.getString(R.string.notif_update_body_format, versionName)

        // Tapping opens MainActivity with an extra that routes to Settings → App Updates.
        val launchIntent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("open_updates", true)
            }
        val contentPi = launchIntent?.let {
            PendingIntent.getActivity(
                context, 801_000, it,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        val builder = baseBuilder(context, CHANNEL_SOFT, title, body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        if (contentPi != null) builder.setContentIntent(contentPi)
        NotificationManagerCompat.from(context).notify(800_000, builder.build())
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * "Remind me tomorrow" action for a single item. Re-shows that item
     * ~24h later via [SnoozeActionReceiver] → [SnoozedReminderWorker].
     */
    private fun snoozeItemAction(context: Context, itemId: Long, daysLeft: Int): NotificationCompat.Action {
        val snoozeIntent = Intent(context, SnoozeActionReceiver::class.java).apply {
            action = SnoozeActionReceiver.ACTION_SNOOZE
            putExtra(SnoozeActionReceiver.EXTRA_ITEM_ID, itemId)
            putExtra(SnoozeActionReceiver.EXTRA_DAYS_LEFT, daysLeft)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            notifId(itemId, daysLeft),
            snoozeIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Action.Builder(
            R.drawable.ic_launcher_foreground,
            context.getString(R.string.notif_action_snooze),
            pendingIntent
        ).build()
    }

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
