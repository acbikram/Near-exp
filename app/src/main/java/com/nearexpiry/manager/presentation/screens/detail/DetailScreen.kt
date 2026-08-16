package com.nearexpiry.manager.presentation.screens.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.nearexpiry.manager.R
import com.nearexpiry.manager.presentation.components.Code39Barcode
import com.nearexpiry.manager.presentation.components.ExpiryDateField
import com.nearexpiry.manager.presentation.components.UnitTypeSelector
import com.nearexpiry.manager.presentation.screens.detail.viewmodel.DetailViewModel
import com.nearexpiry.manager.presentation.theme.CyanAccent
import com.nearexpiry.manager.presentation.theme.ErrorRed
import com.nearexpiry.manager.presentation.theme.GreenAccent
import com.nearexpiry.manager.presentation.theme.OrangeAccent
import com.nearexpiry.manager.utils.ExpiryDateUtils
import com.nearexpiry.manager.utils.LanguageManager
import com.nearexpiry.manager.utils.QuantityFormatter
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    navController: NavController,
    itemId: Long,
    viewModel: DetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var editingQuantity by remember { mutableStateOf(false) }
    var editingExpiry by remember { mutableStateOf(false) }
    var editingUnit by remember { mutableStateOf(false) }
    var showQuickActions by remember { mutableStateOf(false) }

    LaunchedEffect(itemId) {
        viewModel.loadItem(itemId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.item_details)) },
                actions = {
                    IconButton(onClick = { showQuickActions = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (uiState.item != null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column {
                        val item = uiState.item!!
                        val isArabic = LanguageManager.isArabic()
                        val today = java.time.LocalDate.now()
                        val expiry = ExpiryDateUtils.parseOrNull(item.expiryDate)
                        val (expiryLabel, expiryColor) = when {
                            expiry?.isBefore(today) == true -> "EXPIRED" to ErrorRed
                            expiry == today -> "EXPIRES TODAY" to OrangeAccent
                            expiry != null && !expiry.isAfter(today.plusDays(7)) -> "EXPIRES IN 1-7 DAYS" to OrangeAccent
                            else -> "SAFE EXPIRY" to GreenAccent
                        }
                        // Sr No. — scan-order rank within the project.
                        uiState.srNo?.let { srNo ->
                            Text(
                                text = stringResource(R.string.sr_no_format, srNo),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        // Title: name in the app's current language (falls back to
                        // the other language, then item code, then barcode).
                        Text(
                            text = item.displayName,
                            style = MaterialTheme.typography.titleLarge
                        )
                        // Secondary line: name in the *other* language, if available
                        val secondaryName = if (isArabic) {
                            item.productName?.takeIf { it.isNotBlank() }
                        } else {
                            item.productNameArabic?.takeIf { it.isNotBlank() }
                        }
                        if (!secondaryName.isNullOrBlank()) {
                            Text(
                                secondaryName,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        // Item Code
                        if (!item.itemCode.isNullOrBlank()) {
                            Text(
                                stringResource(R.string.item_code_format, item.itemCode),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        // Barcode + Unit
                        Text(
                            text = if (!item.unit.isNullOrBlank())
                                stringResource(R.string.barcode_unit_format, item.barcode, item.unit)
                            else
                                stringResource(R.string.barcode_format, item.barcode),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (uiState.projectName.isNotBlank()) {
                            Text(
                                text = "Project: ${uiState.projectName}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = CyanAccent
                            )
                        }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.item_quantity),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = CyanAccent
                                )
                                Text(
                                    text = if (!item.unit.isNullOrBlank()) {
                                        "${QuantityFormatter.format(item.quantity)} ${item.unit}"
                                    } else {
                                        QuantityFormatter.format(item.quantity)
                                    },
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = OrangeAccent
                                )
                                if (!uiState.isStockMode) {
                                    HorizontalDivider(color = CyanAccent.copy(alpha = 0.35f))
                                    Text(
                                        text = stringResource(R.string.item_expiry_date),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = CyanAccent
                                    )
                                    Text(
                                        text = item.expiryDate,
                                        style = MaterialTheme.typography.titleLarge,
                                        color = expiryColor
                                    )
                                }
                            }
                        }
                        if (!uiState.isStockMode) {
                            Surface(
                                color = expiryColor.copy(alpha = 0.15f),
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                Text(
                                    text = expiryLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = expiryColor,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                    // Scannable barcode (white card, Code39) — encodes the item/POS
                    // code exactly (falls back to the scanned barcode if no item
                    // code is on file), for reading with a USB/wireless hand
                    // scanner into the computer as an exact Item Code.
                    run {
                        val item = uiState.item!!
                        val scanValue = item.itemCode?.takeIf { it.isNotBlank() } ?: item.barcode
                        Column {
                            Text(
                                text = stringResource(R.string.item_code_barcode_label),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(6.dp))
                            Code39Barcode(
                                value = scanValue,
                                modifier = Modifier.fillMaxWidth(),
                                onSwipeLeft = { viewModel.goToNext() },
                                onSwipeRight = { viewModel.goToPrevious() }
                            )
                        }
                    }
                    if (editingExpiry && !uiState.isStockMode) {
                        ExpiryDateField(
                            value = uiState.expiryDate,
                            onValueChange = viewModel::updateExpiryDate,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !uiState.isSaving
                        )
                    }
                    if (editingQuantity) {
                        OutlinedTextField(
                            value = uiState.quantityText,
                            onValueChange = viewModel::updateQuantity,
                            label = { Text(stringResource(R.string.quantity_label)) },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            isError = uiState.quantityError != null,
                            supportingText = { uiState.quantityError?.let { Text(it) } },
                            enabled = !uiState.isSaving
                        )
                    }
                    if (editingUnit) {
                        UnitTypeSelector(
                            selectedUnit = uiState.unitText,
                            onUnitSelected = viewModel::updateUnit,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Button(
                        onClick = { viewModel.saveChanges() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isSaving && uiState.quantityError == null && uiState.expiryDate.isNotBlank()
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(stringResource(R.string.save_changes))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { editingQuantity = !editingQuantity },
                            modifier = Modifier.weight(1f),
                            enabled = !uiState.isSaving
                        ) { Text("Edit Quantity") }
                        if (!uiState.isStockMode) {
                            OutlinedButton(
                                onClick = { editingExpiry = !editingExpiry },
                                modifier = Modifier.weight(1f),
                                enabled = !uiState.isSaving
                            ) { Text("Change Expiry") }
                        }
                    }
                    OutlinedButton(
                        onClick = { editingUnit = !editingUnit },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isSaving
                    ) { Text("Edit UOM / Unit Type") }
                    Button(
                        onClick = viewModel::requestMove,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                        enabled = !uiState.isSaving
                    ) { Text("Move to Project") }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        enabled = !uiState.isSaving
                    ) { Text(stringResource(R.string.delete)) }
                }
            }
        }
    }

    if (showQuickActions) {
        ModalBottomSheet(onDismissRequest = { showQuickActions = false }) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Quick actions",
                    style = MaterialTheme.typography.titleLarge
                )
                ListItem(
                    headlineContent = { Text("Edit Quantity") },
                    supportingContent = { Text("Update the current item quantity") },
                    modifier = Modifier.clickable {
                        editingQuantity = true
                        showQuickActions = false
                    }
                )
                if (!uiState.isStockMode) {
                    ListItem(
                        headlineContent = { Text("Change Expiry") },
                        supportingContent = { Text("Update the expiry date") },
                        modifier = Modifier.clickable {
                            editingExpiry = true
                            showQuickActions = false
                        }
                    )
                }
                ListItem(
                    headlineContent = { Text("Edit UOM / Unit Type") },
                    supportingContent = { Text("Choose PCS, KG, CTN, or OFR") },
                    modifier = Modifier.clickable {
                        editingUnit = true
                        showQuickActions = false
                    }
                )
                ListItem(
                    headlineContent = { Text("Move to Project") },
                    supportingContent = { Text("Move this item to another project") },
                    modifier = Modifier.clickable {
                        viewModel.requestMove()
                        showQuickActions = false
                    }
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (uiState.showMoveDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissMoveDialog,
            title = { Text("Move to project") },
            text = {
                if (uiState.otherProjects.isEmpty()) {
                    Text("Create another project in Settings before moving this item.")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Choose the destination project.")
                        uiState.otherProjects.forEach { project ->
                            TextButton(onClick = { viewModel.moveItem(project.id) }, modifier = Modifier.fillMaxWidth()) {
                                Text(project.name, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = viewModel::dismissMoveDialog) { Text(stringResource(R.string.cancel)) } }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_item)) },
            text = { Text(stringResource(R.string.delete_item_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteItem()
                        showDeleteDialog = false
                    }
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (uiState.error != null) {
        LaunchedEffect(uiState.error) {
            scope.launch {
                snackbarHostState.showSnackbar(uiState.error!!)
                viewModel.clearError()
            }
        }
    }

    if (uiState.navigateBack) {
        LaunchedEffect(Unit) {
            navController.popBackStack()
        }
    }
}
