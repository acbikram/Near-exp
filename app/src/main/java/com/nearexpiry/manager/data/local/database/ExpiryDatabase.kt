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
import com.nearexpiry.manager.data.local.entity.CustomProductEntity
import com.nearexpiry.manager.data.local.entity.ExpiryItemEntity
import com.nearexpiry.manager.data.local.entity.ProjectEntity
import com.nearexpiry.manager.data.local.typeconverter.Converters

@Database(
    entities = [ExpiryItemEntity::class, CustomProductEntity::class, ProjectEntity::class],
    version = 7,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class ExpiryDatabase : RoomDatabase() {
    abstract fun expiryItemDao(): ExpiryItemDao
    abstract fun customProductDao(): CustomProductDao
    abstract fun projectDao(): ProjectDao

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
                    // Safety net for any future version jump that doesn't ship
                    // an explicit Migration (e.g. a skipped/forgotten one).
                    // Explicit migrations above always take priority over this.
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
