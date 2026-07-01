package com.nearexpiry.manager.presentation.screens.scan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nearexpiry.manager.R
import com.nearexpiry.manager.domain.model.ExpiryItem
import com.nearexpiry.manager.utils.ExpiryDateUtils

/**
 * Shown when a scanned item already exists in the current project. Lists every
 * existing entry (expiry date + quantity) and lets the user, per entry, add to
 * or replace its quantity — or add the item under a new expiry date.
 */
@Composable
fun ExistingItemDialog(
    productName: String,
    entries: List<ExpiryItem>,
    onAddQty: (entryId: Long) -> Unit,
    onReplaceQty: (entryId: Long) -> Unit,
    onAddNewDate: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.existing_item_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (productName.isNotBlank()) {
                    Text(
                        productName,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(Modifier.height(4.dp))
                }
                Text(
                    stringResource(R.string.existing_item_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))

                entries.forEach { entry ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                stringResource(
                                    R.string.existing_entry_line,
                                    ExpiryDateUtils.toCsvDate(entry.expiryDate),
                                    formatQty(entry.quantity),
                                    entry.unit ?: ""
                                ),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { onAddQty(entry.id) }) {
                                    Text(stringResource(R.string.add_qty))
                                }
                                OutlinedButton(onClick = { onReplaceQty(entry.id) }) {
                                    Text(stringResource(R.string.replace_qty))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onAddNewDate) {
                Text(stringResource(R.string.add_new_expiry_date))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

private fun formatQty(q: Double): String =
    if (q == q.toLong().toDouble()) q.toLong().toString() else q.toString()
