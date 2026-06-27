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
     * Looks up a scanned barcode in the catalog.
     *
     * Strategy (mirrors Price_Tag_Final.py normalize_barcode logic):
     *   1. Exact match — fastest, covers most cases.
     *   2. Left-strip leading zeros then match — handles scanners that strip zeros.
     *   3. Zero-pad to 13 digits then match — handles scanners that pad to EAN-13.
     *   4. Try progressively longer zero-padding, in case the DB stores it padded.
     *
     * The source data contains duplicate barcodes (same product as "EA"/"POS"
     * vs "CTN"/offer). When duplicates exist we prefer EA, then POS, then CTN.
     */
    suspend fun findByBarcode(barcode: String): ProductCatalogEntry? = withContext(Dispatchers.IO) {
        if (barcode.isBlank()) return@withContext null
        val db = openHelper.openReadable()
        val order = "CASE barcode_type WHEN 'EA' THEN 0 WHEN 'POS' THEN 1 WHEN 'CTN' THEN 2 ELSE 3 END"

        // 1. Exact match
        queryOneByBarcode(db, barcode, order)?.let { return@withContext it }

        // 2. Strip leading zeros (e.g. "0072714834561" -> "72714834561")
        val stripped = barcode.trimStart('0')
        if (stripped.isNotEmpty() && stripped != barcode) {
            queryOneByBarcode(db, stripped, order)?.let { return@withContext it }
        }

        // 3 & 4. Zero-pad up to 13 digits (EAN-13 normalization) — handles a
        // scanner that strips zeros while the DB stores them padded.
        if (barcode.isNotEmpty() && barcode.all { it.isDigit() } && barcode.length < 13) {
            for (len in (barcode.length + 1)..13) {
                val padded = barcode.padStart(len, '0')
                queryOneByBarcode(db, padded, order)?.let { return@withContext it }
            }
        }

        null
    }

    private fun queryOneByBarcode(
        db: android.database.sqlite.SQLiteDatabase,
        barcode: String,
        order: String
    ): ProductCatalogEntry? {
        return db.query("products", columns, "barcode = ?", arrayOf(barcode), null, null, order, "1")
            .use { cursor -> if (cursor.moveToFirst()) cursor.toProductCatalogEntry() else null }
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

    /** Read-only handle to the product catalog. Safe to call from a background thread. */
    fun importCatalog(input: java.io.InputStream): Int = openHelper.importCatalog(input)

    /** Number of products currently in the catalog (0 if empty). */
    fun countProducts(): Int = openHelper.countProducts()

    /**
     * Searches the catalog by partial English/Arabic name or POS/barcode.
     * Returns up to [limit] matches, EA/POS preferred. Used by the manual-entry
     * "search by name" flow when a barcode won't scan.
     */
    suspend fun search(query: String, limit: Int = 25): List<ProductCatalogEntry> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext emptyList()
        val like = "%$q%"
        val db = openHelper.openReadable()
        db.query(
            "products",
            columns,
            "name_en LIKE ? OR name_ar LIKE ? OR pos_code LIKE ? OR barcode LIKE ?",
            arrayOf(like, like, like, like),
            null,
            null,
            "CASE barcode_type WHEN 'EA' THEN 0 WHEN 'POS' THEN 1 WHEN 'CTN' THEN 2 ELSE 3 END",
            limit.toString()
        ).use { cursor ->
            val out = ArrayList<ProductCatalogEntry>(cursor.count)
            while (cursor.moveToNext()) out.add(cursor.toProductCatalogEntry())
            out
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
