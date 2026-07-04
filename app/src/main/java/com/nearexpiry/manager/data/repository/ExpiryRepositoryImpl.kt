package com.nearexpiry.manager.data.repository

import com.nearexpiry.manager.data.local.database.ExpiryDatabase
import com.nearexpiry.manager.data.local.entity.ExpiryItemEntity
import com.nearexpiry.manager.data.local.entity.RecycleBinEntity
import com.nearexpiry.manager.data.local.entity.toBinEntity
import com.nearexpiry.manager.data.local.entity.toItemEntity
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

    override fun getAllItems(projectId: Long): Flow<List<ExpiryItem>> {
        return dao.getAllItems(projectId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getItemsOnce(projectId: Long): List<ExpiryItem> {
        return dao.getAllItemsOnce(projectId).map { it.toDomain() }
    }

    override suspend fun getItemById(id: Long): ExpiryItem? {
        return dao.getItemById(id)?.toDomain()
    }

    override suspend fun findByBarcodeExpiryUnit(projectId: Long, barcode: String, expiryDate: String, unit: String?): ExpiryItem? {
        return dao.findByBarcodeExpiryUnit(projectId, barcode, expiryDate, unit)?.toDomain()
    }

    override suspend fun findDuplicate(projectId: Long, itemCode: String?, barcode: String, expiryDate: String, unit: String?): ExpiryItem? {
        // Prefer matching on POS/item code (same product across barcodes);
        // fall back to barcode for codeless items.
        val code = itemCode?.takeIf { it.isNotBlank() }
        return if (code != null) {
            dao.findByItemCodeExpiryUnit(projectId, code, expiryDate, unit)?.toDomain()
        } else {
            dao.findByBarcodeExpiryUnit(projectId, barcode, expiryDate, unit)?.toDomain()
        }
    }

    override suspend fun findAllForItem(projectId: Long, itemCode: String?, barcode: String): List<ExpiryItem> {
        val code = itemCode?.takeIf { it.isNotBlank() }
        return dao.findAllForItem(projectId, code, barcode).map { it.toDomain() }
    }

    override suspend fun insertItem(item: ExpiryItemEntity): Long {
        return dao.insert(item)
    }

    override suspend fun updateItem(item: ExpiryItemEntity) {
        dao.update(item)
    }

    override suspend fun deleteItem(item: ExpiryItem) {
        moveToBin(listOf(item.toEntity()))
        dao.delete(item.toEntity())
    }

    override suspend fun deleteItemsByIds(ids: List<Long>) {
        if (ids.isEmpty()) return
        moveToBin(dao.getByIds(ids))
        dao.deleteByIds(ids)
    }

    override suspend fun deleteAllInProject(projectId: Long) {
        moveToBin(dao.getAllItemsOnce(projectId))
        dao.deleteAllInProject(projectId)
    }

    // ── Recycle bin ────────────────────────────────────────────────────────

    private val binDao = database.recycleBinDao()
    private val projDao = database.projectDao()

    /** Copies [entities] into the recycle bin (with their project's name). */
    private suspend fun moveToBin(entities: List<ExpiryItemEntity>) {
        if (entities.isEmpty()) return
        val now = System.currentTimeMillis()
        val names = HashMap<Long, String>()
        val bins = entities.map { e ->
            val name = names.getOrPut(e.projectId) {
                projDao.getProjectById(e.projectId)?.name ?: "Deleted project"
            }
            e.toBinEntity(projectName = name, deletedAt = now)
        }
        binDao.insertAll(bins)
    }

    override fun getBinItems(): Flow<List<RecycleBinEntity>> = binDao.getAll()

    override suspend fun restoreFromBin(binIds: List<Long>, fallbackProjectId: Long): Int {
        if (binIds.isEmpty()) return 0
        val entries = binDao.getByIds(binIds)
        var restored = 0
        for (entry in entries) {
            // Back to the original project if it still exists, else the fallback.
            val target = if (projDao.getProjectById(entry.projectId) != null) entry.projectId else fallbackProjectId
            dao.insert(entry.toItemEntity(target))
            restored++
        }
        binDao.deleteByIds(binIds)
        return restored
    }

    override suspend fun deleteFromBinPermanently(binIds: List<Long>) {
        if (binIds.isNotEmpty()) binDao.deleteByIds(binIds)
    }

    override suspend fun removeFromBinByOriginalIds(originalIds: List<Long>) {
        if (originalIds.isNotEmpty()) binDao.deleteByOriginalIds(originalIds)
    }

    override suspend fun purgeOldBinEntries(maxAgeDays: Int) {
        val threshold = System.currentTimeMillis() - maxAgeDays * 24L * 60 * 60 * 1000
        binDao.purgeOlderThan(threshold)
    }

    override suspend fun moveItemsToProject(ids: List<Long>, targetProjectId: Long) {
        if (ids.isNotEmpty()) {
            dao.moveItemsToProject(ids, targetProjectId, System.currentTimeMillis())
        }
    }
}
