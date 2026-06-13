package com.nearexpiry.manager.domain.model

data class ExpiryItem(
    val id: Long,
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
