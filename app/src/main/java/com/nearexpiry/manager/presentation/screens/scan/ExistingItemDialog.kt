package com.nearexpiry.manager.presentation.screens.scan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nearexpiry.manager.R
import com.nearexpiry.manager.domain.model.ExpiryItem
import com.nearexpiry.manager.presentation.theme.OrangeAccent
import com.nearexpiry.manager.presentation.theme.YellowAccent
import com.nearexpiry.manager.utils.ExpiryDateUtils
import com.nearexpiry.manager.utils.QuantityFormatter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Shown when a scanned item already exists in the current project. Lists every
 * existing entry (expiry date + quantity) and lets the user, per entry, add to
 * or replace its quantity — or add the item under a new expiry date.
 */
@Composable
fun ExistingItemDialog(
    productName: String?,
    itemCode: String?,
    entries: List<ExpiryItem>,
    onAddQty: (entryId: Long) -> Unit,
    onReplaceQty: (entryId: Long) -> Unit,
    onAddNewDate: () -> Unit,
    /** Stock entries share one inventory line and therefore never offer a new expiry date. */
    showExpiryActions: Boolean = true,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.existing_item_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                val displayedItemCode = itemCode?.takeIf { it.isNotBlank() }
                    ?: entries.firstOrNull()?.itemCode?.takeIf { it.isNotBlank() }
                    ?: entries.firstOrNull()?.barcode
                if (!displayedItemCode.isNullOrBlank()) {
                    Text(
                        text = stringResource(R.string.item_code_format, displayedItemCode),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = OrangeAccent
                    )
                    Spacer(Modifier.height(4.dp))
                }
                if (!productName.isNullOrBlank()) {
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
                                text = if (showExpiryActions) {
                                    stringResource(
                                        R.string.existing_entry_line,
                                        ExpiryDateUtils.toCsvDate(entry.expiryDate),
                                        QuantityFormatter.format(entry.quantity),
                                        entry.unit ?: ""
                                    )
                                } else {
                                    "Current quantity: ${QuantityFormatter.format(entry.quantity)}${entry.unit?.let { " $it" }.orEmpty()}"
                                },
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.last_scanned_format, formatTimestamp(entry.updatedAt)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { onAddQty(entry.id) },
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = YellowAccent,
                                        contentColor = Color(0xFF201A00)
                                    )
                                ) {
                                    Text(stringResource(R.string.add_qty), fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = { onReplaceQty(entry.id) },
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = YellowAccent,
                                        contentColor = Color(0xFF201A00)
                                    )
                                ) {
                                    Text(stringResource(R.string.replace_qty), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (showExpiryActions) {
                TextButton(onClick = onAddNewDate) {
                    Text(stringResource(R.string.add_new_expiry_date))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

private fun formatTimestamp(timestampMillis: Long): String =
    Instant.ofEpochMilli(timestampMillis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
