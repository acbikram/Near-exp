package com.nearexpiry.manager.data.local.catalog

import android.database.Cursor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProductCatalogDao @Inject constructor(
    private val openHelper: ProductCatalogOpenHelper
) {

    private val columns = arrayOf("barcode", "pos_code", "name_en", "name_ar", "uom", "barcode_type")

    /**
     * Looks up a scanned barcode in the bundled catalog.
     *
     * The source data contains a handful of duplicate barcodes (e.g. the
     * same product listed once as "EA" - each/unit - and once as "CTN" -
     * carton). When duplicates exist we prefer the "EA" entry since that's
     * what a shopper actually scans off a single item.
     */
    suspend fun findByBarcode(barcode: String): ProductCatalogEntry? = withContext(Dispatchers.IO) {
        val db = openHelper.openReadable()
        db.query(
            "products",
            columns,
            "barcode = ?",
            arrayOf(barcode),
            null,
            null,
            "CASE barcode_type WHEN 'EA' THEN 0 WHEN 'POS' THEN 1 WHEN 'CTN' THEN 2 ELSE 3 END",
            "1"
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toProductCatalogEntry() else null
        }
    }

    /**
     * Looks up a product by its POS/Item Code (column B in the source
     * spreadsheet) — used by CSV import to fill in ITEM_DESCRIPTION/UOM
     * when those columns are blank in the import file.
     *
     * Same EA/POS/CTN preference ordering as [findByBarcode], since the
     * same pos_code can also have multiple barcode_type rows.
     */
    suspend fun findByPosCode(posCode: String): ProductCatalogEntry? = withContext(Dispatchers.IO) {
        val db = openHelper.openReadable()
        db.query(
            "products",
            columns,
            "pos_code = ?",
            arrayOf(posCode),
            null,
            null,
            "CASE barcode_type WHEN 'EA' THEN 0 WHEN 'POS' THEN 1 WHEN 'CTN' THEN 2 ELSE 3 END",
            "1"
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toProductCatalogEntry() else null
        }
    }

    private fun Cursor.toProductCatalogEntry(): ProductCatalogEntry = ProductCatalogEntry(
        barcode = getString(0),
        posCode = getStringOrNull(1),
        nameEn = getStringOrNull(2),
        nameAr = getStringOrNull(3),
        uom = getStringOrNull(4),
        barcodeType = getStringOrNull(5)
    )

    private fun Cursor.getStringOrNull(index: Int): String? =
        if (isNull(index)) null else getString(index)
}
