package com.nearexpiry.manager.presentation.screens.export

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nearexpiry.manager.domain.model.ExpiryItem
import com.nearexpiry.manager.domain.repository.ExpiryRepository
import com.nearexpiry.manager.domain.repository.ProjectRepository
import com.nearexpiry.manager.utils.ActiveProjectManager
import com.nearexpiry.manager.utils.BranchDirectory
import com.nearexpiry.manager.utils.CompanyReportBuilder
import com.nearexpiry.manager.utils.CompanyReportExcel
import com.nearexpiry.manager.utils.CsvExporter
import com.nearexpiry.manager.utils.ExpiryDateUtils
import com.nearexpiry.manager.utils.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ExportViewModel @Inject constructor(
    private val repository: ExpiryRepository,
    private val projectRepository: ProjectRepository,
    private val activeProjectManager: ActiveProjectManager,
    private val branchDirectory: BranchDirectory,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    data class ExportUiState(
        val allItems: List<ExpiryItem> = emptyList(),
        val totalRecords: Int = 0,
        val isExporting: Boolean = false,
        val error: String? = null,
        val success: Boolean = false,
        /** Active project name, used in the CSV filename. */
        val projectName: String = "",
        /** Set once a CSV has been written to a shareable cache file; consumed by the UI to launch a share sheet. */
        val shareFileUri: Uri? = null,
        // ── Company Near-Expiry report ───────────────────────────────────
        /** Shows the Branch ID prompt before generating the company report. */
        val showBranchIdDialog: Boolean = false,
        /** Last-used Branch ID, pre-filled into the prompt. */
        val lastBranchId: String = "",
        /** Shows the month-selection step after a valid Branch ID. */
        val showMonthPicker: Boolean = false,
        /** Branch resolved from the entered ID, carried into the month step. */
        val pendingBranchId: String = "",
        val pendingBranchName: String = "",
        val pendingBranchArea: String = "",
        /** Distinct expiry months available in the project (chronological). */
        val availableMonths: List<CompanyReportBuilder.YearMonth> = emptyList(),
        /** Set when a generated .xlsx is ready to share (separate from CSV uri). */
        val reportFileUri: Uri? = null,
        /** Human summary after a successful report. */
        val reportSummary: String? = null,

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
            activeProjectManager.activeProjectIdFlow
                .onEach { projectId ->
                    val name = projectRepository.getProjectById(projectId)?.name ?: ""
                    _uiState.update { it.copy(projectName = name) }
                }
                .flatMapLatest { projectId -> repository.getAllItems(projectId) }
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
    /**
     * CSV filename including the active project name, so files from different
     * inventories don't get mixed up, e.g. "Project 1_1718000000000.csv".
     * Non-filename-safe characters in the project name are replaced with '_'.
     */
    fun buildCsvFilename(): String {
        val safeName = _uiState.value.projectName
            .ifBlank { "NearExpiry" }
            .replace(Regex("[^A-Za-z0-9 _-]"), "_")
            .replace(' ', '_')
        return "${safeName}_${System.currentTimeMillis()}.csv"
    }

    fun shareAsCsv(context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, error = null, success = false) }
            try {
                val items = _uiState.value.itemsToExport
                val uri = withContext(Dispatchers.IO) {
                    val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
                    val file = File(exportDir, buildCsvFilename())
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

    // ── Company Near-Expiry report (.xlsm) ─────────────────────────────────

    /** Opens the Branch ID prompt, pre-filling the last-used ID. */
    fun startCompanyReport() {
        viewModelScope.launch {
            val last = preferencesManager.getLastBranchId()
            _uiState.update { it.copy(showBranchIdDialog = true, lastBranchId = last) }
        }
    }

    fun dismissBranchIdDialog() {
        _uiState.update { it.copy(showBranchIdDialog = false) }
    }

    fun dismissMonthPicker() {
        _uiState.update { it.copy(showMonthPicker = false) }
    }

    /**
     * Validates [branchId] against the bundled branch directory. On success,
     * resolves the branch and opens the month-selection step listing every
     * expiry month present in the active project. On an unknown Branch ID,
     * stops and surfaces an error instead of proceeding.
     */
    fun validateBranchAndPickMonths(branchId: String) {
        viewModelScope.launch {
            val id = branchId.trim()
            val branch = branchDirectory.lookup(id)
            if (branch == null) {
                _uiState.update {
                    it.copy(
                        showBranchIdDialog = false,
                        error = "Branch ID \"$id\" not found. Please check the ID and try again."
                    )
                }
                return@launch
            }
            preferencesManager.setLastBranchId(id)
            val months = CompanyReportBuilder.availableMonths(_uiState.value.allItems)
            if (months.isEmpty()) {
                _uiState.update {
                    it.copy(showBranchIdDialog = false, error = "This project has no items to report.")
                }
                return@launch
            }
            _uiState.update {
                it.copy(
                    showBranchIdDialog = false,
                    showMonthPicker = true,
                    pendingBranchId = id,
                    pendingBranchName = branch.name,
                    pendingBranchArea = branch.area,
                    availableMonths = months
                )
            }
        }
    }

    /**
     * Generates the company .xlsx for the active project's items whose expiry
     * falls in [selectedMonths], sorted by scan order (first scanned → first
     * row). Branch info comes from the previously-validated Branch ID.
     */
    fun generateForMonths(context: Context, selectedMonths: Set<CompanyReportBuilder.YearMonth>) {
        viewModelScope.launch {
            if (selectedMonths.isEmpty()) {
                _uiState.update { it.copy(showMonthPicker = false, error = "Please select at least one month.") }
                return@launch
            }
            val id = _uiState.value.pendingBranchId
            _uiState.update { it.copy(showMonthPicker = false, isExporting = true, error = null) }
            try {
                val rows = CompanyReportBuilder.buildRows(
                    items = _uiState.value.allItems,
                    selectedMonths = selectedMonths,
                    area = _uiState.value.pendingBranchArea,
                    branchId = id,
                    branchName = _uiState.value.pendingBranchName
                )
                if (rows.isEmpty()) {
                    _uiState.update { it.copy(isExporting = false, error = "No items in the selected months.") }
                    return@launch
                }
                val uri = withContext(Dispatchers.IO) {
                    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
                    val file = File(dir, CompanyReportBuilder.fileName(id, selectedMonths))
                    FileOutputStream(file).use { out -> CompanyReportExcel.write(out, rows) }
                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                }
                val label = selectedMonths.sorted().joinToString(", ") {
                    java.time.Month.of(it.month)
                        .getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH)
                }
                _uiState.update {
                    it.copy(isExporting = false, reportFileUri = uri,
                        reportSummary = "${rows.size} items · $label")
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isExporting = false, error = e.message) }
            }
        }
    }

    fun consumeReportFileUri() {
        _uiState.update { it.copy(reportFileUri = null) }
    }

    fun clearReportSummary() {
        _uiState.update { it.copy(reportSummary = null) }
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
