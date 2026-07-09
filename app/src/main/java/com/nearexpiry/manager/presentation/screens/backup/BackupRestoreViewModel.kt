package com.nearexpiry.manager.presentation.screens.backup

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nearexpiry.manager.data.local.entity.ExpiryItemEntity
import com.nearexpiry.manager.data.local.entity.toEntity
import com.nearexpiry.manager.domain.repository.ExpiryRepository
import com.nearexpiry.manager.domain.repository.ProductCatalogRepository
import com.nearexpiry.manager.domain.repository.ProjectRepository
import com.nearexpiry.manager.utils.ActiveProjectManager
import com.nearexpiry.manager.utils.CsvImporter
import com.nearexpiry.manager.utils.JsonBackup
import com.nearexpiry.manager.utils.LocalFileServer
import com.nearexpiry.manager.utils.AutoBackup
import com.nearexpiry.manager.utils.XlsxReportReader
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class BackupRestoreViewModel @Inject constructor(
    private val repository: ExpiryRepository,
    private val csvImporter: CsvImporter,
    private val catalogRepository: ProductCatalogRepository,
    private val projectRepository: ProjectRepository,
    private val activeProjectManager: ActiveProjectManager,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    /** WiFi catalog-pull progress states for the UI. */
    enum class WifiCatalogState { IDLE, DISCOVERING, DOWNLOADING, SUCCESS, ERROR }

    data class BackupUiState(
        val isLoading: Boolean = false,
        val error: String? = null,
        val success: Boolean = false,
        /** Set after a successful CSV import; null otherwise. */
        // ── Universal Restore Database ──────────────────────────────────
        /** Items parsed from a CSV/XLSX restore, awaiting a project choice. */
        val pendingRestoreItems: List<ExpiryItemEntity> = emptyList(),
        /** Rows in the restore file that couldn't be parsed (bad date/qty/code). */
        val pendingRestoreSkipped: Int = 0,
        /** True while the project-picker dialog is shown. */
        val showRestoreProjectPicker: Boolean = false,
        /** Projects to offer in the picker. */
        val restoreProjects: List<com.nearexpiry.manager.domain.model.Project> = emptyList(),
        /** One-shot result: "new:X:merged:Y:qty:Z" for the snackbar. */
        val restoreResult: String? = null,
        /** One-shot: filename of a just-written internal-storage backup. */
        val internalBackupName: String? = null,
        /** Product count after a successful catalog update; null otherwise. */
        val catalogUpdateCount: Int? = null,
        // ── WiFi catalog pull ────────────────────────────────────────────
        val wifiState: WifiCatalogState = WifiCatalogState.IDLE,
        val wifiStatus: String = "",
        val wifiProgress: Float = 0f,
        /** Current catalog product count, for the status indicator. */
        val catalogCount: Int = 0
    )

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    init {
        refreshCatalogCount()
    }

    /** Reloads the catalog product count for the status indicator. */
    fun refreshCatalogCount() {
        viewModelScope.launch {
            val count = runCatching { catalogRepository.catalogProductCount() }.getOrDefault(0)
            _uiState.update { it.copy(catalogCount = count) }
        }
    }

    fun backupToUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, success = false) }
            try {
                val projectId = activeProjectManager.getActiveProjectId()
                val items = repository.getItemsOnce(projectId)
                val entities: List<ExpiryItemEntity> = items.map { it.toEntity() }
                val contentResolver: ContentResolver = context.contentResolver
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    JsonBackup.exportToJson(outputStream, entities)
                }
                _uiState.update { it.copy(isLoading = false, success = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun restoreFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, success = false) }
            try {
                val contentResolver = context.contentResolver
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    val entities = JsonBackup.importFromJson(inputStream)
                    val projectId = activeProjectManager.getActiveProjectId()
                    // Restore replaces only the active project's items, and
                    // forces the restored rows into the active project.
                    repository.deleteAllInProject(projectId)
                    entities.forEach { repository.insertItem(it.copy(id = 0, projectId = projectId)) }
                }
                _uiState.update { it.copy(isLoading = false, success = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }


    /** Backs up EVERY project and its items into one JSON file. */
    fun backupAllProjects(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, success = false) }
            try {
                val projects = projectRepository.getAllProjectsOnce()
                val bundles = projects.map { p ->
                    val items = repository.getItemsOnce(p.id).map { it.toEntity() }
                    com.nearexpiry.manager.utils.ProjectBackup(
                        project = com.nearexpiry.manager.data.local.entity.ProjectEntity(
                            id = p.id, name = p.name, colorHex = p.colorHex, createdAt = p.createdAt
                        ),
                        items = items
                    )
                }
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    JsonBackup.exportAllProjects(out, bundles)
                }
                _uiState.update { it.copy(isLoading = false, success = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    /**
     * Restore Database — one entry point for every backup type, auto-detected
     * from the file's bytes:
     *  • JSON app backup (single- or all-projects) → existing smart restore.
     *  • Company report .xlsx ("Make Excel File")  → parse rows, ask project.
     *  • Exported CSV                              → parse rows, ask project.
     * CSV/XLSX rows are merged into the chosen project: same item code +
     * expiry + unit → quantities added; otherwise inserted as new items.
     */
    fun restoreSmart(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, success = false, restoreResult = null) }
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use {
                    it.readBytes()
                } ?: throw IllegalStateException("Could not read file")

                when {
                    // ── XLSX (zip container starts with "PK") ─────────────
                    bytes.size >= 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() -> {
                        val rows = XlsxReportReader.parse(bytes)
                        if (rows.isEmpty()) throw IllegalStateException(
                            "No items found — is this a Near-Expiry report file?")
                        // Fill Arabic names from the catalog where possible.
                        val entities = XlsxReportReader.toEntities(rows, projectId = 0)
                            .map { enrichFromCatalog(it) }
                        startProjectPicker(entities)
                    }

                    // ── JSON backup (first char is an opening brace/bracket) ─
                    looksLikeJson(bytes) -> {
                        val text = bytes.toString(Charsets.UTF_8)
                        if (JsonBackup.isAllProjectsBackup(text)) {
                            val backup = JsonBackup.importAllProjects(text.byteInputStream())
                            val existing = projectRepository.getAllProjectsOnce().associateBy { it.name }
                            for (bundle in backup.projects) {
                                val targetId = existing[bundle.project.name]?.id
                                    ?: projectRepository.createProject(bundle.project.name, bundle.project.colorHex)
                                repository.deleteAllInProject(targetId)
                                bundle.items.forEach {
                                    repository.insertItem(it.copy(id = 0, projectId = targetId))
                                }
                            }
                            activeProjectManager.ensureValidActiveProject()
                        } else {
                            val entities = JsonBackup.importFromJson(text.byteInputStream())
                            val projectId = activeProjectManager.getActiveProjectId()
                            repository.deleteAllInProject(projectId)
                            entities.forEach { repository.insertItem(it.copy(id = 0, projectId = projectId)) }
                        }
                        _uiState.update { it.copy(isLoading = false, success = true) }
                    }

                    // ── CSV (anything else) ───────────────────────────────
                    else -> {
                        val parsed = csvImporter.parseCsv(bytes.inputStream())
                        if (parsed.imported.isEmpty()) throw IllegalStateException(
                            "No items found — is this an exported CSV file?")
                        startProjectPicker(parsed.imported, parsed.skipped)
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    /** True if the first non-whitespace byte is an opening brace (123) or bracket (91). */
    private fun looksLikeJson(bytes: ByteArray): Boolean {
        for (i in 0 until minOf(bytes.size, 64)) {
            val c = bytes[i].toInt().toChar()
            if (c.isWhitespace()) continue
            return c.code == 123 || c.code == 91
        }
        return false
    }

    /** Fills missing Arabic/English names + unit from the product catalog. */
    private suspend fun enrichFromCatalog(e: ExpiryItemEntity): ExpiryItemEntity {
        val code = e.itemCode ?: return e
        val product = runCatching { catalogRepository.lookupByItemCode(code) }.getOrNull() ?: return e
        return e.copy(
            productName = e.productName ?: product.name,
            productNameArabic = e.productNameArabic ?: product.nameArabic,
            unit = e.unit ?: product.unit,
            barcode = product.barcode.takeIf { it.isNotBlank() } ?: e.barcode
        )
    }

    /** Shows the project-picker dialog for the parsed [items]. */
    private suspend fun startProjectPicker(items: List<ExpiryItemEntity>, skipped: Int = 0) {
        val projects = projectRepository.getAllProjectsOnce()
        _uiState.update {
            it.copy(
                isLoading = false,
                pendingRestoreItems = items,
                pendingRestoreSkipped = skipped,
                restoreProjects = projects,
                showRestoreProjectPicker = true
            )
        }
    }

    /** User picked an existing project (or [newProjectName] to create one). */
    fun confirmRestoreProject(projectId: Long?, newProjectName: String?) {
        val items = _uiState.value.pendingRestoreItems
        if (items.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(showRestoreProjectPicker = false, isLoading = true) }
            try {
                val targetId = projectId
                    ?: projectRepository.createProject(
                        newProjectName?.trim().takeUnless { it.isNullOrEmpty() }
                            ?: "Restored ${System.currentTimeMillis() / 1000}",
                        "#26C6DA"
                    )
                var newCount = 0
                var mergedCount = 0
                var qtyAdded = 0.0
                for (item in items) {
                    val dup = repository.findDuplicate(
                        targetId, item.itemCode, item.barcode, item.expiryDate, item.unit
                    )
                    if (dup != null) {
                        repository.updateItem(
                            dup.copy(
                                quantity = dup.quantity + item.quantity,
                                updatedAt = System.currentTimeMillis()
                            ).toEntity()
                        )
                        mergedCount++
                    } else {
                        repository.insertItem(item.copy(id = 0, projectId = targetId))
                        newCount++
                    }
                    qtyAdded += item.quantity
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        pendingRestoreItems = emptyList(),
                        restoreResult = "new:$newCount:merged:$mergedCount:qty:${fmtQty(qtyAdded)}:skipped:${_uiState.value.pendingRestoreSkipped}"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, pendingRestoreItems = emptyList(), error = e.message)
                }
            }
        }
    }

    fun cancelRestoreProjectPicker() {
        _uiState.update {
            it.copy(showRestoreProjectPicker = false, pendingRestoreItems = emptyList())
        }
    }

    /** "Backup Now To Internal Storage" — same file the daily 12:00 backup writes. */
    fun backupNowToInternal(context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val name = AutoBackup.run(context)
                _uiState.update { it.copy(isLoading = false, internalBackupName = name) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun clearInternalBackupName() {
        _uiState.update { it.copy(internalBackupName = null) }
    }

    fun clearRestoreResult() {
        _uiState.update { it.copy(restoreResult = null) }
    }

    private fun fmtQty(q: Double): String =
        if (q == q.toLong().toDouble()) q.toLong().toString() else String.format(java.util.Locale.US, "%.3f", q)

    /**
     * Replaces the bundled product catalog from a user-selected CSV or
     * products.db file. On success, [BackupUiState.catalogUpdateCount] holds
     * the new product count.
     */
    fun updateCatalog(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, success = false, catalogUpdateCount = null) }
            try {
                val count = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    catalogRepository.updateCatalog(inputStream)
                } ?: 0
                _uiState.update { it.copy(isLoading = false, success = true, catalogUpdateCount = count, catalogCount = count) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Catalog update failed") }
            }
        }
    }

    /**
     * Discovers the PC on the LAN and pulls the latest catalog .db over the
     * PTAGGDB1 protocol — no cable or manual file picking. The PC must be
     * running Price_Tag_Final.py with its WiFi receiver enabled.
     */
    fun pullCatalogFromPc() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(wifiState = WifiCatalogState.DISCOVERING, wifiProgress = 0f, wifiStatus = "")
            }
            val pcs = LocalFileServer.discoverPcs()
            if (pcs.isEmpty()) {
                _uiState.update {
                    it.copy(
                        wifiState = WifiCatalogState.ERROR,
                        wifiStatus = "No PC found. Make sure the Price Tag app is open with WiFi receiver on, and you're on the same WiFi."
                    )
                }
                return@launch
            }
            val pc = pcs.first()
            _uiState.update {
                it.copy(wifiState = WifiCatalogState.DOWNLOADING, wifiStatus = "Downloading from ${pc.name}…")
            }
            try {
                val dbBytes = LocalFileServer.pullCatalogDb(pc) { received, total ->
                    val pct = if (total > 0) received.toFloat() / total else 0f
                    val mb = received / 1_048_576.0
                    _uiState.update {
                        it.copy(wifiProgress = pct, wifiStatus = "%.1f MB received…".format(mb))
                    }
                }
                val count = withContext(Dispatchers.IO) {
                    val tmp = File(appContext.cacheDir, "products_wifi.db")
                    tmp.writeBytes(dbBytes)
                    val c = tmp.inputStream().use { catalogRepository.updateCatalog(it) }
                    tmp.delete()
                    c
                }
                _uiState.update {
                    it.copy(
                        wifiState = WifiCatalogState.SUCCESS,
                        wifiProgress = 1f,
                        wifiStatus = "",
                        success = true,
                        catalogUpdateCount = count,
                        catalogCount = count
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(wifiState = WifiCatalogState.ERROR, wifiStatus = e.message ?: "Download failed")
                }
            }
        }
    }

    fun resetWifiState() {
        _uiState.update { it.copy(wifiState = WifiCatalogState.IDLE, wifiStatus = "", wifiProgress = 0f) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun resetSuccess() {
        _uiState.update { it.copy(success = false, catalogUpdateCount = null) }
    }
}
