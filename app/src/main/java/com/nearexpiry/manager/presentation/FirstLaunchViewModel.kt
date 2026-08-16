package com.nearexpiry.manager.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nearexpiry.manager.utils.LanguageManager
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

    private val _showThemePrompt = MutableStateFlow(false)
    val showThemePrompt: StateFlow<Boolean> = _showThemePrompt.asStateFlow()

    // Startup permissions and OEM battery guidance wait until the required
    // language-then-theme onboarding sequence is complete.
    private val _startupSetupPending = MutableStateFlow(true)
    val startupSetupPending: StateFlow<Boolean> = _startupSetupPending.asStateFlow()

    init {
        viewModelScope.launch {
            // A corrupt or OEM-locked preference store must not make the
            // first visible screen fatal. In that rare case we skip optional
            // first-run prompts; language and appearance remain in Settings.
            val (languageAlreadyShown, themeAlreadyShown) = runCatching {
                preferencesManager.languagePromptShownFlow.first() to
                    preferencesManager.themePromptShownFlow.first()
            }.getOrDefault(true to true)

            _showLanguagePrompt.value = !languageAlreadyShown
            _showThemePrompt.value = languageAlreadyShown && !themeAlreadyShown
            _startupSetupPending.value = !languageAlreadyShown || !themeAlreadyShown
        }
    }

    /** Persists language onboarding before a locale recreation can occur. */
    fun onLanguageSelected(language: LanguageManager.AppLanguage) {
        viewModelScope.launch {
            _showLanguagePrompt.value = false
            runCatching { preferencesManager.setLanguagePromptShown() }
            // Show immediately if the locale does not recreate; if it does,
            // the replacement ViewModel restores this same next step.
            _showThemePrompt.value = true
            LanguageManager.setLanguage(language)
        }
    }

    /** Saves the selected appearance and completes first-startup onboarding. */
    fun onThemeSelected(mode: String) {
        viewModelScope.launch {
            _showThemePrompt.value = false
            runCatching {
                preferencesManager.setThemeMode(mode)
                preferencesManager.setThemePromptShown()
            }
            _startupSetupPending.value = false
        }
    }
}
