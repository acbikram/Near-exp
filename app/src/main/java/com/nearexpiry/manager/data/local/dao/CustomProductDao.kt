package com.nearexpiry.manager.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nearexpiry.manager.data.local.entity.CustomProductEntity

@Dao
interface CustomProductDao {

    @Query("SELECT * FROM custom_products WHERE barcode = :barcode")
    suspend fun findByBarcode(barcode: String): CustomProductEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CustomProductEntity)
}
