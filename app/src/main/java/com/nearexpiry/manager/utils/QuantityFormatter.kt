package com.nearexpiry.manager.utils

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Consistent quantity presentation for the app and its exports.
 * Whole quantities remain whole (for example, "12"); fractional quantities
 * are rounded to at most two decimal places without unnecessary trailing zeroes.
 */
object QuantityFormatter {
    fun format(value: Double): String {
        if (!value.isFinite()) return ""
        return BigDecimal.valueOf(value)
            .setScale(2, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()
    }
}
