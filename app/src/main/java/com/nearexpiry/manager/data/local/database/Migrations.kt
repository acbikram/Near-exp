package com.nearexpiry.manager.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Central place for all Room migrations. Add one object per version bump
 * and register it in [ExpiryDatabase.getInstance] via `.addMigrations(...)`.
 *
 * Keeping these explicit (rather than relying solely on
 * fallbackToDestructiveMigration) means existing users' scan history
 * survives schema changes.
 */

/**
 * v1 -> v2: adds a composite index on (barcode, expiryDate).
 *
 * This index backs [com.nearexpiry.manager.data.local.dao.ExpiryItemDao.findByBarcodeAndExpiry],
 * which is called on every scan to detect duplicates. Without it, that
 * lookup is a full table scan that gets slower as the inventory grows.
 *
 * The index name matches Room's auto-generated convention
 * (`index_<table>_<col1>_<col2>`) for the `indices` entry added to
 * [com.nearexpiry.manager.data.local.entity.ExpiryItemEntity].
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_expiry_items_barcode_expiryDate` " +
                "ON `expiry_items` (`barcode`, `expiryDate`)"
        )
    }
}

/**
 * v2 -> v3: changes quantity column type from INTEGER to REAL (Double).
 *
 * SQLite doesn't support ALTER COLUMN, so we must follow the standard 
 * "new table -> copy -> swap" pattern.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. Create the new table with the correct schema
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `expiry_items_new` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                `barcode` TEXT NOT NULL, 
                `expiryDate` TEXT NOT NULL, 
                `quantity` REAL NOT NULL, 
                `createdAt` INTEGER NOT NULL, 
                `updatedAt` INTEGER NOT NULL
            )
        """.trimIndent())

        // 2. Copy the data from the old table to the new table
        db.execSQL("""
            INSERT INTO `expiry_items_new` (`id`, `barcode`, `expiryDate`, `quantity`, `createdAt`, `updatedAt`)
            SELECT `id`, `barcode`, `expiryDate`, CAST(`quantity` AS REAL), `createdAt`, `updatedAt` FROM `expiry_items`
        """.trimIndent())

        // 3. Remove the old table
        db.execSQL("DROP TABLE `expiry_items`")

        // 4. Rename the new table to the original name
        db.execSQL("ALTER TABLE `expiry_items_new` RENAME TO `expiry_items`")

        // 5. Re-create the index that was dropped with the old table
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_expiry_items_barcode_expiryDate` " +
                "ON `expiry_items` (`barcode`, `expiryDate`)"
        )
    }
}

/**
 * v3 -> v4: adds cached product-name/unit columns resolved from the local
 * product catalog (products.db) at scan time.
 *
 * These are nullable so existing rows simply get NULL until they're
 * re-scanned or edited; the UI falls back to showing the raw barcode when
 * `productName` is null.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `expiry_items` ADD COLUMN `productName` TEXT")
        db.execSQL("ALTER TABLE `expiry_items` ADD COLUMN `productNameArabic` TEXT")
        db.execSQL("ALTER TABLE `expiry_items` ADD COLUMN `unit` TEXT")
    }
}

/**
 * v4 -> v5: adds itemCode column (POS/Item Code from column B of the catalog).
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `expiry_items` ADD COLUMN `itemCode` TEXT")
    }
}

/**
 * v5 -> v6: adds the `custom_products` table — products the user looked up
 * online (Open Food Facts) for a barcode not in the bundled catalog, and
 * chose to save "for future scans". See [com.nearexpiry.manager.data.local.entity.CustomProductEntity].
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `custom_products` (
                `barcode` TEXT NOT NULL PRIMARY KEY,
                `nameEn` TEXT,
                `nameAr` TEXT,
                `unit` TEXT,
                `itemCode` TEXT
            )
        """.trimIndent())
    }
}

/**
 * v6 -> v7: introduces multi-project (isolated inventory) support.
 *
 *  • Creates the `projects` table.
 *  • Inserts a default "Project 1" (id = 1).
 *  • Adds a `projectId` column to `expiry_items` (default 1) and an index on it.
 *  • Assigns every pre-existing item to Project 1, so upgrading users keep
 *    all their data inside the default project.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `projects` (
                `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                `name` TEXT NOT NULL,
                `colorHex` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL
            )
        """.trimIndent())

        // Default project (id = 1). Uses the app's cyan accent as its colour tag.
        db.execSQL(
            "INSERT INTO `projects` (`id`, `name`, `colorHex`, `createdAt`) VALUES (1, 'Project 1', '#26C6DA', ?)",
            arrayOf<Any>(System.currentTimeMillis())
        )

        db.execSQL("ALTER TABLE `expiry_items` ADD COLUMN `projectId` INTEGER NOT NULL DEFAULT 1")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_expiry_items_projectId` ON `expiry_items` (`projectId`)")
        // Existing rows already default to 1 via the column default, but set
        // explicitly to be safe against any odd pre-existing NULLs.
        db.execSQL("UPDATE `expiry_items` SET `projectId` = 1")
    }
}

val ALL_MIGRATIONS: Array<Migration> = arrayOf(
    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7
)
