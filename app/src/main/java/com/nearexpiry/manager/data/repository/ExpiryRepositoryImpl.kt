package com.nearexpiry.manager.data.repository

import androidx.room.withTransaction
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
    private val binDao = database.recycleBinDao()
    private val projDao = database.projectDao()

    override fun getAllItems(projectId: Long): Flow<List<ExpiryItem>> =
        dao.getAllItems(projectId).map { entities -> entities.map { it.toDomain() } }

    override suspend fun getItemsOnce(projectId: Long): List<ExpiryItem> =
        dao.getAllItemsOnce(projectId).map { it.toDomain() }

    override suspend fun getItemById(id: Long): ExpiryItem? = dao.getItemById(id)?.toDomain()

    override suspend fun getSerialNumber(projectId: Long, effectiveOrder: Long, id: Long): Int =
        dao.getSerialNumber(projectId, effectiveOrder, id)

    override suspend fun clearDisplayOrder(projectId: Long) {
        dao.clearDisplayOrder(projectId)
    }

    override suspend fun findByBarcodeExpiryUnit(
        projectId: Long,
        barcode: String,
        expiryDate: String,
        unit: String?
    ): ExpiryItem? = dao.findByBarcodeExpiryUnit(projectId, barcode, expiryDate, unit)?.toDomain()

    override suspend fun findDuplicate(
        projectId: Long,
        itemCode: String?,
        barcode: String,
        expiryDate: String,
        unit: String?
    ): ExpiryItem? = findDuplicateEntity(projectId, itemCode, barcode, expiryDate, unit)?.toDomain()

    private suspend fun findDuplicateEntity(
        projectId: Long,
        itemCode: String?,
        barcode: String,
        expiryDate: String,
        unit: String?
    ): ExpiryItemEntity? {
        val code = itemCode?.takeIf { it.isNotBlank() }
        return if (code != null) {
            dao.findByItemCodeExpiryUnit(projectId, code, expiryDate, unit)
        } else {
            dao.findByBarcodeExpiryUnit(projectId, barcode, expiryDate, unit)
        }
    }

    override suspend fun findAllForItem(
        projectId: Long,
        itemCode: String?,
        barcode: String
    ): List<ExpiryItem> {
        val code = itemCode?.takeIf { it.isNotBlank() }
        return dao.findAllForItem(projectId, code, barcode).map { it.toDomain() }
    }

    override suspend fun insertItem(item: ExpiryItemEntity): Long = dao.insert(item)

    override suspend fun updateItem(item: ExpiryItemEntity) {
        dao.update(item)
    }

    override suspend fun deleteItem(item: ExpiryItem) {
        database.withTransaction {
            val entity = item.toEntity()
            archiveToBin(listOf(entity))
            dao.delete(entity)
        }
    }

    override suspend fun deleteItemsByIds(ids: List<Long>) {
        if (ids.isEmpty()) return
        database.withTransaction {
            archiveToBin(dao.getByIds(ids))
            dao.deleteByIds(ids)
        }
    }

    override suspend fun deleteAllInProject(projectId: Long) {
        database.withTransaction {
            archiveToBin(dao.getAllItemsOnce(projectId))
            dao.deleteAllInProject(projectId)
        }
    }

    override suspend fun replaceProjectItems(projectId: Long, items: List<ExpiryItemEntity>) {
        database.withTransaction {
            archiveToBin(dao.getAllItemsOnce(projectId))
            dao.deleteAllInProject(projectId)
            items.forEach { item ->
                dao.insert(item.copy(id = 0, projectId = projectId))
            }
        }
    }

    // ── Recycle bin ────────────────────────────────────────────────────────

    /** Writes recycle-bin entries inside the caller's transaction. */
    private suspend fun archiveToBin(entities: List<ExpiryItemEntity>) {
        if (entities.isEmpty()) return
        val now = System.currentTimeMillis()
        val names = HashMap<Long, String>()
        val bins = entities.map { entity ->
            val name = names.getOrPut(entity.projectId) {
                projDao.getProjectById(entity.projectId)?.name ?: "Deleted project"
            }
            entity.toBinEntity(projectName = name, deletedAt = now)
        }
        binDao.insertAll(bins)
    }

    override fun getBinItems(): Flow<List<RecycleBinEntity>> = binDao.getAll()

    override suspend fun restoreFromBin(binIds: List<Long>, fallbackProjectId: Long): Int {
        if (binIds.isEmpty()) return 0
        return database.withTransaction {
            val entries = binDao.getByIds(binIds)
            var restored = 0
            entries.forEach { entry ->
                val target = if (projDao.getProjectById(entry.projectId) != null) {
                    entry.projectId
                } else {
                    fallbackProjectId
                }
                dao.insert(entry.toItemEntity(target))
                restored++
            }
            // Delete only the entries that were actually restored. This avoids
            // removing concurrently missing/unknown requested IDs.
            if (entries.isNotEmpty()) binDao.deleteByIds(entries.map { it.id })
            restored
        }
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
        if (ids.isEmpty()) return
        database.withTransaction {
            dao.moveItemsToProject(ids, targetProjectId, System.currentTimeMillis())
        }
    }
}
