package com.nearexpiry.manager.domain.repository

import com.nearexpiry.manager.data.local.entity.ExpiryItemEntity
import com.nearexpiry.manager.domain.model.ExpiryItem
import kotlinx.coroutines.flow.Flow

interface ExpiryRepository {
    /** Items in a single project (the active inventory). */
    fun getAllItems(projectId: Long): Flow<List<ExpiryItem>>
    suspend fun getItemById(id: Long): ExpiryItem?
    /** The item's Sr No. (scan-order rank) within its project. See DAO doc. */
    suspend fun getSerialNumber(projectId: Long, effectiveOrder: Long, id: Long): Int
    /** Clears all manual positions in a project — "Reset to Scan Order". */
    suspend fun clearDisplayOrder(projectId: Long)
    suspend fun findByBarcodeExpiryUnit(projectId: Long, barcode: String, expiryDate: String, unit: String?): ExpiryItem?

    /**
     * The canonical duplicate check: matches on POS/item code + expiry + unit
     * when [itemCode] is present, otherwise falls back to barcode + expiry +
     * unit. Used by scan, manual entry, CSV import and copy/move so that the
     * same product (same item code) is treated as one even across different
     * scanned barcodes.
     */
    suspend fun findDuplicate(projectId: Long, itemCode: String?, barcode: String, expiryDate: String, unit: String?): ExpiryItem?
    /** All entries for the same item (by code/barcode, ignoring expiry) in a project. */
    suspend fun findAllForItem(projectId: Long, itemCode: String?, barcode: String): List<ExpiryItem>
    suspend fun insertItem(item: ExpiryItemEntity): Long
    suspend fun updateItem(item: ExpiryItemEntity)
    suspend fun deleteItem(item: ExpiryItem)
    suspend fun deleteItemsByIds(ids: List<Long>)
    suspend fun deleteAllInProject(projectId: Long)

    // ── Recycle bin ────────────────────────────────────────────────────────
    /** All soft-deleted items, newest first. */
    fun getBinItems(): kotlinx.coroutines.flow.Flow<List<com.nearexpiry.manager.data.local.entity.RecycleBinEntity>>
    /** Restores bin entries to their original project (or [fallbackProjectId] if gone). Returns count restored. */
    suspend fun restoreFromBin(binIds: List<Long>, fallbackProjectId: Long): Int
    suspend fun deleteFromBinPermanently(binIds: List<Long>)
    /** Removes bin copies after an Undo re-insert. */
    suspend fun removeFromBinByOriginalIds(originalIds: List<Long>)
    /** Drops bin entries older than [maxAgeDays]. */
    suspend fun purgeOldBinEntries(maxAgeDays: Int)

    /** Move = reassign the given items to [targetProjectId]. */
    suspend fun moveItemsToProject(ids: List<Long>, targetProjectId: Long)

    /** Snapshot read used by copy/move and notifications. */
    suspend fun getItemsOnce(projectId: Long): List<ExpiryItem>
}
