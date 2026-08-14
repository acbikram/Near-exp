package com.nearexpiry.manager.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.nearexpiry.manager.data.local.dao.CustomProductDao
import com.nearexpiry.manager.data.local.dao.ExpiryItemDao
import com.nearexpiry.manager.data.local.dao.ProjectDao
import com.nearexpiry.manager.data.local.dao.RecycleBinDao
import com.nearexpiry.manager.data.local.entity.CustomProductEntity
import com.nearexpiry.manager.data.local.entity.ExpiryItemEntity
import com.nearexpiry.manager.data.local.entity.ProjectEntity
import com.nearexpiry.manager.data.local.entity.RecycleBinEntity
import com.nearexpiry.manager.data.local.typeconverter.Converters

@Database(
    entities = [ExpiryItemEntity::class, CustomProductEntity::class, ProjectEntity::class, RecycleBinEntity::class],
    version = 11,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class ExpiryDatabase : RoomDatabase() {
    abstract fun expiryItemDao(): ExpiryItemDao
    abstract fun customProductDao(): CustomProductDao
    abstract fun projectDao(): ProjectDao
    abstract fun recycleBinDao(): RecycleBinDao

    companion object {
        @Volatile
        private var INSTANCE: ExpiryDatabase? = null

        fun getInstance(context: Context): ExpiryDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ExpiryDatabase::class.java,
                    "expiry_database"
                )
                    .addMigrations(*ALL_MIGRATIONS)
                    // Seeds the default "Project 1" the moment a brand-new
                    // database is created (fresh install, or after a
                    // destructive rebuild). Upgrading users get Project 1 from
                    // MIGRATION_6_7 instead, so onCreate won't run for them.
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            db.execSQL(
                                "INSERT INTO `projects` (`id`, `name`, `colorHex`, `createdAt`) " +
                                    "VALUES (1, 'Project 1', '#26C6DA', ?)",
                                arrayOf<Any>(System.currentTimeMillis())
                            )
                        }
                    })
                    // NOTE: no fallbackToDestructiveMigration() — every schema
                    // change MUST ship an explicit Migration. Without this, a
                    // forgotten migration crashes loudly in testing instead of
                    // silently wiping all of the user's inventory in production.
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
