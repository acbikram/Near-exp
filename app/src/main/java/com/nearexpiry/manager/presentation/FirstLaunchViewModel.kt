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
            // A corrupt or OEM-locked preference store must not make the
            // first visible screen fatal. In that rare case we skip this
            // optional first-run prompt; language remains available later in
            // Settings.
            val alreadyShown = runCatching {
                preferencesManager.languagePromptShownFlow.first()
            }.getOrDefault(true)
            _showLanguagePrompt.value = !alreadyShown
        }
    }

    fun onLanguagePromptDismissed() {
        viewModelScope.launch {
            // Hide the optional dialog before persisting its completion so an
            // OEM-specific DataStore failure cannot leave the activity in an
            // unstable first-launch state.
            _showLanguagePrompt.value = false
            runCatching { preferencesManager.setLanguagePromptShown() }
        }
    }
}
