package com.nearexpiry.manager.presentation.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nearexpiry.manager.domain.model.ExpiryItem
import com.nearexpiry.manager.domain.model.Project
import com.nearexpiry.manager.domain.repository.ExpiryRepository
import com.nearexpiry.manager.domain.repository.ProjectRepository
import com.nearexpiry.manager.utils.ActiveProjectManager
import com.nearexpiry.manager.utils.ExpiryDateUtils
import com.nearexpiry.manager.utils.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** Predefined colour tags offered when creating/editing a project. */
val PROJECT_COLORS = listOf(
    "#26C6DA", // cyan
    "#66BB6A", // green
    "#FFA726", // orange
    "#EF5350", // red
    "#AB47BC", // purple
    "#42A5F5", // blue
    "#FFEE58", // yellow
    "#8D6E63"  // brown
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val repository: ExpiryRepository,
    private val projectRepository: ProjectRepository,
    private val activeProjectManager: ActiveProjectManager
) : ViewModel() {

    /** A project plus a small summary for the Settings list. */
    data class ProjectSummary(
        val project: Project,
        val itemCount: Int,
        val nearestExpiry: String?
    )

    data class SettingsUiState(
        val scanSound: Boolean = true,
        val vibration: Boolean = true,
        val projects: List<ProjectSummary> = emptyList(),
        val activeProjectId: Long = 1L,
        val message: String? = null
    )

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesManager.scanSoundFlow.collect { sound ->
                _uiState.update { it.copy(scanSound = sound) }
            }
        }
        viewModelScope.launch {
            preferencesManager.vibrationFlow.collect { vibration ->
                _uiState.update { it.copy(vibration = vibration) }
            }
        }
        observeProjects()
    }

    /**
     * Builds the project list with per-project item count + nearest expiry,
     * recomputing whenever projects change. Item lists are read per project
     * via a one-shot query inside the collector.
     */
    private fun observeProjects() {
        viewModelScope.launch {
            combine(
                projectRepository.getAllProjects(),
                activeProjectManager.activeProjectIdFlow
            ) { projects, activeId -> projects to activeId }
                .collect { (projects, activeId) ->
                    val today = LocalDate.now()
                    val summaries = projects.map { project ->
                        val items = repository.getItemsOnce(project.id)
                        ProjectSummary(
                            project = project,
                            itemCount = items.size,
                            nearestExpiry = nearestExpiry(items, today)
                        )
                    }
                    _uiState.update { it.copy(projects = summaries, activeProjectId = activeId) }
                }
        }
    }

    private fun nearestExpiry(items: List<ExpiryItem>, today: LocalDate): String? {
        return items
            .mapNotNull { ExpiryDateUtils.parseOrNull(it.expiryDate) }
            .filter { !it.isBefore(today) }
            .minOrNull()
            ?.let { ExpiryDateUtils.toCsvDate(it.toString()) }
    }

    fun toggleScanSound(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setScanSound(enabled) }
    }

    fun toggleVibration(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setVibration(enabled) }
    }

    /** Clears all records in the *active* project only. */
    fun clearAllRecords() {
        viewModelScope.launch {
            repository.deleteAllInProject(activeProjectManager.getActiveProjectId())
        }
    }

    // ── Project management ─────────────────────────────────────────────────

    fun switchProject(id: Long) {
        viewModelScope.launch { activeProjectManager.setActiveProject(id) }
    }

    fun createProject(name: String, colorHex: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val id = projectRepository.createProject(trimmed, colorHex)
            // Switch to the newly created project so the user lands in it.
            activeProjectManager.setActiveProject(id)
        }
    }

    fun renameProject(id: Long, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { projectRepository.renameProject(id, trimmed) }
    }

    fun updateProjectColor(id: Long, colorHex: String) {
        viewModelScope.launch { projectRepository.updateProjectColor(id, colorHex) }
    }

    fun cloneProject(sourceId: Long, newName: String, colorHex: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val id = projectRepository.cloneProject(sourceId, trimmed, colorHex)
            activeProjectManager.setActiveProject(id)
        }
    }

    fun deleteProject(id: Long) {
        viewModelScope.launch {
            val ok = projectRepository.deleteProject(id)
            if (!ok) {
                _uiState.update { it.copy(message = "CANNOT_DELETE_LAST") }
            } else {
                // If the deleted project was active, fall back to a valid one.
                activeProjectManager.ensureValidActiveProject()
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
