package com.nearexpiry.manager.presentation.screens.recyclebin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.nearexpiry.manager.R
import com.nearexpiry.manager.utils.ExpiryDateUtils
import com.nearexpiry.manager.utils.LanguageManager
import com.nearexpiry.manager.utils.QuantityFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Recycle Bin: everything deleted in the last 30 days. Each entry can be
 * restored to its project or removed permanently; entries older than 30 days
 * are purged automatically on app start.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecycleBinScreen(
    navController: NavController,
    viewModel: RecycleBinViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val isArabic = LanguageManager.isArabic()

    val restoredTemplate = stringResource(R.string.bin_restored_count)
    val deletedTemplate = stringResource(R.string.bin_deleted_count)

    // One-shot result messages → snackbar.
    uiState.message?.let { msg ->
        LaunchedEffect(msg) {
            val text = when {
                msg.startsWith("restored:") ->
                    String.format(restoredTemplate, msg.removePrefix("restored:"))
                msg.startsWith("deleted:") ->
                    String.format(deletedTemplate, msg.removePrefix("deleted:"))
                else -> msg
            }
            snackbarHostState.showSnackbar(text)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.recycle_bin)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (uiState.entries.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.Delete, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.bin_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Text(
                            stringResource(R.string.bin_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    // Group by deletion batch: items deleted in one action share
                    // the exact same deletedAt timestamp.
                    val batches = uiState.entries.groupBy { it.deletedAt }
                    batches.forEach { (deletedAt, batchEntries) ->
                        if (batchEntries.size > 1) {
                            item(key = "batch-$deletedAt") {
                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            stringResource(
                                                R.string.bin_batch_header,
                                                batchEntries.size,
                                                SimpleDateFormat("dd-MMM-yy HH:mm", Locale.ENGLISH)
                                                    .format(Date(deletedAt))
                                            ),
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedButton(onClick = {
                                                viewModel.restore(batchEntries.map { it.id })
                                            }) {
                                                Icon(Icons.Default.Restore, contentDescription = null)
                                                Spacer(Modifier.width(4.dp))
                                                Text(stringResource(R.string.bin_restore_all))
                                            }
                                            OutlinedButton(onClick = {
                                                viewModel.askDeletePermanently(batchEntries.map { it.id })
                                            }) {
                                                Icon(Icons.Default.DeleteForever, contentDescription = null)
                                                Spacer(Modifier.width(4.dp))
                                                Text(stringResource(R.string.bin_delete_all))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        items(batchEntries, key = { it.id }) { entry ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                val displayName = if (isArabic) {
                                    entry.productNameArabic?.takeIf { it.isNotBlank() }
                                        ?: entry.productName?.takeIf { it.isNotBlank() }
                                } else {
                                    entry.productName?.takeIf { it.isNotBlank() }
                                        ?: entry.productNameArabic?.takeIf { it.isNotBlank() }
                                }
                                Text(
                                    displayName ?: (entry.itemCode ?: entry.barcode),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    stringResource(
                                        R.string.bin_entry_line,
                                        entry.itemCode?.takeIf { it.isNotBlank() } ?: entry.barcode,
                                        ExpiryDateUtils.toCsvDate(entry.expiryDate),
                                        formatQty(entry.quantity),
                                        entry.unit ?: ""
                                    ),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    stringResource(
                                        R.string.bin_entry_meta,
                                        entry.projectName,
                                        SimpleDateFormat("dd-MMM-yy HH:mm", Locale.ENGLISH)
                                            .format(Date(entry.deletedAt))
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { viewModel.restore(listOf(entry.id)) }) {
                                        Icon(Icons.Default.Restore, contentDescription = null)
                                        Spacer(Modifier.width(4.dp))
                                        Text(stringResource(R.string.bin_restore))
                                    }
                                    OutlinedButton(onClick = { viewModel.askDeletePermanently(listOf(entry.id)) }) {
                                        Icon(Icons.Default.DeleteForever, contentDescription = null)
                                        Spacer(Modifier.width(4.dp))
                                        Text(stringResource(R.string.bin_delete_forever))
                                    }
                                }
                            }
                        }
                    }
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }

    if (uiState.confirmDeleteIds.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelDelete() },
            title = { Text(stringResource(R.string.bin_confirm_title)) },
            text = { Text(stringResource(R.string.bin_confirm_body)) },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmDeletePermanently() }) {
                    Text(stringResource(R.string.bin_delete_forever))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDelete() }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

private fun formatQty(q: Double): String = QuantityFormatter.format(q)
