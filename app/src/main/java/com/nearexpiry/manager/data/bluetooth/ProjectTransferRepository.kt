package com.nearexpiry.manager.data.bluetooth

import androidx.room.withTransaction
import com.nearexpiry.manager.data.local.database.ExpiryDatabase
import com.nearexpiry.manager.data.local.entity.ProjectEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectTransferRepository @Inject constructor(
    private val database: ExpiryDatabase
) {
    suspend fun exportProject(projectId: Long): ProjectTransferModel? {
        val project = database.projectDao().getProjectById(projectId) ?: return null
        val items = database.expiryItemDao().getAllItemsOnce(projectId)
        return ProjectTransferModel(
            sourceProjectId = project.id,
            project = project.toTransferProject(),
            items = items.map { it.toTransferItem() }
        ).also { it.validate().getOrThrow() }
    }

    /** Imports as a separate project so receiving data can never overwrite inventory. */
    suspend fun importAsNewProject(model: ProjectTransferModel): Long = database.withTransaction {
        model.validate().getOrThrow()
        val existingNames = database.projectDao().getAllProjectsOnce().map { it.name }.toSet()
        val baseName = model.project.name.trim().ifEmpty { "Bluetooth Project" }
        val name = uniqueName(baseName, existingNames)
        val projectId = database.projectDao().insert(
            ProjectEntity(
                name = name,
                colorHex = model.project.colorHex,
                createdAt = System.currentTimeMillis(),
                hasCustomSort = model.project.hasCustomSort,
                isStockMode = model.project.isStockMode
            )
        )
        model.items.forEach { item ->
            database.expiryItemDao().insert(item.toEntity(projectId))
        }
        projectId
    }

    private fun uniqueName(base: String, existing: Set<String>): String {
        if (base !in existing) return base
        var index = 2
        while ("$base (Bluetooth $index)" in existing) index++
        return "$base (Bluetooth $index)"
    }
}
