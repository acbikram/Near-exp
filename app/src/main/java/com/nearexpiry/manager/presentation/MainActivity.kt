package com.nearexpiry.manager.presentation

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import com.nearexpiry.manager.presentation.components.FirstLaunchLanguageDialog
import com.nearexpiry.manager.presentation.navigation.NearExpiryNavHost
import com.nearexpiry.manager.presentation.theme.NearExpiryManagerTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Extends AppCompatActivity (not plain ComponentActivity) so that
 * AppCompatDelegate.setApplicationLocales(...) (used for the in-app
 * English/Arabic language switch in Settings) can correctly wrap this
 * activity's resources with the chosen locale and trigger a recreate on
 * API levels below 33. On API 33+ the system LocaleManager handles this
 * directly, but AppCompatActivity is still required for consistent
 * behavior across all supported OS versions (minSdk 29).
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val firstLaunchViewModel: FirstLaunchViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            NearExpiryManagerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NearExpiryNavHost()

                    // ── First-launch language picker ─────────────────────────
                    val showLanguagePrompt by firstLaunchViewModel.showLanguagePrompt.collectAsState()
                    if (showLanguagePrompt) {
                        FirstLaunchLanguageDialog(
                            onDismiss = { firstLaunchViewModel.onLanguagePromptDismissed() }
                        )
                    }
                }
            }
        }
    }
}
