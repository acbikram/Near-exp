package com.nearexpiry.manager.utils

import com.nearexpiry.manager.domain.model.ExpiryItem
import com.opencsv.CSVWriter
import java.io.OutputStream
import java.io.OutputStreamWriter

/**
 * Writes records in the stock-take CSV layout:
 *   A: POS Code        — [ExpiryItem.itemCode] (Item Code / Pos Code)
 *   B: ITEM_DESCRIPTION — [ExpiryItem.productName] (English description)
 *   C: UOM             — [ExpiryItem.unit] (Unit Type)
 *   D: Qty             — [ExpiryItem.quantity]
 *   E: Expiry Date     — [ExpiryItem.expiryDate], formatted as dd-MMM-yy (e.g. 28-Sep-26)
 *   F: Warning         — ⚠️ flag when the same item (POS code) + expiry date
 *                        appears with more than one unit type; blank otherwise.
 *
 * Columns A–E mirror what [CsvImporter] reads for round-tripping. The Warning
 * column (F) is informational only — [CsvImporter] ignores any extra columns.
 *
 * POS Code is always written as plain text. Every field is force-quoted
 * (CSVWriter default), so codes with leading zeros or long digit runs are
 * preserved verbatim in the file and re-import exactly. (Spreadsheet apps
 * may still visually reformat a numeric-looking code on open — that's a
 * display choice in the app, not data loss in the file.)
 */
object CsvExporter {

    val HEADER = arrayOf("POS Code", "ITEM_DESCRIPTION", "UOM", "Qty", "Expiry Date", "Warning")

    /** Warning text written in column F for items that share POS code + expiry but differ in unit. */
    const val MIXED_UNIT_WARNING = "\u26A0\uFE0F Same item with different Unit"

    fun writeCsv(outputStream: OutputStream, items: List<ExpiryItem>) {
        // Key items by POS code (falling back to barcode) + expiry date, and
        // find which keys have more than one distinct unit. Those rows get the
        // mixed-unit warning so it's visible across all export options.
        val unitsByKey: Map<String, Set<String?>> = items.groupBy { mixedUnitKey(it) }
            .mapValues { (_, group) -> group.map { it.unit?.takeIf { u -> u.isNotBlank() } }.toSet() }

        CSVWriter(OutputStreamWriter(outputStream)).use { writer ->
            // CSVWriter default: every field wrapped in double quotes, which
            // keeps POS Code as exact text (preserves leading zeros, etc.).
            writer.writeNext(HEADER)
            items.forEach { item ->
                val distinctUnits = unitsByKey[mixedUnitKey(item)] ?: emptySet()
                val warning = if (distinctUnits.size > 1) MIXED_UNIT_WARNING else ""
                writer.writeNext(
                    arrayOf(
                        item.itemCode ?: "",
                        item.productName ?: "",
                        item.unit ?: "",
                        QuantityFormatter.format(item.quantity),
                        ExpiryDateUtils.toCsvDate(item.expiryDate),
                        warning
                    )
                )
            }
        }
    }

    /** Grouping key for detecting the same item+expiry sold under multiple units. */
    private fun mixedUnitKey(item: ExpiryItem): String {
        val code = item.itemCode?.takeIf { it.isNotBlank() } ?: item.barcode
        return "$code|${item.expiryDate}"
    }
}
