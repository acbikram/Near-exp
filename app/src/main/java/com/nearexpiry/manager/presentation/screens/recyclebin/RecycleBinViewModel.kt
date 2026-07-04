package com.nearexpiry.manager.presentation.screens.recyclebin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nearexpiry.manager.data.local.entity.RecycleBinEntity
import com.nearexpiry.manager.domain.repository.ExpiryRepository
import com.nearexpiry.manager.utils.ActiveProjectManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Recycle Bin: lists soft-deleted items (auto-purged after 30 days). Items can
 * be restored to their original project (falling back to the active project if
 * the original was deleted) or removed permanently.
 */
@HiltViewModel
class RecycleBinViewModel @Inject constructor(
    private val repository: ExpiryRepository,
    private val activeProjectManager: ActiveProjectManager
) : ViewModel() {

    data class BinUiState(
        val entries: List<RecycleBinEntity> = emptyList(),
        /** One-shot message (e.g. "3 items restored"). */
        val message: String? = null,
        /** Bin ids awaiting permanent-delete confirmation; empty = no dialog. */
        val confirmDeleteIds: List<Long> = emptyList()
    )

    private val _uiState = MutableStateFlow(BinUiState())
    val uiState: StateFlow<BinUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getBinItems().collect { list ->
                _uiState.update { it.copy(entries = list) }
            }
        }
    }

    fun restore(binIds: List<Long>) {
        if (binIds.isEmpty()) return
        viewModelScope.launch {
            val fallback = activeProjectManager.getActiveProjectId()
            val n = repository.restoreFromBin(binIds, fallback)
            _uiState.update { it.copy(message = "restored:$n") }
        }
    }

    fun askDeletePermanently(binIds: List<Long>) {
        if (binIds.isEmpty()) return
        _uiState.update { it.copy(confirmDeleteIds = binIds) }
    }

    fun confirmDeletePermanently() {
        val ids = _uiState.value.confirmDeleteIds
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.deleteFromBinPermanently(ids)
            _uiState.update { it.copy(confirmDeleteIds = emptyList(), message = "deleted:${ids.size}") }
        }
    }

    fun cancelDelete() {
        _uiState.update { it.copy(confirmDeleteIds = emptyList()) }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
