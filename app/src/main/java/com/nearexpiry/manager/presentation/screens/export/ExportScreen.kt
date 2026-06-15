package com.nearexpiry.manager.presentation.screens.export

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.nearexpiry.manager.R
import com.nearexpiry.manager.domain.model.ExpiryItem
import com.nearexpiry.manager.presentation.components.BottomNavigationBar
import com.nearexpiry.manager.presentation.theme.CyanAccent
import com.nearexpiry.manager.presentation.theme.GreenAccent
import com.nearexpiry.manager.presentation.theme.OrangeAccent
import com.nearexpiry.manager.presentation.theme.SubtleGray
import com.nearexpiry.manager.presentation.theme.SurfaceDark
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    navController: NavController,
    viewModel: ExportViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val exportSuccessMsg = stringResource(R.string.export_successful)
    val shareCsvLabel = stringResource(R.string.share_csv)

    val itemsToExport = uiState.itemsToExport
    val canExport = !uiState.isExporting && itemsToExport.isNotEmpty()

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            viewModel.exportToUri(context, uri)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.export_data)) }) },
        bottomBar = { BottomNavigationBar(navController) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                item {
                    Text(stringResource(R.string.export_all_records_csv), style = MaterialTheme.typography.headlineSmall)
                }
                item {
                    Text(
                        stringResource(R.string.total_records_count_format, uiState.totalRecords),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                // ── Export Selected Data Only toggle ─────────────────────
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    stringResource(R.string.export_selected_data_only),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        color = CyanAccent,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Switch(
                                    checked = uiState.useSelectiveExport,
                                    onCheckedChange = viewModel::setUseSelectiveExport,
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = GreenAccent,
                                        checkedTrackColor = GreenAccent.copy(alpha = 0.4f)
                                    )
                                )
                            }

                            if (uiState.useSelectiveExport) {
                                Spacer(Modifier.height(12.dp))

                                // ── Mode selector ──────────────────────────
                                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                    SegmentedButton(
                                        selected = uiState.exportMode == ExportMode.BY_FILTER,
                                        onClick = { viewModel.setExportMode(ExportMode.BY_FILTER) },
                                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                                    ) {
                                        Text(stringResource(R.string.export_mode_by_tag_type), style = MaterialTheme.typography.labelMedium)
                                    }
                                    SegmentedButton(
                                        selected = uiState.exportMode == ExportMode.SELECT_ITEMS,
                                        onClick = { viewModel.setExportMode(ExportMode.SELECT_ITEMS) },
                                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                                    ) {
                                        Text(stringResource(R.string.export_mode_select_items), style = MaterialTheme.typography.labelMedium)
                                    }
                                }

                                Spacer(Modifier.height(12.dp))

                                when (uiState.exportMode) {
                                    ExportMode.BY_FILTER -> ByFilterSection(uiState, viewModel)
                                    ExportMode.SELECT_ITEMS -> { /* item list rendered below as its own LazyColumn items */ }
                                }
                            }

                            Spacer(Modifier.height(12.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.export_record_count_format, itemsToExport.size),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = if (itemsToExport.isEmpty()) MaterialTheme.colorScheme.error else GreenAccent,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }

                // ── Select Items mode: checkable list of every record ─────
                if (uiState.useSelectiveExport && uiState.exportMode == ExportMode.SELECT_ITEMS) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.selectAllItemsForExport() },
                                modifier = Modifier.weight(1f)
                            ) { Text(stringResource(R.string.export_select_all)) }
                            OutlinedButton(
                                onClick = { viewModel.clearAllItemsForExport() },
                                modifier = Modifier.weight(1f)
                            ) { Text(stringResource(R.string.export_clear_all)) }
                        }
                    }
                    items(uiState.allItems, key = { it.id }) { selectItem ->
                        SelectableExportItemRow(
                            item = selectItem,
                            checked = selectItem.id in uiState.selectedItemIds,
                            onToggle = { viewModel.toggleItemSelection(selectItem.id) }
                        )
                    }
                }

                // ── Save / Share buttons ───────────────────────────────────
                item {
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = { saveLauncher.launch(viewModel.buildCsvFilename()) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = canExport
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.save_csv))
                    }
                }
                item {
                    Button(
                        onClick = { viewModel.shareAsCsv(context) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = canExport
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.share_csv))
                    }
                }
            }

            // Loading overlay
            if (uiState.isExporting) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier.width(200.dp),
                        elevation = CardDefaults.cardElevation(4.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator()
                            Text(stringResource(R.string.exporting), style = MaterialTheme.typography.bodyLarge)
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

    if (uiState.success) {
        LaunchedEffect(Unit) {
            scope.launch {
                snackbarHostState.showSnackbar(exportSuccessMsg)
                viewModel.resetSuccess()
            }
        }
    }

    uiState.shareFileUri?.let { uri ->
        LaunchedEffect(uri) {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(sendIntent, shareCsvLabel))
            viewModel.consumeShareFileUri()
        }
    }
}

/** Tag/Type chips + From/To date range pickers (AND-combined, empty = no restriction). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ByFilterSection(
    uiState: ExportViewModel.ExportUiState,
    viewModel: ExportViewModel
) {
    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker by remember { mutableStateOf(false) }
    val dateFmt = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd") }

    Text(
        stringResource(R.string.export_filter_by_type),
        style = MaterialTheme.typography.labelLarge.copy(color = SubtleGray)
    )
    Spacer(Modifier.height(6.dp))
    FlowRowChips(
        options = EXPORT_UNIT_CHIPS,
        selected = uiState.selectedUnits,
        onToggle = viewModel::toggleUnit
    )

    Spacer(Modifier.height(16.dp))
    Text(
        stringResource(R.string.export_date_range),
        style = MaterialTheme.typography.labelLarge.copy(color = SubtleGray)
    )
    Spacer(Modifier.height(6.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DateFieldButton(
            label = stringResource(R.string.export_from_date),
            date = uiState.dateFrom,
            formatter = dateFmt,
            onClick = { showFromPicker = true },
            onClear = { viewModel.setDateFrom(null) },
            modifier = Modifier.weight(1f)
        )
        DateFieldButton(
            label = stringResource(R.string.export_to_date),
            date = uiState.dateTo,
            formatter = dateFmt,
            onClick = { showToPicker = true },
            onClear = { viewModel.setDateTo(null) },
            modifier = Modifier.weight(1f)
        )
    }

    if (showFromPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = uiState.dateFrom?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showFromPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        viewModel.setDateFrom(
                            Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        )
                    }
                    showFromPicker = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showFromPicker = false }) { Text(stringResource(R.string.cancel)) }
            }
        ) { DatePicker(state = state) }
    }

    if (showToPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = uiState.dateTo?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showToPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        viewModel.setDateTo(
                            Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        )
                    }
                    showToPicker = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showToPicker = false }) { Text(stringResource(R.string.cancel)) }
            }
        ) { DatePicker(state = state) }
    }
}

@Composable
private fun DateFieldButton(
    label: String,
    date: LocalDate?,
    formatter: DateTimeFormatter,
    onClick: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(date?.format(formatter) ?: label, maxLines = 1)
        }
        if (date != null) {
            TextButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.export_clear_date), style = MaterialTheme.typography.labelSmall, color = SubtleGray)
            }
        }
    }
}

/** Simple wrapping row of multi-select FilterChips (avoids pulling in the experimental FlowRow API). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowChips(
    options: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(
                selected = option in selected,
                onClick = { onToggle(option) },
                label = {
                    Text(if (option == EXPORT_UNIT_OTHER) stringResource(R.string.export_unit_other) else option)
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = CyanAccent.copy(alpha = 0.2f),
                    selectedLabelColor = CyanAccent
                )
            )
        }
    }
}

@Composable
private fun SelectableExportItemRow(
    item: ExpiryItem,
    checked: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        onClick = onToggle,
        colors = CardDefaults.cardColors(
            containerColor = if (checked) CyanAccent.copy(alpha = 0.12f) else SurfaceDark
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(checkedColor = CyanAccent)
            )
            Spacer(Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = CyanAccent,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.expiry_format, item.expiryDate),
                        style = MaterialTheme.typography.bodySmall.copy(color = GreenAccent)
                    )
                    Text(
                        text = stringResource(
                            R.string.qty_format,
                            if (item.quantity % 1.0 == 0.0) item.quantity.toInt().toString() else item.quantity.toString()
                        ),
                        style = MaterialTheme.typography.bodySmall.copy(color = OrangeAccent)
                    )
                    if (item.unit != null) {
                        Text(
                            text = item.unit,
                            style = MaterialTheme.typography.bodySmall.copy(color = SubtleGray)
                        )
                    }
                }
            }
        }
    }
}
