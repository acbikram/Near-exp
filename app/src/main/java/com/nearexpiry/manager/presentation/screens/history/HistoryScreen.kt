package com.nearexpiry.manager.presentation.screens.history

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.*
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.nearexpiry.manager.R
import com.nearexpiry.manager.domain.model.ExpiryItem
import com.nearexpiry.manager.presentation.components.ActiveProjectHeader
import com.nearexpiry.manager.presentation.components.BottomNavigationBar
import com.nearexpiry.manager.presentation.navigation.Screen
import com.nearexpiry.manager.presentation.theme.AppDimens
import com.nearexpiry.manager.presentation.theme.CyanAccent
import com.nearexpiry.manager.presentation.theme.ErrorRed
import com.nearexpiry.manager.presentation.theme.GreenAccent
import com.nearexpiry.manager.presentation.theme.OrangeAccent
import com.nearexpiry.manager.presentation.theme.SubtleGray
import com.nearexpiry.manager.presentation.theme.SurfaceDark
import com.nearexpiry.manager.presentation.theme.SurfaceVariant
import com.nearexpiry.manager.utils.QuantityFormatter
import com.nearexpiry.manager.utils.ExpiryDateUtils
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
    var showSortMenu by remember { mutableStateOf(false) }
    var showDateMenu by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showMonthPicker by remember { mutableStateOf(false) }

    // Apply the filter/sort passed from the dashboard on first composition
    LaunchedEffect(initialFilter, initialSort) {
        viewModel.applyInitialFilterAndSort(initialFilter, initialSort)
    }

    // First back-press in selection mode exits selection mode instead of
    // leaving the screen; a second back-press then navigates away normally.
    BackHandler(enabled = uiState.selectionMode) {
        viewModel.exitSelectionMode()
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
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                        IconButton(onClick = { viewModel.selectAllVisible() }) {
                            Icon(Icons.Default.DoneAll, contentDescription = stringResource(R.string.select_all_visible), tint = CyanAccent)
                        }
                        // Move Up/Down — only meaningful (and shown) while the list
                        // is sorted in scan order, matching Sr No.
                        if (uiState.sortOrder == SortOrder.NEWEST) {
                            IconButton(
                                onClick = { viewModel.moveSelectedUp() },
                                enabled = uiState.selectedIds.isNotEmpty()
                            ) {
                                Icon(
                                    Icons.Default.KeyboardArrowUp,
                                    contentDescription = stringResource(R.string.move_up),
                                    tint = if (uiState.selectedIds.isNotEmpty()) CyanAccent else SubtleGray
                                )
                            }
                            IconButton(
                                onClick = { viewModel.moveSelectedDown() },
                                enabled = uiState.selectedIds.isNotEmpty()
                            ) {
                                Icon(
                                    Icons.Default.KeyboardArrowDown,
                                    contentDescription = stringResource(R.string.move_down),
                                    tint = if (uiState.selectedIds.isNotEmpty()) CyanAccent else SubtleGray
                                )
                            }
                        }
                        // Copy to another project
                        IconButton(
                            onClick = { viewModel.requestProjectAction(HistoryViewModel.ProjectAction.COPY) },
                            enabled = uiState.selectedIds.isNotEmpty() && uiState.otherProjects.isNotEmpty()
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = stringResource(R.string.copy_to_project),
                                tint = if (uiState.selectedIds.isNotEmpty() && uiState.otherProjects.isNotEmpty()) CyanAccent else SubtleGray
                            )
                        }
                        // Move to another project
                        IconButton(
                            onClick = { viewModel.requestProjectAction(HistoryViewModel.ProjectAction.MOVE) },
                            enabled = uiState.selectedIds.isNotEmpty() && uiState.otherProjects.isNotEmpty()
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.DriveFileMove,
                                contentDescription = stringResource(R.string.move_to_project),
                                tint = if (uiState.selectedIds.isNotEmpty() && uiState.otherProjects.isNotEmpty()) CyanAccent else SubtleGray
                            )
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
            ActiveProjectHeader(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
            if (!uiState.isStockMode) {
                Text(
                    text = "Expiry status",
                    style = MaterialTheme.typography.labelLarge.copy(color = CyanAccent, fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HistoryFilterChip("All", Filter.ALL, uiState, viewModel)
                    HistoryFilterChip("Expired", Filter.EXPIRED, uiState, viewModel)
                    HistoryFilterChip("Today", Filter.TODAY, uiState, viewModel)
                    HistoryFilterChip("1-7 Days", Filter.ONE_TO_SEVEN, uiState, viewModel)
                    HistoryFilterChip("8-30 Days", Filter.EIGHT_TO_THIRTY, uiState, viewModel)
                    HistoryFilterChip("Later", Filter.LATER, uiState, viewModel)
                }
            }

            // ── Filter / sort controls ──────────────────────────────────────
            // Horizontally scrollable so it can never clip/overflow, regardless
            // of how many chips are shown (e.g. the "Reset to Scan Order" chip
            // only appears sometimes) or how narrow the screen is.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                        // By specific expiry date / month
                        Box {
                            IconButton(onClick = { showDateMenu = true }) {
                                Icon(
                                    Icons.Default.CalendarMonth,
                                    contentDescription = stringResource(R.string.filter_by_date_or_month),
                                    tint = if (uiState.specificDate != null || uiState.specificMonth != null) OrangeAccent else CyanAccent
                                )
                            }
                            DropdownMenu(
                                expanded = showDateMenu,
                                onDismissRequest = { showDateMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(if (uiState.isStockMode) "Filter by scan date" else stringResource(R.string.filter_by_expiry_date)) },
                                    leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                                    onClick = {
                                        showDateMenu = false
                                        showDatePicker = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (uiState.isStockMode) "Filter by scan month" else stringResource(R.string.filter_by_expiry_month)) },
                                    leadingIcon = { Icon(Icons.Default.CalendarMonth, contentDescription = null) },
                                    onClick = {
                                        showDateMenu = false
                                        showMonthPicker = true
                                    },
                                    enabled = uiState.availableMonths.isNotEmpty()
                                )
                                if (uiState.specificDate != null || uiState.specificMonth != null) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.clear_date_filter)) },
                                        leadingIcon = { Icon(Icons.Default.Close, contentDescription = null) },
                                        onClick = {
                                            showDateMenu = false
                                            viewModel.clearSpecificDateFilters()
                                        }
                                    )
                                }
                            }
                        }
                        Box {
                            AssistChip(
                                onClick = { showSortMenu = true },
                                leadingIcon = {
                                    Icon(Icons.Default.Sort, contentDescription = stringResource(R.string.sort), tint = CyanAccent)
                                },
                                label = {
                                    Text(
                                        text = stringResource(
                                            when (uiState.sortOrder) {
                                                SortOrder.NEWEST -> if (uiState.hasCustomSort) R.string.sort_custom else R.string.sort_newest
                                                SortOrder.OLDEST -> R.string.sort_oldest
                                                SortOrder.EXPIRY_DATE -> R.string.sort_expiry_date
                                                SortOrder.QUANTITY -> R.string.sort_quantity
                                                SortOrder.ITEM_CODE_ASC -> R.string.sort_item_code_asc
                                                SortOrder.ITEM_CODE_DESC -> R.string.sort_item_code_desc
                                            }
                                        ),
                                        color = CyanAccent
                                    )
                                }
                            )
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(if (uiState.hasCustomSort) stringResource(R.string.sort_custom) else stringResource(R.string.sort_newest)) },
                                    onClick = {
                                        showSortMenu = false
                                        viewModel.setSortOrder(SortOrder.NEWEST)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.sort_oldest)) },
                                    onClick = {
                                        showSortMenu = false
                                        viewModel.setSortOrder(SortOrder.OLDEST)
                                    }
                                )
                                if (!uiState.isStockMode) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.sort_expiry_date)) },
                                        onClick = {
                                            showSortMenu = false
                                            viewModel.setSortOrder(SortOrder.EXPIRY_DATE)
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.sort_quantity)) },
                                    onClick = {
                                        showSortMenu = false
                                        viewModel.setSortOrder(SortOrder.QUANTITY)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.sort_item_code_asc)) },
                                    onClick = {
                                        showSortMenu = false
                                        viewModel.setSortOrder(SortOrder.ITEM_CODE_ASC)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.sort_item_code_desc)) },
                                    onClick = {
                                        showSortMenu = false
                                        viewModel.setSortOrder(SortOrder.ITEM_CODE_DESC)
                                    }
                                )
                            }
                        }
                        // "Reset to Scan Order" — only relevant once the project
                        // has actually been manually reordered.
                        if (uiState.sortOrder == SortOrder.NEWEST && uiState.hasCustomSort) {
                            AssistChip(
                                onClick = { viewModel.resetToScanOrder() },
                                leadingIcon = {
                                    Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.reset_to_scan_order), tint = CyanAccent)
                                },
                                label = { Text(stringResource(R.string.reset_to_scan_order), color = CyanAccent) }
                            )
                        }
                        FilterChip(
                            selected = uiState.unitFilter != UnitFilter.ALL,
                            onClick = { viewModel.cycleUnitFilter() },
                            label = {
                                Text(
                                    if (uiState.unitFilter == UnitFilter.ALL)
                                        stringResource(R.string.unit_filter_all)
                                    else
                                        uiState.unitFilter.label,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = OrangeAccent.copy(alpha = 0.2f),
                                selectedLabelColor = OrangeAccent
                            )
                        )
            }

            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::updateSearchQuery,
                label = { Text(stringResource(R.string.search_by_name_or_barcode)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = SubtleGray) },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SurfaceDark,
                    unfocusedContainerColor = SurfaceDark
                )
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
            if (!uiState.isStockMode && uiState.filteredItems.isNotEmpty()) {
                ExpiryRiskTimeline(items = uiState.filteredItems)
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = AppDimens.ScreenPadding, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.filteredItems, key = { it.id }) { item ->
                    HistoryItemCard(
                        item = item,
                        srNo = uiState.srNoMap[item.id],
                        showExpiryStatus = !uiState.isStockMode,
                        modifier = Modifier.animateItemPlacement(),
                        selectionMode = uiState.selectionMode,
                        isSelected = item.id in uiState.selectedIds,
                        onClick = {
                            if (uiState.selectionMode) {
                                viewModel.toggleItemSelection(item.id)
                            } else {
                                viewModel.prepareItemNavigation()
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
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(top = 40.dp, bottom = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null, tint = SubtleGray, modifier = Modifier.size(34.dp))
                            Text("No matching products", style = MaterialTheme.typography.titleSmall, color = CyanAccent)
                            Text(
                                if (uiState.searchQuery.isBlank()) {
                                    if (uiState.isStockMode) "Scan catalog products to build this stock check."
                                    else "Try another expiry status or add products from Scan."
                                } else "Try a product name, item code, or barcode.",
                                style = MaterialTheme.typography.bodySmall,
                                color = SubtleGray
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = { navController.navigate(Screen.Scan.route) },
                                shape = MaterialTheme.shapes.small,
                                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = Color(0xFF003344))
                            ) {
                                Text(stringResource(R.string.scan))
                            }
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
    // ── "Can't move selected items together" popup (non-contiguous selection) ─
    if (uiState.showMoveBlockedMessage) {
        AlertDialog(
            onDismissRequest = { viewModel.clearMoveBlockedMessage() },
            title = { Text(stringResource(R.string.move_blocked_title)) },
            text = { Text(stringResource(R.string.move_blocked_message)) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearMoveBlockedMessage() }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }

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
                val filterLabel = when {
                    uiState.specificDate != null -> ExpiryDateUtils.toCsvDate(uiState.specificDate!!)
                    uiState.specificMonth != null ->
                        uiState.availableMonths.firstOrNull { it.key == uiState.specificMonth }?.label
                            ?: uiState.specificMonth!!
                    else -> uiState.filter.name.replace('_', ' ')
                }
                Text(
                    stringResource(
                        R.string.delete_filter_items_confirm_format,
                        uiState.itemsInFilter.size,
                        filterLabel
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

    // ── Copy/Move: target project picker ─────────────────────────────────
    if (uiState.projectActionMode != null && uiState.pendingTargetProjectId == null) {
        val isCopy = uiState.projectActionMode == HistoryViewModel.ProjectAction.COPY
        AlertDialog(
            onDismissRequest = { viewModel.dismissProjectAction() },
            title = {
                Text(
                    stringResource(
                        if (isCopy) R.string.copy_to_project else R.string.move_to_project
                    )
                )
            },
            text = {
                Column {
                    Text(
                        stringResource(R.string.select_target_project),
                        style = MaterialTheme.typography.bodyMedium.copy(color = SubtleGray)
                    )
                    Spacer(Modifier.height(8.dp))
                    uiState.otherProjects.forEach { project ->
                        Text(
                            text = project.name,
                            style = MaterialTheme.typography.titleSmall.copy(color = CyanAccent),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.onTargetProjectChosen(project.id) }
                                .padding(vertical = 12.dp)
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { viewModel.dismissProjectAction() }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // ── Copy/Move: Add vs Replace on duplicate collision ─────────────────
    uiState.pendingTargetProjectId?.let { targetId ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissProjectAction() },
            title = { Text(stringResource(R.string.duplicate_found_title)) },
            text = { Text(stringResource(R.string.duplicate_merge_prompt)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.performProjectAction(targetId, com.nearexpiry.manager.domain.model.MergeMode.ADD)
                }) {
                    Text(stringResource(R.string.merge_add))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.performProjectAction(targetId, com.nearexpiry.manager.domain.model.MergeMode.REPLACE)
                }) {
                    Text(stringResource(R.string.merge_replace))
                }
            }
        )
    }

    // ── Copy/Move result snackbar ────────────────────────────────────────
    uiState.copyMoveResult?.let { msg ->
        LaunchedEffect(msg) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearCopyMoveResult()
        }
    }

    // ── Undo-delete snackbar ─────────────────────────────────────────────
    uiState.undoDeleteItems?.let { _ ->
        val undoMsg = stringResource(R.string.deleted_count_format, uiState.undoDeleteCount)
        val undoAction = stringResource(R.string.undo)
        LaunchedEffect(uiState.undoDeleteItems) {
            val result = snackbarHostState.showSnackbar(
                message = undoMsg,
                actionLabel = undoAction,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoDelete()
            } else {
                viewModel.clearUndoDelete()
            }
        }
    }

    // ── By Expiry Date: date picker ──────────────────────────────────────
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val iso = java.time.Instant.ofEpochMilli(millis)
                            .atZone(java.time.ZoneOffset.UTC).toLocalDate().toString()
                        viewModel.setSpecificDate(iso)
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.cancel)) }
            }
        ) { DatePicker(state = datePickerState) }
    }

    // ── By Expiry Month: pick from months present in the data ────────────
    if (showMonthPicker) {
        AlertDialog(
            onDismissRequest = { showMonthPicker = false },
            title = { Text(stringResource(R.string.filter_by_expiry_month)) },
            text = {
                LazyColumn {
                    items(uiState.availableMonths, key = { it.key }) { month ->
                        Text(
                            text = month.label,
                            style = MaterialTheme.typography.titleSmall.copy(color = CyanAccent),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setSpecificMonth(month.key)
                                    showMonthPicker = false
                                }
                                .padding(vertical = 12.dp)
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showMonthPicker = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun HistoryFilterChip(
    label: String,
    filter: Filter,
    state: HistoryViewModel.HistoryUiState,
    viewModel: HistoryViewModel
) {
    FilterChip(
        selected = state.filter == filter && state.specificDate == null && state.specificMonth == null,
        onClick = { viewModel.setFilter(filter) },
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = CyanAccent.copy(alpha = 0.2f),
            selectedLabelColor = CyanAccent
        )
    )
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HistoryItemCard(
    item: ExpiryItem,
    srNo: Int? = null,
    /** False for Stock Mode, which has no expiry workflow or expiry visuals. */
    showExpiryStatus: Boolean = true,
    selectionMode: Boolean = false,
    isSelected: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    val today = java.time.LocalDate.now()
    val expiryDate = ExpiryDateUtils.parseOrNull(item.expiryDate)
    val (expiryLabel, expiryColor) = when {
        expiryDate?.isBefore(today) == true -> "EXPIRED" to ErrorRed
        expiryDate == today -> "TODAY" to OrangeAccent
        expiryDate != null && !expiryDate.isAfter(today.plusDays(7)) -> "1-7 DAYS" to OrangeAccent
        else -> "SAFE" to GreenAccent
    }
    val swipeState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled) {
                onLongClick()
                false
            } else {
                true
            }
        }
    )
    SwipeToDismissBox(
        state = swipeState,
        backgroundContent = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = CyanAccent.copy(alpha = 0.12f),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    text = "Select item",
                    color = CyanAccent,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 30.dp)
                )
            }
        }
    ) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) CyanAccent.copy(alpha = 0.12f) else SurfaceDark
        ),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(
            modifier = Modifier.padding(AppDimens.CardPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showExpiryStatus) {
                Box(
                    modifier = Modifier
                        .width(5.dp)
                        .height(84.dp)
                        .background(expiryColor, RoundedCornerShape(8.dp))
                )
                Spacer(Modifier.width(10.dp))
            }
            if (selectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() },
                    colors = CheckboxDefaults.colors(checkedColor = CyanAccent)
                )
                Spacer(Modifier.width(4.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                if (showExpiryStatus) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = expiryColor.copy(alpha = 0.16f)
                    ) {
                        Text(
                            text = expiryLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = expiryColor,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }
                if (srNo != null) {
                    Text(
                        text = stringResource(R.string.sr_no_format, srNo),
                        style = MaterialTheme.typography.labelSmall.copy(color = SubtleGray)
                    )
                }
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
                        style = MaterialTheme.typography.bodyMedium.copy(color = OrangeAccent)
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
                    if (showExpiryStatus) {
                        Text(
                            text = stringResource(R.string.expiry_format, item.expiryDate),
                            style = MaterialTheme.typography.bodyMedium.copy(color = expiryColor)
                        )
                    }
                    Text(
                        text = if (item.unit != null)
                            stringResource(
                                R.string.qty_unit_format,
                                QuantityFormatter.format(item.quantity),
                                item.unit
                            )
                        else
                            stringResource(
                                R.string.qty_format,
                                QuantityFormatter.format(item.quantity)
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
}

private fun formatTimestamp(timestamp: Long): String {
    return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .format(LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault()))
}
