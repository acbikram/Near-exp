package com.nearexpiry.manager.data.local.catalog

/**
 * A single row from the bundled `products.db` catalog (built from
 * Price_Tag_Master_CTN.xlsx). This is read-only reference data shipped
 * with the app -- it is NOT part of the user's Room database.
 */
data class ProductCatalogEntry(
    val barcode: String,
    val posCode: String?,
    val nameEn: String?,
    val nameAr: String?,
    val uom: String?,
    val barcodeType: String?
)
