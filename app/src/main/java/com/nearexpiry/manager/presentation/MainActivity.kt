package com.nearexpiry.manager.presentation

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.nearexpiry.manager.notifications.DailyExpiryAlarmScheduler
import com.nearexpiry.manager.presentation.components.BatteryOptimizationDialog
import com.nearexpiry.manager.presentation.components.FirstLaunchGoogleDriveDialog
import com.nearexpiry.manager.presentation.components.FirstLaunchLanguageDialog
import com.nearexpiry.manager.presentation.components.FirstLaunchThemeDialog
import com.nearexpiry.manager.presentation.components.NotificationPermissionDialog
import com.nearexpiry.manager.presentation.navigation.NearExpiryNavHost
import com.nearexpiry.manager.presentation.theme.NearExpiryManagerTheme
import com.nearexpiry.manager.utils.PreferencesManager
import dagger.hilt.android.AndroidEntryPoint

/**
 * Extends AppCompatActivity (not plain ComponentActivity) so the in-app
 * English/Arabic locale switch works across API levels.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val firstLaunchViewModel: FirstLaunchViewModel by viewModels()

    @javax.inject.Inject
    lateinit var preferencesManager: PreferencesManager

    // True when we should show the "enable notifications in Settings" dialog
    // (i.e. the user has hard-denied, so the system won't show the prompt again).
    private val showNotifSettingsDialog = mutableStateOf(false)

    // True when battery optimization is still restricting the app — shown
    // until the user allows it or dismisses (re-shown next launch if still
    // restricted, same pattern as the notification dialog).
    private val showBatteryOptDialog = mutableStateOf(false)
    private var batteryOptDismissedThisSession = false

    // Avoid re-launching the system prompt repeatedly within one resume.
    private var permissionRequestedThisResume = false

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val canAskAgain = shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
            showNotifSettingsDialog.value = !canAskAgain
        } else {
            showNotifSettingsDialog.value = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val openUpdates = intent?.getBooleanExtra("open_updates", false) == true
        val autoUpdate = intent?.getBooleanExtra("auto_update", false) == true
        setContent {
            val themeMode by preferencesManager.themeModeFlow.collectAsState(initial = "dark")
            val darkTheme = when (themeMode) {
                "light" -> false
                "system" -> isSystemInDarkTheme()
                else -> true
            }
            NearExpiryManagerTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NearExpiryNavHost(openUpdates = openUpdates, autoUpdate = autoUpdate)

                    val showLanguagePrompt by firstLaunchViewModel.showLanguagePrompt.collectAsState()
                    val showThemePrompt by firstLaunchViewModel.showThemePrompt.collectAsState()
                    val showGoogleDrivePrompt by firstLaunchViewModel.showGoogleDrivePrompt.collectAsState()
                    val googleDriveError by firstLaunchViewModel.googleDriveError.collectAsState()
                    val startupSetupPending by firstLaunchViewModel.startupSetupPending.collectAsState()
                    val googleDriveSetupLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.StartActivityForResult()
                    ) { result ->
                        firstLaunchViewModel.onGoogleDriveAddNow(result.data)
                    }

                    LaunchedEffect(startupSetupPending) {
                        if (!startupSetupPending) {
                            ensureNotificationPermission()
                            ensureBatteryOptimizationExempt()
                        }
                    }

                    if (showLanguagePrompt) {
                        FirstLaunchLanguageDialog(
                            onLanguageSelected = firstLaunchViewModel::onLanguageSelected
                        )
                    } else if (showThemePrompt) {
                        FirstLaunchThemeDialog(
                            initialMode = themeMode,
                            onConfirm = firstLaunchViewModel::onThemeSelected
                        )
                    } else if (showGoogleDrivePrompt) {
                        FirstLaunchGoogleDriveDialog(
                            error = googleDriveError,
                            onAddNow = {
                                googleDriveSetupLauncher.launch(firstLaunchViewModel.googleDriveSignInIntent())
                            },
                            onSkip = firstLaunchViewModel::onGoogleDriveSkip
                        )
                    }

                    if (!showLanguagePrompt && !showThemePrompt && !showGoogleDrivePrompt && showNotifSettingsDialog.value) {
                        NotificationPermissionDialog(
                            onOpenSettings = { openAppNotificationSettings() },
                            onDismiss = { showNotifSettingsDialog.value = false }
                        )
                    }

                    // On first launch, keep OEM battery-optimization UI out
                    // of the same composition as the mandatory language picker.
                    // Some EMUI builds are unstable when two modal surfaces are
                    // requested during their initial activity resume.
                    if (!showLanguagePrompt && !showThemePrompt && !showGoogleDrivePrompt && showBatteryOptDialog.value) {
                        BatteryOptimizationDialog(
                            onAllow = { requestIgnoreBatteryOptimizations() },
                            onDismiss = {
                                showBatteryOptDialog.value = false
                                batteryOptDismissedThisSession = true
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        permissionRequestedThisResume = false
        // Required first-startup language and appearance selection comes before
        // optional notification and battery prompts.
        if (!firstLaunchViewModel.startupSetupPending.value) {
            ensureNotificationPermission()
            ensureBatteryOptimizationExempt()
        }
        // If the user has just enabled Android's exact-alarm access, replace
        // the fallback alarm immediately with the precise next local 8:00 AM.
        runCatching { DailyExpiryAlarmScheduler.schedule(this) }
    }

    /**
     * On every launch/resume, if notification permission isn't granted, prompt
     * for it. Once granted this is a no-op. On Android < 13 the permission is
     * implicit so there's nothing to do.
     */
    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (granted) {
            showNotifSettingsDialog.value = false
            return
        }

        val canAskViaSystem = shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) ||
            !hasRequestedNotifBefore()

        if (canAskViaSystem && !permissionRequestedThisResume) {
            permissionRequestedThisResume = true
            markNotifRequested()
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            showNotifSettingsDialog.value = true
        }
    }

    private fun openAppNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        }
        runCatching { startActivity(intent) }.onFailure {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.fromParts("package", packageName, null)
                }
            )
        }
    }

    /**
     * On every launch/resume, if the app is still restricted by battery
     * optimization, prompt to allow it. This is what makes the daily 8 AM
     * expiry notification reliable on phones (Honor/Huawei, Samsung, Xiaomi)
     * that otherwise delay background work regardless of how it's scheduled.
     * Dismissing hides it only for this session; it reappears next launch
     * until the user allows it or Android grants it automatically.
     */
    private fun ensureBatteryOptimizationExempt() {
        if (batteryOptDismissedThisSession) return
        // Some Honor/Huawei Android 10–12 builds throw from PowerManager while
        // evaluating this optional OEM battery setting. It must never prevent
        // the Home screen from opening; a later Settings visit can still show
        // the guidance where the device supports it.
        val exempt = runCatching {
            val pm = getSystemService(android.os.PowerManager::class.java)
            pm?.isIgnoringBatteryOptimizations(packageName) ?: true
        }.getOrDefault(true)
        showBatteryOptDialog.value = !exempt
    }

    private fun requestIgnoreBatteryOptimizations() {
        showBatteryOptDialog.value = false
        val intent = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            android.net.Uri.parse("package:$packageName")
        )
        runCatching { startActivity(intent) }.onFailure {
            // Some OEMs (Honor/Huawei especially) block the direct-request
            // intent; fall back to the general battery settings screen.
            runCatching {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
    }

    private fun hasRequestedNotifBefore(): Boolean =
        getSharedPreferences("perm_flags", MODE_PRIVATE).getBoolean("notif_requested", false)

    private fun markNotifRequested() {
        getSharedPreferences("perm_flags", MODE_PRIVATE).edit()
            .putBoolean("notif_requested", true).apply()
    }
}
