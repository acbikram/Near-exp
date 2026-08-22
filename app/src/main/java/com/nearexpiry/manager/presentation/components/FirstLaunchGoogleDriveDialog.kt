package com.nearexpiry.manager.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nearexpiry.manager.R
import com.nearexpiry.manager.presentation.theme.CyanAccent

/** Optional third step after language and appearance on a new installation. */
@Composable
fun FirstLaunchGoogleDriveDialog(
    error: String?,
    onAddNow: () -> Unit,
    onSkip: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* user chooses Add Now or Skip */ },
        title = {
            Text(
                stringResource(R.string.google_drive_first_launch_title),
                style = MaterialTheme.typography.titleMedium.copy(color = CyanAccent)
            )
        },
        text = {
            Column {
                Text(
                    stringResource(R.string.google_drive_first_launch_description),
                    style = MaterialTheme.typography.bodySmall
                )
                if (!error.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onAddNow) {
                Text(stringResource(R.string.google_drive_add_now), color = CyanAccent)
            }
        },
        dismissButton = {
            TextButton(onClick = onSkip) {
                Text(stringResource(R.string.google_drive_skip))
            }
        }
    )
}
