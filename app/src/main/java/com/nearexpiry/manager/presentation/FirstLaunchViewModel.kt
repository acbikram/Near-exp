package com.nearexpiry.manager.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nearexpiry.manager.utils.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FirstLaunchViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _showLanguagePrompt = MutableStateFlow(false)
    val showLanguagePrompt: StateFlow<Boolean> = _showLanguagePrompt.asStateFlow()

    init {
        viewModelScope.launch {
            val alreadyShown = preferencesManager.languagePromptShownFlow.first()
            _showLanguagePrompt.value = !alreadyShown
        }
    }

    fun onLanguagePromptDismissed() {
        viewModelScope.launch {
            preferencesManager.setLanguagePromptShown()
            _showLanguagePrompt.value = false
        }
    }
}
