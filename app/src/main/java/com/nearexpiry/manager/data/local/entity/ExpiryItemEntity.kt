package com.nearexpiry.manager.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.nearexpiry.manager.domain.model.ExpiryItem
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "expiry_items",
    indices = [Index(value = ["barcode", "expiryDate"]), Index(value = ["projectId"])]
)
data class ExpiryItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val barcode: String,
    val expiryDate: String,
    val quantity: Double,
    val createdAt: Long,
    val updatedAt: Long,
    val productName: String? = null,
    val productNameArabic: String? = null,
    val unit: String? = null,
    /** POS/Item Code from column B of the product catalog (posCode). */
    val itemCode: String? = null,
    /** The project (isolated inventory) this item belongs to. Defaults to 1 ("Project 1"). */
    val projectId: Long = 1,
    /**
     * Null = use [createdAt] (true scan order) for ordering/Sr No./export.
     * Non-null after Move Up/Down: a manually-assigned position that
     * overrides [createdAt] for ordering purposes, WITHOUT ever touching the
     * real scan timestamp — this is what makes "Reset to Scan Order"
     * possible: it just clears this column back to null everywhere in the
     * project, restoring the true original chronology losslessly.
     */
    val displayOrder: Long? = null
)

fun ExpiryItemEntity.toDomain() = ExpiryItem(
    id = id,
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
    displayOrder = displayOrder
)

fun ExpiryItem.toEntity() = ExpiryItemEntity(
    id = id,
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
    displayOrder = displayOrder
)
