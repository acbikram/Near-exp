package com.nearexpiry.manager.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExpiryDateShortcutTest {

    @Test
    fun `automatic date remains off through the first four matching selections`() {
        var date: String? = null
        var count = 0L
        repeat(4) {
            val state = ExpiryDateShortcut.recordSelection(date, count, "2026-12-31")
            date = state.selectedDate
            count = state.consecutiveSelections
            assertNull(state.automaticDate)
        }
        assertEquals(4L, count)
    }

    @Test
    fun `fifth matching selection activates the automatic date`() {
        var date: String? = null
        var count = 0L
        repeat(5) {
            val state = ExpiryDateShortcut.recordSelection(date, count, "2026-12-31")
            date = state.selectedDate
            count = state.consecutiveSelections
            if (count < ExpiryDateShortcut.THRESHOLD) assertNull(state.automaticDate)
            else assertEquals("2026-12-31", state.automaticDate)
        }
        assertEquals(5L, count)
    }

    @Test
    fun `a different date restarts the sequence and disables automatic reuse`() {
        var date: String? = null
        var count = 0L
        repeat(5) {
            val state = ExpiryDateShortcut.recordSelection(date, count, "2026-12-31")
            date = state.selectedDate
            count = state.consecutiveSelections
        }

        val changed = ExpiryDateShortcut.recordSelection(date, count, "2027-01-01")

        assertEquals("2027-01-01", changed.selectedDate)
        assertEquals(1L, changed.consecutiveSelections)
        assertNull(changed.automaticDate)
    }
}
