package com.nearexpiry.manager.utils

import com.nearexpiry.manager.data.local.entity.ExpiryItemEntity
import com.nearexpiry.manager.domain.repository.ProductCatalogRepository
import com.opencsv.CSVReader
import java.io.InputStream
import java.io.InputStreamReader
import javax.inject.Inject

/**
 * Reverse of [CsvExporter] — bulk-adds items from a CSV file, for use as an
 * initial stock-take import.
 *
 * Expected header (case-insensitive, column order flexible):
 *   POS Code, ITEM_DESCRIPTION, UOM, Qty, Expiry Date
 *
 * - `POS Code`, `Qty`, and `Expiry Date` are required.
 * - `POS Code` is always treated as plain text (kept verbatim, leading
 *   zeros preserved — never parsed as a number).
 * - `Expiry Date` uses the dd-MMM-yy format (e.g. "28-Sep-26"); a plain
 *   ISO date is also accepted as a fallback.
 * - `ITEM_DESCRIPTION` and `UOM` are optional. If either is blank, this
 *   looks up the POS Code in the bundled product catalog
 *   ([ProductCatalogRepository.lookupByItemCode]) and fills in the English
 *   description / unit from there when a match is found.
 *
 * Every imported row becomes a brand-new item (new auto-generated id);
 * existing records are never overwritten. Rows missing a required field, an
 * unparsable Expiry Date, or an unparsable/non-positive Qty are skipped and
 * counted in [ImportResult.skipped] rather than aborting the whole import.
 *
 * The CSV has no barcode column — [ExpiryItemEntity.barcode] is filled with
 * the POS Code itself so the NOT NULL constraint is satisfied and existing
 * duplicate-detection (by barcode + expiry date) still behaves sensibly for
 * repeated stock-takes of the same item code.
 */
class CsvImporter @Inject constructor(
    private val productCatalogRepository: ProductCatalogRepository
) {

    data class ImportResult(
        val imported: List<ExpiryItemEntity>,
        val skipped: Int,
        val totalRows: Int,
        /** Per-reason skip counts, for surfacing *why* rows were skipped. */
        val skippedMissingPosCode: Int = 0,
        val skippedBadDate: Int = 0,
        val skippedBadQty: Int = 0,
        /** How many parsed rows were merged into an existing item (same barcode+expiry+unit). */
        val merged: Int = 0
    )

    suspend fun parseCsv(inputStream: InputStream): ImportResult {
        val rows = CSVReader(InputStreamReader(inputStream)).use { it.readAll() }
        if (rows.isEmpty()) return ImportResult(emptyList(), 0, 0)

        // Normalise header cells: strip UTF-8 BOM, surrounding quotes/whitespace.
        val header = rows.first().map {
            it.replace("\uFEFF", "").trim().trim('"', '\'').lowercase()
        }
        val dataRows = rows.drop(1)

        fun colIndex(vararg names: String): Int {
            for (name in names) {
                val idx = header.indexOf(name.lowercase())
                if (idx >= 0) return idx
            }
            return -1
        }

        val idxPosCode    = colIndex("POS Code", "PosCode", "ItemCode", "Item Code", "Pos_Code")
        val idxDesc       = colIndex("ITEM_DESCRIPTION", "ItemDescription", "ProductName", "Description", "Item Description")
        val idxUom        = colIndex("UOM", "Unit")
        val idxQty        = colIndex("Qty", "Quantity", "QTY")
        val idxExpiry     = colIndex("Expiry Date", "ExpiryDate", "Expiry", "Exp Date", "Exp")

        var skipped = 0
        var skippedMissingPosCode = 0
        var skippedBadDate = 0
        var skippedBadQty = 0
        val imported = mutableListOf<ExpiryItemEntity>()
        val now = System.currentTimeMillis()

        for (row in dataRows) {
            if (row.size == 1 && row[0].isBlank()) continue // trailing blank line
            // Also skip a row that's entirely empty cells.
            if (row.all { it.isBlank() }) continue

            // POS Code is always treated as plain text — kept verbatim, never
            // parsed as a number, so leading zeros / long codes are preserved.
            val posCode = idxPosCode.takeIf { it >= 0 }?.let { row.getOrNull(it) }
                ?.replace("\uFEFF", "")?.trim()?.trim('"', '\'')
            val rawExpiry = idxExpiry.takeIf { it >= 0 }?.let { row.getOrNull(it) }?.trim()
            val quantityStr = idxQty.takeIf { it >= 0 }?.let { row.getOrNull(it) }?.trim()
                ?.replace(",", "")          // tolerate thousands separators like "1,000"
            val quantity = quantityStr?.toDoubleOrNull()

            // Convert the CSV date (dd-MMM-yy, e.g. "28-Sep-26") to the ISO form
            // the app stores internally; null if it can't be parsed.
            val expiryDate = rawExpiry?.let { ExpiryDateUtils.fromCsvDate(it) }

            // Track skip reasons separately so the result can explain itself.
            if (posCode.isNullOrBlank()) { skipped++; skippedMissingPosCode++; continue }
            if (expiryDate == null)      { skipped++; skippedBadDate++; continue }
            if (quantity == null || quantity <= 0) { skipped++; skippedBadQty++; continue }

            var description = idxDesc.takeIf { it >= 0 }?.let { row.getOrNull(it) }?.trim()?.takeIf { it.isNotBlank() }
            var unit = idxUom.takeIf { it >= 0 }?.let { row.getOrNull(it) }?.trim()?.takeIf { it.isNotBlank() }
            var descriptionArabic: String? = null

            // ITEM_DESCRIPTION (and/or UOM) blank → fill in from the local catalog by POS Code.
            if (description == null || unit == null) {
                val catalogInfo = productCatalogRepository.lookupByItemCode(posCode)
                if (catalogInfo != null) {
                    if (description == null) {
                        description = catalogInfo.name
                        descriptionArabic = catalogInfo.nameArabic
                    }
                    if (unit == null) unit = catalogInfo.unit
                }
            }

            imported.add(
                ExpiryItemEntity(
                    barcode = posCode,
                    expiryDate = expiryDate,
                    quantity = quantity,
                    createdAt = now,
                    updatedAt = now,
                    productName = description,
                    productNameArabic = descriptionArabic,
                    unit = unit,
                    itemCode = posCode
                )
            )
        }

        return ImportResult(
            imported = imported,
            skipped = skipped,
            totalRows = dataRows.size,
            skippedMissingPosCode = skippedMissingPosCode,
            skippedBadDate = skippedBadDate,
            skippedBadQty = skippedBadQty
        )
    }
}
