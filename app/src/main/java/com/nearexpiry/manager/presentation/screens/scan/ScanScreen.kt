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
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
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
import com.nearexpiry.manager.presentation.theme.SurfaceVariant
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Camera/scanner box takes 17% of the screen height (half of previous 35%); the list takes the rest. */
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
            // Camera goes to standby — unbind it
            try { cameraController.unbind() } catch (_: Exception) {}
            isCameraBound = false
        } else if (!scannerInactive && !isCameraBound && hasCameraPermission) {
            // Camera released from standby (e.g. after cancel/save) — re-bind immediately
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

    val showScanFab = scannerInactive && 
        !uiState.showExpiryDialog && 
        !uiState.showQuantityDialog && 
        !uiState.showDuplicateDialog && 
        !uiState.showEditDialog && 
        !uiState.showDeleteConfirmDialog

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { BottomNavigationBar(navController) },
        floatingActionButton = {
            if (showScanFab) {
                FloatingActionButton(
                    onClick = { viewModel.restartScanner() },
                    containerColor = GreenAccent,
                    contentColor = Color(0xFF002200)
                ) {
                    Icon(Icons.Filled.PhotoCamera, contentDescription = "Scan")
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // ── Camera area (fixed height, never expands) ─────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(CAMERA_AREA_WEIGHT)
                    .background(Color.Black)
            ) {
                when {
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
                        // Camera preview fills the entire box; the overlay draws
                        // a dark mask OUTSIDE the glowing frame so the camera
                        // is only visible inside the glowing border.
                        Box(modifier = Modifier.fillMaxSize()) {
                            ScannerView(
                                cameraController = cameraController,
                                onBarcodeScanned = { barcode ->
                                    if (barcode.format in setOf(
                                            Barcode.FORMAT_EAN_13,
                                            Barcode.FORMAT_EAN_8,
                                            Barcode.FORMAT_UPC_A,
                                            Barcode.FORMAT_UPC_E
                                        )) {
                                        viewModel.onBarcodeScanned(barcode.rawValue ?: return@ScannerView)
                                    }
                                },
                                // Fill the entire camera area
                                modifier = Modifier.fillMaxSize()
                            )
                            // Overlay: dark mask outside the frame + glowing border on top
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

    // Edit dialog for recent scan items
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

    // Delete confirmation dialog
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

// ── Recent scan card with Edit / Delete icons ─────────────────────────────

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
                Text(
                    text = item.productName ?: item.barcode,
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = CyanAccent,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
                if (item.productName != null) {
                    Text(
                        text = item.barcode,
                        style = MaterialTheme.typography.bodySmall.copy(color = SubtleGray)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Expiry: ${item.expiryDate}",
                        style = MaterialTheme.typography.bodySmall.copy(color = GreenAccent)
                    )
                    Text(
                        text = "Qty: ${item.quantity}",
                        style = MaterialTheme.typography.bodySmall.copy(color = OrangeAccent)
                    )
                }
                Text(
                    text = formatTimestamp(item.createdAt),
                    style = MaterialTheme.typography.bodySmall.copy(color = SubtleGray)
                )
            }
            // Edit button
            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit",
                    tint = CyanAccent,
                    modifier = Modifier.size(20.dp)
                )
            }
            // Delete button
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = ErrorRed,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ── Inline edit dialog ────────────────────────────────────────────────────

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
    var qtyText by remember(quantity) { mutableStateOf(if (quantity % 1.0 == 0.0) quantity.toInt().toString() else quantity.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Edit Item",
                style = MaterialTheme.typography.titleMedium.copy(color = CyanAccent)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!productName.isNullOrBlank()) {
                    Text(
                        text = productName,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
                Text(
                    text = "Barcode: $barcode",
                    style = MaterialTheme.typography.bodyMedium.copy(color = SubtleGray)
                )
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
                        if (v.count { char -> char == '.' } <= 1 && v.all { char -> char.isDigit() || char == '.' }) {
                            qtyText = v
                            qtyText.toDoubleOrNull()?.let { onQuantityChange(it) }
                        }
                    },
                    label = { Text("Quantity") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Save", color = GreenAccent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun formatTimestamp(timestamp: Long): String {
    return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .format(LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault()))
}
