package com.nearexpiry.manager.presentation.screens.scan

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nearexpiry.manager.R

@Composable
fun DamageDuplicateDialog(
    onAddQuantity: () -> Unit,
    onKeepSeparate: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.same_item_same_carton)) },
        text = { Text(stringResource(R.string.same_item_same_carton_message)) },
        confirmButton = { Button(onClick = onAddQuantity) { Text(stringResource(R.string.add_qty)) } },
        dismissButton = { Button(onClick = onKeepSeparate) { Text(stringResource(R.string.keep_separate)) } }
    )
}
