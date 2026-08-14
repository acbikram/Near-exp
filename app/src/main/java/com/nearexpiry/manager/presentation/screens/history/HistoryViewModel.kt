package com.nearexpiry.manager.presentation.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nearexpiry.manager.domain.model.ExpiryItem
import com.nearexpiry.manager.data.local.entity.toEntity
import com.nearexpiry.manager.domain.model.Project
import com.nearexpiry.manager.domain.repository.ExpiryRepository
import com.nearexpiry.manager.domain.repository.ProjectRepository
import com.nearexpiry.manager.utils.ActiveProjectManager
import com.nearexpiry.manager.utils.ExpiryDateUtils
import com.nearexpiry.manager.utils.StockProjectClassifier
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

enum class Filter { ALL, EXPIRED, TODAY, ONE_TO_SEVEN, EIGHT_TO_THIRTY, LATER }
enum class SortOrder { NEWEST, OLDEST, EXPIRY_DATE, QUANTITY }

/**
 * Unit-type filter for the History screen, AND-combined with the date
 * [Filter]. ALL = no restriction; OTHER = items whose unit is null/blank or
 * isn't one of the known catalog units.
 */
enum class UnitFilter(val label: String) {
    ALL("ALL"),
    PCS("PCS"),
    OFR("OFR"),
    CTN("CTN"),
    KGS("KGS"),
    OTHER("OTHER")
}

private val KNOWN_UNITS = setOf("PCS", "OFR", "CTN", "KGS")

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: ExpiryRepository,
    private val projectRepository: ProjectRepository,
    private val activeProjectManager: ActiveProjectManager,
    private val itemNavigationContext: com.nearexpiry.manager.utils.ItemNavigationContext
) : ViewModel() {

    data class HistoryUiState(
        val allItems: List<ExpiryItem> = emptyList(),
        /** Item id -> Sr No. (scan-order rank within the project, 1-based). */
        val srNoMap: Map<Long, Int> = emptyMap(),
        /** Whether the active project has been manually reordered via Move Up/Down. */
        val hasCustomSort: Boolean = false,
        /** True for permanent inventory/stock-check projects with no expiry workflow. */
        val isStockMode: Boolean = false,
        val filteredItems: List<ExpiryItem> = emptyList(),
        /** Items matching the current [filter] only (ignores search) — used for "delete all in filter". */
        val itemsInFilter: List<ExpiryItem> = emptyList(),
        val searchQuery: String = "",
        val filter: Filter = Filter.ALL,
        val unitFilter: UnitFilter = UnitFilter.ALL,
        /**
         * A specific expiry date filter (ISO "yyyy-MM-dd") OR a specific
         * expiry month filter ("yyyy-MM"). When either is set it overrides
         * the relative [filter] bucket (All/Expired/Today/7d/30d). They're
         * mutually exclusive with each other; the unit filter still combines.
         */
        val specificDate: String? = null,
        val specificMonth: String? = null,
        /** Distinct expiry months present in this project's items, newest first ("yyyy-MM" → label). */
        val availableMonths: List<MonthOption> = emptyList(),
        val sortOrder: SortOrder = SortOrder.NEWEST,
        val error: String? = null,
        // ── Selection mode ───────────────────────────────────────────────
        val selectionMode: Boolean = false,
        val selectedIds: Set<Long> = emptySet(),
        // ── Confirmation dialogs ─────────────────────────────────────────
        val showDeleteSelectedConfirm: Boolean = false,
        val showDeleteFilterConfirm: Boolean = false,
        // ── Copy/Move to another project ─────────────────────────────────
        /** Other projects (excludes the active one) available as copy/move targets. */
        val otherProjects: List<Project> = emptyList(),
        /** When non-null, the target-project picker dialog is shown in this mode. */
        val projectActionMode: ProjectAction? = null,
        /** Target chosen but awaiting Add/Replace choice because of a collision. */
        val pendingTargetProjectId: Long? = null,
        /** One-shot message after a copy/move completes, e.g. "Moved 5, merged 2". */
        val copyMoveResult: String? = null,
        /** Items just deleted, kept briefly so the user can Undo. Null = nothing to undo. */
        val undoDeleteItems: List<ExpiryItem>? = null,
        /** One-shot count to show in the Undo snackbar. */
        val undoDeleteCount: Int = 0,
        /** One-shot: true triggers the "can't move selected items together" popup. */
        val showMoveBlockedMessage: Boolean = false
    )

    enum class ProjectAction { COPY, MOVE }

    /** An expiry month present in the data: [key] is "yyyy-MM", [label] like "Sep 2026". */
    data class MonthOption(val key: String, val label: String)

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    private var activeProjectId: Long = 1L

    init {
        observeItems()
        observeOtherProjects()
        observeActiveProjectCustomSort()
    }

    /**
     * Called from the composable after the ViewModel is created to apply the
     * initial filter/sort passed from the dashboard stat-card tap.
     */
    /**
     * Called right before navigating to an item's Detail screen, so swipe
     * left/right there moves through this exact same list — respecting
     * whatever filter/sort/search is currently applied.
     */
    fun prepareItemNavigation() {
        itemNavigationContext.set(_uiState.value.filteredItems.map { it.id })
    }

    fun applyInitialFilterAndSort(filterStr: String, sortStr: String) {
        val filter = when (filterStr) {
            "EXPIRED" -> Filter.EXPIRED
            "TODAY" -> Filter.TODAY
            "ONE_TO_SEVEN", "SEVEN_DAYS" -> Filter.ONE_TO_SEVEN
            "EIGHT_TO_THIRTY", "THIRTY_DAYS" -> Filter.EIGHT_TO_THIRTY
            "LATER" -> Filter.LATER
            else -> Filter.ALL
        }
        val sort = when (sortStr) {
            "EXPIRY_DATE" -> SortOrder.EXPIRY_DATE
            "QUANTITY"    -> SortOrder.QUANTITY
            "OLDEST"      -> SortOrder.OLDEST
            else          -> SortOrder.NEWEST
        }
        _uiState.update {
            it.copy(
                filter = if (it.isStockMode) Filter.ALL else filter,
                sortOrder = if (it.isStockMode && sort == SortOrder.EXPIRY_DATE) SortOrder.NEWEST else sort
            )
        }
        applyFiltersAndSort()
    }

    private fun observeItems() {
        viewModelScope.launch {
            activeProjectManager.activeProjectIdFlow
                .onEach { activeProjectId = it }
                .flatMapLatest { projectId -> repository.getAllItems(projectId) }
                .catch { e -> _uiState.update { it.copy(error = e.message) } }
                .collect { items ->
                    // Sr No. = rank by scan order (createdAt, then id as a
                    // tie-break), independent of the current filter/sort —
                    // matches DAO getSerialNumber's exact ordering so an item
                    // always shows the same number in History and Detail.
                    val srNoMap = items
                        .sortedWith(compareBy({ it.effectiveOrder }, { it.id }))
                        .mapIndexed { index, item -> item.id to (index + 1) }
                        .toMap()
                    _uiState.update { it.copy(allItems = items, srNoMap = srNoMap) }
                    applyFiltersAndSort()
                }
        }
    }

    private fun observeOtherProjects() {
        viewModelScope.launch {
            combine(
                projectRepository.getAllProjects(),
                activeProjectManager.activeProjectIdFlow
            ) { projects, activeId ->
                projects.filter { it.id != activeId }
            }.collect { others ->
                _uiState.update { it.copy(otherProjects = others) }
            }
        }
    }

    /** Keeps the "Scan Order" vs "Custom Sort" label in sync with the active project. */
    private fun observeActiveProjectCustomSort() {
        viewModelScope.launch {
            combine(
                projectRepository.getAllProjects(),
                activeProjectManager.activeProjectIdFlow
            ) { projects, activeId ->
                projects.firstOrNull { it.id == activeId }
            }.collect { project ->
                val isStockProject = StockProjectClassifier.isStockProject(project?.isStockMode == true, project?.name)
                _uiState.update {
                    it.copy(
                        hasCustomSort = project?.hasCustomSort == true,
                        isStockMode = isStockProject,
                        filter = if (isStockProject) Filter.ALL else it.filter
                    )
                }
                applyFiltersAndSort()
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        applyFiltersAndSort()
    }

    fun cycleFilter() {
        val next = when (_uiState.value.filter) {
            Filter.ALL -> Filter.EXPIRED
            Filter.EXPIRED -> Filter.TODAY
            Filter.TODAY -> Filter.ONE_TO_SEVEN
            Filter.ONE_TO_SEVEN -> Filter.EIGHT_TO_THIRTY
            Filter.EIGHT_TO_THIRTY -> Filter.LATER
            Filter.LATER -> Filter.ALL
        }
        setFilter(next)
    }

    /** Selects a relative expiry bucket from the dedicated History filter chips. */
    fun setFilter(filter: Filter) {
        _uiState.update { it.copy(filter = filter, specificDate = null, specificMonth = null) }
        applyFiltersAndSort()
    }

    /** Sets a specific expiry-date filter ("yyyy-MM-dd"); clears month + bucket override. */
    fun setSpecificDate(isoDate: String) {
        _uiState.update { it.copy(specificDate = isoDate, specificMonth = null, filter = Filter.ALL) }
        applyFiltersAndSort()
    }

    /** Sets a specific expiry-month filter ("yyyy-MM"); clears date + bucket override. */
    fun setSpecificMonth(yearMonth: String) {
        _uiState.update { it.copy(specificMonth = yearMonth, specificDate = null, filter = Filter.ALL) }
        applyFiltersAndSort()
    }

    /** Clears both specific date and month filters (back to the relative bucket). */
    fun clearSpecificDateFilters() {
        _uiState.update { it.copy(specificDate = null, specificMonth = null) }
        applyFiltersAndSort()
    }

    fun toggleSortOrder() {
        val state = _uiState.value
        val next = if (state.isStockMode) {
            when (state.sortOrder) {
                SortOrder.NEWEST -> SortOrder.OLDEST
                SortOrder.OLDEST -> SortOrder.QUANTITY
                SortOrder.QUANTITY, SortOrder.EXPIRY_DATE -> SortOrder.NEWEST
            }
        } else {
            when (state.sortOrder) {
                SortOrder.NEWEST -> SortOrder.OLDEST
                SortOrder.OLDEST -> SortOrder.EXPIRY_DATE
                SortOrder.EXPIRY_DATE -> SortOrder.QUANTITY
                SortOrder.QUANTITY -> SortOrder.NEWEST
            }
        }
        _uiState.update { it.copy(sortOrder = next) }
        applyFiltersAndSort()
    }

    fun cycleUnitFilter() {
        val next = when (_uiState.value.unitFilter) {
            UnitFilter.ALL   -> UnitFilter.PCS
            UnitFilter.PCS   -> UnitFilter.OFR
            UnitFilter.OFR   -> UnitFilter.CTN
            UnitFilter.CTN   -> UnitFilter.KGS
            UnitFilter.KGS   -> UnitFilter.OTHER
            UnitFilter.OTHER -> UnitFilter.ALL
        }
        _uiState.update { it.copy(unitFilter = next) }
        applyFiltersAndSort()
    }

    private fun matchesUnitFilter(item: ExpiryItem, unitFilter: UnitFilter): Boolean = when (unitFilter) {
        UnitFilter.ALL   -> true
        UnitFilter.OTHER -> {
            val u = item.unit?.takeIf { it.isNotBlank() }
            u == null || u.uppercase() !in KNOWN_UNITS
        }
        else -> item.unit?.equals(unitFilter.label, ignoreCase = true) == true
    }

    /**
     * Whether [item] passes the active "date dimension" filter. A specific
     * expiry date or month, when set, overrides the relative [filter] bucket.
     */
    private fun matchesDateDimension(item: ExpiryItem, state: HistoryUiState, today: LocalDate): Boolean {
        if (state.isStockMode) {
            val scanDate = java.time.Instant.ofEpochMilli(item.createdAt)
                .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            state.specificDate?.let { return scanDate.toString() == it }
            state.specificMonth?.let { ym ->
                return "%04d-%02d".format(scanDate.year, scanDate.monthValue) == ym
            }
            return true
        }
        // Specific date overrides everything else in the date dimension.
        state.specificDate?.let { iso ->
            return item.expiryDate == iso ||
                ExpiryDateUtils.parseOrNull(item.expiryDate)?.toString() == iso
        }
        // Specific month ("yyyy-MM") overrides the bucket filter.
        state.specificMonth?.let { ym ->
            val d = ExpiryDateUtils.parseOrNull(item.expiryDate) ?: return false
            return "%04d-%02d".format(d.year, d.monthValue) == ym
        }
        return matchesDateFilter(item, state.filter, today)
    }

    private fun matchesDateFilter(item: ExpiryItem, filter: Filter, today: LocalDate): Boolean {
        val date = ExpiryDateUtils.parseOrNull(item.expiryDate)
        return when (filter) {
            Filter.ALL -> true
            Filter.EXPIRED -> date?.isBefore(today) == true
            Filter.TODAY -> date == today
            Filter.ONE_TO_SEVEN -> date != null && !date.isBefore(today.plusDays(1)) && !date.isAfter(today.plusDays(7))
            Filter.EIGHT_TO_THIRTY -> date != null && !date.isBefore(today.plusDays(8)) && !date.isAfter(today.plusDays(30))
            Filter.LATER -> date?.isAfter(today.plusDays(30)) == true
        }
    }

    private fun applyFiltersAndSort() {
        val state = _uiState.value
        val today = LocalDate.now()

        // Items matching the date dimension AND unit filter (no search) — used by
        // "Delete N Item(s) In This Filter" and the live count.
        val itemsInFilter = state.allItems.filter {
            matchesDateDimension(it, state, today) && matchesUnitFilter(it, state.unitFilter)
        }

        var filtered = itemsInFilter

        if (state.searchQuery.isNotBlank()) {
            filtered = filtered.filter {
                it.barcode.contains(state.searchQuery, ignoreCase = true) ||
                    it.productName?.contains(state.searchQuery, ignoreCase = true) == true ||
                    it.productNameArabic?.contains(state.searchQuery, ignoreCase = true) == true ||
                    it.itemCode?.contains(state.searchQuery, ignoreCase = true) == true
            }
        }

        // Sort by nearest expiry date first (ascending) for EXPIRY_DATE
        filtered = when (state.sortOrder) {
            // "Scan Order" / "Custom Sort": last-scanned-or-last-moved at the
            // top. Uses effectiveOrder (displayOrder if manually set via Move
            // Up/Down, else true createdAt), tie-broken by id — this exactly
            // matches the DAO's Sr No. ranking, so the visible order and each
            // row's Sr No. never disagree.
            SortOrder.NEWEST      -> filtered.sortedWith(compareByDescending<ExpiryItem> { it.effectiveOrder }.thenByDescending { it.id })
            // True original scan chronology, oldest first — unaffected by any
            // manual reordering (always ignores displayOrder).
            SortOrder.OLDEST      -> filtered.sortedWith(compareBy<ExpiryItem> { it.createdAt }.thenBy { it.id })
            SortOrder.EXPIRY_DATE -> filtered.sortedBy { it.expiryDate }
            SortOrder.QUANTITY    -> filtered.sortedByDescending { it.quantity }
        }

        // Drop selections that no longer exist (e.g. item deleted elsewhere).
        val validIds = state.allItems.map { it.id }.toSet()
        val prunedSelection = state.selectedIds.intersect(validIds)

        // Distinct expiry months for regular projects, or scan months for Stock Mode.
        val monthFmt = java.time.format.DateTimeFormatter.ofPattern("MMM yyyy", java.util.Locale.ENGLISH)
        val availableMonths = if (state.isStockMode) {
            state.allItems
                .map { java.time.Instant.ofEpochMilli(it.createdAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate() }
                .map { "%04d-%02d".format(it.year, it.monthValue) to it.withDayOfMonth(1) }
                .distinctBy { it.first }
                .sortedByDescending { it.second }
                .map { MonthOption(key = it.first, label = it.second.format(monthFmt)) }
        } else {
            state.allItems
                .mapNotNull { ExpiryDateUtils.parseOrNull(it.expiryDate) }
                .map { "%04d-%02d".format(it.year, it.monthValue) to it.withDayOfMonth(1) }
                .distinctBy { it.first }
                .sortedByDescending { it.second }
                .map { MonthOption(key = it.first, label = it.second.format(monthFmt)) }
        }

        _uiState.update {
            it.copy(
                filteredItems = filtered,
                itemsInFilter = itemsInFilter,
                selectedIds = prunedSelection,
                availableMonths = availableMonths
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    // ── Selection mode ───────────────────────────────────────────────────────

    /** Enters selection mode, optionally pre-selecting [initialId] (e.g. from a long-press). */
    fun enterSelectionMode(initialId: Long? = null) {
        _uiState.update {
            it.copy(
                selectionMode = true,
                selectedIds = if (initialId != null) setOf(initialId) else it.selectedIds
            )
        }
    }

    fun exitSelectionMode() {
        _uiState.update { it.copy(selectionMode = false, selectedIds = emptySet()) }
    }

    fun toggleItemSelection(id: Long) {
        _uiState.update { state ->
            val current = state.selectedIds
            val updated = if (id in current) current - id else current + id
            state.copy(selectedIds = updated)
        }
    }

    /** Selects every item currently visible in [filteredItems] (respects search + filter). */
    fun selectAllVisible() {
        _uiState.update { state ->
            state.copy(selectedIds = state.selectedIds + state.filteredItems.map { it.id }.toSet())
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedIds = emptySet()) }
    }

    // ── Move Up / Move Down (only meaningful in scan order = Sr No. order) ──

    /**
     * True when Move Up/Down should be shown: the list is sorted OLDEST
     * (ascending scan order), which is the only view where top-to-bottom
     * position matches Sr No. order — moving only means something there.
     */
    fun canShowMoveButtons(): Boolean = _uiState.value.sortOrder == SortOrder.NEWEST

    fun moveSelectedUp() = moveSelected(up = true)
    fun moveSelectedDown() = moveSelected(up = false)

    private fun moveSelected(up: Boolean) {
        val state = _uiState.value
        if (state.sortOrder != SortOrder.NEWEST || state.selectedIds.isEmpty()) return

        val list = state.filteredItems
        val selectedIndices = list.withIndex()
            .filter { it.value.id in state.selectedIds }
            .map { it.index }
            .sorted()
        if (selectedIndices.isEmpty()) return

        // Must be one unbroken run in the current (scan-order) list.
        val contiguous = selectedIndices.last() - selectedIndices.first() + 1 == selectedIndices.size
        if (!contiguous) {
            _uiState.update { it.copy(showMoveBlockedMessage = true) }
            return
        }

        val minIdx = selectedIndices.first()
        val maxIdx = selectedIndices.last()

        val windowStart: Int
        val windowEnd: Int
        val rotateLeft: Boolean   // true: first item of window moves to the end
        if (up) {
            if (minIdx == 0) return // already at the very top
            windowStart = minIdx - 1
            windowEnd = maxIdx
            rotateLeft = true
        } else {
            if (maxIdx == list.lastIndex) return // already at the very bottom
            windowStart = minIdx
            windowEnd = maxIdx + 1
            rotateLeft = false
        }

        // Reassign the SAME set of effective-order values within
        // [windowStart..windowEnd] to the items in their new order, writing
        // them into displayOrder (never createdAt). This swaps the block past
        // its single adjacent neighbor while leaving every item outside the
        // window (and therefore its Sr No.) completely untouched — and keeps
        // the true original scan timestamp intact underneath, which is what
        // makes "Reset to Scan Order" possible without any data loss.
        val window = list.subList(windowStart, windowEnd + 1)
        val orders = window.map { it.effectiveOrder }
        val newOrder = if (rotateLeft) window.drop(1) + window.first()
                       else listOf(window.last()) + window.dropLast(1)

        viewModelScope.launch {
            var changed = false
            newOrder.forEachIndexed { i, item ->
                val newEffectiveOrder = orders[i]
                if (item.effectiveOrder != newEffectiveOrder) {
                    repository.updateItem(item.copy(displayOrder = newEffectiveOrder).toEntity())
                    changed = true
                }
            }
            if (changed) {
                val projectId = activeProjectManager.getActiveProjectId()
                projectRepository.setHasCustomSort(projectId, true)
                _uiState.update { it.copy(hasCustomSort = true) }
            }
            // Selection stays on the same items (now re-sorted), so the user
            // can tap Move Up/Down again immediately to keep moving the block.
        }
    }

    /** Reverts the active project to true scan-chronology order, undoing all
     *  manual Move Up/Down arrangement losslessly (the real scan timestamps
     *  were never touched). */
    fun resetToScanOrder() {
        viewModelScope.launch {
            val projectId = activeProjectManager.getActiveProjectId()
            repository.clearDisplayOrder(projectId)
            projectRepository.setHasCustomSort(projectId, false)
            _uiState.update { it.copy(hasCustomSort = false) }
        }
    }

    fun clearMoveBlockedMessage() {
        _uiState.update { it.copy(showMoveBlockedMessage = false) }
    }

    // ── Delete: selected items ─────────────────────────────────────────────

    fun requestDeleteSelected() {
        if (_uiState.value.selectedIds.isNotEmpty()) {
            _uiState.update { it.copy(showDeleteSelectedConfirm = true) }
        }
    }

    fun dismissDeleteSelectedConfirm() {
        _uiState.update { it.copy(showDeleteSelectedConfirm = false) }
    }

    fun confirmDeleteSelected() {
        viewModelScope.launch {
            val ids = _uiState.value.selectedIds.toList()
            // Snapshot the rows before deleting so the user can Undo.
            val deleted = _uiState.value.allItems.filter { it.id in ids }
            repository.deleteItemsByIds(ids)
            _uiState.update {
                it.copy(
                    showDeleteSelectedConfirm = false,
                    selectionMode = false,
                    selectedIds = emptySet(),
                    undoDeleteItems = deleted,
                    undoDeleteCount = deleted.size
                )
            }
        }
    }

    // ── Delete: everything in the current filter (ignores search) ──────────

    fun requestDeleteFilter() {
        if (_uiState.value.itemsInFilter.isNotEmpty()) {
            _uiState.update { it.copy(showDeleteFilterConfirm = true) }
        }
    }

    fun dismissDeleteFilterConfirm() {
        _uiState.update { it.copy(showDeleteFilterConfirm = false) }
    }

    fun confirmDeleteFilter() {
        viewModelScope.launch {
            val itemsToDelete = _uiState.value.itemsInFilter
            val ids = itemsToDelete.map { it.id }
            repository.deleteItemsByIds(ids)
            _uiState.update {
                it.copy(
                    showDeleteFilterConfirm = false,
                    selectionMode = false,
                    selectedIds = emptySet(),
                    undoDeleteItems = itemsToDelete,
                    undoDeleteCount = itemsToDelete.size
                )
            }
        }
    }

    /** Re-inserts the most recently deleted items (Undo). */
    fun undoDelete() {
        val items = _uiState.value.undoDeleteItems ?: return
        viewModelScope.launch {
            // Re-insert as fresh rows (id = 0 → Room assigns new ids); all other
            // data (project, qty, expiry, names, unit) is preserved.
            items.forEach { repository.insertItem(it.toEntity().copy(id = 0)) }
            // The delete moved copies into the recycle bin — remove them so an
            // undone delete doesn't leave duplicates restorable from the bin.
            repository.removeFromBinByOriginalIds(items.map { it.id })
            _uiState.update { it.copy(undoDeleteItems = null, undoDeleteCount = 0) }
        }
    }

    fun clearUndoDelete() {
        _uiState.update { it.copy(undoDeleteItems = null, undoDeleteCount = 0) }
    }

    // ── Copy / Move selected items to another project ──────────────────────

    /** Opens the target-project picker for the chosen action (COPY or MOVE). */
    fun requestProjectAction(action: ProjectAction) {
        if (_uiState.value.selectedIds.isNotEmpty()) {
            _uiState.update { it.copy(projectActionMode = action) }
        }
    }

    fun dismissProjectAction() {
        _uiState.update { it.copy(projectActionMode = null, pendingTargetProjectId = null) }
    }

    /**
     * The user picked a target project. If any selected item would collide
     * with an existing item there (same barcode + expiry + unit), ask whether
     * to Add or Replace; otherwise perform immediately (merge mode is moot).
     */
    fun onTargetProjectChosen(targetProjectId: Long) {
        val ids = _uiState.value.selectedIds.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val hasCollision = ids.any { id ->
                val item = _uiState.value.allItems.firstOrNull { it.id == id } ?: return@any false
                repository.findByBarcodeExpiryUnit(
                    targetProjectId, item.barcode, item.expiryDate, item.unit
                ) != null
            }
            if (hasCollision) {
                // Defer to the Add/Replace dialog.
                _uiState.update { it.copy(pendingTargetProjectId = targetProjectId) }
            } else {
                performProjectAction(targetProjectId, com.nearexpiry.manager.domain.model.MergeMode.ADD)
            }
        }
    }

    /** Performs the pending copy/move into [targetProjectId] with the chosen [mergeMode]. */
    fun performProjectAction(targetProjectId: Long, mergeMode: com.nearexpiry.manager.domain.model.MergeMode) {
        val action = _uiState.value.projectActionMode ?: return
        val ids = _uiState.value.selectedIds.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val merged = when (action) {
                ProjectAction.COPY -> projectRepository.copyItemsToProject(ids, targetProjectId, mergeMode)
                ProjectAction.MOVE -> projectRepository.moveItemsToProject(ids, targetProjectId, mergeMode)
            }
            val movedOrCopied = ids.size
            val verb = if (action == ProjectAction.COPY) "Copied" else "Moved"
            val msg = if (merged > 0) "$verb $movedOrCopied, merged $merged" else "$verb $movedOrCopied"
            _uiState.update {
                it.copy(
                    projectActionMode = null,
                    pendingTargetProjectId = null,
                    selectionMode = false,
                    selectedIds = emptySet(),
                    copyMoveResult = msg
                )
            }
        }
    }

    fun clearCopyMoveResult() {
        _uiState.update { it.copy(copyMoveResult = null) }
    }
}
