package com.nearexpiry.manager.utils

import java.time.LocalDate
import java.time.YearMonth

object DateRange {
    data class Range(val min: LocalDate, val max: LocalDate)

    fun get(): Range {
        val today = LocalDate.now()
        val maxDate = YearMonth.from(today).plusMonths(4).atEndOfMonth()
        return Range(today, maxDate)
    }
}
