package com.nearexpiry.manager.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.nearexpiry.manager.domain.model.ExpiryItem
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "expiry_items",
    indices = [Index(value = ["barcode", "expiryDate"])]
)
data class ExpiryItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val barcode: String,
    val expiryDate: String,
    val quantity: Double,
    val createdAt: Long,
    val updatedAt: Long,
    /** Product name resolved from the local catalog at scan time (English). */
    val productName: String? = null,
    /** Product name resolved from the local catalog at scan time (Arabic). */
    val productNameArabic: String? = null,
    /** Unit of measure resolved from the local catalog (e.g. "PCS", "KGS"). */
    val unit: String? = null
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
    unit = unit
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
    unit = unit
)
