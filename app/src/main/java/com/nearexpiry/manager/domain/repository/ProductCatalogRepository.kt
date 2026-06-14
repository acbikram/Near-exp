package com.nearexpiry.manager.domain.repository

import com.nearexpiry.manager.domain.model.ProductInfo

interface ProductCatalogRepository {
    /** Returns product info for [barcode], or null if it's not in the catalog. */
    suspend fun lookup(barcode: String): ProductInfo?
}
