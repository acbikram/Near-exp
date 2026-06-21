package com.nearexpiry.manager.data.local.catalog

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Opens the read-only product catalog database that ships as an asset
 * (`app/src/main/assets/products.db`, built from Price_Tag_Master_CTN.xlsx,
 * ~135,725 products keyed by barcode).
 *
 * SQLite can't query a database directly inside the APK's assets, so on
 * first use we copy it into the app's databases directory. If you replace
 * `products.db` with an updated export later, bump [CATALOG_VERSION] so the
 * new copy gets picked up on the next app start.
 */
@Singleton
class ProductCatalogOpenHelper @Inject constructor(
    @ApplicationContext private val context: Context
) : SQLiteOpenHelper(context, DB_NAME, null, SQLITE_VERSION) {

    companion object {
        private const val DB_NAME = "products.db"
        private const val ASSET_PATH = "products.db"

        // Internal SQLiteOpenHelper version - unrelated to CATALOG_VERSION below.
        private const val SQLITE_VERSION = 1

        // Bump this whenever you ship a new products.db asset so the copy
        // in app storage gets refreshed.
        private const val CATALOG_VERSION = 1

        // Written into the version file after a user imports their own catalog,
        // so the bundled asset never overwrites it on a later launch.
        private const val USER_IMPORTED_MARKER = "user-imported"
    }

    private val dbFile: File by lazy { context.getDatabasePath(DB_NAME) }
    private val versionFile: File by lazy { File(dbFile.parentFile, "$DB_NAME.catalog_version") }

    init {
        copyDatabaseIfNeeded()
    }

    @Synchronized
    private fun copyDatabaseIfNeeded() {
        val currentMarker = versionFile.takeIf { it.exists() }?.readText()?.trim()
        // Never overwrite a catalog the user imported themselves.
        if (dbFile.exists() && currentMarker == USER_IMPORTED_MARKER) return
        val currentVersion = currentMarker?.toIntOrNull()
        if (dbFile.exists() && currentVersion == CATALOG_VERSION) return

        dbFile.parentFile?.mkdirs()
        context.assets.open(ASSET_PATH).use { input ->
            dbFile.outputStream().use { output -> input.copyTo(output) }
        }
        versionFile.writeText(CATALOG_VERSION.toString())
    }

    /** Read-only handle to the product catalog. Safe to call from a background thread. */
    fun openReadable(): SQLiteDatabase = readableDatabase

    /**
     * Replaces the catalog contents from a user-provided file so products can
     * be refreshed without rebuilding the app. Accepts either:
     *   • a CSV exported from the master — columns matched by header name:
     *     Barcode, Pos Code, English Desc, [Arabic Desc], Prm Uom, Barcode Type
     *     (header names are flexible; see [csvColumnIndex]), or
     *   • a prebuilt products.db SQLite file (swapped in directly).
     * Returns the number of products afterwards. Throws on an Excel/.xlsx file
     * (the user must export it as CSV first).
     */
    @Synchronized
    fun importCatalog(rawInput: java.io.InputStream): Int {
        val input = java.io.BufferedInputStream(rawInput)
        input.mark(64)
        val prefix = ByteArray(16)
        val read = input.read(prefix).coerceAtLeast(0)
        input.reset()
        val head = String(prefix, 0, read, Charsets.ISO_8859_1)
        return when {
            head.startsWith("SQLite format 3") -> replaceWithSqlite(input)
            read >= 2 && prefix[0] == 'P'.code.toByte() && prefix[1] == 'K'.code.toByte() ->
                throw IllegalArgumentException(
                    "This looks like an Excel (.xlsx) file. Please save it as CSV (UTF-8) and import that.")
            else -> replaceWithCsv(input)
        }
    }

    /** Overwrites the on-disk catalog DB with a user-supplied SQLite file. */
    private fun replaceWithSqlite(input: java.io.InputStream): Int {
        close() // release our handle before swapping the file
        val tmp = File(dbFile.parentFile, "$DB_NAME.import_tmp")
        tmp.outputStream().use { out -> input.copyTo(out) }

        // Validate it actually has a usable products table before committing.
        val count = try {
            SQLiteDatabase.openDatabase(tmp.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                db.rawQuery("SELECT COUNT(*) FROM products", null).use { c ->
                    if (c.moveToFirst()) c.getInt(0) else 0
                }
            }
        } catch (e: Exception) {
            tmp.delete()
            throw IllegalArgumentException("That .db file doesn't contain a valid product catalog.", e)
        }

        // Remove old DB + WAL/SHM side files, then move the new one into place.
        listOf(dbFile, File("${dbFile.path}-wal"), File("${dbFile.path}-shm")).forEach { it.delete() }
        if (!tmp.renameTo(dbFile)) {
            tmp.copyTo(dbFile, overwrite = true)
            tmp.delete()
        }
        // Mark the on-disk copy as user-supplied so the bundled asset doesn't
        // overwrite it on next launch.
        versionFile.writeText(USER_IMPORTED_MARKER)
        return count
    }

    /**
     * Rebuilds the `products` table from a CSV. Existing rows are cleared and
     * replaced. Header names are matched case-insensitively and flexibly so
     * exports with slightly different column titles still work.
     */
    private fun replaceWithCsv(input: java.io.InputStream): Int {
        val reader = input.bufferedReader(Charsets.UTF_8)
        val lines = reader.readLines()
        if (lines.isEmpty()) throw IllegalArgumentException("The CSV file is empty.")

        // Strip a UTF-8 BOM from the header line if present.
        val headerLine = lines.first().removePrefix("\uFEFF")
        val header = parseCsvLine(headerLine).map { it.trim().lowercase() }

        val idxBarcode = csvColumnIndex(header, "barcode", "bar code")
        val idxPosCode = csvColumnIndex(header, "pos code", "poscode", "pos_code", "item code", "itemcode")
        val idxNameEn  = csvColumnIndex(header, "english desc", "english description", "name_en", "name en", "description", "desc")
        val idxNameAr  = csvColumnIndex(header, "arabic desc", "arabic description", "name_ar", "name ar")
        val idxUom     = csvColumnIndex(header, "prm uom", "uom", "unit")
        val idxType    = csvColumnIndex(header, "barcode type", "barcode_type", "type")

        if (idxBarcode < 0 || idxPosCode < 0) {
            throw IllegalArgumentException(
                "CSV must have at least 'Barcode' and 'Pos Code' columns (header row).")
        }

        val db = writableDatabase
        var count = 0
        db.beginTransaction()
        try {
            db.execSQL("DELETE FROM products")
            val stmt = db.compileStatement(
                "INSERT INTO products (barcode, pos_code, name_en, name_ar, uom, barcode_type) VALUES (?,?,?,?,?,?)"
            )
            for (i in 1 until lines.size) {
                val raw = lines[i]
                if (raw.isBlank()) continue
                val cols = parseCsvLine(raw)
                val barcode = cols.getOrNull(idxBarcode)?.trim().orEmpty()
                val posCode = cols.getOrNull(idxPosCode)?.trim().orEmpty()
                if (barcode.isEmpty() && posCode.isEmpty()) continue

                stmt.clearBindings()
                stmt.bindString(1, barcode)
                stmt.bindString(2, posCode)
                bindOrNull(stmt, 3, if (idxNameEn >= 0) cols.getOrNull(idxNameEn)?.trim() else null)
                bindOrNull(stmt, 4, if (idxNameAr >= 0) cols.getOrNull(idxNameAr)?.trim() else null)
                bindOrNull(stmt, 5, if (idxUom >= 0) cols.getOrNull(idxUom)?.trim() else null)
                bindOrNull(stmt, 6, if (idxType >= 0) cols.getOrNull(idxType)?.trim() else null)
                stmt.executeInsert()
                count++
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        versionFile.writeText(USER_IMPORTED_MARKER)
        return count
    }

    private fun bindOrNull(stmt: android.database.sqlite.SQLiteStatement, index: Int, value: String?) {
        if (value.isNullOrEmpty()) stmt.bindNull(index) else stmt.bindString(index, value)
    }

    /** Returns the index of the first header matching any of [names], or -1. */
    private fun csvColumnIndex(header: List<String>, vararg names: String): Int {
        for (name in names) {
            val idx = header.indexOf(name.lowercase())
            if (idx >= 0) return idx
        }
        return -1
    }

    /** Minimal CSV line parser handling quoted fields and escaped quotes. */
    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                    sb.append('"'); i++   // escaped quote
                }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> { result.add(sb.toString()); sb.setLength(0) }
                else -> sb.append(c)
            }
            i++
        }
        result.add(sb.toString())
        return result
    }

    override fun onCreate(db: SQLiteDatabase) {
        // No-op: the database is fully pre-populated and copied from assets above.
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // No-op: catalog refreshes are handled via copyDatabaseIfNeeded()/CATALOG_VERSION.
    }
}
