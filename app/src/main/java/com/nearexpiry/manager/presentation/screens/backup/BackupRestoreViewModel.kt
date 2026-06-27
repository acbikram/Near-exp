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
import com.nearexpiry.manager.utils.ActiveProjectManager
import com.nearexpiry.manager.utils.CsvImporter
import com.nearexpiry.manager.utils.JsonBackup
import com.nearexpiry.manager.utils.LocalFileServer
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
        val csvImportResult: CsvImporter.ImportResult? = null,
        /** Product count after a successful catalog update; null otherwise. */
        val catalogUpdateCount: Int? = null,
        // ── WiFi catalog pull ────────────────────────────────────────────
        val wifiState: WifiCatalogState = WifiCatalogState.IDLE,
        val wifiStatus: String = "",
        val wifiProgress: Float = 0f
    )

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

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

    /**
     * Bulk-adds items from a CSV file (see [CsvImporter] for the expected
     * column format). Unlike [restoreFromUri], this is additive — existing
     * records are kept, each valid row is inserted as a new item. Useful
     * for an initial stock-take across many products at once.
     */
    fun importCsv(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, success = false, csvImportResult = null) }
            try {
                val parsed = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    csvImporter.parseCsv(inputStream)
                } ?: CsvImporter.ImportResult(emptyList(), 0, 0)

                // Merge rule: if an item with the same barcode (POS code) +
                // expiry date + unit already exists (from a prior scan/import,
                // or an earlier row in this same batch), add to its quantity
                // instead of inserting a duplicate. Different unit → kept as a
                // separate row (and flagged in exports via the warning column).
                val projectId = activeProjectManager.getActiveProjectId()
                var mergedCount = 0
                for (entity in parsed.imported) {
                    val existing = repository.findDuplicate(
                        projectId, entity.itemCode, entity.barcode, entity.expiryDate, entity.unit
                    )
                    if (existing != null) {
                        repository.updateItem(
                            existing.copy(
                                quantity = existing.quantity + entity.quantity,
                                updatedAt = System.currentTimeMillis(),
                                // Backfill description/itemCode if the existing row lacked it.
                                productName = existing.productName ?: entity.productName,
                                productNameArabic = existing.productNameArabic ?: entity.productNameArabic,
                                itemCode = existing.itemCode ?: entity.itemCode
                            ).toEntity()
                        )
                        mergedCount++
                    } else {
                        // Force imported rows into the active project.
                        repository.insertItem(entity.copy(projectId = projectId))
                    }
                }

                val result = parsed.copy(merged = mergedCount)

                _uiState.update {
                    it.copy(isLoading = false, success = true, csvImportResult = result)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

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
                _uiState.update { it.copy(isLoading = false, success = true, catalogUpdateCount = count) }
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
                        catalogUpdateCount = count
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
        _uiState.update { it.copy(success = false, csvImportResult = null, catalogUpdateCount = null) }
    }
}
