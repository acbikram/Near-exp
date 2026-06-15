package com.nearexpiry.manager.data.local.dao

import androidx.room.*
import com.nearexpiry.manager.data.local.entity.ExpiryItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpiryItemDao {
    @Query("SELECT * FROM expiry_items ORDER BY createdAt DESC")
    fun getAllItems(): Flow<List<ExpiryItemEntity>>

    /** One-shot (non-Flow) read used by the notification worker. */
    @Query("SELECT * FROM expiry_items")
    suspend fun getAllItemsOnce(): List<ExpiryItemEntity>

    @Query("SELECT * FROM expiry_items WHERE id = :id")
    suspend fun getItemById(id: Long): ExpiryItemEntity?

    @Query("SELECT * FROM expiry_items WHERE barcode = :barcode AND expiryDate = :expiryDate LIMIT 1")
    suspend fun findByBarcodeAndExpiry(barcode: String, expiryDate: String): ExpiryItemEntity?

    /**
     * Finds an existing row with the same barcode, expiry date, AND unit
     * (NULL-safe). Used by CSV import to merge truly-identical rows.
     */
    @Query("""
        SELECT * FROM expiry_items
        WHERE barcode = :barcode AND expiryDate = :expiryDate
          AND ((unit IS NULL AND :unit IS NULL) OR unit = :unit)
        LIMIT 1
    """)
    suspend fun findByBarcodeExpiryUnit(barcode: String, expiryDate: String, unit: String?): ExpiryItemEntity?

    @Insert
    suspend fun insert(item: ExpiryItemEntity): Long

    @Update
    suspend fun update(item: ExpiryItemEntity)

    @Delete
    suspend fun delete(item: ExpiryItemEntity)

    @Query("DELETE FROM expiry_items")
    suspend fun deleteAll()

    @Query("DELETE FROM expiry_items WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM expiry_items")
    suspend fun getCount(): Int
}
