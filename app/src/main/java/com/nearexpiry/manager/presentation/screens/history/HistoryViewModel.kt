package com.nearexpiry.manager.presentation.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nearexpiry.manager.domain.model.ExpiryItem
import com.nearexpiry.manager.domain.repository.ExpiryRepository
import com.nearexpiry.manager.utils.ExpiryDateUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

enum class Filter { ALL, EXPIRED, TODAY, SEVEN_DAYS, THIRTY_DAYS }
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

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: ExpiryRepository
) : ViewModel() {

    data class HistoryUiState(
        val allItems: List<ExpiryItem> = emptyList(),
        val filteredItems: List<ExpiryItem> = emptyList(),
        /** Items matching the current [filter] only (ignores search) — used for "delete all in filter". */
        val itemsInFilter: List<ExpiryItem> = emptyList(),
        val searchQuery: String = "",
        val filter: Filter = Filter.ALL,
        val unitFilter: UnitFilter = UnitFilter.ALL,
        val sortOrder: SortOrder = SortOrder.NEWEST,
        val error: String? = null,
        // ── Selection mode ───────────────────────────────────────────────
        val selectionMode: Boolean = false,
        val selectedIds: Set<Long> = emptySet(),
        // ── Confirmation dialogs ─────────────────────────────────────────
        val showDeleteSelectedConfirm: Boolean = false,
        val showDeleteFilterConfirm: Boolean = false
    )

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        observeItems()
    }

    /**
     * Called from the composable after the ViewModel is created to apply the
     * initial filter/sort passed from the dashboard stat-card tap.
     */
    fun applyInitialFilterAndSort(filterStr: String, sortStr: String) {
        val filter = when (filterStr) {
            "EXPIRED"     -> Filter.EXPIRED
            "SEVEN_DAYS"  -> Filter.SEVEN_DAYS
            "THIRTY_DAYS" -> Filter.THIRTY_DAYS
            "TODAY"       -> Filter.TODAY
            else          -> Filter.ALL
        }
        val sort = when (sortStr) {
            "EXPIRY_DATE" -> SortOrder.EXPIRY_DATE
            "QUANTITY"    -> SortOrder.QUANTITY
            "OLDEST"      -> SortOrder.OLDEST
            else          -> SortOrder.NEWEST
        }
        _uiState.update { it.copy(filter = filter, sortOrder = sort) }
        applyFiltersAndSort()
    }

    private fun observeItems() {
        viewModelScope.launch {
            repository.getAllItems()
                .catch { e -> _uiState.update { it.copy(error = e.message) } }
                .collect { items ->
                    _uiState.update { it.copy(allItems = items) }
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
            Filter.ALL        -> Filter.EXPIRED
            Filter.EXPIRED    -> Filter.TODAY
            Filter.TODAY      -> Filter.SEVEN_DAYS
            Filter.SEVEN_DAYS -> Filter.THIRTY_DAYS
            Filter.THIRTY_DAYS -> Filter.ALL
        }
        _uiState.update { it.copy(filter = next) }
        applyFiltersAndSort()
    }

    fun toggleSortOrder() {
        val next = when (_uiState.value.sortOrder) {
            SortOrder.NEWEST      -> SortOrder.OLDEST
            SortOrder.OLDEST      -> SortOrder.EXPIRY_DATE
            SortOrder.EXPIRY_DATE -> SortOrder.QUANTITY
            SortOrder.QUANTITY    -> SortOrder.NEWEST
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

    private fun matchesDateFilter(item: ExpiryItem, filter: Filter, today: LocalDate): Boolean = when (filter) {
        Filter.ALL         -> true
        Filter.EXPIRED     -> ExpiryDateUtils.isExpired(item.expiryDate, today)
        Filter.TODAY       -> ExpiryDateUtils.isExpiringToday(item.expiryDate, today)
        Filter.SEVEN_DAYS  -> ExpiryDateUtils.isExpiringWithin(item.expiryDate, 7, today)
        Filter.THIRTY_DAYS -> ExpiryDateUtils.isExpiringWithin(item.expiryDate, 30, today)
    }

    private fun applyFiltersAndSort() {
        val state = _uiState.value
        val today = LocalDate.now()

        // Items matching the date filter AND unit filter (no search) — used by
        // "Delete N Item(s) In This Filter" and the live count.
        val itemsInFilter = state.allItems.filter {
            matchesDateFilter(it, state.filter, today) && matchesUnitFilter(it, state.unitFilter)
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
            SortOrder.NEWEST      -> filtered.sortedByDescending { it.createdAt }
            SortOrder.OLDEST      -> filtered.sortedBy { it.createdAt }
            SortOrder.EXPIRY_DATE -> filtered.sortedBy { it.expiryDate }
            SortOrder.QUANTITY    -> filtered.sortedByDescending { it.quantity }
        }

        // Drop selections that no longer exist (e.g. item deleted elsewhere).
        val validIds = state.allItems.map { it.id }.toSet()
        val prunedSelection = state.selectedIds.intersect(validIds)

        _uiState.update {
            it.copy(
                filteredItems = filtered,
                itemsInFilter = itemsInFilter,
                selectedIds = prunedSelection
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
            repository.deleteItemsByIds(ids)
            _uiState.update {
                it.copy(
                    showDeleteSelectedConfirm = false,
                    selectionMode = false,
                    selectedIds = emptySet()
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
            val ids = _uiState.value.itemsInFilter.map { it.id }
            repository.deleteItemsByIds(ids)
            _uiState.update {
                it.copy(
                    showDeleteFilterConfirm = false,
                    selectionMode = false,
                    selectedIds = emptySet()
                )
            }
        }
    }
}
