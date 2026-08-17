package com.nearexpiry.manager.presentation.screens.backup

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.nearexpiry.manager.R
import com.nearexpiry.manager.presentation.components.GlassActionButton
import com.nearexpiry.manager.presentation.components.GlassActionTone
import kotlinx.coroutines.launch

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
                GlassActionButton(
                    label = stringResource(R.string.backup_database),
                    onClick = { backupLauncher.launch("NearExpiry_backup_${System.currentTimeMillis()}.json") },
                    enabled = !uiState.isLoading
                )
                GlassActionButton(
                    label = stringResource(R.string.backup_all_projects),
                    onClick = { backupAllLauncher.launch("NearExpiry_all_projects_${System.currentTimeMillis()}.json") },
                    enabled = !uiState.isLoading
                )
                GlassActionButton(
                    label = stringResource(R.string.backup_now_internal),
                    onClick = { viewModel.backupNowToInternal(context) },
                    enabled = !uiState.isLoading,
                    tone = GlassActionTone.Neutral
                )
                Text(
                    stringResource(R.string.auto_backup_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                GlassActionButton(
                    label = stringResource(R.string.restore_database),
                    onClick = { restoreLauncher.launch(arrayOf("*/*")) },
                    enabled = !uiState.isLoading,
                    tone = GlassActionTone.Warning
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // ── Catalog status indicator ─────────────────────────────────
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

                Text(
                    stringResource(R.string.update_catalog_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                GlassActionButton(
                    label = stringResource(R.string.update_catalog),
                    onClick = { catalogUpdateLauncher.launch(arrayOf("*/*")) },
                    enabled = !uiState.isLoading
                )

                // ── Get latest catalog from PC over WiFi ─────────────────────
                val wifiBusy = uiState.wifiState == BackupRestoreViewModel.WifiCatalogState.DISCOVERING ||
                    uiState.wifiState == BackupRestoreViewModel.WifiCatalogState.DOWNLOADING
                GlassActionButton(
                    label = when (uiState.wifiState) {
                        BackupRestoreViewModel.WifiCatalogState.DISCOVERING -> stringResource(R.string.wifi_searching)
                        BackupRestoreViewModel.WifiCatalogState.DOWNLOADING -> stringResource(R.string.wifi_downloading)
                        else -> stringResource(R.string.get_catalog_wifi)
                    },
                    onClick = { viewModel.pullCatalogFromPc() },
                    enabled = !uiState.isLoading && !wifiBusy
                )
                if (wifiBusy) {
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
                    Text(
                        uiState.wifiStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (uiState.wifiState == BackupRestoreViewModel.WifiCatalogState.ERROR)
                            MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // ── Global Stock Recheck File ──────────────────────────────
                // The Catalog File validates every app project. This separate
                // list is applied only to valid catalog scans in Stock/Recheck
                // projects to decide whether a quantity check is required.
                Spacer(Modifier.height(4.dp))
                GlassActionButton(
                    label = stringResource(R.string.stock_recheck_file_excel),
                    onClick = {
                        recheckExcelLauncher.launch(
                            arrayOf(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                "application/vnd.ms-excel",
                                "application/octet-stream"
                            )
                        )
                    },
                    enabled = !uiState.isLoading
                )
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
