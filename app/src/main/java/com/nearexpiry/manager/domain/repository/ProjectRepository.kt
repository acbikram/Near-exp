package com.nearexpiry.manager.domain.repository

import com.nearexpiry.manager.data.local.entity.ExpiryItemEntity
import com.nearexpiry.manager.domain.model.MergeMode
import com.nearexpiry.manager.domain.model.Project
import com.nearexpiry.manager.domain.model.ProjectRestoreBundle
import com.nearexpiry.manager.domain.model.ProjectRestoreMergeResult
import kotlinx.coroutines.flow.Flow

interface ProjectRepository {
    fun getAllProjects(): Flow<List<Project>>
    suspend fun getAllProjectsOnce(): List<Project>
    suspend fun getProjectById(id: Long): Project?
    suspend fun getProjectCount(): Int

    suspend fun createProject(name: String, colorHex: String): Long
    suspend fun renameProject(id: Long, newName: String)
    suspend fun updateProjectColor(id: Long, colorHex: String)
    /** Flips the "Custom Sort" flag (set by Move Up/Down, cleared by Reset to Scan Order). */
    suspend fun setHasCustomSort(projectId: Long, value: Boolean)

    /**
     * Deletes a project and all its items. Refuses (returns false) if it's
     * the last remaining project, so there's always at least one.
     */
    suspend fun deleteProject(id: Long): Boolean

    /**
     * Creates a new project [newName] and copies *all* items from [sourceId]
     * into it. Returns the new project's id.
     */
    suspend fun cloneProject(sourceId: Long, newName: String, colorHex: String): Long

    /**
     * Copies the given items into [targetProjectId] (originals stay put).
     * Items that match an existing one in the target (same barcode + expiry
     * + unit) are merged per [mergeMode] (ADD = sum, REPLACE = overwrite).
     * Returns the number merged.
     */
    suspend fun copyItemsToProject(itemIds: List<Long>, targetProjectId: Long, mergeMode: MergeMode): Int

    /**
     * Moves the given items into [targetProjectId] (removed from their
     * current project). Items matching an existing one in the target are
     * merged per [mergeMode] and the moved source row removed. Returns
     * number merged.
     */
    suspend fun moveItemsToProject(itemIds: List<Long>, targetProjectId: Long, mergeMode: MergeMode): Int

    /**
     * Atomically merges imported rows into an existing project or creates a
     * new one and merges into it as one database operation.
     */
    suspend fun restoreItemsIntoProject(
        projectId: Long?,
        newProjectName: String?,
        colorHex: String,
        items: List<ExpiryItemEntity>
    ): ProjectRestoreMergeResult

    /**
     * Atomically replaces the items of every backed-up project. Existing
     * projects with matching names are reused; otherwise a project is created.
     * Projects not represented in [bundles] are left unchanged.
     */
    suspend fun restoreProjectsFromBackup(bundles: List<ProjectRestoreBundle>)
}
