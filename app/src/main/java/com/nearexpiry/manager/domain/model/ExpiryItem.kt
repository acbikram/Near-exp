package com.nearexpiry.manager.domain.model

data class ExpiryItem(
    val id: Long,
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
