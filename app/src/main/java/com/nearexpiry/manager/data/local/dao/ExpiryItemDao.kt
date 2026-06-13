package com.nearexpiry.manager.data.local.dao

import androidx.room.*
import com.nearexpiry.manager.data.local.entity.ExpiryItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpiryItemDao {
    @Query("SELECT * FROM expiry_items ORDER BY createdAt DESC")
    fun getAllItems(): Flow<List<ExpiryItemEntity>>

    @Query("SELECT * FROM expiry_items WHERE id = :id")
    suspend fun getItemById(id: Long): ExpiryItemEntity?

    @Query("SELECT * FROM expiry_items WHERE barcode = :barcode AND expiryDate = :expiryDate LIMIT 1")
    suspend fun findByBarcodeAndExpiry(barcode: String, expiryDate: String): ExpiryItemEntity?

    @Insert
    suspend fun insert(item: ExpiryItemEntity): Long

    @Update
    suspend fun update(item: ExpiryItemEntity)

    @Delete
    suspend fun delete(item: ExpiryItemEntity)

    @Query("DELETE FROM expiry_items")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM expiry_items")
    suspend fun getCount(): Int
}
