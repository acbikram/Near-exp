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
        val recentItems: List<ExpiryItem> = emptyList(),
        val activeProjectName: String = "",
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
                val name = projectRepository.getProjectById(id)?.name ?: ""
                _uiState.update { it.copy(activeProjectName = name) }
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

                    val recentItems = allItems.sortedByDescending { it.createdAt }.take(5)

                    _uiState.update {
                        it.copy(
                            totalRecords = totalRecords,
                            uniqueProducts = uniqueProducts,
                            totalQuantity = totalQuantity,
                            expiredCount = expiredCount,
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
