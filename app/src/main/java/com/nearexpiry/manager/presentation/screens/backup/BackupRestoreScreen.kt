package com.nearexpiry.manager.presentation.screens.backup

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
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

    val csvImportLauncher = rememberLauncherForActivityResult(
        // text/csv isn't always registered as a MIME type handler on Android,
        // so accept any file and let CsvImporter validate the contents.
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importCsv(context, uri)
        }
    }

    val catalogUpdateLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.updateCatalog(context, uri)
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
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = { backupLauncher.launch("NearExpiry_backup_${System.currentTimeMillis()}.json") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isLoading
                ) {
                    Text(stringResource(R.string.backup_database))
                }
                Button(
                    onClick = { backupAllLauncher.launch("NearExpiry_all_projects_${System.currentTimeMillis()}.json") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isLoading
                ) {
                    Text(stringResource(R.string.backup_all_projects))
                }
                Button(
                    onClick = { restoreLauncher.launch(arrayOf("application/json")) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isLoading
                ) {
                    Text(stringResource(R.string.restore_database))
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Text(
                    stringResource(R.string.import_csv_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = { csvImportLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isLoading
                ) {
                    Text(stringResource(R.string.import_csv))
                }

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
                Button(
                    onClick = { catalogUpdateLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isLoading
                ) {
                    Text(stringResource(R.string.update_catalog))
                }

                // ── Get latest catalog from PC over WiFi ─────────────────────
                val wifiBusy = uiState.wifiState == BackupRestoreViewModel.WifiCatalogState.DISCOVERING ||
                    uiState.wifiState == BackupRestoreViewModel.WifiCatalogState.DOWNLOADING
                Button(
                    onClick = { viewModel.pullCatalogFromPc() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isLoading && !wifiBusy
                ) {
                    Text(
                        when (uiState.wifiState) {
                            BackupRestoreViewModel.WifiCatalogState.DISCOVERING -> stringResource(R.string.wifi_searching)
                            BackupRestoreViewModel.WifiCatalogState.DOWNLOADING -> stringResource(R.string.wifi_downloading)
                            else -> stringResource(R.string.get_catalog_wifi)
                        }
                    )
                }
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
        val csvResult = uiState.csvImportResult
        val catalogCount = uiState.catalogUpdateCount
        LaunchedEffect(Unit) {
            scope.launch {
                val message = when {
                    catalogCount != null ->
                        context.getString(R.string.update_catalog_result_format, catalogCount)
                    csvResult != null -> {
                        val added = csvResult.imported.size - csvResult.merged
                        when {
                            csvResult.skipped > 0 -> context.getString(
                                R.string.import_csv_result_detail_format,
                                added,
                                csvResult.merged,
                                csvResult.skipped,
                                csvResult.skippedBadDate,
                                csvResult.skippedBadQty,
                                csvResult.skippedMissingPosCode
                            )
                            else -> context.getString(
                                R.string.import_csv_result_format,
                                added,
                                csvResult.merged
                            )
                        }
                    }
                    else -> operationSuccessMsg
                }
                snackbarHostState.showSnackbar(message)
                viewModel.resetSuccess()
            }
        }
    }
}
