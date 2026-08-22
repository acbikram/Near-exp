package com.nearexpiry.manager.presentation

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nearexpiry.manager.utils.GoogleDriveBackupManager
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
    private val preferencesManager: PreferencesManager,
    private val googleDriveBackupManager: GoogleDriveBackupManager
) : ViewModel() {

    private val _showLanguagePrompt = MutableStateFlow(false)
    val showLanguagePrompt: StateFlow<Boolean> = _showLanguagePrompt.asStateFlow()

    private val _showThemePrompt = MutableStateFlow(false)
    val showThemePrompt: StateFlow<Boolean> = _showThemePrompt.asStateFlow()

    private val _showGoogleDrivePrompt = MutableStateFlow(false)
    val showGoogleDrivePrompt: StateFlow<Boolean> = _showGoogleDrivePrompt.asStateFlow()

    private val _googleDriveError = MutableStateFlow<String?>(null)
    val googleDriveError: StateFlow<String?> = _googleDriveError.asStateFlow()

    // Startup permissions and OEM battery guidance wait until the required
    // language-then-theme onboarding sequence is complete.
    private val _startupSetupPending = MutableStateFlow(true)
    val startupSetupPending: StateFlow<Boolean> = _startupSetupPending.asStateFlow()

    init {
        viewModelScope.launch {
            // A corrupt or OEM-locked preference store must not make the
            // first visible screen fatal. In that rare case we skip optional
            // first-run prompts; language and appearance remain in Settings.
            val onboarding = runCatching {
                Triple(
                    preferencesManager.languagePromptShownFlow.first(),
                    preferencesManager.themePromptShownFlow.first(),
                    preferencesManager.isGoogleDriveOnboardingPending()
                )
            }.getOrDefault(Triple(true, true, false))
            val languageAlreadyShown = onboarding.first
            val themeAlreadyShown = onboarding.second
            val googleDriveOnboardingPending = onboarding.third

            _showLanguagePrompt.value = !languageAlreadyShown
            _showThemePrompt.value = languageAlreadyShown && !themeAlreadyShown
            _showGoogleDrivePrompt.value = languageAlreadyShown && themeAlreadyShown && googleDriveOnboardingPending
            _startupSetupPending.value = !languageAlreadyShown || !themeAlreadyShown || googleDriveOnboardingPending
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
                preferencesManager.beginGoogleDriveOnboarding()
            }
            _showGoogleDrivePrompt.value = true
        }
    }

    fun googleDriveSignInIntent(): Intent = googleDriveBackupManager.signInIntent()

    fun onGoogleDriveAddNow(data: Intent?) {
        viewModelScope.launch {
            _googleDriveError.value = null
            try {
                googleDriveBackupManager.handleSignInResult(data)
                preferencesManager.completeGoogleDriveOnboarding()
                _showGoogleDrivePrompt.value = false
                _startupSetupPending.value = false
            } catch (e: Exception) {
                _googleDriveError.value = e.message ?: "Google Drive sign-in was not completed"
            }
        }
    }

    fun onGoogleDriveSkip() {
        viewModelScope.launch {
            preferencesManager.completeGoogleDriveOnboarding()
            _showGoogleDrivePrompt.value = false
            _startupSetupPending.value = false
        }
    }
}
