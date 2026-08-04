package com.nearexpiry.manager.presentation.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nearexpiry.manager.domain.model.ExpiryItem
import com.nearexpiry.manager.domain.model.Project
import com.nearexpiry.manager.domain.repository.ExpiryRepository
import com.nearexpiry.manager.domain.repository.ProjectRepository
import com.nearexpiry.manager.utils.ActiveProjectManager
import com.nearexpiry.manager.utils.AppUpdater
import com.nearexpiry.manager.notifications.NotificationHelper
import com.nearexpiry.manager.utils.ExpiryDateUtils
import com.nearexpiry.manager.utils.PreferencesManager
import com.nearexpiry.manager.BuildConfig
import android.content.Context
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    private val activeProjectManager: ActiveProjectManager,
    private val database: com.nearexpiry.manager.data.local.database.ExpiryDatabase,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    /** A project plus a small summary for the Settings list. */
    data class ProjectSummary(
        val project: Project,
        val itemCount: Int,
        val nearestExpiry: String?
    )

    /** Update-check lifecycle for the "Check for Updates" row. */
    enum class UpdateState { IDLE, CHECKING, UP_TO_DATE, AVAILABLE, DOWNLOADING, DOWNLOADED, ERROR }

    data class SettingsUiState(
        val notificationTestResult: com.nearexpiry.manager.notifications.NotificationDiagnosticResult? = null,
        val projects: List<ProjectSummary> = emptyList(),
        val activeProjectId: Long = 1L,
        val message: String? = null,
        // ── App update ───────────────────────────────────────────────────
        val currentVersionName: String = BuildConfig.VERSION_NAME,
        val updateState: UpdateState = UpdateState.IDLE,
        val updateVersionName: String = "",
        val updateNotes: String = "",
        val updateApkUrl: String = "",
        val updateProgress: Float = 0f,
        /** 0..100 for the progress label. */
        val updateProgressPercent: Int = 0,
        val updateError: String = ""
    )

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        observeProjects()
        observeDownloadWork()
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

    // ── App update (GitHub Releases) ───────────────────────────────────────

    /** Whether a check is currently meaningful (not mid-download/install). */
    fun checkForUpdate(autoStartDownload: Boolean = false) {
        _uiState.update { it.copy(updateState = UpdateState.CHECKING, updateError = "") }
        viewModelScope.launch {
            when (val r = AppUpdater.check(
                currentVersionCode = BuildConfig.VERSION_CODE.toLong(),
                currentVersionName = BuildConfig.VERSION_NAME
            )) {
                is AppUpdater.CheckResult.UpdateAvailable -> {
                    // If we already downloaded this exact version, offer Install only.
                    val already = AppUpdater.downloadedApk(appContext, r.info.versionName) != null
                    _uiState.update {
                        it.copy(
                            updateState = if (already) UpdateState.DOWNLOADED else UpdateState.AVAILABLE,
                            updateVersionName = r.info.versionName,
                            updateNotes = r.info.notes,
                            updateApkUrl = r.info.apkUrl,
                            updateProgress = if (already) 1f else 0f,
                            updateProgressPercent = if (already) 100 else 0
                        )
                    }
                    NotificationHelper.postUpdateAvailableNotification(appContext, r.info.versionName)
                    // Coming from the notification's "Update Now": start at once.
                    if (autoStartDownload && !already) downloadUpdate()
                }
                AppUpdater.CheckResult.UpToDate ->
                    _uiState.update { it.copy(updateState = UpdateState.UP_TO_DATE) }
                AppUpdater.CheckResult.NoRelease ->
                    _uiState.update { it.copy(updateState = UpdateState.UP_TO_DATE) }
                is AppUpdater.CheckResult.Error ->
                    _uiState.update { it.copy(updateState = UpdateState.ERROR, updateError = r.message) }
            }
        }
    }

    /**
     * Starts the update download as a background job (WorkManager, with a
     * visible progress notification) so it keeps going even if the user
     * leaves Settings or backgrounds the app. Does NOT auto-install when
     * done — the state becomes DOWNLOADED and the user taps "Install Now"
     * (in-app or from the notification).
     */
    fun downloadUpdate() {
        val url = _uiState.value.updateApkUrl
        val version = _uiState.value.updateVersionName
        if (url.isBlank() || version.isBlank()) return

        // Already downloaded → nothing to do, straight to DOWNLOADED state.
        if (AppUpdater.downloadedApk(appContext, version) != null) {
            _uiState.update {
                it.copy(updateState = UpdateState.DOWNLOADED, updateProgress = 1f, updateProgressPercent = 100)
            }
            return
        }

        _uiState.update { it.copy(updateState = UpdateState.DOWNLOADING, updateProgress = 0f, updateProgressPercent = 0) }
        com.nearexpiry.manager.notifications.UpdateDownloadWorker.enqueue(appContext, url, version)
    }

    /** Watches the background download's WorkInfo and mirrors it into the UI
     *  state — started once at init, so a download already running in the
     *  background (from a previous app session) is picked up automatically. */
    private fun observeDownloadWork() {
        viewModelScope.launch {
            androidx.work.WorkManager.getInstance(appContext)
                .getWorkInfosForUniqueWorkFlow(com.nearexpiry.manager.notifications.UpdateDownloadWorker.WORK_NAME)
                .collect { infos ->
                    val info = infos.firstOrNull() ?: return@collect
                    when (info.state) {
                        androidx.work.WorkInfo.State.RUNNING -> {
                            val percent = info.progress.getInt(
                                com.nearexpiry.manager.notifications.UpdateDownloadWorker.KEY_PROGRESS_PERCENT, -1
                            )
                            _uiState.update {
                                it.copy(
                                    updateState = UpdateState.DOWNLOADING,
                                    updateProgress = if (percent >= 0) percent / 100f else it.updateProgress,
                                    updateProgressPercent = if (percent >= 0) percent else it.updateProgressPercent
                                )
                            }
                        }
                        androidx.work.WorkInfo.State.SUCCEEDED -> {
                            _uiState.update {
                                it.copy(updateState = UpdateState.DOWNLOADED, updateProgress = 1f, updateProgressPercent = 100)
                            }
                        }
                        androidx.work.WorkInfo.State.FAILED -> {
                            val err = info.outputData.getString(
                                com.nearexpiry.manager.notifications.UpdateDownloadWorker.KEY_ERROR
                            ) ?: "Download failed"
                            _uiState.update { it.copy(updateState = UpdateState.ERROR, updateError = err) }
                        }
                        else -> {}
                    }
                }
        }
    }

    /** Launches the installer for the already-downloaded APK (no re-download). */
    fun installUpdate() {
        val version = _uiState.value.updateVersionName
        val file = AppUpdater.downloadedApk(appContext, version)
        if (file == null) {
            // Nothing stored (e.g. cleaned up) — fall back to downloading.
            downloadUpdate()
            return
        }
        viewModelScope.launch {
            runCatching { AppUpdater.install(appContext, file) }
                .onFailure { _uiState.update { s -> s.copy(updateState = UpdateState.ERROR, updateError = it.message ?: "Install failed") } }
        }
    }

    fun dismissUpdateState() {
        _uiState.update { it.copy(updateState = UpdateState.IDLE, updateError = "") }
    }

    /**
     * Runs the exact same expiry-notification check the daily 8 AM job runs,
     * immediately, and shows the result — for diagnosing why notifications
     * aren't appearing without waiting for the next scheduled run.
     */
    fun testExpiryNotificationNow() {
        viewModelScope.launch {
            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.nearexpiry.manager.notifications.ExpiryNotificationWorker
                    .runDiagnostic(appContext, database, preferencesManager)
            }
            _uiState.update { it.copy(notificationTestResult = result) }
        }
    }

    fun clearNotificationTestResult() {
        _uiState.update { it.copy(notificationTestResult = null) }
    }
}
