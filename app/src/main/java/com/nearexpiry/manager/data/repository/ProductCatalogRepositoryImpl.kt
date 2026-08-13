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

    /**
     * Resolves scanned input only from the imported catalog. A valid scan may
     * match either the catalog barcode or its POS/item-code column; saved custom
     * products and online lookups intentionally do not bypass this validation.
     */
    override suspend fun lookup(barcode: String): ProductInfo? {
        val catalogBarcode = dao.findByBarcode(barcode)
        if (catalogBarcode != null) {
            val name = catalogBarcode.nameEn?.takeIf { it.isNotBlank() }
            val nameArabic = catalogBarcode.nameAr?.takeIf { it.isNotBlank() }
            if (name != null || nameArabic != null) {
                val unit = when (catalogBarcode.barcodeType?.uppercase()) {
                    "EA", "POS" -> catalogBarcode.uom?.takeIf { it.isNotBlank() } ?: catalogBarcode.barcodeType
                    else -> catalogBarcode.barcodeType?.takeIf { it.isNotBlank() } ?: catalogBarcode.uom
                }
                return ProductInfo(
                    barcode = catalogBarcode.barcode,
                    name = name,
                    nameArabic = nameArabic,
                    unit = unit,
                    itemCode = catalogBarcode.posCode?.takeIf { it.isNotBlank() }
                )
            }
        }
        return lookupByItemCode(barcode)
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

    override suspend fun updateCatalog(inputStream: java.io.InputStream): Int =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            dao.importCatalog(inputStream)
        }

    override suspend fun catalogProductCount(): Int =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            dao.countProducts()
        }

    override suspend fun searchCatalog(query: String): List<ProductInfo> =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            dao.search(query).mapNotNull { entry ->
                val name = entry.nameEn?.takeIf { it.isNotBlank() }
                val nameArabic = entry.nameAr?.takeIf { it.isNotBlank() }
                if (name == null && nameArabic == null) return@mapNotNull null
                val unit = when (entry.barcodeType?.uppercase()) {
                    "EA", "POS" -> entry.uom?.takeIf { it.isNotBlank() } ?: entry.barcodeType
                    else         -> entry.barcodeType?.takeIf { it.isNotBlank() } ?: entry.uom
                }
                ProductInfo(
                    barcode = entry.barcode,
                    name = name,
                    nameArabic = nameArabic,
                    unit = unit,
                    itemCode = entry.posCode?.takeIf { it.isNotBlank() }
                )
            }
        }
}
