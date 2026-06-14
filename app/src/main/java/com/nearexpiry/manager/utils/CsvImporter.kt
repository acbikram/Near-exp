package com.nearexpiry.manager.utils

import com.nearexpiry.manager.data.local.entity.ExpiryItemEntity
import com.opencsv.CSVReader
import java.io.InputStream
import java.io.InputStreamReader
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Reverse of [CsvExporter] — bulk-adds items from a CSV file, for use as an
 * initial stock-take import.
 *
 * Expected header (case-insensitive, column order flexible):
 *   Barcode, ItemCode, ProductName, ProductNameArabic, Unit, ExpiryDate, Quantity, CreatedAt, UpdatedAt
 *
 * Only `Barcode`, `ExpiryDate`, and `Quantity` are required; all other
 * columns are optional and may be blank or absent entirely. `CreatedAt`/
 * `UpdatedAt` are parsed if present (matching the ISO-local-date-time format
 * CsvExporter writes); if missing or unparsable, the current time is used —
 * every imported row is treated as a brand-new item (new auto-generated id),
 * never overwriting existing records.
 *
 * Rows that are missing a required field, have an unparsable ExpiryDate, or
 * an unparsable/non-positive Quantity are skipped and counted in
 * [ImportResult.skipped] rather than aborting the whole import.
 */
object CsvImporter {

    data class ImportResult(
        val imported: List<ExpiryItemEntity>,
        val skipped: Int,
        val totalRows: Int
    )

    private val TIMESTAMP_FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    fun parseCsv(inputStream: InputStream): ImportResult {
        CSVReader(InputStreamReader(inputStream)).use { reader ->
            val rows = reader.readAll()
            if (rows.isEmpty()) return ImportResult(emptyList(), 0, 0)

            val header = rows.first().map { it.trim().lowercase() }
            val dataRows = rows.drop(1)

            fun colIndex(name: String): Int = header.indexOf(name.lowercase())

            val idxBarcode    = colIndex("Barcode")
            val idxItemCode   = colIndex("ItemCode")
            val idxName       = colIndex("ProductName")
            val idxNameAr     = colIndex("ProductNameArabic")
            val idxUnit       = colIndex("Unit")
            val idxExpiry     = colIndex("ExpiryDate")
            val idxQuantity   = colIndex("Quantity")
            val idxCreatedAt  = colIndex("CreatedAt")
            val idxUpdatedAt  = colIndex("UpdatedAt")

            var skipped = 0
            val imported = mutableListOf<ExpiryItemEntity>()
            val now = System.currentTimeMillis()

            for (row in dataRows) {
                if (row.size == 1 && row[0].isBlank()) continue // trailing blank line

                val barcode = idxBarcode.takeIf { it >= 0 }?.let { row.getOrNull(it) }?.trim()
                val expiryDate = idxExpiry.takeIf { it >= 0 }?.let { row.getOrNull(it) }?.trim()
                val quantityStr = idxQuantity.takeIf { it >= 0 }?.let { row.getOrNull(it) }?.trim()
                val quantity = quantityStr?.toDoubleOrNull()

                if (barcode.isNullOrBlank() ||
                    expiryDate.isNullOrBlank() ||
                    ExpiryDateUtils.parseOrNull(expiryDate) == null ||
                    quantity == null || quantity <= 0
                ) {
                    skipped++
                    continue
                }

                val createdAt = idxCreatedAt.takeIf { it >= 0 }
                    ?.let { row.getOrNull(it) }?.trim()
                    ?.let { parseTimestampOrNull(it) }
                    ?: now
                val updatedAt = idxUpdatedAt.takeIf { it >= 0 }
                    ?.let { row.getOrNull(it) }?.trim()
                    ?.let { parseTimestampOrNull(it) }
                    ?: now

                fun strOrNull(idx: Int): String? =
                    idx.takeIf { it >= 0 }?.let { row.getOrNull(it) }?.trim()?.takeIf { it.isNotBlank() }

                imported.add(
                    ExpiryItemEntity(
                        barcode = barcode,
                        expiryDate = expiryDate,
                        quantity = quantity,
                        createdAt = createdAt,
                        updatedAt = updatedAt,
                        productName = strOrNull(idxName),
                        productNameArabic = strOrNull(idxNameAr),
                        unit = strOrNull(idxUnit),
                        itemCode = strOrNull(idxItemCode)
                    )
                )
            }

            return ImportResult(imported, skipped, dataRows.size)
        }
    }

    private fun parseTimestampOrNull(value: String): Long? = try {
        LocalDateTime.parse(value, TIMESTAMP_FMT)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    } catch (e: Exception) {
        null
    }
}
