package com.nearexpiry.manager.presentation.screens.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.nearexpiry.manager.R
import com.nearexpiry.manager.notifications.DailyExpiryAlarmScheduler
import com.nearexpiry.manager.notifications.ExpiryNotificationWorker
import com.nearexpiry.manager.presentation.components.BottomNavigationBar
import com.nearexpiry.manager.presentation.components.GlassActionButton
import com.nearexpiry.manager.presentation.components.GlassActionTone
import com.nearexpiry.manager.presentation.components.GlassSectionCard
import com.nearexpiry.manager.presentation.components.GlassSelectableOption
import com.nearexpiry.manager.presentation.navigation.Screen
import com.nearexpiry.manager.presentation.theme.CyanAccent
import com.nearexpiry.manager.presentation.theme.ErrorRed
import com.nearexpiry.manager.presentation.theme.GreenAccent
import com.nearexpiry.manager.presentation.theme.OrangeAccent
import com.nearexpiry.manager.presentation.theme.SubtleGray
import com.nearexpiry.manager.presentation.theme.SurfaceDark
import com.nearexpiry.manager.presentation.theme.SurfaceVariant
import com.nearexpiry.manager.utils.LanguageManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    autoStartUpdate: Boolean = false,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }
    var languageExpanded by rememberSaveable { mutableStateOf(false) }
    var appearanceExpanded by rememberSaveable { mutableStateOf(false) }
    var dataManagementExpanded by rememberSaveable { mutableStateOf(false) }

    // Arrived from the notification's "Update Now": check + auto-start download.
    LaunchedEffect(autoStartUpdate) {
        if (autoStartUpdate) viewModel.checkForUpdate(autoStartDownload = true)
    }

    // Surface the "cannot delete the last project" message as a toast.
    val cannotDeleteMsg = stringResource(R.string.project_cannot_delete_last)
    LaunchedEffect(uiState.message) {
        if (uiState.message == "CANNOT_DELETE_LAST") {
            android.widget.Toast.makeText(context, cannotDeleteMsg, android.widget.Toast.LENGTH_LONG).show()
            viewModel.clearMessage()
        }
    }

    // ── Language selection ────────────────────────────────────────────────────
    var currentLanguage by remember { mutableStateOf(LanguageManager.getCurrentLanguage()) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { BottomNavigationBar(navController) }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {

            // ── Projects ───────────────────────────────────────────────────
            item {
                GlassSectionCard {
                    ProjectsSection(
                        projects = uiState.projects,
                        activeProjectId = uiState.activeProjectId,
                        onSwitch = { viewModel.switchProject(it) },
                        onCreate = { name, color -> viewModel.createProject(name, color) },
                        onRename = { id, name -> viewModel.renameProject(id, name) },
                        onRecolor = { id, color -> viewModel.updateProjectColor(id, color) },
                        onClone = { id, name, color -> viewModel.cloneProject(id, name, color) },
                        onDelete = { viewModel.deleteProject(it) }
                    )
                }
            }

            // ── Language ───────────────────────────────────────────────────
            item {
                GlassSectionCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { languageExpanded = !languageExpanded }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.language), style = MaterialTheme.typography.titleMedium.copy(color = CyanAccent, fontWeight = FontWeight.Bold))
                                Text(
                                    if (languageExpanded) stringResource(R.string.language_description) else "Tap to choose the app language",
                                    style = MaterialTheme.typography.bodySmall.copy(color = SubtleGray)
                                )
                            }
                            Icon(if (languageExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null, tint = CyanAccent)
                        }
                        if (languageExpanded) {
                            Spacer(Modifier.height(12.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                LanguageOptionRow(label = stringResource(R.string.language_system_default), selected = currentLanguage == LanguageManager.AppLanguage.SYSTEM_DEFAULT, onClick = { currentLanguage = LanguageManager.AppLanguage.SYSTEM_DEFAULT; LanguageManager.setLanguage(LanguageManager.AppLanguage.SYSTEM_DEFAULT) })
                                LanguageOptionRow(label = stringResource(R.string.language_english), selected = currentLanguage == LanguageManager.AppLanguage.ENGLISH, onClick = { currentLanguage = LanguageManager.AppLanguage.ENGLISH; LanguageManager.setLanguage(LanguageManager.AppLanguage.ENGLISH) })
                                LanguageOptionRow(label = stringResource(R.string.language_arabic), selected = currentLanguage == LanguageManager.AppLanguage.ARABIC, onClick = { currentLanguage = LanguageManager.AppLanguage.ARABIC; LanguageManager.setLanguage(LanguageManager.AppLanguage.ARABIC) })
                            }
                        }
                    }
                }
            }

            // ── Appearance ─────────────────────────────────────────────────────
            item {
                GlassSectionCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { appearanceExpanded = !appearanceExpanded }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.appearance),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        color = CyanAccent,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Text(
                                    if (appearanceExpanded) {
                                        stringResource(R.string.appearance_description)
                                    } else {
                                        stringResource(
                                            R.string.appearance_current_theme_format,
                                            when (themeMode) {
                                                "light" -> stringResource(R.string.theme_light)
                                                "system" -> stringResource(R.string.theme_system)
                                                else -> stringResource(R.string.theme_dark)
                                            }
                                        )
                                    },
                                    style = MaterialTheme.typography.bodySmall.copy(color = SubtleGray)
                                )
                            }
                            Icon(
                                imageVector = if (appearanceExpanded) {
                                    Icons.Default.KeyboardArrowUp
                                } else {
                                    Icons.Default.KeyboardArrowDown
                                },
                                contentDescription = if (appearanceExpanded) {
                                    "Collapse appearance"
                                } else {
                                    "Expand appearance"
                                },
                                tint = CyanAccent
                            )
                        }
                        if (appearanceExpanded) {
                            Spacer(Modifier.height(10.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(
                                    "dark" to R.string.theme_dark,
                                    "light" to R.string.theme_light,
                                    "system" to R.string.theme_system
                                ).forEach { (mode, labelRes) ->
                                    GlassSelectableOption(
                                        label = stringResource(labelRes),
                                        selected = themeMode == mode,
                                        onClick = { viewModel.setThemeMode(mode) },
                                        detail = stringResource(
                                            when (mode) {
                                                "dark" -> R.string.theme_dark_description
                                                "light" -> R.string.theme_light_description
                                                else -> R.string.theme_system_description
                                            }
                                        ),
                                        trailingContent = { ThemePreviewSwatch(mode = mode, selected = themeMode == mode) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Data Management ────────────────────────────────────────────────
            item {
                GlassSectionCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { dataManagementExpanded = !dataManagementExpanded }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.data_management), style = MaterialTheme.typography.titleMedium.copy(color = CyanAccent, fontWeight = FontWeight.Bold))
                                Text(
                                    if (dataManagementExpanded) {
                                        stringResource(R.string.data_management_expanded_description)
                                    } else {
                                        stringResource(R.string.data_management_collapsed_description)
                                    },
                                    style = MaterialTheme.typography.bodySmall.copy(color = SubtleGray)
                                )
                            }
                            Icon(if (dataManagementExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null, tint = CyanAccent)
                        }
                        if (dataManagementExpanded) {
                            Spacer(Modifier.height(12.dp))
                            GlassActionButton(
                                label = stringResource(R.string.backup_restore),
                                supportingText = stringResource(R.string.glass_backup_restore_detail),
                                onClick = { navController.navigate(Screen.BackupRestore.route) }
                            )
                            Spacer(Modifier.height(8.dp))
                            GlassActionButton(
                                label = stringResource(R.string.recycle_bin),
                                supportingText = stringResource(R.string.glass_recycle_bin_detail),
                                onClick = { navController.navigate(Screen.RecycleBin.route) },
                                tone = GlassActionTone.Neutral
                            )
                            Spacer(Modifier.height(8.dp))
                            GlassActionButton(
                                label = stringResource(R.string.test_notification_now),
                                supportingText = stringResource(R.string.glass_test_notification_detail),
                                onClick = { viewModel.testExpiryNotificationNow() },
                                tone = GlassActionTone.Warning
                            )
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !DailyExpiryAlarmScheduler.canScheduleExact(context)) {
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = stringResource(R.string.exact_alarm_access_title),
                                    style = MaterialTheme.typography.titleSmall.copy(color = OrangeAccent, fontWeight = FontWeight.Bold)
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.exact_alarm_access_description),
                                    style = MaterialTheme.typography.bodySmall.copy(color = SubtleGray)
                                )
                                Spacer(Modifier.height(8.dp))
                                GlassActionButton(
                                    label = stringResource(R.string.exact_alarm_access_button),
                                    onClick = {
                                        val intent = Intent(
                                            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                            Uri.parse("package:${context.packageName}")
                                        )
                                        runCatching { context.startActivity(intent) }
                                    },
                                    tone = GlassActionTone.Warning
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            GlassActionButton(
                                label = stringResource(R.string.clear_all_records),
                                onClick = { showClearDialog = true },
                                tone = GlassActionTone.Destructive
                            )
                        }
                    }
                }
            }

            // ── App Updates ──────────────────────────────────────────────
            item {
                GlassSectionCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.app_updates),
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = CyanAccent,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.current_version_format, uiState.currentVersionName),
                            style = MaterialTheme.typography.bodySmall,
                            color = SubtleGray
                        )
                        Spacer(Modifier.height(12.dp))

                        when (uiState.updateState) {
                            SettingsViewModel.UpdateState.AVAILABLE,
                            SettingsViewModel.UpdateState.DOWNLOADING,
                            SettingsViewModel.UpdateState.DOWNLOADED -> {
                                Text(
                                    stringResource(R.string.update_available_format, uiState.updateVersionName),
                                    style = MaterialTheme.typography.bodyMedium.copy(color = OrangeAccent),
                                    fontWeight = FontWeight.Bold
                                )
                                if (uiState.updateState == SettingsViewModel.UpdateState.DOWNLOADING) {
                                    Spacer(Modifier.height(8.dp))
                                    LinearProgressIndicator(
                                        progress = { uiState.updateProgress },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        stringResource(R.string.downloading_percent_format, uiState.updateProgressPercent),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SubtleGray
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = when (uiState.updateState) {
                                        SettingsViewModel.UpdateState.DOWNLOADING ->
                                            stringResource(R.string.update_install_automatic)
                                        SettingsViewModel.UpdateState.DOWNLOADED ->
                                            stringResource(R.string.update_install_manual)
                                        else -> stringResource(R.string.update_preparing_download)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SubtleGray
                                )
                                if (uiState.updateState == SettingsViewModel.UpdateState.DOWNLOADED) {
                                    Spacer(Modifier.height(12.dp))
                                    GlassActionButton(
                                        label = stringResource(R.string.install_now),
                                        onClick = { viewModel.installUpdate() },
                                        tone = GlassActionTone.Success
                                    )
                                }
                            }
                            else -> {
                                GlassActionButton(
                                    label = if (uiState.updateState == SettingsViewModel.UpdateState.CHECKING)
                                        stringResource(R.string.checking_for_updates)
                                    else stringResource(R.string.check_for_updates),
                                    onClick = { viewModel.checkForUpdate() },
                                    enabled = uiState.updateState != SettingsViewModel.UpdateState.CHECKING
                                )
                                Spacer(Modifier.height(6.dp))
                                when (uiState.updateState) {
                                    SettingsViewModel.UpdateState.UP_TO_DATE -> Text(
                                        stringResource(R.string.you_have_latest),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SubtleGray
                                    )
                                    SettingsViewModel.UpdateState.ERROR -> Text(
                                        stringResource(R.string.update_check_failed_format, uiState.updateError),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ErrorRed
                                    )
                                    else -> {}
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.clear_all_records)) },
            text = { Text(stringResource(R.string.clear_all_records_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAllRecords()
                    showClearDialog = false
                }) { Text(stringResource(R.string.clear)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    uiState.notificationTestResult?.let { r ->
        AlertDialog(
            onDismissRequest = { viewModel.clearNotificationTestResult() },
            title = { Text(stringResource(R.string.notification_test_result_title)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    if (r.error != null) {
                        Text(
                            stringResource(R.string.notification_test_error_format, r.error),
                            color = MaterialTheme.colorScheme.error
                        )
                    } else if (!r.permissionGranted) {
                        Text(stringResource(R.string.notification_test_no_permission))
                    } else {
                        Text(stringResource(R.string.notification_test_project_format, r.projectName))
                        Text(stringResource(R.string.notification_test_total_items_format, r.totalItemsInProject))
                        if (r.itemsWithUnparsableDates > 0) {
                            Text(stringResource(R.string.notification_test_bad_dates_format, r.itemsWithUnparsableDates))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(
                                R.string.notification_test_tier_format,
                                stringResource(R.string.days_away_today),
                                r.tierCounts[0] ?: 0
                            )
                        )
                        Text(
                            stringResource(
                                R.string.notification_test_tier_format,
                                stringResource(R.string.days_away_3),
                                r.tierCounts[3] ?: 0
                            )
                        )
                        Text(
                            stringResource(
                                R.string.notification_test_tier_format,
                                stringResource(R.string.days_away_7),
                                r.tierCounts[7] ?: 0
                            )
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.notification_test_posted_format, r.notificationsPosted),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        if (r.notificationsPosted > 0) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.notification_test_check_shade),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.clearNotificationTestResult() }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }
}

@Composable
private fun LanguageOptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    GlassSelectableOption(
        label = label,
        selected = selected,
        onClick = onClick
    )
}

@Composable
private fun ThemePreviewSwatch(mode: String, selected: Boolean) {
    val previewColor = when (mode) {
        "dark" -> Color(0xFF142630)
        "light" -> Color(0xFFF5FBFE)
        else -> SurfaceVariant
    }
    val borderColor = if (selected) CyanAccent else SubtleGray.copy(alpha = 0.6f)
    Box(
        modifier = Modifier
            .width(36.dp)
            .height(24.dp)
            .background(previewColor, RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
    )
}

@Composable
private fun NotifInfoRowRemoved() {}
