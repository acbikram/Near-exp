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
 *
 * This mirrors the columns expected by [CsvImporter] for round-tripping.
 *
 * POS Code is always written as plain text. Every field is force-quoted
 * (CSVWriter default), so codes with leading zeros or long digit runs are
 * preserved verbatim in the file and re-import exactly. (Spreadsheet apps
 * may still visually reformat a numeric-looking code on open — that's a
 * display choice in the app, not data loss in the file.)
 */
object CsvExporter {

    val HEADER = arrayOf("POS Code", "ITEM_DESCRIPTION", "UOM", "Qty", "Expiry Date")

    fun writeCsv(outputStream: OutputStream, items: List<ExpiryItem>) {
        CSVWriter(OutputStreamWriter(outputStream)).use { writer ->
            // CSVWriter default: every field wrapped in double quotes, which
            // keeps POS Code as exact text (preserves leading zeros, etc.).
            writer.writeNext(HEADER)
            items.forEach { item ->
                writer.writeNext(
                    arrayOf(
                        item.itemCode ?: "",
                        item.productName ?: "",
                        item.unit ?: "",
                        if (item.quantity % 1.0 == 0.0) item.quantity.toInt().toString() else item.quantity.toString(),
                        ExpiryDateUtils.toCsvDate(item.expiryDate)
                    )
                )
            }
        }
    }
}
