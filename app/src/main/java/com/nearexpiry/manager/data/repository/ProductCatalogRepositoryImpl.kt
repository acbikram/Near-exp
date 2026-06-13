package com.nearexpiry.manager.data.repository

import com.nearexpiry.manager.data.local.catalog.ProductCatalogDao
import com.nearexpiry.manager.domain.model.ProductInfo
import com.nearexpiry.manager.domain.repository.ProductCatalogRepository
import javax.inject.Inject

class ProductCatalogRepositoryImpl @Inject constructor(
    private val dao: ProductCatalogDao
) : ProductCatalogRepository {

    override suspend fun lookup(barcode: String): ProductInfo? {
        val entry = dao.findByBarcode(barcode) ?: return null
        val name = entry.nameEn?.takeIf { it.isNotBlank() }
        val nameArabic = entry.nameAr?.takeIf { it.isNotBlank() }
        if (name == null && nameArabic == null) return null
        return ProductInfo(
            barcode = entry.barcode,
            name = name,
            nameArabic = nameArabic,
            unit = entry.uom
        )
    }
}
