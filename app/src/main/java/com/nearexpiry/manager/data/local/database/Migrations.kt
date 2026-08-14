package com.nearexpiry.manager.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room migrations run in a transaction, so a normal upgrade is atomic. These
 * helpers are deliberately limited to the migration-repair step below: they
 * make additive repairs safe for databases left in an inconsistent state by a
 * legacy pre-release build, a device-level restore, or interrupted external
 * file handling. Table names are internal constants, never user input.
 */
private fun tableExists(db: SupportSQLiteDatabase, table: String): Boolean =
    db.query("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = '$table'").use {
        it.moveToFirst()
    }

private fun columnExists(db: SupportSQLiteDatabase, table: String, column: String): Boolean =
    db.query("PRAGMA table_info(`$table`)").use { cursor ->
        val nameIndex = cursor.getColumnIndex("name")
        while (cursor.moveToNext()) {
            if (cursor.getString(nameIndex) == column) return@use true
        }
        false
    }

private fun addColumnIfMissing(
    db: SupportSQLiteDatabase,
    table: String,
    column: String,
    definition: String
) {
    if (!columnExists(db, table, column)) {
        db.execSQL("ALTER TABLE `$table` ADD COLUMN `$column` $definition")
    }
}

/**
 * SQLite cannot add or change a DEFAULT constraint in place. Rebuild the two
 * affected tables inside Room's migration transaction so version-10 databases
 * produced by different historical paths converge on one validated schema.
 * Every persisted row is copied before the original table is replaced.
 */
private fun normalizeExpiryItemsSchema(db: SupportSQLiteDatabase) {
    db.execSQL("DROP TABLE IF EXISTS `expiry_items_repaired`")
    db.execSQL(
        """
        CREATE TABLE `expiry_items_repaired` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `barcode` TEXT NOT NULL,
            `expiryDate` TEXT NOT NULL,
            `quantity` REAL NOT NULL,
            `createdAt` INTEGER NOT NULL,
            `updatedAt` INTEGER NOT NULL,
            `productName` TEXT,
            `productNameArabic` TEXT,
            `unit` TEXT,
            `itemCode` TEXT,
            `projectId` INTEGER NOT NULL DEFAULT 1,
            `displayOrder` INTEGER
        )
        """.trimIndent()
    )
    db.execSQL(
        """
        INSERT INTO `expiry_items_repaired`
            (`id`, `barcode`, `expiryDate`, `quantity`, `createdAt`, `updatedAt`,
             `productName`, `productNameArabic`, `unit`, `itemCode`, `projectId`, `displayOrder`)
        SELECT `id`, `barcode`, `expiryDate`, `quantity`, `createdAt`, `updatedAt`,
               `productName`, `productNameArabic`, `unit`, `itemCode`,
               COALESCE(`projectId`, 1), `displayOrder`
        FROM `expiry_items`
        """.trimIndent()
    )
    db.execSQL("DROP TABLE `expiry_items`")
    db.execSQL("ALTER TABLE `expiry_items_repaired` RENAME TO `expiry_items`")
}

private fun normalizeProjectsSchema(db: SupportSQLiteDatabase) {
    db.execSQL("DROP TABLE IF EXISTS `projects_repaired`")
    db.execSQL(
        """
        CREATE TABLE `projects_repaired` (
            `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
            `name` TEXT NOT NULL,
            `colorHex` TEXT NOT NULL,
            `createdAt` INTEGER NOT NULL,
            `hasCustomSort` INTEGER NOT NULL DEFAULT 0,
            `isStockMode` INTEGER NOT NULL DEFAULT 0
        )
        """.trimIndent()
    )
    db.execSQL(
        """
        INSERT INTO `projects_repaired`
            (`id`, `name`, `colorHex`, `createdAt`, `hasCustomSort`, `isStockMode`)
        SELECT `id`, `name`, `colorHex`, `createdAt`,
               COALESCE(`hasCustomSort`, 0), COALESCE(`isStockMode`, 0)
        FROM `projects`
        """.trimIndent()
    )
    db.execSQL("DROP TABLE `projects`")
    db.execSQL("ALTER TABLE `projects_repaired` RENAME TO `projects`")
}

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
 * This index backs the duplicate-lookup queries (barcode + expiry),
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

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `recycle_bin` (
                `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                `originalId` INTEGER NOT NULL,
                `barcode` TEXT NOT NULL,
                `expiryDate` TEXT NOT NULL,
                `quantity` REAL NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `productName` TEXT,
                `productNameArabic` TEXT,
                `unit` TEXT,
                `itemCode` TEXT,
                `projectId` INTEGER NOT NULL,
                `projectName` TEXT NOT NULL,
                `deletedAt` INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recycle_bin_deletedAt` ON `recycle_bin` (`deletedAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recycle_bin_originalId` ON `recycle_bin` (`originalId`)")
    }
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Nullable "manual position" column — null means "use createdAt" (true
        // scan order). Move Up/Down sets this instead of touching createdAt,
        // so the real scan timestamp is preserved and "Reset to Scan Order"
        // is always possible losslessly.
        db.execSQL("ALTER TABLE `expiry_items` ADD COLUMN `displayOrder` INTEGER")
        // Per-project flag: true once any item has a custom displayOrder —
        // purely a "Scan Order" vs "Custom Sort" label switch.
        db.execSQL("ALTER TABLE `projects` ADD COLUMN `hasCustomSort` INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Stock Mode is intentionally latched at the project level so a Stock
        // project remains inventory-focused even if the user later renames it.
        db.execSQL("ALTER TABLE `projects` ADD COLUMN `isStockMode` INTEGER NOT NULL DEFAULT 0")
        // Upgrade existing eligible stock-check projects that already contain
        // inventory. Empty projects remain normal until their first item is saved.
        db.execSQL(
            """
            UPDATE `projects`
            SET `isStockMode` = 1
            WHERE LOWER(`name`) LIKE '%stock%'
              AND EXISTS (SELECT 1 FROM `expiry_items` WHERE `expiry_items`.`projectId` = `projects`.`id`)
            """.trimIndent()
        )
    }
}

/**
 * v10 -> v11: non-destructive repair pass for legacy installations.
 *
 * All historical migrations above remain the canonical upgrade path. This
 * final pass repairs missing additive columns/tables and indexes, then
 * normalizes SQL defaults that SQLite cannot change in place. Normalization
 * copies every persisted row inside the Room migration transaction before the
 * legacy table is replaced. It also recreates project records for orphaned
 * item project IDs so legacy inventory remains visible rather than failing
 * project queries.
 */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        if (!tableExists(db, "expiry_items")) {
            db.execSQL(
                """
                CREATE TABLE `expiry_items` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `barcode` TEXT NOT NULL,
                    `expiryDate` TEXT NOT NULL,
                    `quantity` REAL NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    `productName` TEXT,
                    `productNameArabic` TEXT,
                    `unit` TEXT,
                    `itemCode` TEXT,
                    `projectId` INTEGER NOT NULL DEFAULT 1,
                    `displayOrder` INTEGER
                )
                """.trimIndent()
            )
        } else {
            addColumnIfMissing(db, "expiry_items", "productName", "TEXT")
            addColumnIfMissing(db, "expiry_items", "productNameArabic", "TEXT")
            addColumnIfMissing(db, "expiry_items", "unit", "TEXT")
            addColumnIfMissing(db, "expiry_items", "itemCode", "TEXT")
            addColumnIfMissing(db, "expiry_items", "projectId", "INTEGER NOT NULL DEFAULT 1")
            addColumnIfMissing(db, "expiry_items", "displayOrder", "INTEGER")
            normalizeExpiryItemsSchema(db)
        }

        if (!tableExists(db, "custom_products")) {
            db.execSQL(
                """
                CREATE TABLE `custom_products` (
                    `barcode` TEXT NOT NULL PRIMARY KEY,
                    `nameEn` TEXT,
                    `nameAr` TEXT,
                    `unit` TEXT,
                    `itemCode` TEXT
                )
                """.trimIndent()
            )
        } else {
            addColumnIfMissing(db, "custom_products", "itemCode", "TEXT")
        }

        if (!tableExists(db, "projects")) {
            db.execSQL(
                """
                CREATE TABLE `projects` (
                    `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                    `name` TEXT NOT NULL,
                    `colorHex` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `hasCustomSort` INTEGER NOT NULL DEFAULT 0,
                    `isStockMode` INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
        } else {
            addColumnIfMissing(db, "projects", "hasCustomSort", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "projects", "isStockMode", "INTEGER NOT NULL DEFAULT 0")
            normalizeProjectsSchema(db)
        }

        if (!tableExists(db, "recycle_bin")) {
            db.execSQL(
                """
                CREATE TABLE `recycle_bin` (
                    `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                    `originalId` INTEGER NOT NULL,
                    `barcode` TEXT NOT NULL,
                    `expiryDate` TEXT NOT NULL,
                    `quantity` REAL NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    `productName` TEXT,
                    `productNameArabic` TEXT,
                    `unit` TEXT,
                    `itemCode` TEXT,
                    `projectId` INTEGER NOT NULL,
                    `projectName` TEXT NOT NULL,
                    `deletedAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }

        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_expiry_items_barcode_expiryDate` " +
                "ON `expiry_items` (`barcode`, `expiryDate`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_expiry_items_projectId` " +
                "ON `expiry_items` (`projectId`)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recycle_bin_deletedAt` ON `recycle_bin` (`deletedAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recycle_bin_originalId` ON `recycle_bin` (`originalId`)")

        val now = System.currentTimeMillis()
        db.execSQL(
            """
            INSERT OR IGNORE INTO `projects`
                (`id`, `name`, `colorHex`, `createdAt`, `hasCustomSort`, `isStockMode`)
            VALUES (1, 'Project 1', '#26C6DA', ?, 0, 0)
            """.trimIndent(),
            arrayOf<Any>(now)
        )
        db.execSQL("UPDATE `expiry_items` SET `projectId` = 1 WHERE `projectId` IS NULL")
        // Preserve items whose project rows were lost instead of silently
        // redirecting them to Project 1 or making them disappear from Home.
        db.execSQL(
            """
            INSERT OR IGNORE INTO `projects`
                (`id`, `name`, `colorHex`, `createdAt`, `hasCustomSort`, `isStockMode`)
            SELECT DISTINCT `expiry_items`.`projectId`,
                'Recovered Project ' || `expiry_items`.`projectId`,
                '#26C6DA', ?, 0, 0
            FROM `expiry_items`
            LEFT JOIN `projects` ON `projects`.`id` = `expiry_items`.`projectId`
            WHERE `projects`.`id` IS NULL
            """.trimIndent(),
            arrayOf<Any>(now)
        )
        // v9->v10 originally latched only names containing "stock". Include
        // existing Recheck projects so their Stock Mode survives a later rename.
        db.execSQL(
            """
            UPDATE `projects`
            SET `isStockMode` = 1
            WHERE `isStockMode` = 0
              AND (LOWER(`name`) LIKE '%stock%' OR LOWER(`name`) LIKE '%recheck%')
              AND EXISTS (
                  SELECT 1 FROM `expiry_items`
                  WHERE `expiry_items`.`projectId` = `projects`.`id`
              )
            """.trimIndent()
        )
    }
}

/**
 * v11 -> v12: fixes project-flag writes from legacy Room schemas.
 *
 * Room's Kotlin constructor defaults are not SQL defaults unless explicitly
 * declared with [androidx.room.ColumnInfo]. Rebuild the table so both flags
 * have database-level defaults and normalize any rows created by an older
 * incomplete insert. Existing project IDs and all inventory references remain
 * unchanged.
 */
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        normalizeProjectsSchema(db)
        db.execSQL(
            """
            INSERT OR IGNORE INTO `projects`
                (`id`, `name`, `colorHex`, `createdAt`, `hasCustomSort`, `isStockMode`)
            VALUES (1, 'Project 1', '#26C6DA', ?, 0, 0)
            """.trimIndent(),
            arrayOf<Any>(System.currentTimeMillis())
        )
    }
}

val ALL_MIGRATIONS: Array<Migration> = arrayOf(
    MIGRATION_1_2,
    MIGRATION_2_3,
    MIGRATION_3_4,
    MIGRATION_4_5,
    MIGRATION_5_6,
    MIGRATION_6_7,
    MIGRATION_7_8,
    MIGRATION_8_9,
    MIGRATION_9_10,
    MIGRATION_10_11,
    MIGRATION_11_12
)
