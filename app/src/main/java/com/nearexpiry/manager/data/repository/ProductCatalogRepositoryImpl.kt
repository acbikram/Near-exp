package com.nearexpiry.manager.data.repository

import com.nearexpiry.manager.data.local.catalog.ProductCatalogDao
import com.nearexpiry.manager.data.local.database.ExpiryDatabase
import com.nearexpiry.manager.data.local.entity.CustomProductEntity
import com.nearexpiry.manager.data.remote.OpenFoodFactsApi
import com.nearexpiry.manager.domain.model.ProductInfo
import com.nearexpiry.manager.domain.repository.ProductCatalogRepository
import javax.inject.Inject

class ProductCatalogRepositoryImpl @Inject constructor(
    private val dao: ProductCatalogDao,
    expiryDatabase: ExpiryDatabase
) : ProductCatalogRepository {

    private val customProductDao = expiryDatabase.customProductDao()

    override suspend fun lookup(barcode: String): ProductInfo? {
        // 1. Bundled catalog (products.db) — the primary, offline source.
        val entry = dao.findByBarcode(barcode)
        if (entry != null) {
            val name = entry.nameEn?.takeIf { it.isNotBlank() }
            val nameArabic = entry.nameAr?.takeIf { it.isNotBlank() }
            if (name != null || nameArabic != null) {
                // Unit logic:
                //  • EA or POS  → use Prm Uom (column D / uom field)  e.g. "PCS", "KGS"
                //  • CTN / OFR / anything else → use barcode type itself (column E) e.g. "CTN", "OFR"
                val unit = when (entry.barcodeType?.uppercase()) {
                    "EA", "POS" -> entry.uom?.takeIf { it.isNotBlank() } ?: entry.barcodeType
                    else         -> entry.barcodeType?.takeIf { it.isNotBlank() } ?: entry.uom
                }
                return ProductInfo(
                    barcode = entry.barcode,
                    name = name,
                    nameArabic = nameArabic,
                    unit = unit,
                    itemCode = entry.posCode?.takeIf { it.isNotBlank() }
                )
            }
        }

        // 2. User's saved custom products (from a previous online lookup).
        val custom = customProductDao.findByBarcode(barcode) ?: return null
        val name = custom.nameEn?.takeIf { it.isNotBlank() }
        val nameArabic = custom.nameAr?.takeIf { it.isNotBlank() }
        if (name == null && nameArabic == null) return null
        return ProductInfo(
            barcode = custom.barcode,
            name = name,
            nameArabic = nameArabic,
            unit = custom.unit,
            itemCode = custom.itemCode
        )
    }

    override suspend fun lookupByItemCode(itemCode: String): ProductInfo? {
        val entry = dao.findByPosCode(itemCode) ?: return null
        val name = entry.nameEn?.takeIf { it.isNotBlank() }
        val nameArabic = entry.nameAr?.takeIf { it.isNotBlank() }
        if (name == null && nameArabic == null) return null

        val unit = when (entry.barcodeType?.uppercase()) {
            "EA", "POS" -> entry.uom?.takeIf { it.isNotBlank() } ?: entry.barcodeType
            else         -> entry.barcodeType?.takeIf { it.isNotBlank() } ?: entry.uom
        }

        return ProductInfo(
            barcode = entry.barcode,
            name = name,
            nameArabic = nameArabic,
            unit = unit,
            itemCode = entry.posCode?.takeIf { it.isNotBlank() } ?: itemCode
        )
    }

    override suspend fun lookupOnline(barcode: String): ProductInfo? =
        OpenFoodFactsApi.fetchProduct(barcode)

    override suspend fun saveCustomProduct(info: ProductInfo) {
        customProductDao.upsert(
            CustomProductEntity(
                barcode = info.barcode,
                nameEn = info.name,
                nameAr = info.nameArabic,
                unit = info.unit,
                itemCode = info.itemCode
            )
        )
    }
}
