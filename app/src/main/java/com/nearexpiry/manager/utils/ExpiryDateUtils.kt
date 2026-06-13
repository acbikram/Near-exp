package com.nearexpiry.manager.utils

import java.time.LocalDate
import java.time.format.DateTimeParseException

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
}
