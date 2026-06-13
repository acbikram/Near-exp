package com.nearexpiry.manager.data.repository

import com.nearexpiry.manager.data.local.dao.ExpiryItemDao
import com.nearexpiry.manager.data.local.database.ExpiryDatabase
import com.nearexpiry.manager.data.local.entity.ExpiryItemEntity
import com.nearexpiry.manager.data.local.entity.toDomain
import com.nearexpiry.manager.data.local.entity.toEntity
import com.nearexpiry.manager.domain.model.ExpiryItem
import com.nearexpiry.manager.domain.repository.ExpiryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExpiryRepositoryImpl @Inject constructor(
    private val database: ExpiryDatabase
) : ExpiryRepository {

    private val dao = database.expiryItemDao()

    override fun getAllItems(): Flow<List<ExpiryItem>> {
        return dao.getAllItems().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getItemById(id: Long): ExpiryItem? {
        return dao.getItemById(id)?.toDomain()
    }

    override suspend fun findByBarcodeAndExpiry(barcode: String, expiryDate: String): ExpiryItem? {
        return dao.findByBarcodeAndExpiry(barcode, expiryDate)?.toDomain()
    }

    override suspend fun insertItem(item: ExpiryItemEntity): Long {
        return dao.insert(item)
    }

    override suspend fun updateItem(item: ExpiryItemEntity) {
        dao.update(item)
    }

    override suspend fun deleteItem(item: ExpiryItem) {
        dao.delete(item.toEntity())
    }

    override suspend fun deleteAllItems() {
        dao.deleteAll()
    }
}
