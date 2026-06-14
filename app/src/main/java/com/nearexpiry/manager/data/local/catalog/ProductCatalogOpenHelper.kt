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
    }

    private val dbFile: File by lazy { context.getDatabasePath(DB_NAME) }
    private val versionFile: File by lazy { File(dbFile.parentFile, "$DB_NAME.catalog_version") }

    init {
        copyDatabaseIfNeeded()
    }

    @Synchronized
    private fun copyDatabaseIfNeeded() {
        val currentVersion = versionFile.takeIf { it.exists() }?.readText()?.trim()?.toIntOrNull()
        if (dbFile.exists() && currentVersion == CATALOG_VERSION) return

        dbFile.parentFile?.mkdirs()
        context.assets.open(ASSET_PATH).use { input ->
            dbFile.outputStream().use { output -> input.copyTo(output) }
        }
        versionFile.writeText(CATALOG_VERSION.toString())
    }

    /** Read-only handle to the product catalog. Safe to call from a background thread. */
    fun openReadable(): SQLiteDatabase = readableDatabase

    override fun onCreate(db: SQLiteDatabase) {
        // No-op: the database is fully pre-populated and copied from assets above.
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // No-op: catalog refreshes are handled via copyDatabaseIfNeeded()/CATALOG_VERSION.
    }
}
