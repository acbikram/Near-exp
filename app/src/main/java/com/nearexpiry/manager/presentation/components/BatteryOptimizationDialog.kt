package com.nearexpiry.manager.presentation.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nearexpiry.manager.R

/**
 * Shown once (until granted or dismissed) asking the user to exempt the app
 * from battery optimization. Some phones (Honor/Huawei, Samsung, Xiaomi)
 * aggressively restrict background work otherwise, which can delay the daily
 * 8 AM expiry notifications even though they're scheduled correctly.
 */
@Composable
fun BatteryOptimizationDialog(
    onAllow: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.battery_opt_dialog_title)) },
        text = { Text(stringResource(R.string.battery_opt_dialog_body)) },
        confirmButton = {
            TextButton(onClick = onAllow) {
                Text(stringResource(R.string.battery_opt_allow))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.notif_perm_later))
            }
        }
    )
}
