package com.nearexpiry.manager.domain.repository

import com.nearexpiry.manager.data.local.entity.ExpiryItemEntity
import com.nearexpiry.manager.domain.model.ExpiryItem
import kotlinx.coroutines.flow.Flow

interface ExpiryRepository {
    /** Items in a single project (the active inventory). */
    fun getAllItems(projectId: Long): Flow<List<ExpiryItem>>
    suspend fun getItemById(id: Long): ExpiryItem?
    suspend fun findByBarcodeExpiryUnit(projectId: Long, barcode: String, expiryDate: String, unit: String?): ExpiryItem?

    /**
     * The canonical duplicate check: matches on POS/item code + expiry + unit
     * when [itemCode] is present, otherwise falls back to barcode + expiry +
     * unit. Used by scan, manual entry, CSV import and copy/move so that the
     * same product (same item code) is treated as one even across different
     * scanned barcodes.
     */
    suspend fun findDuplicate(projectId: Long, itemCode: String?, barcode: String, expiryDate: String, unit: String?): ExpiryItem?
    suspend fun insertItem(item: ExpiryItemEntity): Long
    suspend fun updateItem(item: ExpiryItemEntity)
    suspend fun deleteItem(item: ExpiryItem)
    suspend fun deleteItemsByIds(ids: List<Long>)
    suspend fun deleteAllInProject(projectId: Long)

    /** Move = reassign the given items to [targetProjectId]. */
    suspend fun moveItemsToProject(ids: List<Long>, targetProjectId: Long)

    /** Snapshot read used by copy/move and notifications. */
    suspend fun getItemsOnce(projectId: Long): List<ExpiryItem>
}
