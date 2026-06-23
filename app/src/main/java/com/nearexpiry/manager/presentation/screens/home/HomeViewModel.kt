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
    private val activeProjectManager: ActiveProjectManager
) : ViewModel() {

    data class HomeUiState(
        val totalRecords: Int = 0,
        val uniqueProducts: Int = 0,
        val totalQuantity: Double = 0.0,
        val expiredCount: Int = 0,
        val expiringIn7Days: Int = 0,
        val expiringIn30Days: Int = 0,
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

                    val expiredCount = allItems.count {
                        ExpiryDateUtils.isExpired(it.expiryDate, today)
                    }
                    val expiringIn7Days = allItems.count {
                        ExpiryDateUtils.isExpiringWithin(it.expiryDate, 7, today)
                    }
                    val expiringIn30Days = allItems.count {
                        ExpiryDateUtils.isExpiringWithin(it.expiryDate, 30, today)
                    }

                    // Items expiring within 3 days from today (includes any
                    // already-expired), soonest expiry first.
                    val expiringSoonItems = allItems
                        .filter { ExpiryDateUtils.isExpiringWithin(it.expiryDate, 3, today) }
                        .sortedBy { ExpiryDateUtils.parseOrNull(it.expiryDate) }

                    _uiState.update {
                        it.copy(
                            totalRecords = totalRecords,
                            uniqueProducts = uniqueProducts,
                            totalQuantity = totalQuantity,
                            expiredCount = expiredCount,
                            expiringIn7Days = expiringIn7Days,
                            expiringIn30Days = expiringIn30Days,
                            expiringSoonItems = expiringSoonItems,
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
