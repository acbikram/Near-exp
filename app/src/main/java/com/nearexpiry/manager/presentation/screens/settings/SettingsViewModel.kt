package com.nearexpiry.manager.presentation.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nearexpiry.manager.domain.repository.ExpiryRepository
import com.nearexpiry.manager.utils.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val repository: ExpiryRepository
) : ViewModel() {

    data class SettingsUiState(
        val scanSound: Boolean = true,
        val vibration: Boolean = true
    )

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesManager.scanSoundFlow.collect { sound ->
                _uiState.update { it.copy(scanSound = sound) }
            }
        }
        viewModelScope.launch {
            preferencesManager.vibrationFlow.collect { vibration ->
                _uiState.update { it.copy(vibration = vibration) }
            }
        }
    }

    fun toggleScanSound(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setScanSound(enabled)
        }
    }

    fun toggleVibration(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setVibration(enabled)
        }
    }

    fun clearAllRecords() {
        viewModelScope.launch {
            repository.deleteAllItems()
        }
    }
}
