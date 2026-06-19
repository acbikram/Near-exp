package com.nearexpiry.manager.domain.model

/**
 * Product info resolved from the local catalog for a scanned barcode.
 */
data class ProductInfo(
    val barcode: String,
    val name: String?,
    val nameArabic: String?,
    val unit: String?,
    /** POS/Item Code from column B of the product catalog. */
    val itemCode: String? = null
)
