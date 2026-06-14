package com.nearexpiry.manager.presentation.screens.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }

    // ── Notification permission (Android 13+) ────────────────────────────────
    var hasNotifPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            else true
        )
    }
    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotifPermission = granted
        if (granted) {
            ExpiryNotificationWorker.schedule(context)
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

            // ── Expiry Notifications ─────────────────────────────────────────
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    shape  = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.expiry_notifications),
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = CyanAccent,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.notifications_description),
                            style = MaterialTheme.typography.bodySmall.copy(color = SubtleGray)
                        )
                        Spacer(Modifier.height(12.dp))

                        NotifInfoRow(
                            label = stringResource(R.string.days_away_15),
                            sublabel = stringResource(R.string.soft_reminder),
                            color = GreenAccent
                        )
                        Spacer(Modifier.height(6.dp))
                        NotifInfoRow(
                            label = stringResource(R.string.days_away_7),
                            sublabel = stringResource(R.string.soft_reminder),
                            color = OrangeAccent
                        )
                        Spacer(Modifier.height(6.dp))
                        NotifInfoRow(
                            label = stringResource(R.string.days_away_3),
                            sublabel = stringResource(R.string.urgent_alert_desc),
                            color = ErrorRed
                        )

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotifPermission) {
                            Spacer(Modifier.height(12.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                            Spacer(Modifier.height(12.dp))
                            Text(
                                stringResource(R.string.notif_permission_required),
                                style = MaterialTheme.typography.bodySmall.copy(color = ErrorRed)
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CyanAccent.copy(alpha = 0.15f),
                                    contentColor = CyanAccent
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(stringResource(R.string.grant_notification_permission))
                            }
                        } else if (hasNotifPermission) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.notifications_enabled),
                                style = MaterialTheme.typography.bodySmall.copy(color = GreenAccent)
                            )
                        }
                    }
                }
            }

            // ── Scan Settings ──────────────────────────────────────────────────
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    shape  = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.scan_settings),
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = CyanAccent,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    stringResource(R.string.beep_on_scan),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                                Text(
                                    stringResource(R.string.beep_on_scan_subtitle),
                                    style = MaterialTheme.typography.bodySmall.copy(color = SubtleGray)
                                )
                            }
                            Switch(
                                checked = uiState.scanSound,
                                onCheckedChange = viewModel::toggleScanSound,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = GreenAccent,
                                    checkedTrackColor = GreenAccent.copy(alpha = 0.4f)
                                )
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    stringResource(R.string.vibrate_on_scan),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                                Text(
                                    stringResource(R.string.vibrate_on_scan_subtitle),
                                    style = MaterialTheme.typography.bodySmall.copy(color = SubtleGray)
                                )
                            }
                            Switch(
                                checked = uiState.vibration,
                                onCheckedChange = viewModel::toggleVibration,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = GreenAccent,
                                    checkedTrackColor = GreenAccent.copy(alpha = 0.4f)
                                )
                            )
                        }
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
private fun NotifInfoRow(label: String, sublabel: String, color: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(label, style = MaterialTheme.typography.bodyMedium.copy(color = color))
            Text(sublabel, style = MaterialTheme.typography.bodySmall.copy(color = SubtleGray))
        }
    }
}
