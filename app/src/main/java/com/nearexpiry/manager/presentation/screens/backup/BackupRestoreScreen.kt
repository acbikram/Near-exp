package com.nearexpiry.manager.presentation.screens.backup

import android.content.Context
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.nearexpiry.manager.R
import com.nearexpiry.manager.presentation.components.GlassActionButton
import com.nearexpiry.manager.presentation.components.GlassActionTone
import com.nearexpiry.manager.presentation.components.GlassSectionCard
import com.nearexpiry.manager.presentation.components.PriceTagPairingQrScannerDialog
import com.nearexpiry.manager.presentation.theme.CyanAccent
import com.nearexpiry.manager.presentation.theme.SubtleGray
import com.nearexpiry.manager.utils.GoogleDriveBackupManager
import com.nearexpiry.manager.utils.QuantityFormatter
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(
    navController: NavController,
    viewModel: BackupRestoreViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val operationSuccessMsg = stringResource(R.string.operation_successful)

    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.backupToUri(context, uri)
        }
    }

    val googleDriveSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.handleGoogleDriveSignIn(result.data)
    }
    val googleDriveAuthorizationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.handleGoogleDriveAuthorizationResult(result.data)
    }
    var showBackupDestinationDialog by remember { mutableStateOf(false) }
    var showGoogleDriveRestoreDialog by remember { mutableStateOf(false) }
    var showDeleteRecheckDialog by remember { mutableStateOf(false) }
    var selectedGoogleDriveBackup by remember { mutableStateOf<GoogleDriveBackupManager.DriveBackupFile?>(null) }
    var backupRestoreExpanded by rememberSaveable { mutableStateOf(false) }
    var googleDriveExpanded by rememberSaveable { mutableStateOf(false) }
    var catalogExpanded by rememberSaveable { mutableStateOf(false) }
    val backupRestoreHeaderInteraction = remember { MutableInteractionSource() }
    val googleDriveHeaderInteraction = remember { MutableInteractionSource() }
    val catalogHeaderInteraction = remember { MutableInteractionSource() }
    LaunchedEffect(uiState.googleDriveSwitchRequest) {
        if (uiState.googleDriveSwitchRequest > 0L) {
            googleDriveSignInLauncher.launch(viewModel.googleDriveSignInIntent())
        }
    }
    LaunchedEffect(uiState.googleDriveAuthorizationRequest) {
        if (uiState.googleDriveAuthorizationRequest > 0L) {
            viewModel.consumeGoogleDriveAuthorizationPendingIntent()?.let { pendingIntent ->
                googleDriveAuthorizationLauncher.launch(
                    IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                )
            }
        }
    }

    val backupAllLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.backupAllProjects(context, uri)
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.restoreSmart(context, uri)
        }
    }

    val catalogUpdateLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.updateCatalog(context, uri)
        }
    }

    val recheckExcelLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importRecheckExcel(context, uri)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.backup_restore)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                BackupExpandableSection(
                    title = stringResource(R.string.backup_restore),
                    summary = stringResource(R.string.backup_restore_section_hint),
                    expanded = backupRestoreExpanded,
                    interactionSource = backupRestoreHeaderInteraction,
                    onToggle = {
                        backupRestoreExpanded = !backupRestoreExpanded
                        if (backupRestoreExpanded) {
                            googleDriveExpanded = false
                            catalogExpanded = false
                        }
                    }
                ) {
                    GlassActionButton(
                        label = stringResource(R.string.backup_database),
                        onClick = {
                            backupRestoreExpanded = false
                            backupLauncher.launch("NearExpiry_backup_${System.currentTimeMillis()}.json")
                        },
                        enabled = !uiState.isLoading,
                        tone = GlassActionTone.Neutral
                    )
                    Spacer(Modifier.height(8.dp))
                    GlassActionButton(
                        label = stringResource(R.string.backup_all_projects),
                        onClick = {
                            backupRestoreExpanded = false
                            backupAllLauncher.launch("NearExpiry_all_projects_${System.currentTimeMillis()}.json")
                        },
                        enabled = !uiState.isLoading,
                        tone = GlassActionTone.Neutral
                    )
                    Spacer(Modifier.height(8.dp))
                    GlassActionButton(
                        label = stringResource(R.string.backup_now),
                        onClick = {
                            backupRestoreExpanded = false
                            showBackupDestinationDialog = true
                        },
                        enabled = !uiState.isLoading,
                        tone = GlassActionTone.Neutral
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(R.string.auto_backup_twice_daily_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    GlassActionButton(
                        label = stringResource(R.string.restore_database),
                        onClick = {
                            backupRestoreExpanded = false
                            restoreLauncher.launch(arrayOf("*/*"))
                        },
                        enabled = !uiState.isLoading,
                        tone = GlassActionTone.Neutral
                    )
                }

                val googleDriveHealth = when {
                    uiState.googleDriveUploadInProgress -> stringResource(R.string.google_drive_health_uploading)
                    uiState.googleDriveConsentRequired -> stringResource(R.string.google_drive_health_attention)
                    uiState.googleDriveLastUploadError.isNotBlank() -> stringResource(R.string.google_drive_health_attention)
                    uiState.googleDrivePendingBackupName.isNotBlank() -> stringResource(R.string.google_drive_health_waiting)
                    uiState.googleDriveLastSuccessTime > 0L -> stringResource(R.string.google_drive_health_up_to_date)
                    else -> stringResource(R.string.google_drive_health_no_backup)
                }
                BackupExpandableSection(
                    title = stringResource(R.string.google_drive_backup),
                    summary = if (uiState.googleDriveAccountEmail.isBlank()) {
                        stringResource(R.string.google_drive_section_disconnected)
                    } else {
                        stringResource(
                            R.string.google_drive_section_summary_format,
                            uiState.googleDriveAccountEmail,
                            googleDriveHealth
                        )
                    },
                    expanded = googleDriveExpanded,
                    interactionSource = googleDriveHeaderInteraction,
                    onToggle = {
                        googleDriveExpanded = !googleDriveExpanded
                        if (googleDriveExpanded) {
                            backupRestoreExpanded = false
                            catalogExpanded = false
                        }
                    }
                ) {
                    if (uiState.googleDriveAccountEmail.isBlank()) {
                        Text(
                            stringResource(R.string.google_drive_connect_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        GlassActionButton(
                            label = stringResource(R.string.google_drive_connect),
                            onClick = {
                                googleDriveExpanded = false
                                googleDriveSignInLauncher.launch(viewModel.googleDriveSignInIntent())
                            },
                            enabled = !uiState.isLoading,
                            tone = GlassActionTone.Neutral
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.google_drive_connected_format, uiState.googleDriveAccountEmail),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.google_drive_auto_enabled),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            googleDriveHealth,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = when {
                                uiState.googleDriveUploadInProgress -> MaterialTheme.colorScheme.primary
                                uiState.googleDriveConsentRequired -> MaterialTheme.colorScheme.error
                                uiState.googleDriveLastUploadError.isNotBlank() -> MaterialTheme.colorScheme.error
                                uiState.googleDrivePendingBackupName.isNotBlank() -> MaterialTheme.colorScheme.tertiary
                                uiState.googleDriveLastSuccessTime > 0L -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        if (uiState.googleDriveLastSuccessTime > 0L) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                stringResource(
                                    R.string.google_drive_last_success_format,
                                    uiState.googleDriveLastSuccessName.removePrefix("NearExpiry_auto_backup_").removeSuffix(".json"),
                                    formatDriveTimestamp(uiState.googleDriveLastSuccessTime)
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        GlassActionButton(
                            label = stringResource(R.string.backup_now),
                            onClick = {
                                googleDriveExpanded = false
                                viewModel.backupNowToGoogleDrive(context)
                            },
                            enabled = !uiState.isLoading,
                            tone = GlassActionTone.Neutral
                        )
                        Spacer(Modifier.height(8.dp))
                        GlassActionButton(
                            label = stringResource(R.string.google_drive_restore),
                            onClick = {
                                googleDriveExpanded = false
                                viewModel.loadGoogleDriveBackups()
                                showGoogleDriveRestoreDialog = true
                            },
                            enabled = !uiState.isLoading,
                            tone = GlassActionTone.Neutral
                        )
                        Spacer(Modifier.height(8.dp))
                        GlassActionButton(
                            label = stringResource(R.string.google_drive_switch),
                            onClick = {
                                googleDriveExpanded = false
                                viewModel.switchGoogleDriveAccount()
                            },
                            enabled = !uiState.isLoading,
                            tone = GlassActionTone.Neutral
                        )
                        Spacer(Modifier.height(8.dp))
                        GlassActionButton(
                            label = stringResource(R.string.google_drive_disconnect),
                            onClick = {
                                googleDriveExpanded = false
                                viewModel.disconnectGoogleDrive()
                            },
                            enabled = !uiState.isLoading,
                            tone = GlassActionTone.Neutral
                        )
                    }
                    if (uiState.googleDriveStatus.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            uiState.googleDriveStatus,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (uiState.googleDrivePendingBackupName.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.google_drive_upload_pending_format, uiState.googleDrivePendingBackupName),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    if (uiState.googleDriveLastUploadError.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.google_drive_upload_error_format, uiState.googleDriveLastUploadError),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (uiState.googleDriveConsentRequired) {
                        Spacer(Modifier.height(8.dp))
                        GlassActionButton(
                            label = stringResource(R.string.google_drive_grant_permission),
                            onClick = {
                                googleDriveExpanded = false
                                viewModel.requestGoogleDriveAuthorization()
                            },
                            enabled = !uiState.isLoading,
                            tone = GlassActionTone.Neutral
                        )
                    } else if (uiState.googleDrivePendingBackupName.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        GlassActionButton(
                            label = stringResource(R.string.retry),
                            onClick = {
                                googleDriveExpanded = false
                                viewModel.retryGoogleDriveUpload()
                            },
                            enabled = !uiState.isLoading,
                            tone = GlassActionTone.Neutral
                        )
                    }
                }

                val wifiBusy = uiState.wifiState == BackupRestoreViewModel.WifiCatalogState.DISCOVERING ||
                    uiState.wifiState == BackupRestoreViewModel.WifiCatalogState.DOWNLOADING
                BackupExpandableSection(
                    title = stringResource(R.string.catalog_section),
                    summary = stringResource(R.string.catalog_section_hint),
                    expanded = catalogExpanded,
                    interactionSource = catalogHeaderInteraction,
                    onToggle = {
                        catalogExpanded = !catalogExpanded
                        if (catalogExpanded) {
                            backupRestoreExpanded = false
                            googleDriveExpanded = false
                        }
                    }
                ) {
                    Text(
                        text = if (uiState.catalogCount > 0)
                            stringResource(R.string.catalog_status_count, uiState.catalogCount)
                        else
                            stringResource(R.string.catalog_status_empty),
                        style = MaterialTheme.typography.titleSmall,
                        color = if (uiState.catalogCount > 0)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.update_catalog_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = when (uiState.priceTagConnectionState) {
                            BackupRestoreViewModel.PriceTagConnectionState.NOT_PAIRED ->
                                stringResource(R.string.price_tag_connection_not_paired)
                            BackupRestoreViewModel.PriceTagConnectionState.QR_SCAN_READY ->
                                stringResource(R.string.price_tag_connection_qr_ready)
                            BackupRestoreViewModel.PriceTagConnectionState.CONFIRMING_PC ->
                                stringResource(R.string.price_tag_connection_confirming)
                            BackupRestoreViewModel.PriceTagConnectionState.PAIRING ->
                                stringResource(R.string.price_tag_connection_pairing)
                            BackupRestoreViewModel.PriceTagConnectionState.PAIRED ->
                                stringResource(
                                    R.string.price_tag_connection_paired_format,
                                    uiState.pairedPriceTagPcName
                                )
                            BackupRestoreViewModel.PriceTagConnectionState.TESTING ->
                                stringResource(R.string.price_tag_connection_testing)
                            BackupRestoreViewModel.PriceTagConnectionState.CONNECTION_FAILED ->
                                stringResource(R.string.price_tag_connection_failed)
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = if (uiState.priceTagConnectionState == BackupRestoreViewModel.PriceTagConnectionState.CONNECTION_FAILED)
                            MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    GlassActionButton(
                        label = stringResource(R.string.pair_price_tag_pc),
                        onClick = { viewModel.preparePriceTagQrScan() },
                        enabled = uiState.priceTagConnectionState != BackupRestoreViewModel.PriceTagConnectionState.PAIRING &&
                            uiState.priceTagConnectionState != BackupRestoreViewModel.PriceTagConnectionState.TESTING,
                        tone = GlassActionTone.Neutral
                    )
                    if (uiState.pairedPriceTagPcName.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        GlassActionButton(
                            label = stringResource(R.string.price_tag_test_connection),
                            onClick = { viewModel.testPairedPriceTagPc() },
                            enabled = uiState.priceTagConnectionState != BackupRestoreViewModel.PriceTagConnectionState.TESTING &&
                                uiState.priceTagConnectionState != BackupRestoreViewModel.PriceTagConnectionState.PAIRING,
                            tone = GlassActionTone.Neutral
                        )
                        Spacer(Modifier.height(8.dp))
                        GlassActionButton(
                            label = stringResource(R.string.price_tag_forget_pc),
                            onClick = { viewModel.forgetPairedPriceTagPc() },
                            enabled = uiState.priceTagConnectionState != BackupRestoreViewModel.PriceTagConnectionState.PAIRING,
                            tone = GlassActionTone.Destructive
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    GlassActionButton(
                        label = stringResource(R.string.update_catalog),
                        onClick = {
                            catalogExpanded = false
                            catalogUpdateLauncher.launch(arrayOf("*/*"))
                        },
                        enabled = !uiState.isLoading,
                        tone = GlassActionTone.Neutral
                    )
                    Spacer(Modifier.height(8.dp))
                    GlassActionButton(
                        label = when (uiState.wifiState) {
                            BackupRestoreViewModel.WifiCatalogState.DISCOVERING -> stringResource(R.string.wifi_searching)
                            BackupRestoreViewModel.WifiCatalogState.DOWNLOADING -> stringResource(R.string.wifi_downloading)
                            else -> stringResource(R.string.get_catalog_wifi)
                        },
                        onClick = {
                            catalogExpanded = false
                            viewModel.pullCatalogFromPc()
                        },
                        enabled = !uiState.isLoading && !wifiBusy,
                        tone = GlassActionTone.Neutral
                    )
                    if (wifiBusy) {
                        Spacer(Modifier.height(8.dp))
                        if (uiState.wifiState == BackupRestoreViewModel.WifiCatalogState.DOWNLOADING && uiState.wifiProgress > 0f) {
                            LinearProgressIndicator(
                                progress = { uiState.wifiProgress },
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                    if (uiState.wifiStatus.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            uiState.wifiStatus,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (uiState.wifiState == BackupRestoreViewModel.WifiCatalogState.ERROR)
                                MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    GlassActionButton(
                        label = stringResource(R.string.stock_recheck_file_excel),
                        onClick = {
                            catalogExpanded = false
                            recheckExcelLauncher.launch(
                                arrayOf(
                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                    "application/vnd.ms-excel",
                                    "application/octet-stream"
                                )
                            )
                        },
                        enabled = !uiState.isLoading,
                        tone = GlassActionTone.Neutral
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (uiState.recheckCodeCount > 0) {
                            stringResource(
                                R.string.recheck_file_selected_format,
                                uiState.recheckFileName.ifBlank { stringResource(R.string.recheck_excel_default_name) },
                                uiState.recheckCodeCount,
                                uiState.recheckSourceCodeRowCount
                            )
                        } else {
                            stringResource(R.string.recheck_file_select_description)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (uiState.recheckCodeCount > 0) {
                        Spacer(Modifier.height(8.dp))
                        GlassActionButton(
                            label = stringResource(R.string.recheck_file_delete),
                            onClick = { showDeleteRecheckDialog = true },
                            enabled = !uiState.isLoading,
                            tone = GlassActionTone.Destructive
                        )
                    }
                    if (uiState.recheckDamageExpiryItemCount > 0) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = stringResource(
                                R.string.recheck_damage_expiry_summary,
                                uiState.recheckDamageExpiryItemCount,
                                QuantityFormatter.format(uiState.recheckDamageExpiryTotal)
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier.width(200.dp),
                        elevation = CardDefaults.cardElevation(4.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator()
                            Text(stringResource(R.string.processing), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }

    if (uiState.error != null) {
        LaunchedEffect(uiState.error) {
            scope.launch {
                snackbarHostState.showSnackbar(uiState.error!!)
                viewModel.clearError()
            }
        }
    }

    if (uiState.success) {
        val catalogCount = uiState.catalogUpdateCount
        LaunchedEffect(Unit) {
            scope.launch {
                val message = when {
                    catalogCount != null ->
                        context.getString(R.string.update_catalog_result_format, catalogCount)
                    else -> operationSuccessMsg
                }
                snackbarHostState.showSnackbar(message)
                viewModel.resetSuccess()
            }
        }
    }

    if (showDeleteRecheckDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteRecheckDialog = false },
            title = { Text(stringResource(R.string.recheck_file_delete_title)) },
            text = { Text(stringResource(R.string.recheck_file_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteRecheckDialog = false
                    catalogExpanded = false
                    viewModel.deleteRecheckFile()
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteRecheckDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (uiState.priceTagConnectionState == BackupRestoreViewModel.PriceTagConnectionState.QR_SCAN_READY) {
        PriceTagPairingQrScannerDialog(
            onPayloadScanned = { payload -> viewModel.handlePriceTagQrScanned(payload) },
            onDismiss = { viewModel.dismissPriceTagPairing() }
        )
    }

    uiState.pendingPriceTagPairing?.takeIf {
        uiState.priceTagConnectionState == BackupRestoreViewModel.PriceTagConnectionState.CONFIRMING_PC
    }?.let { pairing ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissPriceTagPairing() },
            title = { Text(stringResource(R.string.price_tag_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.price_tag_confirm_message,
                        pairing.pcName,
                        pairing.host,
                        pairing.port
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmPriceTagPairing() }) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissPriceTagPairing() }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showBackupDestinationDialog) {
        var uploadToDrive by remember(uiState.googleDriveAccountEmail, uiState.googleDriveBackupEnabled) {
            mutableStateOf(uiState.googleDriveAccountEmail.isNotBlank() && uiState.googleDriveBackupEnabled)
        }
        AlertDialog(
            onDismissRequest = { showBackupDestinationDialog = false },
            title = { Text(stringResource(R.string.backup_now)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.backup_internal_always_hint))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = uploadToDrive,
                            onCheckedChange = { uploadToDrive = it },
                            enabled = uiState.googleDriveAccountEmail.isNotBlank()
                        )
                        Text(
                            if (uiState.googleDriveAccountEmail.isNotBlank())
                                stringResource(R.string.backup_google_drive_optional)
                            else stringResource(R.string.backup_google_drive_connect_first)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.backupNow(context, uploadToDrive)
                    showBackupDestinationDialog = false
                }) { Text(stringResource(R.string.backup_now)) }
            },
            dismissButton = {
                TextButton(onClick = { showBackupDestinationDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showGoogleDriveRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showGoogleDriveRestoreDialog = false },
            title = { Text(stringResource(R.string.google_drive_restore_pick_title)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        stringResource(R.string.google_drive_restore_safety_hint),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    if (!uiState.isLoading && uiState.googleDriveBackups.isEmpty()) {
                        Text(stringResource(R.string.google_drive_no_backups))
                    }
                    uiState.googleDriveBackups.forEach { backup ->
                        OutlinedButton(
                            onClick = { selectedGoogleDriveBackup = backup },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(backup.name.removePrefix("NearExpiry_auto_backup_").removeSuffix(".json"))
                                Text(
                                    stringResource(
                                        R.string.google_drive_restore_detail_format,
                                        formatDriveCreatedTime(backup.createdTime),
                                        Formatter.formatFileSize(context, backup.sizeBytes)
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showGoogleDriveRestoreDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    selectedGoogleDriveBackup?.let { backup ->
        AlertDialog(
            onDismissRequest = { selectedGoogleDriveBackup = null },
            title = { Text(stringResource(R.string.google_drive_restore_confirm_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(backup.name.removePrefix("NearExpiry_auto_backup_").removeSuffix(".json"))
                    Text(
                        stringResource(
                            R.string.google_drive_restore_detail_format,
                            formatDriveCreatedTime(backup.createdTime),
                            Formatter.formatFileSize(context, backup.sizeBytes)
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        stringResource(R.string.google_drive_restore_confirm_message),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    selectedGoogleDriveBackup = null
                    showGoogleDriveRestoreDialog = false
                    viewModel.restoreFromGoogleDrive(backup.id)
                }) { Text(stringResource(R.string.restore_database), color = CyanAccent) }
            },
            dismissButton = {
                TextButton(onClick = { selectedGoogleDriveBackup = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // ── Universal Restore: project picker + result ─────────────────────────
    if (uiState.showRestoreProjectPicker) {
        var newProjectName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { viewModel.cancelRestoreProjectPicker() },
            title = { Text(stringResource(R.string.restore_pick_project_title)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    Text(
                        stringResource(
                            R.string.restore_pick_project_hint,
                            uiState.pendingRestoreItems.size
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    uiState.restoreProjects.forEach { project ->
                        OutlinedButton(
                            onClick = { viewModel.confirmRestoreProject(project.id, null) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(project.name) }
                        Spacer(Modifier.height(4.dp))
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    OutlinedTextField(
                        value = newProjectName,
                        onValueChange = { newProjectName = it },
                        label = { Text(stringResource(R.string.restore_new_project_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = { viewModel.confirmRestoreProject(null, newProjectName) },
                        enabled = newProjectName.trim().isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.restore_create_project)) }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { viewModel.cancelRestoreProjectPicker() }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    uiState.recheckImportResult?.let { result ->
        LaunchedEffect(result) {
            scope.launch {
                snackbarHostState.showSnackbar(result)
                viewModel.clearRecheckImportResult()
            }
        }
    }

    uiState.internalBackupName?.let { name ->
        LaunchedEffect(name) {
            scope.launch {
                snackbarHostState.showSnackbar(
                    context.getString(R.string.backup_internal_done, name)
                )
                viewModel.clearInternalBackupName()
            }
        }
    }

    uiState.restoreResult?.let { result ->
        LaunchedEffect(result) {
            scope.launch {
                // Encoded as "new:X:merged:Y:qty:Z:skipped:S".
                val parts = result.split(":")
                val text = when {
                    parts.size >= 8 && (parts[7].toIntOrNull() ?: 0) > 0 ->
                        context.getString(
                            R.string.restore_result_skipped_format,
                            parts[1], parts[3], parts[5], parts[7]
                        )
                    parts.size >= 6 ->
                        context.getString(
                            R.string.restore_result_format,
                            parts[1], parts[3], parts[5]
                        )
                    else -> result
                }
                snackbarHostState.showSnackbar(text)
                viewModel.clearRestoreResult()
            }
        }
    }
}

@Composable
private fun BackupExpandableSection(
    title: String,
    summary: String,
    expanded: Boolean,
    interactionSource: MutableInteractionSource,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    GlassSectionCard(
        selected = expanded,
        interactionSource = interactionSource
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onToggle
                    )
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = CyanAccent,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall.copy(color = SubtleGray)
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = CyanAccent
                )
            }
            if (expanded) {
                Spacer(Modifier.height(12.dp))
                content()
            }
        }
    }
}

private val driveTimestampFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")

private fun formatDriveTimestamp(timestampMillis: Long): String = runCatching {
    Instant.ofEpochMilli(timestampMillis)
        .atZone(ZoneId.systemDefault())
        .format(driveTimestampFormatter)
}.getOrDefault("")

private fun formatDriveCreatedTime(createdTime: String): String = runCatching {
    Instant.parse(createdTime)
        .atZone(ZoneId.systemDefault())
        .format(driveTimestampFormatter)
}.getOrDefault(createdTime.ifBlank { "—" })
