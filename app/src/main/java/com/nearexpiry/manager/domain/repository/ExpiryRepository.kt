package com.nearexpiry.manager.domain.repository

import com.nearexpiry.manager.data.local.entity.ExpiryItemEntity
import com.nearexpiry.manager.domain.model.ExpiryItem
import kotlinx.coroutines.flow.Flow

interface ExpiryRepository {
    fun getAllItems(): Flow<List<ExpiryItem>>
    suspend fun getItemById(id: Long): ExpiryItem?
    suspend fun findByBarcodeAndExpiry(barcode: String, expiryDate: String): ExpiryItem?
    suspend fun insertItem(item: ExpiryItemEntity): Long
    suspend fun updateItem(item: ExpiryItemEntity)
    suspend fun deleteItem(item: ExpiryItem)
    suspend fun deleteAllItems()
}
