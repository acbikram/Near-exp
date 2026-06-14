package com.nearexpiry.manager.presentation.screens.export

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nearexpiry.manager.domain.model.ExpiryItem
import com.nearexpiry.manager.domain.repository.ExpiryRepository
import com.nearexpiry.manager.utils.CsvExporter
import com.nearexpiry.manager.utils.ExpiryDateUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import javax.inject.Inject

/** The "Tag/Type" values an item's [ExpiryItem.unit] can take — see ProductCatalogRepositoryImpl. */
enum class ExportMode { BY_FILTER, SELECT_ITEMS }

val EXPORT_UNIT_OPTIONS = listOf("PCS", "OFR", "CTN", "KGS")

/**
 * Sentinel chip value for items whose [ExpiryItem.unit] is null or doesn't
 * match any of [EXPORT_UNIT_OPTIONS] — e.g. a scanned barcode that wasn't
 * found in the local catalog. Without this, selecting any real unit chip
 * would silently exclude these items from export.
 */
const val EXPORT_UNIT_OTHER = "OTHER"

/** All chips shown in the "By Tag/Type" filter, including the "Other" catch-all. */
val EXPORT_UNIT_CHIPS = EXPORT_UNIT_OPTIONS + EXPORT_UNIT_OTHER

@HiltViewModel
class ExportViewModel @Inject constructor(
    private val repository: ExpiryRepository
) : ViewModel() {

    data class ExportUiState(
        val allItems: List<ExpiryItem> = emptyList(),
        val totalRecords: Int = 0,
        val isExporting: Boolean = false,
        val error: String? = null,
        val success: Boolean = false,
        /** Set once a CSV has been written to a shareable cache file; consumed by the UI to launch a share sheet. */
        val shareFileUri: Uri? = null,

        // ── Selective export ────────────────────────────────────────────
        val useSelectiveExport: Boolean = false,
        val exportMode: ExportMode = ExportMode.BY_FILTER,
        /** Multi-select Tag/Type chips (PCS/OFR/CTN/KGS). Empty = no restriction. */
        val selectedUnits: Set<String> = emptySet(),
        val dateFrom: LocalDate? = null,
        val dateTo: LocalDate? = null,
        /** Hand-picked item IDs for ExportMode.SELECT_ITEMS. */
        val selectedItemIds: Set<Long> = emptySet()
    ) {
        /**
         * The exact set of items that will be written to the CSV given the
         * current selective-export settings. When [useSelectiveExport] is
         * false this is simply [allItems] (full export, unchanged behavior).
         */
        val itemsToExport: List<ExpiryItem>
            get() {
                if (!useSelectiveExport) return allItems
                return when (exportMode) {
                    ExportMode.SELECT_ITEMS -> allItems.filter { it.id in selectedItemIds }
                    ExportMode.BY_FILTER -> allItems.filter { item ->
                        // Tag/Type: AND-combined with date range. Empty selection = no restriction.
                        // The "Other" chip matches items whose unit is null/blank or doesn't
                        // match any of the known catalog units (e.g. unmatched scans), so
                        // those items aren't silently excluded when filtering by type.
                        val unitMatch = selectedUnits.isEmpty() || run {
                            val unit = item.unit?.takeIf { it.isNotBlank() }
                            val isKnownUnit = unit != null && unit in EXPORT_UNIT_OPTIONS
                            (unit != null && unit in selectedUnits) ||
                                (EXPORT_UNIT_OTHER in selectedUnits && !isKnownUnit)
                        }

                        val expiry = ExpiryDateUtils.parseOrNull(item.expiryDate)
                        val fromOk = dateFrom == null || (expiry != null && !expiry.isBefore(dateFrom))
                        val toOk = dateTo == null || (expiry != null && !expiry.isAfter(dateTo))

                        unitMatch && fromOk && toOk
                    }
                }
            }
    }

    private val _uiState = MutableStateFlow(ExportUiState())
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    init {
        observeItems()
    }

    /**
     * Continuously collects the items Flow from Room so the displayed
     * record count always reflects the latest scans without needing to
     * leave and re-enter this screen.
     */
    private fun observeItems() {
        viewModelScope.launch {
            repository.getAllItems()
                .catch { e -> _uiState.update { it.copy(error = e.message) } }
                .collect { items ->
                    _uiState.update { state ->
                        val validIds = items.map { it.id }.toSet()
                        state.copy(
                            allItems = items,
                            totalRecords = items.size,
                            selectedItemIds = state.selectedItemIds.intersect(validIds)
                        )
                    }
                }
        }
    }

    // ── Selective export controls ───────────────────────────────────────────

    fun setUseSelectiveExport(enabled: Boolean) {
        _uiState.update { it.copy(useSelectiveExport = enabled) }
    }

    fun setExportMode(mode: ExportMode) {
        _uiState.update { it.copy(exportMode = mode) }
    }

    fun toggleUnit(unit: String) {
        _uiState.update { state ->
            val current = state.selectedUnits
            state.copy(selectedUnits = if (unit in current) current - unit else current + unit)
        }
    }

    fun setDateFrom(date: LocalDate?) {
        _uiState.update { it.copy(dateFrom = date) }
    }

    fun setDateTo(date: LocalDate?) {
        _uiState.update { it.copy(dateTo = date) }
    }

    fun toggleItemSelection(id: Long) {
        _uiState.update { state ->
            val current = state.selectedItemIds
            state.copy(selectedItemIds = if (id in current) current - id else current + id)
        }
    }

    fun selectAllItemsForExport() {
        _uiState.update { it.copy(selectedItemIds = it.allItems.map { item -> item.id }.toSet()) }
    }

    fun clearAllItemsForExport() {
        _uiState.update { it.copy(selectedItemIds = emptySet()) }
    }

    // ── Export actions ───────────────────────────────────────────────────────

    /** "Save CSV" — write the CSV directly to a user-chosen location via SAF. */
    fun exportToUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, error = null, success = false) }
            try {
                val items = _uiState.value.itemsToExport
                val contentResolver: ContentResolver = context.contentResolver
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    CsvExporter.writeCsv(outputStream, items)
                }
                _uiState.update { it.copy(isExporting = false, success = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isExporting = false, error = e.message) }
            }
        }
    }

    /**
     * "Share CSV" — write the CSV to a file under the app's cache directory
     * (covered by the existing FileProvider `cache-path` entry), then expose
     * a `content://` URI via [shareFileUri] for the UI to hand off to
     * [android.content.Intent.ACTION_SEND].
     */
    fun shareAsCsv(context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, error = null, success = false) }
            try {
                val items = _uiState.value.itemsToExport
                val uri = withContext(Dispatchers.IO) {
                    val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
                    val file = File(exportDir, "NearExpiry_${System.currentTimeMillis()}.csv")
                    FileOutputStream(file).use { outputStream ->
                        CsvExporter.writeCsv(outputStream, items)
                    }
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                }
                _uiState.update { it.copy(isExporting = false, shareFileUri = uri) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isExporting = false, error = e.message) }
            }
        }
    }

    /** Called by the UI once it has launched the share sheet for [shareFileUri]. */
    fun consumeShareFileUri() {
        _uiState.update { it.copy(shareFileUri = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun resetSuccess() {
        _uiState.update { it.copy(success = false) }
    }
}
