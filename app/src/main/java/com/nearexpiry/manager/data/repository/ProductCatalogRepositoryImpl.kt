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
            unit = unit
        )
    }
}
