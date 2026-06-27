package com.nearexpiry.manager.domain.repository

import com.nearexpiry.manager.domain.model.ProductInfo

interface ProductCatalogRepository {
    /**
     * Returns product info for [barcode] from the bundled catalog, then the
     * user's saved custom products, or null if it's in neither.
     */
    suspend fun lookup(barcode: String): ProductInfo?

    /**
     * Returns product info for a given POS/Item Code (column B), or null if
     * it's not in the bundled catalog. Used by CSV import to fill in
     * ITEM_DESCRIPTION/UOM when those columns are blank.
     */
    suspend fun lookupByItemCode(itemCode: String): ProductInfo?

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

    /**
     * Replaces the bundled product catalog with a user-provided CSV or
     * products.db file. Returns the number of products afterwards.
     */
    suspend fun updateCatalog(inputStream: java.io.InputStream): Int

    /** Current number of products in the catalog (0 if empty). */
    suspend fun catalogProductCount(): Int

    /** Searches the catalog by partial name/POS/barcode for manual entry. */
    suspend fun searchCatalog(query: String): List<ProductInfo>
}
