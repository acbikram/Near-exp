package com.nearexpiry.manager.data.local.dao

import androidx.room.*
import com.nearexpiry.manager.data.local.entity.ExpiryItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpiryItemDao {

    // ── Project-scoped reads ──────────────────────────────────────────────────

    @Query("SELECT * FROM expiry_items WHERE projectId = :projectId ORDER BY createdAt DESC")
    fun getAllItems(projectId: Long): Flow<List<ExpiryItemEntity>>

    /** One-shot (non-Flow) read used by the notification worker, scoped to a project. */
    @Query("SELECT * FROM expiry_items WHERE projectId = :projectId")
    suspend fun getAllItemsOnce(projectId: Long): List<ExpiryItemEntity>

    @Query("SELECT * FROM expiry_items WHERE id = :id")
    suspend fun getItemById(id: Long): ExpiryItemEntity?

    /**
     * Finds an existing row in [projectId] with the same barcode, expiry date,
     * AND unit (NULL-safe). Used as the fallback duplicate check for items
     * that have no POS/item code.
     */
    @Query("""
        SELECT * FROM expiry_items
        WHERE projectId = :projectId AND barcode = :barcode AND expiryDate = :expiryDate
          AND ((unit IS NULL AND :unit IS NULL) OR unit = :unit)
        LIMIT 1
    """)
    suspend fun findByBarcodeExpiryUnit(projectId: Long, barcode: String, expiryDate: String, unit: String?): ExpiryItemEntity?

    /**
     * Finds an existing row in [projectId] with the same POS/item code, expiry
     * date, AND unit (NULL-safe). This is the primary duplicate check: two
     * items with the same itemCode are the same product even if their scanned
     * barcodes differ.
     */
    @Query("""
        SELECT * FROM expiry_items
        WHERE projectId = :projectId AND itemCode = :itemCode AND expiryDate = :expiryDate
          AND ((unit IS NULL AND :unit IS NULL) OR unit = :unit)
        LIMIT 1
    """)
    suspend fun findByItemCodeExpiryUnit(projectId: Long, itemCode: String, expiryDate: String, unit: String?): ExpiryItemEntity?

    @Query("SELECT COUNT(*) FROM expiry_items WHERE projectId = :projectId")
    suspend fun getCount(projectId: Long): Int

    /**
     * All entries for the same item in a project, ignoring expiry — matched by
     * item code when present, otherwise by barcode. Used to show existing
     * expiry dates + quantities when re-scanning an item. Newest first.
     */
    @Query("""
        SELECT * FROM expiry_items
        WHERE projectId = :projectId
          AND (
                (:itemCode IS NOT NULL AND :itemCode != '' AND itemCode = :itemCode)
             OR ((:itemCode IS NULL OR :itemCode = '') AND barcode = :barcode)
          )
        ORDER BY createdAt DESC
    """)
    suspend fun findAllForItem(projectId: Long, itemCode: String?, barcode: String): List<ExpiryItemEntity>

    // ── Writes ────────────────────────────────────────────────────────────────

    @Insert
    suspend fun insert(item: ExpiryItemEntity): Long

    @Update
    suspend fun update(item: ExpiryItemEntity)

    @Delete
    suspend fun delete(item: ExpiryItemEntity)

    @Query("DELETE FROM expiry_items WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    /** Clears every item in a single project. */
    @Query("DELETE FROM expiry_items WHERE projectId = :projectId")
    suspend fun deleteAllInProject(projectId: Long)

    /** Reassigns the given items to another project (the "Move" operation). */
    @Query("UPDATE expiry_items SET projectId = :targetProjectId, updatedAt = :now WHERE id IN (:ids)")
    suspend fun moveItemsToProject(ids: List<Long>, targetProjectId: Long, now: Long)
}
