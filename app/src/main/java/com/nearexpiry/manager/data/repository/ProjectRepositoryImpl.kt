package com.nearexpiry.manager.data.repository

import androidx.room.withTransaction
import com.nearexpiry.manager.data.local.database.ExpiryDatabase
import com.nearexpiry.manager.data.local.entity.ExpiryItemEntity
import com.nearexpiry.manager.data.local.entity.ProjectEntity
import com.nearexpiry.manager.data.local.entity.toBinEntity
import com.nearexpiry.manager.domain.model.MergeMode
import com.nearexpiry.manager.domain.model.Project
import com.nearexpiry.manager.domain.model.ProjectRestoreBundle
import com.nearexpiry.manager.domain.model.ProjectRestoreMergeResult
import com.nearexpiry.manager.domain.repository.ProjectRepository
import com.nearexpiry.manager.utils.StockProjectClassifier
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
    private val binDao = database.recycleBinDao()

    /** POS-code-first duplicate lookup (falls back to barcode for codeless items). */
    private suspend fun findDuplicate(
        targetProjectId: Long,
        src: ExpiryItemEntity
    ): ExpiryItemEntity? {
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

    override suspend fun deleteProject(id: Long): Boolean = database.withTransaction {
        // Never allow deleting the last remaining project. Re-check existence
        // inside the same transaction to avoid a time-of-check/time-of-use gap.
        if (projectDao.getProjectCount() <= 1) return@withTransaction false
        val project = projectDao.getProjectById(id) ?: return@withTransaction false
        archiveToBin(itemDao.getAllItemsOnce(id), project.name)
        itemDao.deleteAllInProject(id)
        projectDao.deleteById(id)
        true
    }

    override suspend fun cloneProject(sourceId: Long, newName: String, colorHex: String): Long =
        database.withTransaction {
            val source = projectDao.getProjectById(sourceId)
            val newId = projectDao.insert(
                ProjectEntity(
                    name = newName,
                    colorHex = colorHex,
                    createdAt = System.currentTimeMillis(),
                    isStockMode = source?.isStockMode == true
                )
            )
            // Copy every item from the source into the new project as fresh rows.
            val now = System.currentTimeMillis()
            itemDao.getAllItemsOnce(sourceId).forEach { item ->
                itemDao.insert(item.copy(id = 0, projectId = newId, createdAt = now, updatedAt = now))
            }
            latchStockModeIfEligible(newId)
            newId
        }

    override suspend fun copyItemsToProject(
        itemIds: List<Long>,
        targetProjectId: Long,
        mergeMode: MergeMode
    ): Int = database.withTransaction {
        var merged = 0
        val now = System.currentTimeMillis()
        itemIds.forEach { id ->
            val src = itemDao.getItemById(id) ?: return@forEach
            if (src.projectId == targetProjectId) return@forEach // no-op self copy
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
                itemDao.insert(src.copy(id = 0, projectId = targetProjectId, createdAt = now, updatedAt = now))
            }
        }
        latchStockModeIfEligible(targetProjectId)
        merged
    }

    override suspend fun moveItemsToProject(
        itemIds: List<Long>,
        targetProjectId: Long,
        mergeMode: MergeMode
    ): Int = database.withTransaction {
        var merged = 0
        val now = System.currentTimeMillis()
        itemIds.forEach { id ->
            val src = itemDao.getItemById(id) ?: return@forEach
            if (src.projectId == targetProjectId) return@forEach // already there
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
        latchStockModeIfEligible(targetProjectId)
        merged
    }

    override suspend fun restoreItemsIntoProject(
        projectId: Long?,
        newProjectName: String?,
        colorHex: String,
        items: List<ExpiryItemEntity>
    ): ProjectRestoreMergeResult = database.withTransaction {
        val targetId = projectId ?: projectDao.insert(
            ProjectEntity(
                name = newProjectName?.trim().takeUnless { it.isNullOrEmpty() }
                    ?: "Restored ${System.currentTimeMillis() / 1000}",
                colorHex = colorHex,
                createdAt = System.currentTimeMillis()
            )
        )
        check(projectDao.getProjectById(targetId) != null) { "Restore project no longer exists" }

        var inserted = 0
        var merged = 0
        var quantityAdded = 0.0
        val now = System.currentTimeMillis()
        items.forEach { item ->
            val existing = findDuplicate(targetId, item)
            if (existing != null) {
                itemDao.update(
                    existing.copy(
                        quantity = existing.quantity + item.quantity,
                        updatedAt = now,
                        productName = existing.productName ?: item.productName,
                        productNameArabic = existing.productNameArabic ?: item.productNameArabic,
                        itemCode = existing.itemCode ?: item.itemCode
                    )
                )
                merged++
            } else {
                itemDao.insert(item.copy(id = 0, projectId = targetId))
                inserted++
            }
            quantityAdded += item.quantity
        }
        latchStockModeIfEligible(targetId)
        ProjectRestoreMergeResult(targetId, inserted, merged, quantityAdded)
    }

    override suspend fun restoreProjectsFromBackup(bundles: List<ProjectRestoreBundle>) {
        if (bundles.isEmpty()) return
        database.withTransaction {
            val existingByName = projectDao.getAllProjectsOnce().associateBy { it.name }.toMutableMap()
            bundles.forEach { bundle ->
                var target = existingByName[bundle.name] ?: ProjectEntity(
                    name = bundle.name,
                    colorHex = bundle.colorHex,
                    createdAt = System.currentTimeMillis(),
                    isStockMode = bundle.isStockMode
                ).let { created ->
                    val id = projectDao.insert(created)
                    created.copy(id = id).also { existingByName[bundle.name] = it }
                }
                if (bundle.isStockMode && !target.isStockMode) {
                    target = target.copy(isStockMode = true)
                    projectDao.update(target)
                    existingByName[bundle.name] = target
                }

                // Preserve the current restore behavior: archive the project's
                // old inventory, then replace it. The archive/delete/insert
                // sequence is one transaction, so it cannot become partial.
                archiveToBin(itemDao.getAllItemsOnce(target.id), target.name)
                itemDao.deleteAllInProject(target.id)
                bundle.items.forEach { item ->
                    itemDao.insert(item.copy(id = 0, projectId = target.id))
                }
                latchStockModeIfEligible(target.id)
            }
        }
    }

    private suspend fun archiveToBin(items: List<ExpiryItemEntity>, projectName: String) {
        if (items.isEmpty()) return
        val deletedAt = System.currentTimeMillis()
        binDao.insertAll(items.map { it.toBinEntity(projectName, deletedAt) })
    }

    private fun ProjectEntity.toDomain() = Project(
        id = id,
        name = name,
        colorHex = colorHex,
        createdAt = createdAt,
        hasCustomSort = hasCustomSort,
        isStockMode = isStockMode
    )

    /** Latches Stock Mode once a project name contains "stock" and it has inventory. */
    private suspend fun latchStockModeIfEligible(projectId: Long) {
        val project = projectDao.getProjectById(projectId) ?: return
        if (project.isStockMode || !StockProjectClassifier.hasStockKeyword(project.name)) return
        if (itemDao.getAllItemsOnce(projectId).isNotEmpty()) {
            projectDao.update(project.copy(isStockMode = true))
        }
    }

    override suspend fun activateStockModeIfEligible(projectId: Long) = database.withTransaction {
        latchStockModeIfEligible(projectId)
    }

    override suspend fun setHasCustomSort(projectId: Long, value: Boolean) {
        val project = projectDao.getProjectById(projectId) ?: return
        projectDao.update(project.copy(hasCustomSort = value))
    }
}
