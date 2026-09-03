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
    // The CSV file uses the human-friendly dd-MMM-yy format for export; import
    // accepts that plus a range of other common formats (see [fromCsvDate]).

    private val CSV_DATE_FMT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("dd-MMM-yy", Locale.ENGLISH)

    /**
     * Formats accepted on import, tried in order. Covers the canonical
     * dd-MMM-yy plus the most common spreadsheet/locale variants so valid
     * rows aren't silently skipped over a formatting mismatch.
     */
    private val IMPORT_DATE_FORMATS: List<DateTimeFormatter> = listOf(
        DateTimeFormatter.ofPattern("dd-MMM-yy", Locale.ENGLISH),   // 28-Sep-26
        DateTimeFormatter.ofPattern("d-MMM-yy", Locale.ENGLISH),    // 1-Aug-26
        DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH), // 28-Sep-2026
        DateTimeFormatter.ofPattern("d-MMM-yyyy", Locale.ENGLISH),  // 1-Aug-2026
        DateTimeFormatter.ofPattern("dd/MMM/yy", Locale.ENGLISH),   // 28/Sep/26
        DateTimeFormatter.ofPattern("dd-MMMM-yy", Locale.ENGLISH),  // 28-September-26
        DateTimeFormatter.ofPattern("dd-MMMM-yyyy", Locale.ENGLISH),// 28-September-2026
        DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH),  // 28/09/2026
        DateTimeFormatter.ofPattern("d/M/yyyy", Locale.ENGLISH),    // 1/8/2026
        DateTimeFormatter.ofPattern("dd/MM/yy", Locale.ENGLISH),    // 28/09/26
        DateTimeFormatter.ofPattern("d/M/yy", Locale.ENGLISH),      // 1/8/26
        DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.ENGLISH),  // 09/28/2026 (US)
        DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.ENGLISH),  // 28.09.2026
        DateTimeFormatter.ISO_LOCAL_DATE                            // 2026-09-28
    )

    /**
     * Formats the stored ISO date for Item Details and its date barcode.
     * Month is not padded; day is always two digits: 2026-08-03 → 8/03/2026.
     * Invalid values are returned unchanged so presentation never destroys data.
     */
    fun toItemDetailsDate(isoDateStr: String): String {
        val date = parseOrNull(isoDateStr) ?: return isoDateStr
        return date.format(DateTimeFormatter.ofPattern("M/dd/yyyy", Locale.ENGLISH))
    }

    /** ISO stored date ("2026-09-28") → CSV format ("28-Sep-26"). */
    fun toCsvDate(isoDateStr: String): String {
        val date = parseOrNull(isoDateStr) ?: return isoDateStr
        return date.format(CSV_DATE_FMT)
    }

    /**
     * Parses a date string from an imported CSV into the ISO form the app
     * stores internally. Tries each format in [IMPORT_DATE_FORMATS]; also
     * normalises the month casing (e.g. "SEP"/"sep" → "Sep") so case
     * differences don't cause a skip. Returns null only if nothing matches.
     */
    fun fromCsvDate(csvDateStr: String): String? {
        // Strip a possible UTF-8 BOM and surrounding whitespace/quotes.
        val cleaned = csvDateStr
            .replace("\uFEFF", "")
            .trim()
            .trim('"', '\'')
        if (cleaned.isEmpty()) return null

        // Title-case any 3+-letter month token so "SEP"/"sep" both work,
        // since DateTimeFormatter month parsing is case-sensitive here.
        val normalised = cleaned.split('-', '/', '.', ' ').joinToString(
            separator = " "
        ) { token ->
            if (token.length >= 3 && token.any { it.isLetter() }) {
                token.lowercase().replaceFirstChar { it.uppercase() }
            } else token
        }

        for (fmt in IMPORT_DATE_FORMATS) {
            // Try both the original cleaned string and the month-normalised one.
            for (candidate in listOf(cleaned, normalised)) {
                try {
                    return LocalDate.parse(candidate, fmt).toString()
                } catch (e: DateTimeParseException) {
                    // try next
                }
            }
        }
        return null
    }
}
