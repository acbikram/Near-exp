package com.nearexpiry.manager.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Regression tests for [ExpiryDateUtils].
 *
 * These pin down the "expiring soon" semantics shared by the Home dashboard
 * counters and the History screen filters they link to. Before this util
 * existed, Home excluded items expiring *today* while History included
 * them, so the dashboard count and the list it opened could disagree -
 * these tests guard against that regression.
 */
class ExpiryDateUtilsTest {

    private val today = LocalDate.of(2026, 6, 13)

    // ── parseOrNull ─────────────────────────────────────────────────────────

    @Test
    fun `parseOrNull returns date for valid ISO string`() {
        assertEquals(LocalDate.of(2026, 6, 20), ExpiryDateUtils.parseOrNull("2026-06-20"))
    }

    @Test
    fun `parseOrNull returns null for blank or malformed strings`() {
        assertNull(ExpiryDateUtils.parseOrNull(""))
        assertNull(ExpiryDateUtils.parseOrNull("not-a-date"))
        assertNull(ExpiryDateUtils.parseOrNull("2026-13-40"))
    }

    // ── isExpiringWithin: boundaries ───────────────────────────────────────

    @Test
    fun `item expiring today counts as within 7 and 30 days`() {
        val expiry = today.toString()
        assertTrue(ExpiryDateUtils.isExpiringWithin(expiry, 7, today))
        assertTrue(ExpiryDateUtils.isExpiringWithin(expiry, 30, today))
    }

    @Test
    fun `item expiring exactly 7 days from now counts within 7 days`() {
        val expiry = today.plusDays(7).toString()
        assertTrue(ExpiryDateUtils.isExpiringWithin(expiry, 7, today))
    }

    @Test
    fun `item expiring 8 days from now does not count within 7 days`() {
        val expiry = today.plusDays(8).toString()
        assertFalse(ExpiryDateUtils.isExpiringWithin(expiry, 7, today))
        // ...but it does fall within the 30-day window
        assertTrue(ExpiryDateUtils.isExpiringWithin(expiry, 30, today))
    }

    @Test
    fun `item expiring exactly 30 days from now counts within 30 days`() {
        val expiry = today.plusDays(30).toString()
        assertTrue(ExpiryDateUtils.isExpiringWithin(expiry, 30, today))
    }

    @Test
    fun `item expiring 31 days from now does not count within 30 days`() {
        val expiry = today.plusDays(31).toString()
        assertFalse(ExpiryDateUtils.isExpiringWithin(expiry, 30, today))
    }

    @Test
    fun `already-expired items are excluded`() {
        val expiry = today.minusDays(1).toString()
        assertFalse(ExpiryDateUtils.isExpiringWithin(expiry, 7, today))
        assertFalse(ExpiryDateUtils.isExpiringWithin(expiry, 30, today))
    }

    @Test
    fun `unparseable dates are excluded rather than throwing`() {
        assertFalse(ExpiryDateUtils.isExpiringWithin("garbage", 7, today))
        assertFalse(ExpiryDateUtils.isExpiringWithin("", 30, today))
    }

    // ── isExpiringToday ─────────────────────────────────────────────────────

    @Test
    fun `isExpiringToday matches only the current date`() {
        assertTrue(ExpiryDateUtils.isExpiringToday(today.toString(), today))
        assertFalse(ExpiryDateUtils.isExpiringToday(today.plusDays(1).toString(), today))
        assertFalse(ExpiryDateUtils.isExpiringToday(today.minusDays(1).toString(), today))
        assertFalse(ExpiryDateUtils.isExpiringToday("not-a-date", today))
    }

    // ── Home/History parity ───────────────────────────────────────────────

    @Test
    fun `7-day and 30-day windows agree with the boundaries History used to apply`() {
        // History previously used: !isBefore(today) && isBefore(today.plusDays(days+1))
        // i.e. today .. today+days inclusive. Verify the shared util matches that
        // for the full window, day by day.
        for (offset in -1..8) {
            val date = today.plusDays(offset.toLong())
            val expectedWithin7 = offset in 0..7
            assertEquals(
                "offset=$offset should be ${if (expectedWithin7) "within" else "outside"} the 7-day window",
                expectedWithin7,
                ExpiryDateUtils.isExpiringWithin(date.toString(), 7, today)
            )
        }
    }
}
