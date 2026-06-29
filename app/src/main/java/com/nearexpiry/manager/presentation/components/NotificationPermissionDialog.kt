package com.nearexpiry.manager.presentation.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nearexpiry.manager.R

/**
 * Shown when notification permission is denied and the OS will no longer show
 * the system prompt (hard-denied). Guides the user to enable notifications in
 * system Settings. Re-appears on each launch until notifications are enabled.
 */
@Composable
fun NotificationPermissionDialog(
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.notif_perm_dialog_title)) },
        text = { Text(stringResource(R.string.notif_perm_dialog_body)) },
        confirmButton = {
            TextButton(onClick = onOpenSettings) {
                Text(stringResource(R.string.notif_perm_open_settings))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.notif_perm_later))
            }
        }
    )
}
