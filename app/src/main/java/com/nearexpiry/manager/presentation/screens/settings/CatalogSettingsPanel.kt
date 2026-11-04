package com.nearexpiry.manager.presentation.screens.settings

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nearexpiry.manager.R
import com.nearexpiry.manager.presentation.components.GlassActionButton
import com.nearexpiry.manager.presentation.components.GlassActionTone
import com.nearexpiry.manager.presentation.components.PriceTagPairingQrScannerDialog
import com.nearexpiry.manager.ui.components.BluetoothCatalogSyncDialog

@Composable
fun CatalogSettingsPanel(
    context: Context,
    viewModel: com.nearexpiry.manager.presentation.screens.backup.BackupRestoreViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    val updateLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.updateCatalog(context, uri)
    }
    val recheckLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importRecheckExcel(context, uri)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            if (state.catalogCount > 0) stringResource(R.string.catalog_status_count, state.catalogCount)
            else stringResource(R.string.catalog_status_empty),
            style = MaterialTheme.typography.titleSmall,
            color = if (state.catalogCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
        Text(stringResource(R.string.update_catalog_description), style = MaterialTheme.typography.bodySmall)
        Text(
            if (state.pairedPriceTagPcName.isBlank()) stringResource(R.string.price_tag_connection_not_paired)
            else stringResource(R.string.price_tag_connection_paired_format, state.pairedPriceTagPcName),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        GlassActionButton(
            label = stringResource(R.string.pair_price_tag_pc),
            onClick = viewModel::preparePriceTagQrScan,
            enabled = !state.isLoading,
            tone = GlassActionTone.Neutral
        )
        if (state.pairedPriceTagPcName.isNotBlank()) {
            GlassActionButton(
                label = stringResource(R.string.price_tag_test_connection),
                onClick = viewModel::testPairedPriceTagPc,
                enabled = !state.isLoading,
                tone = GlassActionTone.Neutral
            )
            GlassActionButton(
                label = stringResource(R.string.price_tag_forget_pc),
                onClick = viewModel::forgetPairedPriceTagPc,
                enabled = !state.isLoading,
                tone = GlassActionTone.Destructive
            )
        }
        GlassActionButton(
            label = stringResource(R.string.update_catalog),
            onClick = { updateLauncher.launch(arrayOf("*/*")) },
            enabled = !state.isLoading,
            tone = GlassActionTone.Neutral
        )
        GlassActionButton(
            label = stringResource(R.string.get_catalog_wifi),
            onClick = viewModel::pullCatalogFromPc,
            enabled = !state.isLoading && state.wifiState != com.nearexpiry.manager.presentation.screens.backup.BackupRestoreViewModel.WifiCatalogState.DISCOVERING && state.wifiState != com.nearexpiry.manager.presentation.screens.backup.BackupRestoreViewModel.WifiCatalogState.DOWNLOADING,
            tone = GlassActionTone.Neutral
        )
        if (state.wifiState == com.nearexpiry.manager.presentation.screens.backup.BackupRestoreViewModel.WifiCatalogState.DISCOVERING || state.wifiState == com.nearexpiry.manager.presentation.screens.backup.BackupRestoreViewModel.WifiCatalogState.DOWNLOADING) {
            LinearProgressIndicator(progress = { state.wifiProgress }, modifier = Modifier.fillMaxWidth())
        }
        if (state.wifiStatus.isNotBlank()) Text(state.wifiStatus, style = MaterialTheme.typography.bodySmall, color = if (state.wifiState == com.nearexpiry.manager.presentation.screens.backup.BackupRestoreViewModel.WifiCatalogState.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        GlassActionButton(
            label = stringResource(R.string.bluetooth_catalog_sync_title),
            onClick = viewModel::openBluetoothCatalogSync,
            enabled = !state.isLoading && !state.bluetoothCatalogBusy,
            tone = GlassActionTone.Neutral
        )
        GlassActionButton(
            label = stringResource(R.string.stock_recheck_file_excel),
            onClick = { recheckLauncher.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.ms-excel", "application/octet-stream")) },
            enabled = !state.isLoading,
            tone = GlassActionTone.Neutral
        )
        Text(
            if (state.recheckCodeCount > 0) stringResource(R.string.recheck_file_selected_format, state.recheckFileName.ifBlank { stringResource(R.string.recheck_excel_default_name) }, state.recheckCodeCount, state.recheckSourceCodeRowCount)
            else stringResource(R.string.recheck_file_select_description),
            style = MaterialTheme.typography.bodySmall
        )
        if (state.recheckCodeCount > 0) {
            GlassActionButton(label = stringResource(R.string.recheck_file_delete), onClick = { showDeleteDialog = true }, enabled = !state.isLoading, tone = GlassActionTone.Destructive)
        }
    }

    if (state.priceTagConnectionState == com.nearexpiry.manager.presentation.screens.backup.BackupRestoreViewModel.PriceTagConnectionState.QR_SCAN_READY) {
        PriceTagPairingQrScannerDialog(onPayloadScanned = viewModel::handlePriceTagQrScanned, onDismiss = viewModel::dismissPriceTagPairing)
    }
    state.pendingPriceTagPairing?.let { pairing ->
        if (state.priceTagConnectionState == com.nearexpiry.manager.presentation.screens.backup.BackupRestoreViewModel.PriceTagConnectionState.CONFIRMING_PC) {
            AlertDialog(onDismissRequest = viewModel::dismissPriceTagPairing, title = { Text(stringResource(R.string.price_tag_confirm_title)) }, text = { Text(stringResource(R.string.price_tag_confirm_message, pairing.pcName, pairing.host, pairing.port)) }, confirmButton = { TextButton(onClick = viewModel::confirmPriceTagPairing) { Text(stringResource(R.string.confirm)) } }, dismissButton = { TextButton(onClick = viewModel::dismissPriceTagPairing) { Text(stringResource(R.string.cancel)) } })
        }
    }
    if (state.showBluetoothCatalogSyncDialog) {
        BluetoothCatalogSyncDialog(
            pairedDevices = state.bluetoothCatalogDevices,
            isBusy = state.bluetoothCatalogBusy,
            statusMessage = state.bluetoothCatalogError,
            isError = state.bluetoothCatalogResult == com.nearexpiry.manager.presentation.screens.backup.BackupRestoreViewModel.CatalogBluetoothResult.FAILURE,
            onDismiss = viewModel::closeBluetoothCatalogSync,
            onCancelTransfer = viewModel::cancelBluetoothCatalogTransfer,
            onRefresh = viewModel::refreshBluetoothCatalogDevices,
            onReceive = viewModel::receiveBluetoothCatalog,
            onSend = viewModel::sendBluetoothCatalog
        )
    }
    if (showDeleteDialog) {
        AlertDialog(onDismissRequest = { showDeleteDialog = false }, title = { Text(stringResource(R.string.recheck_file_delete_title)) }, text = { Text(stringResource(R.string.recheck_file_delete_message)) }, confirmButton = { TextButton(onClick = { showDeleteDialog = false; viewModel.deleteRecheckFile() }) { Text(stringResource(R.string.delete)) } }, dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.cancel)) } })
    }
}
