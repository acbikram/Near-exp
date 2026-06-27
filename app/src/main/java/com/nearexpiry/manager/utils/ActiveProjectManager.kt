package com.nearexpiry.manager.utils

import com.nearexpiry.manager.domain.repository.ProjectRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for "which project is active right now".
 *
 * Wraps [PreferencesManager]'s persisted active-project id. ViewModels
 * collect [activeProjectIdFlow] and re-query their data whenever the user
 * switches projects, so the whole app stays scoped to one inventory.
 *
 * The active id is also mirrored into an in-memory [cachedActiveProjectId]
 * by collecting the DataStore flow once in an app-scoped coroutine. This lets
 * [getActiveProjectId] return instantly without any blocking disk read on the
 * caller's thread (previously it used runBlocking, which risked an ANR when
 * called from the main thread).
 */
@Singleton
class ActiveProjectManager @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val projectRepository: ProjectRepository
) {
    val activeProjectIdFlow: Flow<Long> = preferencesManager.activeProjectIdFlow

    // Long writes/reads are atomic on the JVM; @Volatile guarantees visibility
    // across threads. Seeded to the default project until the flow emits.
    @Volatile
    private var cachedActiveProjectId: Long = 1L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // Keep the cache in sync with the persisted value for the app's lifetime.
        scope.launch {
            activeProjectIdFlow.collect { cachedActiveProjectId = it }
        }
    }

    /** Instant, non-blocking read of the active project id. */
    fun getActiveProjectId(): Long = cachedActiveProjectId

    suspend fun setActiveProject(id: Long) {
        cachedActiveProjectId = id          // reflect immediately
        preferencesManager.setActiveProjectId(id)
    }

    /**
     * Ensures the persisted active project still exists; if not (e.g. it was
     * deleted), switches to the earliest-created remaining project. Called on
     * app start and after a project deletion.
     *
     * As a safety net, if there are no projects at all (which shouldn't
     * normally happen — onCreate/migration seed "Project 1"), it recreates a
     * default project so scanning/import/manual entry always has somewhere to
     * save into.
     */
    suspend fun ensureValidActiveProject() {
        var projects = projectRepository.getAllProjectsOnce()
        if (projects.isEmpty()) {
            projectRepository.createProject("Project 1", "#26C6DA")
            projects = projectRepository.getAllProjectsOnce()
        }
        if (projects.isEmpty()) return
        val current = preferencesManager.getActiveProjectId()
        if (projects.none { it.id == current }) {
            setActiveProject(projects.first().id)
        } else {
            cachedActiveProjectId = current
        }
    }
}
