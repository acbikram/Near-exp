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
import com.nearexpiry.manager.utils.LanguageManager
import com.nearexpiry.manager.utils.LocalFileServer
import com.nearexpiry.manager.utils.ExpiryDateUtils
import com.nearexpiry.manager.utils.PreferencesManager
import com.nearexpiry.manager.utils.RecheckCodeStore
import com.nearexpiry.manager.utils.RecheckExcelReader
import com.nearexpiry.manager.utils.RecheckTemplateStore
import com.nearexpiry.manager.utils.RecheckTemplateWorkbook
import com.nearexpiry.manager.utils.StockProjectClassifier
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
    private val preferencesManager: PreferencesManager,
    private val recheckCodeStore: RecheckCodeStore,
    private val recheckTemplateStore: RecheckTemplateStore
) : ViewModel() {

    data class ExportUiState(
        val allItems: List<ExpiryItem> = emptyList(),
        /** Current project rows; Stock exports use [stockTemplateRecordCount] instead. */
        val totalRecords: Int = 0,
        /** Every POS Code row in the selected Stock Recheck master workbook. */
        val stockTemplateRecordCount: Int = 0,
        val isExporting: Boolean = false,
        val error: String? = null,
        val success: Boolean = false,
        /** Active project name, used in filenames and report labels. */
        val projectName: String = "",
        /** True for a permanent Stock project or a newly named Stock candidate. */
        val isStockMode: Boolean = false,
        /** Set once a CSV has been written to a shareable cache file; consumed by the UI to launch a share sheet. */
        val shareFileUri: Uri? = null,
        // ── Company Near-Expiry report ───────────────────────────────────
        /** Shows the Branch ID prompt before generating the company report. */
        val showBranchIdDialog: Boolean = false,
        /** Where the generated report should go once built: SHARE sheet or send to PC. */
        val reportDestination: ReportDestination = ReportDestination.SHARE,
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
        /** Absolute path of the last generated report (for Send-to-PC). */
        val reportFilePath: String? = null,
        /** Filename of the last generated report. */
        val reportFileName: String? = null,
        /** Human summary after a successful report. */
        val reportSummary: String? = null,
        // ── Send to PC (WiFi) ────────────────────────────────────────────
        val sendToPcState: SendToPcState = SendToPcState.IDLE,
        val sendToPcMessage: String = "",

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
        observeTemplateRecordCount()
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
                    val project = projectRepository.getProjectById(projectId)
                    val isStockMode = StockProjectClassifier.isStockProject(project?.isStockMode == true, project?.name)
                    val templateCount = if (isStockMode) recheckCodeStore.templateOrderedRows().size else 0
                    _uiState.update {
                        it.copy(
                            projectName = project?.name ?: "",
                            isStockMode = isStockMode,
                            stockTemplateRecordCount = templateCount
                        )
                    }
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

    /** Refreshes the displayed Stock export count whenever the selected master workbook changes. */
    private fun observeTemplateRecordCount() {
        viewModelScope.launch {
            recheckCodeStore.observeOrderedRows().collect {
                if (_uiState.value.isStockMode) {
                    val count = recheckCodeStore.templateOrderedRows().size
                    _uiState.update { state -> state.copy(stockTemplateRecordCount = count) }
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

    /**
     * Stock Recheck exports always use the reference workbook's filename,
     * based on the date of the first scanned item in the active project.
     */
    fun buildStockReportFilename(): String =
        "Recheck ${stockReportDateLabel(_uiState.value.allItems)}.xlsx"

    private fun stockReportDateLabel(items: List<ExpiryItem>): String {
        val firstScannedAt = items.minWithOrNull(compareBy<ExpiryItem> { it.createdAt }.thenBy { it.id })?.createdAt
            ?: System.currentTimeMillis()
        return Instant.ofEpochMilli(firstScannedAt)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
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

    /**
     * Builds code-keyed scanned Physical Qty only for rows in the exact master
     * workbook being exported. This deliberately does not depend on the Room
     * index, which can predate a newly selected template or a code-format fix.
     */
    private fun stockTemplateQuantities(
        items: List<ExpiryItem>,
        templateCodes: Set<String>
    ): Map<String, Double> {
        val quantities = linkedMapOf<String, Double>()
        items.forEach { item ->
            val itemCode = recheckCodeStore.normalize(item.itemCode)
            val barcode = recheckCodeStore.normalize(item.barcode)
            val matchingCode = when {
                itemCode != null && itemCode in templateCodes -> itemCode
                barcode != null && barcode in templateCodes -> barcode
                else -> null
            } ?: return@forEach
            quantities[matchingCode] = (quantities[matchingCode] ?: 0.0) + item.quantity
        }
        return quantities
    }

    private data class StockTemplateExport(
        val workbook: ByteArray,
        val templateRowCount: Int,
        val dateLabel: String
    )

    /**
     * Updates the preserved workbook directly. Every template row remains in
     * its original position and retains its formatting; unscanned rows receive
     * physical quantity zero and Total Stock is recalculated with its existing
     * Damage & Expiry quantity.
     */
    private suspend fun buildStockTemplateExport(items: List<ExpiryItem>): StockTemplateExport {
        val template = recheckTemplateStore.read()
            ?: error("Select the Stock Recheck File (Excel) first.")
        val templateRows = withContext(Dispatchers.Default) {
            RecheckExcelReader.readRows(template)
        }
        check(templateRows.isNotEmpty()) {
            "The Stock Recheck Excel file has no POS Code rows to export."
        }
        val templateCodes = templateRows.mapTo(linkedSetOf()) { it.code }
        val quantities = stockTemplateQuantities(items, templateCodes)
        val workbook = withContext(Dispatchers.Default) {
            RecheckTemplateWorkbook.applyQuantities(template, quantities)
        }
        return StockTemplateExport(
            workbook = workbook,
            templateRowCount = templateRows.size,
            dateLabel = stockReportDateLabel(items)
        )
    }

    /** Generates a shareable or Send-to-PC copy of the preserved Stock template. */
    fun generateStockReport(context: Context, sendToPc: Boolean = false) {
        viewModelScope.launch {
            val state = _uiState.value
            if (!state.isStockMode) {
                _uiState.update { it.copy(error = "Stock export is available only for Stock projects.") }
                return@launch
            }
            _uiState.update { it.copy(isExporting = true, error = null, reportFileUri = null) }
            try {
                val export = buildStockTemplateExport(state.allItems)
                val file = withContext(Dispatchers.IO) {
                    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
                    File(dir, "Recheck ${export.dateLabel}.xlsx").also { report ->
                        report.writeBytes(export.workbook)
                    }
                }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        reportFileUri = if (sendToPc) null else uri,
                        reportFilePath = file.absolutePath,
                        reportFileName = file.name,
                        reportSummary = "${export.templateRowCount} template items · Recheck on ${export.dateLabel}"
                    )
                }
                if (sendToPc) sendReportToPc()
            } catch (e: Exception) {
                _uiState.update { it.copy(isExporting = false, error = e.message ?: "Stock export failed") }
            }
        }
    }

    /** Writes the updated master workbook directly to the user-selected Save location. */
    fun exportStockReportToUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            val state = _uiState.value
            if (!state.isStockMode) {
                _uiState.update { it.copy(error = "Stock export is available only for Stock projects.") }
                return@launch
            }
            _uiState.update { it.copy(isExporting = true, error = null, success = false) }
            try {
                val export = buildStockTemplateExport(state.allItems)
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(export.workbook)
                    } ?: error("Unable to open the selected export location")
                }
                _uiState.update { it.copy(isExporting = false, success = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isExporting = false, error = e.message ?: "Stock export failed") }
            }
        }
    }

    // ── Company Near-Expiry report (.xlsm) ─────────────────────────────────

    enum class ReportDestination { SHARE, PC }

    /** Opens the Branch ID prompt, pre-filling the last-used ID. [destination]
     *  decides whether the generated file is shared or sent to the PC. */
    fun startCompanyReport(destination: ReportDestination = ReportDestination.SHARE) {
        viewModelScope.launch {
            val last = preferencesManager.getLastBranchId()
            _uiState.update {
                it.copy(showBranchIdDialog = true, lastBranchId = last, reportDestination = destination)
            }
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
                    branchName = _uiState.value.pendingBranchName,
                    arabic = LanguageManager.isArabic()
                )
                if (rows.isEmpty()) {
                    _uiState.update { it.copy(isExporting = false, error = "No items in the selected months.") }
                    return@launch
                }
                val title = CompanyReportBuilder.title(_uiState.value.pendingBranchName, selectedMonths)
                val file = withContext(Dispatchers.IO) {
                    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
                    val f = File(dir, CompanyReportBuilder.fileName(id, selectedMonths))
                    FileOutputStream(f).use { out -> CompanyReportExcel.write(out, rows, title) }
                    f
                }
                val label = selectedMonths.sorted().joinToString(", ") {
                    java.time.Month.of(it.month)
                        .getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH)
                }
                // Store the file regardless, then route by destination.
                _uiState.update {
                    it.copy(isExporting = false,
                        reportFilePath = file.absolutePath,
                        reportFileName = file.name,
                        reportSummary = "${rows.size} items · $label")
                }
                if (_uiState.value.reportDestination == ReportDestination.PC) {
                    // Send straight to the PC over WiFi (no share sheet).
                    sendReportToPc()
                } else {
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    _uiState.update { it.copy(reportFileUri = uri) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isExporting = false, error = e.message) }
            }
        }
    }

    fun consumeReportFileUri() {
        _uiState.update { it.copy(reportFileUri = null) }
    }

    // ── Send to PC (WiFi) ──────────────────────────────────────────────────

    enum class SendToPcState { IDLE, SEARCHING, SENDING, SUCCESS, NOT_CONNECTED, ERROR }

    /**
     * Sends the last-generated report to the PC over WiFi (PTAGXLSX). If no PC
     * is found on the LAN, sets [SendToPcState.NOT_CONNECTED] so the UI can show
     * connection instructions. The PC must be running Price_Tag_Final.py with
     * its WiFi receiver on, on the same network.
     */
    fun sendReportToPc() {
        val path = _uiState.value.reportFilePath
        val name = _uiState.value.reportFileName
        if (path == null || name == null) {
            _uiState.update { it.copy(sendToPcState = SendToPcState.ERROR, sendToPcMessage = "Generate a report first.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(sendToPcState = SendToPcState.SEARCHING, sendToPcMessage = "") }
            val pcs = LocalFileServer.discoverPcs()
            if (pcs.isEmpty()) {
                _uiState.update { it.copy(sendToPcState = SendToPcState.NOT_CONNECTED) }
                return@launch
            }
            val pc = pcs.first()
            _uiState.update { it.copy(sendToPcState = SendToPcState.SENDING, sendToPcMessage = "Sending to ${pc.name}…") }
            try {
                val bytes = withContext(Dispatchers.IO) { File(path).readBytes() }
                LocalFileServer.sendXlsxToPc(pc, name, bytes)
                _uiState.update { it.copy(sendToPcState = SendToPcState.SUCCESS, sendToPcMessage = "Sent to ${pc.name}") }
            } catch (e: Exception) {
                _uiState.update { it.copy(sendToPcState = SendToPcState.ERROR, sendToPcMessage = e.message ?: "Send failed") }
            }
        }
    }

    fun resetSendToPc() {
        _uiState.update { it.copy(sendToPcState = SendToPcState.IDLE, sendToPcMessage = "") }
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
