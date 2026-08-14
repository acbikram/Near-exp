package com.nearexpiry.manager.presentation.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nearexpiry.manager.domain.model.ExpiryItem
import com.nearexpiry.manager.domain.repository.ExpiryRepository
import com.nearexpiry.manager.domain.repository.ProjectRepository
import com.nearexpiry.manager.utils.ActiveProjectManager
import com.nearexpiry.manager.utils.ExpiryDateUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: ExpiryRepository,
    private val projectRepository: ProjectRepository,
    private val activeProjectManager: ActiveProjectManager,
    private val itemNavigationContext: com.nearexpiry.manager.utils.ItemNavigationContext
) : ViewModel() {

    data class HomeUiState(
        val totalRecords: Int = 0,
        val uniqueProducts: Int = 0,
        val totalQuantity: Double = 0.0,
        /** Dates before today only. */
        val expiredCount: Int = 0,
        /** Today only. */
        val expiringToday: Int = 0,
        /** Tomorrow through seven days from today, inclusive. */
        val expiring1to7Days: Int = 0,
        /** Eight through thirty days from today, inclusive. */
        val expiring8to30Days: Int = 0,
        /** All items expiring within 3 days from today (incl. already expired), soonest first. */
        val expiringSoonItems: List<ExpiryItem> = emptyList(),
        val activeProjectName: String = "",
        /** Colour tag of the active project (hex), used to tint the dashboard project name. */
        val activeProjectColorHex: String = "",
        val error: String? = null
    )

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        observeItems()
        observeActiveProjectName()
    }

    private fun observeActiveProjectName() {
        viewModelScope.launch {
            activeProjectManager.activeProjectIdFlow.collect { id ->
                val project = projectRepository.getProjectById(id)
                _uiState.update {
                    it.copy(
                        activeProjectName = project?.name ?: "",
                        activeProjectColorHex = project?.colorHex ?: ""
                    )
                }
            }
        }
    }

    /**
     * Recomputes the dashboard whenever items change OR the active project
     * changes. flatMapLatest swaps to the new project's item stream on switch.
     */
    private fun observeItems() {
        viewModelScope.launch {
            activeProjectManager.activeProjectIdFlow
                .flatMapLatest { projectId -> repository.getAllItems(projectId) }
                .catch { e -> _uiState.update { it.copy(error = e.message) } }
                .collect { allItems ->
                    val today = LocalDate.now()

                    val totalRecords = allItems.size
                    val uniqueProducts = allItems.map { it.barcode }.distinct().size
                    val totalQuantity = allItems.sumOf { it.quantity }
                    val tomorrow = today.plusDays(1)
                    val daySeven = today.plusDays(7)
                    val dayEight = today.plusDays(8)
                    val dayThirty = today.plusDays(30)
                    val dates = allItems.associateWith { ExpiryDateUtils.parseOrNull(it.expiryDate) }

                    val expiredCount = allItems.count { dates[it]?.isBefore(today) == true }
                    val expiringToday = allItems.count { dates[it] == today }
                    val expiring1to7Days = allItems.count {
                        val date = dates[it]
                        date != null && !date.isBefore(tomorrow) && !date.isAfter(daySeven)
                    }
                    val expiring8to30Days = allItems.count {
                        val date = dates[it]
                        date != null && !date.isBefore(dayEight) && !date.isAfter(dayThirty)
                    }

                    // Items requiring attention in the next three days, including
                    // already-expired records, shown soonest first.
                    val expiringSoonItems = allItems
                        .filter { dates[it]?.isAfter(today.plusDays(3)) == false }
                        .sortedBy { dates[it] }

                    _uiState.update {
                        it.copy(
                            totalRecords = totalRecords,
                            uniqueProducts = uniqueProducts,
                            totalQuantity = totalQuantity,
                            expiredCount = expiredCount,
                            expiringToday = expiringToday,
                            expiring1to7Days = expiring1to7Days,
                            expiring8to30Days = expiring8to30Days,
                            expiringSoonItems = expiringSoonItems,
                            error = null
                        )
                    }
                }
        }
    }

    /** Sets swipe order in Detail to match this screen's expiring-soon list. */
    fun prepareItemNavigation() {
        itemNavigationContext.set(_uiState.value.expiringSoonItems.map { it.id })
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
