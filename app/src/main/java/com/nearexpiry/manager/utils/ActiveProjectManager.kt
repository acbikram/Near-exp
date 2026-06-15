package com.nearexpiry.manager.utils

import com.nearexpiry.manager.domain.repository.ProjectRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for "which project is active right now".
 *
 * Wraps [PreferencesManager]'s persisted active-project id. ViewModels
 * collect [activeProjectIdFlow] and re-query their data whenever the user
 * switches projects, so the whole app stays scoped to one inventory.
 *
 * On switch it validates the target still exists (a project could have been
 * deleted) and falls back to the first available project otherwise.
 */
@Singleton
class ActiveProjectManager @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val projectRepository: ProjectRepository
) {
    val activeProjectIdFlow: Flow<Long> = preferencesManager.activeProjectIdFlow

    fun getActiveProjectId(): Long = preferencesManager.getActiveProjectId()

    suspend fun setActiveProject(id: Long) {
        preferencesManager.setActiveProjectId(id)
    }

    /**
     * Ensures the persisted active project still exists; if not (e.g. it was
     * deleted), switches to the earliest-created remaining project. Called on
     * app start and after a project deletion.
     */
    suspend fun ensureValidActiveProject() {
        val projects = projectRepository.getAllProjectsOnce()
        if (projects.isEmpty()) return
        val current = preferencesManager.getActiveProjectId()
        if (projects.none { it.id == current }) {
            setActiveProject(projects.first().id)
        }
    }
}
