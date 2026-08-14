package com.nearexpiry.manager.presentation.screens.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.nearexpiry.manager.R
import com.nearexpiry.manager.notifications.ExpiryNotificationWorker
import com.nearexpiry.manager.presentation.components.BottomNavigationBar
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
    var showClearDialog by remember { mutableStateOf(false) }

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
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.settings),
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = CyanAccent,
                            fontWeight = FontWeight.Bold
                        )
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
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
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    shape = RoundedCornerShape(12.dp)
                ) {
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
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    shape  = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.language),
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = CyanAccent,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.language_description),
                            style = MaterialTheme.typography.bodySmall.copy(color = SubtleGray)
                        )
                        Spacer(Modifier.height(12.dp))

                        LanguageOptionRow(
                            label = stringResource(R.string.language_system_default),
                            selected = currentLanguage == LanguageManager.AppLanguage.SYSTEM_DEFAULT,
                            onClick = {
                                currentLanguage = LanguageManager.AppLanguage.SYSTEM_DEFAULT
                                LanguageManager.setLanguage(LanguageManager.AppLanguage.SYSTEM_DEFAULT)
                            }
                        )
                        LanguageOptionRow(
                            label = stringResource(R.string.language_english),
                            selected = currentLanguage == LanguageManager.AppLanguage.ENGLISH,
                            onClick = {
                                currentLanguage = LanguageManager.AppLanguage.ENGLISH
                                LanguageManager.setLanguage(LanguageManager.AppLanguage.ENGLISH)
                            }
                        )
                        LanguageOptionRow(
                            label = stringResource(R.string.language_arabic),
                            selected = currentLanguage == LanguageManager.AppLanguage.ARABIC,
                            onClick = {
                                currentLanguage = LanguageManager.AppLanguage.ARABIC
                                LanguageManager.setLanguage(LanguageManager.AppLanguage.ARABIC)
                            }
                        )
                    }
                }
            }

            // ── Data Management ────────────────────────────────────────────────
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    shape  = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.data_management),
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = CyanAccent,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { navController.navigate(Screen.BackupRestore.route) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SurfaceVariant,
                                contentColor   = CyanAccent
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) { Text(stringResource(R.string.backup_restore)) }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { navController.navigate(Screen.RecycleBin.route) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SurfaceVariant,
                                contentColor   = CyanAccent
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) { Text(stringResource(R.string.recycle_bin)) }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { viewModel.testExpiryNotificationNow() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SurfaceVariant,
                                contentColor   = CyanAccent
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) { Text(stringResource(R.string.test_notification_now)) }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { showClearDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ErrorRed.copy(alpha = 0.15f),
                                contentColor   = ErrorRed
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) { Text(stringResource(R.string.clear_all_records)) }
                    }
                }
            }

            // ── App Updates ──────────────────────────────────────────────
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    shape  = RoundedCornerShape(12.dp)
                ) {
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
                                            "The installer will open automatically when the download finishes."
                                        SettingsViewModel.UpdateState.DOWNLOADED ->
                                            "Download complete. Opening the Android installer…"
                                        else -> "Preparing the update download…"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SubtleGray
                                )
                            }
                            else -> {
                                Button(
                                    onClick = { viewModel.checkForUpdate() },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = uiState.updateState != SettingsViewModel.UpdateState.CHECKING,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = SurfaceVariant,
                                        contentColor   = CyanAccent
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        if (uiState.updateState == SettingsViewModel.UpdateState.CHECKING)
                                            stringResource(R.string.checking_for_updates)
                                        else
                                            stringResource(R.string.check_for_updates)
                                    )
                                }
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
                        Text(stringResource(R.string.notification_test_tier_format, "0", r.tierCounts[0] ?: 0))
                        Text(stringResource(R.string.notification_test_tier_format, "3", r.tierCounts[3] ?: 0))
                        Text(stringResource(R.string.notification_test_tier_format, "7", r.tierCounts[7] ?: 0))
                        Text(stringResource(R.string.notification_test_tier_format, "15", r.tierCounts[15] ?: 0))
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = CyanAccent,
                unselectedColor = SubtleGray
            )
        )
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface
            )
        )
    }
}

@Composable
private fun NotifInfoRowRemoved() {}
