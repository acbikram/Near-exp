package com.nearexpiry.manager.presentation.screens.export

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Computer
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
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.nearexpiry.manager.R
import com.nearexpiry.manager.domain.model.ExpiryItem
import com.nearexpiry.manager.presentation.components.BottomNavigationBar
import com.nearexpiry.manager.ui.components.BluetoothProjectSyncDialog
import com.nearexpiry.manager.presentation.components.GlassActionButton
import com.nearexpiry.manager.presentation.components.GlassActionTone
import com.nearexpiry.manager.presentation.components.GlassSectionCard
import com.nearexpiry.manager.presentation.components.GlassSelectableOption
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
    val bluetoothPermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE
            )
        } else {
            emptyList()
        }
    }
    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = bluetoothPermissions.isEmpty() || bluetoothPermissions.all { permission ->
            grants[permission] == true ||
                ContextCompat.checkSelfPermission(context, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (granted) {
            viewModel.openBluetoothSync()
        } else {
            scope.launch {
                snackbarHostState.showSnackbar(stringResource(R.string.bluetooth_permission_required))
            }
        }
    }

    val itemsToExport = uiState.itemsToExport
    // Stock export always writes the entire selected Recheck template, while
    // normal projects export the current filtered/selected item list.
    val visibleExportRecordCount = if (uiState.isStockMode) {
        uiState.stockTemplateRecordCount
    } else {
        itemsToExport.size
    }
    // A selected Stock Recheck template can be exported even before any scan:
    // every template row then receives physical quantity zero.
    val canExport = !uiState.isExporting && if (uiState.isStockMode) true else itemsToExport.isNotEmpty()

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
        containerColor = MaterialTheme.colorScheme.background,
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
                    Text(
                        stringResource(R.string.export_all_records_csv),
                        style = MaterialTheme.typography.headlineSmall.copy(color = CyanAccent, fontWeight = FontWeight.Bold)
                    )
                }
                item {
                    Text(
                        stringResource(R.string.total_records_count_format, visibleExportRecordCount),
                        style = MaterialTheme.typography.bodyLarge.copy(color = SubtleGray)
                    )
                }

                // ── Export Selected Data Only toggle ─────────────────────
                item {
                    GlassSectionCard {
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
                                GlassSelectableOption(
                                    label = stringResource(R.string.export_mode_by_tag_type),
                                    selected = uiState.exportMode == ExportMode.BY_FILTER,
                                    onClick = { viewModel.setExportMode(ExportMode.BY_FILTER) },
                                    trailingContent = {}
                                )
                                Spacer(Modifier.height(8.dp))
                                GlassSelectableOption(
                                    label = stringResource(R.string.export_mode_select_items),
                                    selected = uiState.exportMode == ExportMode.SELECT_ITEMS,
                                    onClick = { viewModel.setExportMode(ExportMode.SELECT_ITEMS) },
                                    trailingContent = {}
                                )

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
                                stringResource(R.string.export_record_count_format, visibleExportRecordCount),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = if (visibleExportRecordCount == 0) MaterialTheme.colorScheme.error else GreenAccent,
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

                // ── Direct Bluetooth project sync ───────────────────────────
                item {
                    GlassActionButton(
                        label = stringResource(R.string.bluetooth_sync_title),
                        icon = Icons.Default.Bluetooth,
                        onClick = {
                            // Open the dialog immediately so a previously granted
                            // Nearby devices permission can never swallow the tap.
                            viewModel.openBluetoothSync()
                            val missingPermissions = bluetoothPermissions.filter {
                                ContextCompat.checkSelfPermission(
                                    context,
                                    it
                                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                            }
                            if (missingPermissions.isNotEmpty()) {
                                bluetoothPermissionLauncher.launch(missingPermissions.toTypedArray())
                            }
                        },
                        tone = GlassActionTone.Neutral
                    )
                }

                // ── Save / Share buttons ───────────────────────────────────
                item {
                    Spacer(Modifier.height(4.dp))
                    GlassActionButton(
                        label = if (uiState.isStockMode) stringResource(R.string.save_stock_check_excel) else stringResource(R.string.save_csv),
                        icon = Icons.Default.Save,
                        onClick = {
                            saveLauncher.launch(
                                if (uiState.isStockMode) viewModel.buildStockReportFilename()
                                else viewModel.buildCsvFilename()
                            )
                        },
                        enabled = canExport,
                        tone = GlassActionTone.Neutral
                    )
                }
                item {
                    GlassActionButton(
                        label = if (uiState.isStockMode) stringResource(R.string.share_stock_check_excel) else stringResource(R.string.share_csv),
                        icon = Icons.Default.Share,
                        onClick = {
                            if (uiState.isStockMode) viewModel.generateStockReport(context)
                            else viewModel.shareAsCsv(context)
                        },
                        enabled = canExport,
                        tone = GlassActionTone.Neutral
                    )
                }
                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    if (uiState.isStockMode) {
                        Text(
                            text = stringResource(R.string.stock_check_excel_title),
                            style = MaterialTheme.typography.titleMedium.copy(color = CyanAccent, fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = stringResource(R.string.stock_check_excel_description_format, uiState.projectName),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                        GlassActionButton(
                            label = when (uiState.sendToPcState) {
                                ExportViewModel.SendToPcState.SEARCHING -> stringResource(R.string.searching_pc)
                                ExportViewModel.SendToPcState.SENDING -> stringResource(R.string.sending_to_pc)
                                else -> stringResource(R.string.send_stock_check_excel_to_pc)
                            },
                            icon = Icons.Default.Computer,
                            onClick = { viewModel.generateStockReport(context, sendToPc = true) },
                            enabled = !uiState.isExporting &&
                                uiState.sendToPcState != ExportViewModel.SendToPcState.SEARCHING &&
                                uiState.sendToPcState != ExportViewModel.SendToPcState.SENDING,
                            tone = GlassActionTone.Neutral
                        )
                    } else {
                        Text(
                            stringResource(R.string.company_report_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        GlassActionButton(
                            label = when (uiState.sendToPcState) {
                                ExportViewModel.SendToPcState.SEARCHING -> stringResource(R.string.searching_pc)
                                ExportViewModel.SendToPcState.SENDING -> stringResource(R.string.sending_to_pc)
                                else -> stringResource(R.string.send_to_pc)
                            },
                            icon = Icons.Default.Computer,
                            onClick = { viewModel.startCompanyReport(ExportViewModel.ReportDestination.PC) },
                            enabled = uiState.sendToPcState != ExportViewModel.SendToPcState.SEARCHING &&
                                      uiState.sendToPcState != ExportViewModel.SendToPcState.SENDING,
                            tone = GlassActionTone.Neutral
                        )
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

    if (uiState.showBluetoothSyncDialog) {
        BluetoothProjectSyncDialog(
            projectName = uiState.projectName,
            pairedDevices = uiState.bluetoothDevices,
            isBusy = uiState.bluetoothSyncBusy,
            statusMessage = when (uiState.bluetoothSyncResult) {
                BluetoothSyncResult.SUCCESS -> stringResource(R.string.bluetooth_sync_complete)
                BluetoothSyncResult.IMPORTED -> stringResource(
                    R.string.bluetooth_sync_imported_format,
                    uiState.bluetoothImportedProjectName.orEmpty()
                )
                BluetoothSyncResult.FAILURE -> stringResource(
                    R.string.bluetooth_sync_failed_format,
                    uiState.bluetoothSyncError.orEmpty()
                )
                BluetoothSyncResult.IDLE -> null
            },
            onDismiss = viewModel::closeBluetoothSync,
            onRefresh = viewModel::refreshBluetoothDevices,
            onReceive = viewModel::receiveBluetoothProject,
            onSend = viewModel::sendBluetoothProject
        )
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
        GlassActionButton(
            label = date?.format(formatter) ?: label,
            icon = Icons.Default.CalendarMonth,
            onClick = onClick,
            tone = GlassActionTone.Neutral
        )
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
            .padding(vertical = 2.dp)
            .border(
                width = if (checked) 1.5.dp else 1.dp,
                color = if (checked) CyanAccent else CyanAccent.copy(alpha = 0.30f),
                shape = RoundedCornerShape(16.dp)
            ),
        onClick = onToggle,
        colors = CardDefaults.cardColors(
            containerColor = if (checked) MaterialTheme.colorScheme.primaryContainer else SurfaceDark
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
