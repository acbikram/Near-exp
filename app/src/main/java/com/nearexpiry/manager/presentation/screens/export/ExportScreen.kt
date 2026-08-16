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
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Description
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.clickable
import com.nearexpiry.manager.utils.CompanyReportBuilder
import com.nearexpiry.manager.utils.QuantityFormatter
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
    val canExport = !uiState.isExporting && if (uiState.isStockMode) uiState.allItems.isNotEmpty() else itemsToExport.isNotEmpty()

    val saveMimeType = if (uiState.isStockMode) {
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    } else {
        "text/csv"
    }
    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(saveMimeType)
    ) { uri ->
        if (uri != null) {
            if (uiState.isStockMode) viewModel.exportStockReportToUri(context, uri)
            else viewModel.exportToUri(context, uri)
        }
    }

    Scaffold(
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
                        onClick = {
                            saveLauncher.launch(
                                if (uiState.isStockMode) viewModel.buildStockReportFilename()
                                else viewModel.buildCsvFilename()
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = canExport
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (uiState.isStockMode) "Save Stock Check Excel" else stringResource(R.string.save_csv))
                    }
                }
                item {
                    Button(
                        onClick = {
                            if (uiState.isStockMode) viewModel.generateStockReport(context)
                            else viewModel.shareAsCsv(context)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = canExport
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (uiState.isStockMode) "Share Stock Check Excel" else stringResource(R.string.share_csv))
                    }
                }
                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    if (uiState.isStockMode) {
                        Text(
                            text = "Stock Check Excel",
                            style = MaterialTheme.typography.titleMedium.copy(color = CyanAccent, fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Exports ${uiState.projectName} in the Recheck layout: scan order, POS code, description, UOM, and quantity.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                        Button(
                            onClick = { viewModel.generateStockReport(context) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = uiState.allItems.isNotEmpty() && !uiState.isExporting
                        ) {
                            Icon(Icons.Default.Description, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Export Stock Check Excel")
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { viewModel.generateStockReport(context, sendToPc = true) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = uiState.allItems.isNotEmpty() &&
                                !uiState.isExporting &&
                                uiState.sendToPcState != ExportViewModel.SendToPcState.SEARCHING &&
                                uiState.sendToPcState != ExportViewModel.SendToPcState.SENDING
                        ) {
                            Icon(Icons.Default.Computer, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                when (uiState.sendToPcState) {
                                    ExportViewModel.SendToPcState.SEARCHING -> stringResource(R.string.searching_pc)
                                    ExportViewModel.SendToPcState.SENDING -> stringResource(R.string.sending_to_pc)
                                    else -> "Send Stock Excel to PC (Wi-Fi)"
                                }
                            )
                        }
                    } else {
                        Text(
                            stringResource(R.string.company_report_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Button(
                            onClick = { viewModel.startCompanyReport(ExportViewModel.ReportDestination.SHARE) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Description, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.make_excel_file))
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { viewModel.startCompanyReport(ExportViewModel.ReportDestination.PC) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = uiState.sendToPcState != ExportViewModel.SendToPcState.SEARCHING &&
                                      uiState.sendToPcState != ExportViewModel.SendToPcState.SENDING
                        ) {
                            Icon(Icons.Default.Computer, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                when (uiState.sendToPcState) {
                                    ExportViewModel.SendToPcState.SEARCHING -> stringResource(R.string.searching_pc)
                                    ExportViewModel.SendToPcState.SENDING -> stringResource(R.string.sending_to_pc)
                                    else -> stringResource(R.string.send_to_pc)
                                }
                            )
                        }
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

    // ── Company report: share the generated .xlsm ──────────────────────────
    uiState.reportFileUri?.let { uri ->
        LaunchedEffect(uri) {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(sendIntent, context.getString(R.string.make_excel_file)))
            viewModel.consumeReportFileUri()
        }
    }

    uiState.reportSummary?.let { summary ->
        LaunchedEffect(summary) {
            scope.launch {
                snackbarHostState.showSnackbar(summary)
                viewModel.clearReportSummary()
            }
        }
    }

    // ── Send-to-PC result handling ─────────────────────────────────────────
    when (uiState.sendToPcState) {
        ExportViewModel.SendToPcState.SUCCESS -> {
            LaunchedEffect(uiState.sendToPcMessage) {
                scope.launch {
                    snackbarHostState.showSnackbar(uiState.sendToPcMessage)
                    viewModel.resetSendToPc()
                }
            }
        }
        ExportViewModel.SendToPcState.ERROR -> {
            LaunchedEffect(uiState.sendToPcMessage) {
                scope.launch {
                    snackbarHostState.showSnackbar(uiState.sendToPcMessage)
                    viewModel.resetSendToPc()
                }
            }
        }
        ExportViewModel.SendToPcState.NOT_CONNECTED -> {
            AlertDialog(
                onDismissRequest = { viewModel.resetSendToPc() },
                title = { Text(stringResource(R.string.pc_not_connected_title)) },
                text = { Text(stringResource(R.string.pc_not_connected_body)) },
                confirmButton = {
                    TextButton(onClick = { viewModel.resetSendToPc() }) {
                        Text(stringResource(R.string.ok))
                    }
                }
            )
        }
        else -> {}
    }

    // ── Branch ID prompt ───────────────────────────────────────────────────
    if (uiState.showBranchIdDialog) {
        var branchId by remember(uiState.lastBranchId) { mutableStateOf(uiState.lastBranchId) }
        AlertDialog(
            onDismissRequest = { viewModel.dismissBranchIdDialog() },
            title = { Text(stringResource(R.string.branch_id_title)) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.branch_id_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = branchId,
                        onValueChange = { branchId = it.filter { c -> c.isDigit() } },
                        label = { Text(stringResource(R.string.branch_id_label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.validateBranchAndPickMonths(branchId) },
                    enabled = branchId.isNotBlank()
                ) {
                    Text(stringResource(R.string.next))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissBranchIdDialog() }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // ── Month selection step ───────────────────────────────────────────────
    if (uiState.showMonthPicker) {
        val selected = remember(uiState.availableMonths) {
            mutableStateListOf<CompanyReportBuilder.YearMonth>()
        }
        AlertDialog(
            onDismissRequest = { viewModel.dismissMonthPicker() },
            title = { Text(stringResource(R.string.select_months_title)) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.select_months_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    uiState.availableMonths.forEach { ym ->
                        val checked = selected.contains(ym)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (checked) selected.remove(ym) else selected.add(ym)
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = {
                                    if (it) selected.add(ym) else selected.remove(ym)
                                }
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(ym.label())
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.generateForMonths(context, selected.toSet()) },
                    enabled = selected.isNotEmpty()
                ) {
                    Text(stringResource(R.string.generate))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissMonthPicker() }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
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
                            QuantityFormatter.format(item.quantity)
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
