package com.nearexpiry.manager.presentation.screens.home

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

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: ExpiryRepository
) : ViewModel() {

    data class HomeUiState(
        val totalRecords: Int = 0,
        val uniqueProducts: Int = 0,
        val totalQuantity: Double = 0.0,
        val expiringIn7Days: Int = 0,
        val expiringIn30Days: Int = 0,
        val recentItems: List<ExpiryItem> = emptyList(),
        val error: String? = null
    )

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        observeItems()
    }

    /**
     * Continuously collects the items Flow from Room so the dashboard
     * recomputes automatically whenever a record is inserted, updated,
     * or deleted - no manual refresh or app restart required.
     */
    private fun observeItems() {
        viewModelScope.launch {
            repository.getAllItems()
                .catch { e -> _uiState.update { it.copy(error = e.message) } }
                .collect { allItems ->
                    val today = LocalDate.now()

                    val totalRecords = allItems.size
                    val uniqueProducts = allItems.map { it.barcode }.distinct().size
                    val totalQuantity = allItems.sumOf { it.quantity }

                    val expiringIn7Days = allItems.count {
                        ExpiryDateUtils.isExpiringWithin(it.expiryDate, 7, today)
                    }
                    val expiringIn30Days = allItems.count {
                        ExpiryDateUtils.isExpiringWithin(it.expiryDate, 30, today)
                    }

                    val recentItems = allItems.sortedByDescending { it.createdAt }.take(5)

                    _uiState.update {
                        it.copy(
                            totalRecords = totalRecords,
                            uniqueProducts = uniqueProducts,
                            totalQuantity = totalQuantity,
                            expiringIn7Days = expiringIn7Days,
                            expiringIn30Days = expiringIn30Days,
                            recentItems = recentItems,
                            error = null
                        )
                    }
                }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
