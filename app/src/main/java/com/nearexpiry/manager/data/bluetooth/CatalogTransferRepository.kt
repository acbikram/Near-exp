package com.nearexpiry.manager.data.bluetooth

import androidx.room.withTransaction
import com.nearexpiry.manager.data.local.catalog.ProductCatalogDao
import com.nearexpiry.manager.data.local.database.ExpiryDatabase
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CatalogTransferRepository @Inject constructor(
    private val catalogDao: ProductCatalogDao,
    private val database: ExpiryDatabase
) {
    suspend fun exportCatalog(): CatalogTransferModel {
        val catalog = catalogDao.exportEntries().map { it.toTransferProduct() }
        val customProducts = database.customProductDao().getAll()
            .map { it.toTransferCustomProduct() }
        return CatalogTransferModel(catalog = catalog, customProducts = customProducts)
            .also { it.validate().getOrThrow() }
    }

    /** Merge received rows without deleting anything that exists only on this phone. */
    suspend fun mergeCatalog(model: CatalogTransferModel): Int {
        model.validate().getOrThrow()
        return database.withTransaction {
            catalogDao.mergeEntries(model.catalog.map { it.toCatalogEntry() })
            model.customProducts.forEach { product ->
                database.customProductDao().upsert(product.toCustomProductEntity())
            }
            catalogDao.countProducts()
        }
    }
}
