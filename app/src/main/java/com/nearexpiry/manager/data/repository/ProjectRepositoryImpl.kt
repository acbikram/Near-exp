package com.nearexpiry.manager.data.repository

import com.nearexpiry.manager.data.local.database.ExpiryDatabase
import com.nearexpiry.manager.data.local.entity.ProjectEntity
import com.nearexpiry.manager.data.local.entity.toBinEntity
import com.nearexpiry.manager.domain.model.MergeMode
import com.nearexpiry.manager.domain.model.Project
import com.nearexpiry.manager.domain.repository.ProjectRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectRepositoryImpl @Inject constructor(
    private val database: ExpiryDatabase
) : ProjectRepository {

    private val projectDao = database.projectDao()
    private val itemDao = database.expiryItemDao()

    /** POS-code-first duplicate lookup (falls back to barcode for codeless items). */
    private suspend fun findDuplicate(
        targetProjectId: Long,
        src: com.nearexpiry.manager.data.local.entity.ExpiryItemEntity
    ): com.nearexpiry.manager.data.local.entity.ExpiryItemEntity? {
        val code = src.itemCode?.takeIf { it.isNotBlank() }
        return if (code != null) {
            itemDao.findByItemCodeExpiryUnit(targetProjectId, code, src.expiryDate, src.unit)
        } else {
            itemDao.findByBarcodeExpiryUnit(targetProjectId, src.barcode, src.expiryDate, src.unit)
        }
    }

    override fun getAllProjects(): Flow<List<Project>> =
        projectDao.getAllProjects().map { list -> list.map { it.toDomain() } }

    override suspend fun getAllProjectsOnce(): List<Project> =
        projectDao.getAllProjectsOnce().map { it.toDomain() }

    override suspend fun getProjectById(id: Long): Project? =
        projectDao.getProjectById(id)?.toDomain()

    override suspend fun getProjectCount(): Int = projectDao.getProjectCount()

    override suspend fun createProject(name: String, colorHex: String): Long =
        projectDao.insert(
            ProjectEntity(name = name, colorHex = colorHex, createdAt = System.currentTimeMillis())
        )

    override suspend fun renameProject(id: Long, newName: String) {
        val existing = projectDao.getProjectById(id) ?: return
        projectDao.update(existing.copy(name = newName))
    }

    override suspend fun updateProjectColor(id: Long, colorHex: String) {
        val existing = projectDao.getProjectById(id) ?: return
        projectDao.update(existing.copy(colorHex = colorHex))
    }

    override suspend fun deleteProject(id: Long): Boolean {
        // Never allow deleting the last remaining project.
        if (projectDao.getProjectCount() <= 1) return false
        // Send the project's items to the recycle bin before removal, so an
        // accidental project delete is recoverable.
        val items = itemDao.getAllItemsOnce(id)
        if (items.isNotEmpty()) {
            val name = projectDao.getProjectById(id)?.name ?: "Deleted project"
            val now = System.currentTimeMillis()
            database.recycleBinDao().insertAll(
                items.map { it.toBinEntity(projectName = name, deletedAt = now) }
            )
        }
        itemDao.deleteAllInProject(id)
        projectDao.deleteById(id)
        return true
    }

    override suspend fun cloneProject(sourceId: Long, newName: String, colorHex: String): Long {
        val newId = projectDao.insert(
            ProjectEntity(name = newName, colorHex = colorHex, createdAt = System.currentTimeMillis())
        )
        // Copy every item from the source into the new project as fresh rows.
        val now = System.currentTimeMillis()
        itemDao.getAllItemsOnce(sourceId).forEach { item ->
            itemDao.insert(
                item.copy(id = 0, projectId = newId, createdAt = now, updatedAt = now)
            )
        }
        return newId
    }

    override suspend fun copyItemsToProject(itemIds: List<Long>, targetProjectId: Long, mergeMode: MergeMode): Int {
        var merged = 0
        val now = System.currentTimeMillis()
        for (id in itemIds) {
            val src = itemDao.getItemById(id) ?: continue
            if (src.projectId == targetProjectId) continue // no-op self copy
            val existing = findDuplicate(targetProjectId, src)
            if (existing != null) {
                val newQty = if (mergeMode == MergeMode.ADD) existing.quantity + src.quantity else src.quantity
                itemDao.update(
                    existing.copy(
                        quantity = newQty,
                        updatedAt = now,
                        productName = existing.productName ?: src.productName,
                        productNameArabic = existing.productNameArabic ?: src.productNameArabic,
                        itemCode = existing.itemCode ?: src.itemCode
                    )
                )
                merged++
            } else {
                itemDao.insert(
                    src.copy(id = 0, projectId = targetProjectId, createdAt = now, updatedAt = now)
                )
            }
        }
        return merged
    }

    override suspend fun moveItemsToProject(itemIds: List<Long>, targetProjectId: Long, mergeMode: MergeMode): Int {
        var merged = 0
        val now = System.currentTimeMillis()
        for (id in itemIds) {
            val src = itemDao.getItemById(id) ?: continue
            if (src.projectId == targetProjectId) continue // already there
            val existing = findDuplicate(targetProjectId, src)
            if (existing != null) {
                // Merge into the target's existing row, then remove the source.
                val newQty = if (mergeMode == MergeMode.ADD) existing.quantity + src.quantity else src.quantity
                itemDao.update(
                    existing.copy(
                        quantity = newQty,
                        updatedAt = now,
                        productName = existing.productName ?: src.productName,
                        productNameArabic = existing.productNameArabic ?: src.productNameArabic,
                        itemCode = existing.itemCode ?: src.itemCode
                    )
                )
                itemDao.delete(src)
                merged++
            } else {
                // No match → just reassign the row's project.
                itemDao.update(src.copy(projectId = targetProjectId, updatedAt = now))
            }
        }
        return merged
    }

    private fun ProjectEntity.toDomain() = Project(
        id = id,
        name = name,
        colorHex = colorHex,
        createdAt = createdAt,
        hasCustomSort = hasCustomSort
    )

    override suspend fun setHasCustomSort(projectId: Long, value: Boolean) {
        val project = projectDao.getProjectById(projectId) ?: return
        projectDao.update(project.copy(hasCustomSort = value))
    }
}
