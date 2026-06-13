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

enum class Filter { ALL, TODAY, SEVEN_DAYS, THIRTY_DAYS }
enum class SortOrder { NEWEST, OLDEST, EXPIRY_DATE, QUANTITY }

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: ExpiryRepository
) : ViewModel() {

    data class HistoryUiState(
        val allItems: List<ExpiryItem> = emptyList(),
        val filteredItems: List<ExpiryItem> = emptyList(),
        val searchQuery: String = "",
        val filter: Filter = Filter.ALL,
        val sortOrder: SortOrder = SortOrder.NEWEST,
        val error: String? = null
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
            Filter.ALL        -> Filter.TODAY
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

    private fun applyFiltersAndSort() {
        val state = _uiState.value
        var filtered = state.allItems

        if (state.searchQuery.isNotBlank()) {
            filtered = filtered.filter {
                it.barcode.contains(state.searchQuery, ignoreCase = true) ||
                    it.productName?.contains(state.searchQuery, ignoreCase = true) == true ||
                    it.productNameArabic?.contains(state.searchQuery, ignoreCase = true) == true
            }
        }

        val today = LocalDate.now()
        filtered = when (state.filter) {
            Filter.ALL   -> filtered
            Filter.TODAY -> filtered.filter {
                ExpiryDateUtils.isExpiringToday(it.expiryDate, today)
            }
            Filter.SEVEN_DAYS -> filtered.filter {
                ExpiryDateUtils.isExpiringWithin(it.expiryDate, 7, today)
            }
            Filter.THIRTY_DAYS -> filtered.filter {
                ExpiryDateUtils.isExpiringWithin(it.expiryDate, 30, today)
            }
        }

        // Sort by nearest expiry date first (ascending) for EXPIRY_DATE
        filtered = when (state.sortOrder) {
            SortOrder.NEWEST      -> filtered.sortedByDescending { it.createdAt }
            SortOrder.OLDEST      -> filtered.sortedBy { it.createdAt }
            SortOrder.EXPIRY_DATE -> filtered.sortedBy { it.expiryDate }
            SortOrder.QUANTITY    -> filtered.sortedByDescending { it.quantity }
        }

        _uiState.update { it.copy(filteredItems = filtered) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
