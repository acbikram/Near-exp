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

    /** Stable notification id for a whole tier (today/3/7/15). */
    fun tierNotifId(daysLeft: Int): Int = 700_000 + daysLeft

    /**
     * Posts ONE grouped notification summarizing all [items] in a tier
     * ([daysLeft] = 0 today, 3, 7, or 15). Most-urgent tiers (0 and 3 days)
     * use the hard/high-priority channel; 7 and 15 days use the soft channel.
     * A "Remind me tomorrow" action is added for the 3/7/15-day tiers (not
     * for the already-expiring "today" tier).
     */
    fun postTierNotification(
        context: Context,
        daysLeft: Int,
        items: List<ExpiryItemEntity>,
        projectName: String
    ) {
        if (items.isEmpty()) return

        val isHard = daysLeft <= 3
        val channel = if (isHard) CHANNEL_HARD else CHANNEL_SOFT

        val titleBase = when (daysLeft) {
            0    -> context.getString(R.string.notif_tier_today_format, items.size)
            else -> context.getString(R.string.notif_tier_days_format, items.size, daysLeft)
        }
        val title = withProject(titleBase, projectName)

        // Body: up to 6 item lines, then "+N more".
        val lines = items.take(6).map { item ->
            val name = displayName(item)
            val qty = formatQty(context, item.quantity, item.unit)
            context.getString(R.string.notif_tier_line_format, name, qty, item.expiryDate)
        }
        val inbox = NotificationCompat.InboxStyle().setBigContentTitle(title)
        lines.forEach { inbox.addLine(it) }
        if (items.size > 6) {
            inbox.setSummaryText(context.getString(R.string.notif_tier_more_format, items.size - 6))
        }
        val body = lines.firstOrNull().orEmpty()

        val builder = baseBuilder(context, channel, title, body)
            .setStyle(inbox)
            .setPriority(if (isHard) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_DEFAULT)

        if (isHard) {
            builder.setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVibrate(longArrayOf(0, 300, 150, 300))
        }

        // "Remind me tomorrow" for 3/7/15-day tiers (not the already-expiring today tier).
        if (daysLeft >= 3) {
            builder.addAction(snoozeTierAction(context, daysLeft, projectName))
        }

        NotificationManagerCompat.from(context).notify(tierNotifId(daysLeft), builder.build())
    }

    /** Prefixes a notification title with the project name when present. */
    private fun withProject(title: String, projectName: String): String =
        if (projectName.isBlank()) title else "[$projectName] $title"

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * "Remind me tomorrow" action for a tier notification. Re-shows the same
     * tier ~24h later via [SnoozeActionReceiver] → [SnoozedReminderWorker].
     */
    private fun snoozeTierAction(context: Context, daysLeft: Int, projectName: String): NotificationCompat.Action {
        val snoozeIntent = Intent(context, SnoozeActionReceiver::class.java).apply {
            action = SnoozeActionReceiver.ACTION_SNOOZE
            putExtra(SnoozeActionReceiver.EXTRA_DAYS_LEFT, daysLeft)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            tierNotifId(daysLeft),
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
