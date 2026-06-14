package com.nearexpiry.manager.domain.repository

import com.nearexpiry.manager.domain.model.ProductInfo

interface ProductCatalogRepository {
    /**
     * Returns product info for [barcode] from the bundled catalog, then the
     * user's saved custom products, or null if it's in neither.
     */
    suspend fun lookup(barcode: String): ProductInfo?

    /**
     * Looks up [barcode] via Open Food Facts (network call). Returns null on
     * any error or if the product isn't found / has no usable name. Purely
     * additive — never modifies local data on its own.
     */
    suspend fun lookupOnline(barcode: String): ProductInfo?

    /**
     * Saves [info] to the local custom-products table so future scans of
     * the same barcode resolve via [lookup] without another network call.
     */
    suspend fun saveCustomProduct(info: ProductInfo)
}
