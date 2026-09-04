package com.nearexpiry.manager.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nearexpiry.manager.R
import com.nearexpiry.manager.data.bluetooth.BluetoothTransferManager

@Composable
fun BluetoothProjectSyncDialog(
    projectName: String,
    pairedDevices: List<BluetoothTransferManager.PairedDevice>,
    isBusy: Boolean,
    statusMessage: String?,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onReceive: () -> Unit,
    onSend: (BluetoothTransferManager.PairedDevice) -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        title = { Text(stringResource(R.string.bluetooth_sync_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.bluetooth_sync_project_format, projectName),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = stringResource(R.string.bluetooth_sync_instruction),
                    style = MaterialTheme.typography.bodySmall
                )
                HorizontalDivider()
                OutlinedButton(
                    onClick = onReceive,
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.bluetooth_sync_receive))
                }
                Text(
                    text = stringResource(R.string.bluetooth_sync_send_to_device),
                    style = MaterialTheme.typography.labelLarge
                )
                if (pairedDevices.isEmpty()) {
                    Text(
                        text = stringResource(R.string.bluetooth_sync_no_devices),
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.height(150.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(pairedDevices, key = { it.address }) { device ->
                            Button(
                                onClick = { onSend(device) },
                                enabled = !isBusy,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(device.name)
                            }
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onRefresh, enabled = !isBusy) {
                        Text(stringResource(R.string.bluetooth_sync_refresh))
                    }
                }
                if (isBusy) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                        Text(stringResource(R.string.bluetooth_sync_working))
                    }
                }
                statusMessage?.let {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            OutlinedButton(onClick = onDismiss, enabled = !isBusy) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
