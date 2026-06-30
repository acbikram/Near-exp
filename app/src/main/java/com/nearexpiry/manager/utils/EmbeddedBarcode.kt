package com.nearexpiry.manager.utils

/**
 * Parses "22"-prefixed embedded-weight barcodes used in-store.
 *
 * Format (13 digits): 22 IIIII QQQQQ C
 *   • positions 3..7  (IIIII) → item code (e.g. 69051)
 *   • positions 8..12 (QQQQQ) → quantity as XX.YYY (e.g. 00350 → 0.350,
 *                                01000 → 1.000)
 *   • position 13     (C)     → check digit (ignored)
 *
 * The item code is then looked up in the catalog exactly like a normal scan,
 * so the product name and unit (Kg/PCS/…) come from the catalog — not from the
 * barcode. The parsed quantity is used directly with that catalog unit.
 */
object EmbeddedBarcode {

    data class Parsed(val itemCode: String, val quantity: Double)

    /** True if [barcode] is a 13-digit code beginning with "22". */
    fun isEmbedded(barcode: String): Boolean =
        barcode.length == 13 && barcode.startsWith("22") && barcode.all { it.isDigit() }

    /**
     * Extracts the item code and quantity from a "22" barcode, or null if it
     * isn't one. Quantity QQQQQ is read as XX.YYY (two whole + three decimal).
     */
    fun parse(barcode: String): Parsed? {
        if (!isEmbedded(barcode)) return null
        val itemCode = barcode.substring(2, 7)     // positions 3..7
        val qtyDigits = barcode.substring(7, 12)   // positions 8..12
        val whole = qtyDigits.substring(0, 2).toIntOrNull() ?: return null
        val frac = qtyDigits.substring(2, 5).toIntOrNull() ?: return null
        val quantity = whole + frac / 1000.0
        return Parsed(itemCode = itemCode, quantity = quantity)
    }
}
