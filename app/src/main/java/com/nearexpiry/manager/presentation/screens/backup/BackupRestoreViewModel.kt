package com.nearexpiry.manager.presentation.screens.backup

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nearexpiry.manager.data.local.entity.ExpiryItemEntity
import com.nearexpiry.manager.data.local.entity.toEntity
import com.nearexpiry.manager.domain.repository.ExpiryRepository
import com.nearexpiry.manager.utils.ActiveProjectManager
import com.nearexpiry.manager.utils.CsvImporter
import com.nearexpiry.manager.utils.JsonBackup
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BackupRestoreViewModel @Inject constructor(
    private val repository: ExpiryRepository,
    private val csvImporter: CsvImporter,
    private val activeProjectManager: ActiveProjectManager
) : ViewModel() {

    data class BackupUiState(
        val isLoading: Boolean = false,
        val error: String? = null,
        val success: Boolean = false,
        /** Set after a successful CSV import; null otherwise. */
        val csvImportResult: CsvImporter.ImportResult? = null
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

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun resetSuccess() {
        _uiState.update { it.copy(success = false, csvImportResult = null) }
    }
}
