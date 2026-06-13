package com.nearexpiry.manager.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.nearexpiry.manager.data.local.dao.ExpiryItemDao
import com.nearexpiry.manager.data.local.entity.ExpiryItemEntity
import com.nearexpiry.manager.data.local.typeconverter.Converters

@Database(
    entities = [ExpiryItemEntity::class],
    version = 5,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class ExpiryDatabase : RoomDatabase() {
    abstract fun expiryItemDao(): ExpiryItemDao

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
