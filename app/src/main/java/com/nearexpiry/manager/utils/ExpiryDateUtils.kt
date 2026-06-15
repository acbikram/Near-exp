package com.nearexpiry.manager.utils

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * Single source of truth for "is this item expiring soon?" logic.
 *
 * Both the Home dashboard ("Expiring in 7d / 30d" counters) and the History
 * screen (7/30-day filters reached by tapping those counters) must agree on
 * exactly which items are included, otherwise the counter and the filtered
 * list it links to can disagree. Previously Home used `isAfter(today)`
 * (excludes items expiring *today*) while History used `!isBefore(today)`
 * (includes items expiring today) – this object fixes that by giving both
 * call sites one shared, inclusive definition.
 */
object ExpiryDateUtils {

    /**
     * Parses [expiryDateStr] (expected ISO-8601, e.g. "2026-06-20") and
     * returns null if it can't be parsed, instead of throwing.
     */
    fun parseOrNull(expiryDateStr: String): LocalDate? =
        try {
            LocalDate.parse(expiryDateStr)
        } catch (e: DateTimeParseException) {
            null
        }

    /**
     * True if [expiryDateStr] falls on [today] or any of the following
     * [days] days (inclusive on both ends). Already-expired items
     * (expiry before [today]) are excluded.
     *
     * Example: `isExpiringWithin(date, 7)` is true for an item expiring
     * today, tomorrow, ... up to 7 days from now (8 distinct days total).
     */
    fun isExpiringWithin(expiryDateStr: String, days: Int, today: LocalDate = LocalDate.now()): Boolean {
        val expiry = parseOrNull(expiryDateStr) ?: return false
        val windowEnd = today.plusDays(days.toLong())
        return !expiry.isBefore(today) && !expiry.isAfter(windowEnd)
    }

    /** True if [expiryDateStr] parses to exactly [today]. */
    fun isExpiringToday(expiryDateStr: String, today: LocalDate = LocalDate.now()): Boolean {
        val expiry = parseOrNull(expiryDateStr) ?: return false
        return expiry.isEqual(today)
    }

    /** True if [expiryDateStr] is strictly before [today] (already expired). */
    fun isExpired(expiryDateStr: String, today: LocalDate = LocalDate.now()): Boolean {
        val expiry = parseOrNull(expiryDateStr) ?: return false
        return expiry.isBefore(today)
    }

    // ── CSV date format (dd-MMM-yy, e.g. "28-Sep-26") ─────────────────────────
    //
    // The app stores expiry dates internally as ISO-8601 ("2026-09-28") — that's
    // what the date picker writes and what all the filtering logic relies on.
    // The CSV file, however, uses the human-friendly dd-MMM-yy format for both
    // import and export. These helpers convert between the two at the CSV boundary.

    private val CSV_DATE_FMT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("dd-MMM-yy", Locale.ENGLISH)

    /** ISO stored date ("2026-09-28") → CSV format ("28-Sep-26"). */
    fun toCsvDate(isoDateStr: String): String {
        val date = parseOrNull(isoDateStr) ?: return isoDateStr
        return date.format(CSV_DATE_FMT)
    }

    /**
     * CSV date → ISO stored date. Accepts the canonical "28-Sep-26" form, and
     * also tolerates a plain ISO date in case a file already uses it. Returns
     * null if neither parses, so the importer can skip the row.
     */
    fun fromCsvDate(csvDateStr: String): String? {
        val trimmed = csvDateStr.trim()
        // Try dd-MMM-yy first (the documented CSV format)
        try {
            return LocalDate.parse(trimmed, CSV_DATE_FMT).toString()
        } catch (e: DateTimeParseException) {
            // fall through
        }
        // Fall back to ISO (lenient — lets users import an ISO-dated file too)
        return parseOrNull(trimmed)?.toString()
    }
}
