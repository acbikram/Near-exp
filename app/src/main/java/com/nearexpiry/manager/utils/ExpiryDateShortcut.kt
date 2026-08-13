package com.nearexpiry.manager.utils

/**
 * Pure rule for the global scan-date shortcut. The date picker remains in use
 * until a user explicitly selects the same ISO date [THRESHOLD] times in a row.
 */
internal object ExpiryDateShortcut {
    const val THRESHOLD = 5L

    internal data class State(
        val selectedDate: String,
        val consecutiveSelections: Long,
        val automaticDate: String?
    )

    fun recordSelection(
        previousDate: String?,
        previousCount: Long,
        selectedDate: String
    ): State {
        val count = if (previousDate == selectedDate) previousCount + 1L else 1L
        return State(
            selectedDate = selectedDate,
            consecutiveSelections = count,
            automaticDate = selectedDate.takeIf { count >= THRESHOLD }
        )
    }
}
