package com.nearexpiry.manager.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A soft-deleted expiry item. Deleting an item moves its row here; restoring
 * moves it back. Entries older than 30 days are purged automatically on app
 * start. Keeping a separate table (rather than a deleted flag on the main
 * table) means none of the existing queries need to change.
 */
@Entity(tableName = "recycle_bin", indices = [Index("deletedAt"), Index("originalId")])
data class RecycleBinEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** The item's id in expiry_items at the moment of deletion (for Undo). */
    val originalId: Long,
    val barcode: String,
    val expiryDate: String,
    val quantity: Double,
    val createdAt: Long,
    val updatedAt: Long,
    val productName: String? = null,
    val productNameArabic: String? = null,
    val unit: String? = null,
    val itemCode: String? = null,
    /** Project the item belonged to (may no longer exist after project delete). */
    val projectId: Long,
    /** Project name at deletion time, shown in the bin even if the project is gone. */
    val projectName: String,
    /** When the item was deleted (epoch millis). */
    val deletedAt: Long
)

fun ExpiryItemEntity.toBinEntity(projectName: String, deletedAt: Long) = RecycleBinEntity(
    originalId = id,
    barcode = barcode,
    expiryDate = expiryDate,
    quantity = quantity,
    createdAt = createdAt,
    updatedAt = updatedAt,
    productName = productName,
    productNameArabic = productNameArabic,
    unit = unit,
    itemCode = itemCode,
    projectId = projectId,
    projectName = projectName,
    deletedAt = deletedAt
)

fun RecycleBinEntity.toItemEntity(targetProjectId: Long) = ExpiryItemEntity(
    id = 0,
    barcode = barcode,
    expiryDate = expiryDate,
    quantity = quantity,
    createdAt = createdAt,
    updatedAt = System.currentTimeMillis(),
    productName = productName,
    productNameArabic = productNameArabic,
    unit = unit,
    itemCode = itemCode,
    projectId = targetProjectId
)
