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

    @Query("""
        SELECT * FROM expiry_items
        WHERE projectId = :projectId AND barcode = :barcode AND expiryDate = :expiryDate
        LIMIT 1
    """)
    suspend fun findByBarcodeAndExpiry(projectId: Long, barcode: String, expiryDate: String): ExpiryItemEntity?

    /**
     * Finds an existing row in [projectId] with the same barcode, expiry date,
     * AND unit (NULL-safe). Used by CSV import and copy/move to merge
     * truly-identical rows within a project.
     */
    @Query("""
        SELECT * FROM expiry_items
        WHERE projectId = :projectId AND barcode = :barcode AND expiryDate = :expiryDate
          AND ((unit IS NULL AND :unit IS NULL) OR unit = :unit)
        LIMIT 1
    """)
    suspend fun findByBarcodeExpiryUnit(projectId: Long, barcode: String, expiryDate: String, unit: String?): ExpiryItemEntity?

    @Query("SELECT COUNT(*) FROM expiry_items WHERE projectId = :projectId")
    suspend fun getCount(projectId: Long): Int

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
