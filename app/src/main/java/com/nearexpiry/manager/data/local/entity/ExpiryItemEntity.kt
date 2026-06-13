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
    val productName: String? = null,
    val productNameArabic: String? = null,
    val unit: String? = null,
    /** POS/Item Code from column B of the product catalog (posCode). */
    val itemCode: String? = null
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
    itemCode = itemCode
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
    itemCode = itemCode
)
