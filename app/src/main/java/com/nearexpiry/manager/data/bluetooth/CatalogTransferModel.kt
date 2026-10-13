package com.nearexpiry.manager.data.bluetooth

import com.nearexpiry.manager.data.local.catalog.ProductCatalogEntry
import com.nearexpiry.manager.data.local.entity.CustomProductEntity
import kotlinx.serialization.Serializable

/** Snapshot of the main product catalog and manually saved products for Bluetooth sync. */
@Serializable
data class CatalogTransferModel(
    val protocol: String = PROTOCOL,
    val version: Int = VERSION,
    val catalog: List<CatalogTransferProduct>,
    val customProducts: List<CatalogTransferCustomProduct>,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val PROTOCOL = "near-expiry-catalog-sync"
        const val VERSION = 1
        const val MAX_CATALOG_ROWS = 250_000
        const val MAX_CUSTOM_PRODUCTS = 100_000
    }

    fun validate(): Result<CatalogTransferModel> = runCatching {
        require(protocol == PROTOCOL) { "Unsupported catalog-sync protocol" }
        require(version == VERSION) { "Unsupported catalog-sync version" }
        require(catalog.size <= MAX_CATALOG_ROWS) { "Catalog contains too many products" }
        require(customProducts.size <= MAX_CUSTOM_PRODUCTS) { "Too many custom products" }
        require(catalog.all { (it.barcode.isNotBlank() || !it.posCode.isNullOrBlank()) && it.barcode.length <= 512 }) {
            "Catalog contains a row without a barcode or POS code"
        }
        require(customProducts.all { it.barcode.isNotBlank() && it.barcode.length <= 512 }) {
            "Custom products contain an invalid barcode"
        }
        this
    }
}

@Serializable
data class CatalogTransferProduct(
    val barcode: String,
    val posCode: String?,
    val nameEn: String?,
    val nameAr: String?,
    val uom: String?,
    val barcodeType: String?
)

@Serializable
data class CatalogTransferCustomProduct(
    val barcode: String,
    val nameEn: String?,
    val nameAr: String?,
    val unit: String?,
    val itemCode: String?
)

fun ProductCatalogEntry.toTransferProduct() = CatalogTransferProduct(
    barcode = barcode,
    posCode = posCode,
    nameEn = nameEn,
    nameAr = nameAr,
    uom = uom,
    barcodeType = barcodeType
)

fun CatalogTransferProduct.toCatalogEntry() = ProductCatalogEntry(
    barcode = barcode,
    posCode = posCode,
    nameEn = nameEn,
    nameAr = nameAr,
    uom = uom,
    barcodeType = barcodeType
)

fun CustomProductEntity.toTransferCustomProduct() = CatalogTransferCustomProduct(
    barcode = barcode,
    nameEn = nameEn,
    nameAr = nameAr,
    unit = unit,
    itemCode = itemCode
)

fun CatalogTransferCustomProduct.toCustomProductEntity() = CustomProductEntity(
    barcode = barcode,
    nameEn = nameEn,
    nameAr = nameAr,
    unit = unit,
    itemCode = itemCode
)
