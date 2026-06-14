package com.nearexpiry.manager.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A product the user looked up online (Open Food Facts) for a barcode not
 * found in the bundled catalog, and chose to save "for future scans".
 *
 * Checked as a fallback by [com.nearexpiry.manager.data.repository.ProductCatalogRepositoryImpl.lookup]
 * after the bundled `products.db` catalog comes up empty, so subsequent
 * scans of the same barcode resolve offline without hitting the network
 * again.
 */
@Entity(tableName = "custom_products")
data class CustomProductEntity(
    @PrimaryKey val barcode: String,
    val nameEn: String?,
    val nameAr: String?,
    val unit: String?,
    val itemCode: String? = null
)
