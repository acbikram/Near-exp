package com.nearexpiry.manager.presentation.screens.scan

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.view.LifecycleCameraController
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.google.mlkit.vision.barcode.common.Barcode
import com.nearexpiry.manager.domain.model.ExpiryItem
import com.nearexpiry.manager.presentation.components.BottomNavigationBar
import com.nearexpiry.manager.presentation.screens.scan.components.BarcodeScannerOverlay
import com.nearexpiry.manager.presentation.screens.scan.components.ScannerInactiveOverlay
import com.nearexpiry.manager.presentation.screens.scan.components.ScannerView
import com.nearexpiry.manager.presentation.screens.scan.viewmodel.ScanViewModel
import com.nearexpiry.manager.presentation.theme.CyanAccent
import com.nearexpiry.manager.presentation.theme.ErrorRed
import com.nearexpiry.manager.presentation.theme.GreenAccent
import com.nearexpiry.manager.presentation.theme.OrangeAccent
import com.nearexpiry.manager.presentation.theme.SubtleGray
import com.nearexpiry.manager.presentation.theme.SurfaceDark
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val CAMERA_AREA_WEIGHT = 0.17f
private const val LIST_AREA_WEIGHT   = 0.83f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    navController: NavController,
    viewModel: ScanViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraController = remember { LifecycleCameraController(context) }
    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var isCameraBound by remember { mutableStateOf(false) }

    val uiState by viewModel.uiState.collectAsState()
    val scannerInactive = uiState.scannerInactive

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        cameraError = if (isGranted) null else "Camera permission denied"
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    LaunchedEffect(hasCameraPermission, scannerInactive, lifecycleOwner) {
        if (hasCameraPermission && !scannerInactive && !isCameraBound) {
            try {
                cameraController.cameraSelector = cameraSelector
                cameraController.bindToLifecycle(lifecycleOwner)
                isCameraBound = true
                cameraError = null
                viewModel.startScanner()
            } catch (e: Exception) {
                cameraError = "Failed to start camera: ${e.message}"
                isCameraBound = false
            }
        }
    }

    LaunchedEffect(scannerInactive) {
        if (scannerInactive && isCameraBound) {
            try { cameraController.unbind() } catch (_: Exception) {}
            isCameraBound = false
        } else if (!scannerInactive && !isCameraBound && hasCameraPermission) {
            try {
                cameraController.cameraSelector = cameraSelector
                cameraController.bindToLifecycle(lifecycleOwner)
                isCameraBound = true
                cameraError = null
                viewModel.startScanner()
            } catch (e: Exception) {
                cameraError = "Failed to start camera: ${e.message}"
                isCameraBound = false
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try { if (isCameraBound) cameraController.unbind() } catch (_: Exception) {}
            viewModel.stopScanner()
        }
    }

    // Dialogs are showing — hide both action buttons
    val dialogsShowing = uiState.showExpiryDialog ||
        uiState.showQuantityDialog ||
        uiState.showDuplicateDialog ||
        uiState.showEditDialog ||
        uiState.showDeleteConfirmDialog

    // Camera FAB: only when scanner is inactive and no dialogs/manual mode
    val showCameraFab = scannerInactive && !dialogsShowing && !uiState.showManualMode

    // Manual mode button: visible whenever no dialogs are showing (incl. when camera active)
    val showManualButton = !dialogsShowing

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { BottomNavigationBar(navController) },
        floatingActionButton = {
            if (showManualButton) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // ── Manual entry shortcut (above camera button) ──────────
                    SmallFloatingActionButton(
                        onClick = {
                            if (uiState.showManualMode) viewModel.exitManualMode()
                            else viewModel.enterManualMode()
                        },
                        containerColor = if (uiState.showManualMode) OrangeAccent else CyanAccent,
                        contentColor = Color.Black
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Keyboard,
                            contentDescription = if (uiState.showManualMode) "Exit manual mode" else "Manual barcode entry"
                        )
                    }

                    // ── Camera / restart scanner button ──────────────────────
                    if (showCameraFab) {
                        FloatingActionButton(
                            onClick = { viewModel.restartScanner() },
                            containerColor = GreenAccent,
                            contentColor = Color(0xFF002200)
                        ) {
                            Icon(Icons.Filled.PhotoCamera, contentDescription = "Scan")
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // ── Camera area ───────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(CAMERA_AREA_WEIGHT)
                    .background(Color.Black)
            ) {
                when {
                    // ── Manual barcode entry mode ─────────────────────────
                    uiState.showManualMode -> {
                        ManualBarcodeInputBox(
                            onBarcodeEntered = { viewModel.onManualBarcodeEntered(it) },
                            onDismiss        = { viewModel.exitManualMode() },
                            modifier         = Modifier.fillMaxSize()
                        )
                    }
                    !hasCameraPermission -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Camera permission required", color = Color.White)
                                Spacer(Modifier.height(8.dp))
                                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                                    Text("Grant Permission")
                                }
                            }
                        }
                    }
                    cameraError != null -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(cameraError ?: "", color = Color.White)
                                Spacer(Modifier.height(8.dp))
                                Button(onClick = {
                                    cameraError = null
                                    isCameraBound = false
                                    if (hasCameraPermission) viewModel.restartScanner()
                                    else permissionLauncher.launch(Manifest.permission.CAMERA)
                                }) { Text("Retry") }
                            }
                        }
                    }
                    scannerInactive -> {
                        ScannerInactiveOverlay(
                            onClick         = { viewModel.restartScanner() },
                            modifier        = Modifier.fillMaxSize(),
                            detectedBarcode = uiState.detectedBarcode,
                            productName     = uiState.pendingProductName
                        )
                    }
                    else -> {
                        Box(modifier = Modifier.fillMaxSize()) {
                            ScannerView(
                                cameraController = cameraController,
                                onBarcodeScanned = { barcode ->
                                    // Accept all common retail/logistics formats:
                                    //  • EAN-13 / EAN-8   — standard consumer barcodes
                                    //  • UPC-A / UPC-E    — North-American consumer barcodes
                                    //  • Code 128          — store-printed price stickers, carton labels
                                    //  • Code 39 / Code 93 — logistics & warehouse labels
                                    //  • ITF (Interleaved 2-of-5) — outer carton / pallet barcodes
                                    if (barcode.format in setOf(
                                            Barcode.FORMAT_EAN_13,
                                            Barcode.FORMAT_EAN_8,
                                            Barcode.FORMAT_UPC_A,
                                            Barcode.FORMAT_UPC_E,
                                            Barcode.FORMAT_CODE_128,
                                            Barcode.FORMAT_CODE_39,
                                            Barcode.FORMAT_CODE_93,
                                            Barcode.FORMAT_ITF
                                        )) {
                                        viewModel.onBarcodeScanned(barcode.rawValue ?: return@ScannerView)
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                            BarcodeScannerOverlay(modifier = Modifier.fillMaxSize())
                        }
                    }
                }
            }

            // ── Recent scans list ─────────────────────────────────────────
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(LIST_AREA_WEIGHT)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp)
            ) {
                items(uiState.recentScans, key = { it.id }) { item ->
                    RecentScanCard(
                        item = item,
                        onEdit = { viewModel.requestEdit(item) },
                        onDelete = { viewModel.requestDelete(item) }
                    )
                }
                if (uiState.recentScans.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text(
                                "No recent scans",
                                style = MaterialTheme.typography.bodyMedium.copy(color = SubtleGray)
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Dialogs ───────────────────────────────────────────────────────────
    if (uiState.showExpiryDialog) {
        ExpiryDatePickerDialog(
            onDateSelected = { date -> viewModel.onExpiryDateSelected(date) },
            onDismiss = { viewModel.dismissDialog() },
            productName = uiState.pendingProductName
        )
    }

    if (uiState.showQuantityDialog) {
        QuantityInputDialog(
            onQuantityConfirmed = { quantity -> viewModel.onQuantityConfirmed(quantity) },
            onDismiss = { viewModel.dismissQuantityDialog() },
            productName = uiState.pendingProductName,
            unit = uiState.pendingUnit
        )
    }

    if (uiState.showDuplicateDialog) {
        DuplicateItemDialog(
            existingQty = uiState.duplicateExistingQty,
            newQty = uiState.duplicateNewQty,
            onConfirm = { viewModel.mergeDuplicateItem() },
            onDismiss = { viewModel.dismissDuplicateDialog() },
            productName = uiState.pendingProductName
        )
    }

    if (uiState.showEditDialog) {
        EditScanItemDialog(
            barcode = uiState.editBarcode,
            productName = uiState.editProductName,
            expiryDate = uiState.editExpiryDate,
            quantity = uiState.editQuantity,
            onExpiryDateChange = { viewModel.updateEditExpiryDate(it) },
            onQuantityChange = { viewModel.updateEditQuantity(it) },
            onConfirm = { viewModel.confirmEdit() },
            onDismiss = { viewModel.dismissEditDialog() }
        )
    }

    if (uiState.showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteDialog() },
            title = { Text("Delete Item") },
            text = { Text("Are you sure you want to delete this scan record?") },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmDelete() }) {
                    Text("Delete", color = ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDeleteDialog() }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// ── Manual barcode input box (replaces camera area in manual mode) ────────

@Composable
private fun ManualBarcodeInputBox(
    onBarcodeEntered: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var barcodeText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Pop the number pad the moment this enters composition.
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    val handleDone = {
        val trimmed = barcodeText.trim()
        if (trimmed.isEmpty()) {
            error = "Please enter a barcode"
        } else {
            keyboardController?.hide()
            onBarcodeEntered(trimmed)
        }
    }

    Box(
        modifier = modifier
            .background(Color(0xFF0D0D0D))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Manual Entry",
                style = MaterialTheme.typography.labelMedium.copy(color = CyanAccent),
                fontWeight = FontWeight.Bold
            )
            OutlinedTextField(
                value = barcodeText,
                onValueChange = { v ->
                    // Only allow digits — no decimals, no letters
                    if (v.all { it.isDigit() }) {
                        barcodeText = v
                        error = null
                    }
                },
                label = { Text("Barcode", color = SubtleGray) },
                singleLine = true,
                isError = error != null,
                supportingText = { error?.let { Text(it, color = ErrorRed) } },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,   // digits only — no decimal key
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { handleDone() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor  = CyanAccent,
                    unfocusedBorderColor = SubtleGray,
                    focusedTextColor    = Color.White,
                    unfocusedTextColor  = Color.White,
                    cursorColor         = CyanAccent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )
            // No Save button — Enter/Done on the number pad is the only way to submit.
            // A Cancel link lets the user go back to camera mode.
            TextButton(onClick = {
                keyboardController?.hide()
                onDismiss()
            }) {
                Text("Cancel", color = SubtleGray)
            }
        }
    }
}

// ── Recent scan card ──────────────────────────────────────────────────────

@Composable
private fun RecentScanCard(
    item: ExpiryItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // Line 1: Barcode  •  ItemCode (if available)
                Text(
                    text = if (item.itemCode != null) "${item.barcode}   •   ${item.itemCode}"
                           else item.barcode,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = CyanAccent,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
                // Line 2: Product name (if known)
                if (!item.productName.isNullOrBlank()) {
                    Text(
                        text = item.productName,
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.White),
                        maxLines = 1
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Expiry: ${item.expiryDate}",
                        style = MaterialTheme.typography.bodySmall.copy(color = GreenAccent)
                    )
                    Text(
                        text = "Qty: ${if (item.quantity % 1.0 == 0.0) item.quantity.toInt() else item.quantity}" +
                            (item.unit?.let { " $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall.copy(color = OrangeAccent)
                    )
                }
                Text(
                    text = formatTimestamp(item.createdAt),
                    style = MaterialTheme.typography.bodySmall.copy(color = SubtleGray)
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = CyanAccent, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = ErrorRed, modifier = Modifier.size(20.dp))
            }
        }
    }
}

// ── Edit dialog ───────────────────────────────────────────────────────────

@Composable
private fun EditScanItemDialog(
    barcode: String,
    productName: String?,
    expiryDate: String,
    quantity: Double,
    onExpiryDateChange: (String) -> Unit,
    onQuantityChange: (Double) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var qtyText by remember(quantity) {
        mutableStateOf(if (quantity % 1.0 == 0.0) quantity.toInt().toString() else quantity.toString())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Item", style = MaterialTheme.typography.titleMedium.copy(color = CyanAccent)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!productName.isNullOrBlank()) {
                    Text(text = productName, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                }
                Text(text = "Barcode: $barcode", style = MaterialTheme.typography.bodyMedium.copy(color = SubtleGray))
                OutlinedTextField(
                    value = expiryDate,
                    onValueChange = onExpiryDateChange,
                    label = { Text("Expiry Date (YYYY-MM-DD)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = qtyText,
                    onValueChange = { v ->
                        if (v.count { c -> c == '.' } <= 1 && v.all { c -> c.isDigit() || c == '.' }) {
                            qtyText = v
                            v.toDoubleOrNull()?.let { onQuantityChange(it) }
                        }
                    },
                    label = { Text("Quantity") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Save", color = GreenAccent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun formatTimestamp(timestamp: Long): String =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .format(LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault()))
