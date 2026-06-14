package com.nearexpiry.manager.presentation.screens.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.nearexpiry.manager.R
import com.nearexpiry.manager.domain.model.ExpiryItem
import com.nearexpiry.manager.presentation.components.BottomNavigationBar
import com.nearexpiry.manager.presentation.navigation.Screen
import com.nearexpiry.manager.presentation.theme.CyanAccent
import com.nearexpiry.manager.presentation.theme.ErrorRed
import com.nearexpiry.manager.presentation.theme.GreenAccent
import com.nearexpiry.manager.presentation.theme.OrangeAccent
import com.nearexpiry.manager.presentation.theme.SubtleGray
import com.nearexpiry.manager.presentation.theme.SurfaceDark
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    navController: NavController,
    viewModel: HistoryViewModel = hiltViewModel(),
    initialFilter: String = "ALL",
    initialSort: String = "NEWEST"
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showOverflowMenu by remember { mutableStateOf(false) }

    // Apply the filter/sort passed from the dashboard on first composition
    LaunchedEffect(initialFilter, initialSort) {
        viewModel.applyInitialFilterAndSort(initialFilter, initialSort)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (uiState.selectionMode) {
                TopAppBar(
                    title = {
                        Text(
                            stringResource(R.string.selected_count_format, uiState.selectedIds.size),
                            style = MaterialTheme.typography.titleLarge.copy(
                                color = CyanAccent,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.exitSelectionMode() }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.exit_selection), tint = CyanAccent)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    actions = {
                        IconButton(onClick = { viewModel.selectAllVisible() }) {
                            Icon(Icons.Default.DoneAll, contentDescription = stringResource(R.string.select_all_visible), tint = CyanAccent)
                        }
                        IconButton(
                            onClick = { viewModel.requestDeleteSelected() },
                            enabled = uiState.selectedIds.isNotEmpty()
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.delete),
                                tint = if (uiState.selectedIds.isNotEmpty()) ErrorRed else SubtleGray
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            stringResource(R.string.history),
                            style = MaterialTheme.typography.titleLarge.copy(
                                color = CyanAccent,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    actions = {
                        IconButton(onClick = { viewModel.toggleSortOrder() }) {
                            Icon(Icons.Default.Sort, contentDescription = stringResource(R.string.sort), tint = CyanAccent)
                        }
                        FilterChip(
                            selected = uiState.filter != Filter.ALL,
                            onClick = { viewModel.cycleFilter() },
                            label = {
                                Text(
                                    uiState.filter.name.replace('_', ' '),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyanAccent.copy(alpha = 0.2f),
                                selectedLabelColor = CyanAccent
                            )
                        )
                        Box {
                            IconButton(onClick = { showOverflowMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options), tint = CyanAccent)
                            }
                            DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.select_items)) },
                                    onClick = {
                                        showOverflowMenu = false
                                        viewModel.enterSelectionMode()
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            stringResource(
                                                R.string.delete_items_in_filter_format,
                                                uiState.itemsInFilter.size
                                            ),
                                            color = ErrorRed
                                        )
                                    },
                                    onClick = {
                                        showOverflowMenu = false
                                        viewModel.requestDeleteFilter()
                                    },
                                    enabled = uiState.itemsInFilter.isNotEmpty()
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                )
            }
        },
        bottomBar = { BottomNavigationBar(navController) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::updateSearchQuery,
                label = { Text(stringResource(R.string.search_by_name_or_barcode)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = SubtleGray) },
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
            )
            // Sort indicator
            Text(
                text = stringResource(
                    R.string.sort_items_summary_format,
                    uiState.sortOrder.name.replace('_', ' '),
                    uiState.filteredItems.size
                ),
                style = MaterialTheme.typography.bodySmall.copy(color = SubtleGray),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.filteredItems, key = { it.id }) { item ->
                    HistoryItemCard(
                        item = item,
                        selectionMode = uiState.selectionMode,
                        isSelected = item.id in uiState.selectedIds,
                        onClick = {
                            if (uiState.selectionMode) {
                                viewModel.toggleItemSelection(item.id)
                            } else {
                                navController.navigate(Screen.Detail.passId(item.id))
                            }
                        },
                        onLongClick = {
                            if (!uiState.selectionMode) {
                                viewModel.enterSelectionMode(item.id)
                            }
                        }
                    )
                }
                if (uiState.filteredItems.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                stringResource(R.string.no_items_found),
                                style = MaterialTheme.typography.bodyLarge.copy(color = SubtleGray)
                            )
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

    // ── Confirm: delete selected items ────────────────────────────────────
    if (uiState.showDeleteSelectedConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteSelectedConfirm() },
            title = { Text(stringResource(R.string.delete_selected_items)) },
            text = {
                Text(stringResource(R.string.delete_selected_items_confirm_format, uiState.selectedIds.size))
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmDeleteSelected() }) {
                    Text(stringResource(R.string.delete), color = ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDeleteSelectedConfirm() }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // ── Confirm: delete everything in the current filter ─────────────────
    if (uiState.showDeleteFilterConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteFilterConfirm() },
            title = { Text(stringResource(R.string.delete_items_in_filter_format, uiState.itemsInFilter.size)) },
            text = {
                Text(
                    stringResource(
                        R.string.delete_filter_items_confirm_format,
                        uiState.itemsInFilter.size,
                        uiState.filter.name.replace('_', ' ')
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmDeleteFilter() }) {
                    Text(stringResource(R.string.delete), color = ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDeleteFilterConfirm() }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryItemCard(
    item: ExpiryItem,
    selectionMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) CyanAccent.copy(alpha = 0.12f) else SurfaceDark
        ),
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() },
                    colors = CheckboxDefaults.colors(checkedColor = CyanAccent)
                )
                Spacer(Modifier.width(4.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = CyanAccent,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
                if (item.itemCode != null) {
                    Text(
                        text = stringResource(R.string.item_code_format, item.itemCode),
                        style = MaterialTheme.typography.bodySmall.copy(color = SubtleGray)
                    )
                }
                if (item.productName != null || item.itemCode != null) {
                    Text(
                        text = stringResource(R.string.barcode_format, item.barcode),
                        style = MaterialTheme.typography.bodySmall.copy(color = SubtleGray)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.expiry_format, item.expiryDate),
                        style = MaterialTheme.typography.bodyMedium.copy(color = GreenAccent)
                    )
                    Text(
                        text = stringResource(
                            R.string.qty_format,
                            if (item.quantity % 1.0 == 0.0) item.quantity.toInt().toString() else item.quantity.toString()
                        ),
                        style = MaterialTheme.typography.bodyMedium.copy(color = OrangeAccent)
                    )
                }
                Text(
                    text = stringResource(R.string.scanned_format, formatTimestamp(item.createdAt)),
                    style = MaterialTheme.typography.bodySmall.copy(color = SubtleGray)
                )
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .format(LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault()))
}
