package com.nearexpiry.manager.presentation.screens.scan

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.view.LifecycleCameraController
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.google.mlkit.vision.barcode.common.Barcode
import com.nearexpiry.manager.R
import com.nearexpiry.manager.domain.model.ExpiryItem
import com.nearexpiry.manager.presentation.components.BottomNavigationBar
import com.nearexpiry.manager.presentation.components.ExpiryDateField
import com.nearexpiry.manager.presentation.screens.scan.components.BarcodeScannerOverlay
import com.nearexpiry.manager.presentation.screens.scan.components.ScannerInactiveOverlay
import com.nearexpiry.manager.presentation.screens.scan.components.ScannerView
import com.nearexpiry.manager.presentation.screens.scan.viewmodel.ScanViewModel
import com.nearexpiry.manager.presentation.theme.CyanAccent
import com.nearexpiry.manager.presentation.theme.ErrorRed
import com.nearexpiry.manager.presentation.theme.GreenAccent
import com.nearexpiry.manager.presentation.theme.YellowAccent
import com.nearexpiry.manager.presentation.theme.SurfaceVariant
import com.nearexpiry.manager.presentation.theme.OrangeAccent
import com.nearexpiry.manager.presentation.theme.SubtleGray
import com.nearexpiry.manager.presentation.theme.SurfaceDark
import com.nearexpiry.manager.utils.LanguageManager
import com.nearexpiry.manager.utils.QuantityFormatter
import kotlinx.coroutines.delay
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

    val cameraPermissionDeniedMsg = stringResource(R.string.camera_permission_required)
    val failedToStartCameraFormat = stringResource(R.string.failed_to_start_camera_format)

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        cameraError = if (isGranted) null else cameraPermissionDeniedMsg
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // Keep the in-app barcode-not-found banner visible for two seconds. The
    // banner is rendered inside the active Manual or Scan Mode entry area,
    // avoiding the Android system toast and its application icon.
    uiState.scanError?.let {
        val errorId = uiState.scanErrorId
        LaunchedEffect(errorId) {
            delay(2_000)
            viewModel.clearScanError(errorId)
        }
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
                cameraError = failedToStartCameraFormat.format(e.message)
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
                cameraError = failedToStartCameraFormat.format(e.message)
                isCameraBound = false
            }
        }
    }

    // ── Torch control: physically on only while the camera is bound and the
    // user has it enabled. Unbinding (idle) cuts the torch automatically; on
    // re-bind this effect re-applies the remembered state.
    LaunchedEffect(isCameraBound, uiState.torchEnabled) {
        if (isCameraBound) {
            try { cameraController.enableTorch(uiState.torchEnabled) } catch (_: Exception) {}
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
        uiState.showExistingItemDialog ||
        uiState.showExistingQtyDialog ||
        uiState.showEditDialog ||
        uiState.showDeleteConfirmDialog

    // Camera FAB: only when scanner is inactive and no dialogs/manual mode
    val showCameraFab = scannerInactive && !dialogsShowing && !uiState.showManualMode

    // Manual mode button: visible whenever no dialogs are showing (incl. when camera active)
    val showManualButton = !dialogsShowing

    // ── Language-aware product name for dialogs ──────────────────────────────
    // Arabic name first when the app is in Arabic (falling back to English),
    // otherwise English first (falling back to Arabic if English is missing).
    val isArabic = LanguageManager.isArabic()
    val pendingDisplayName = if (isArabic) {
        uiState.pendingProductNameArabic?.takeIf { it.isNotBlank() }
            ?: uiState.pendingProductName
    } else {
        uiState.pendingProductName?.takeIf { it.isNotBlank() }
            ?: uiState.pendingProductNameArabic
    }
    val editDisplayName = if (isArabic) {
        uiState.editProductNameArabic?.takeIf { it.isNotBlank() }
            ?: uiState.editProductName
    } else {
        uiState.editProductName?.takeIf { it.isNotBlank() }
            ?: uiState.editProductNameArabic
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { BottomNavigationBar(navController) },
        floatingActionButton = {
            if (showManualButton) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // ── Flashlight toggle (camera mode only, above keyboard) ──
                    if (!uiState.showManualMode) {
                        SmallFloatingActionButton(
                            onClick = { viewModel.toggleTorch() },
                            containerColor = if (uiState.torchEnabled) YellowAccent else SurfaceVariant,
                            contentColor = if (uiState.torchEnabled) Color.Black else Color.White
                        ) {
                            Icon(
                                imageVector = if (uiState.torchEnabled) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                                contentDescription = if (uiState.torchEnabled)
                                    stringResource(R.string.flash_off)
                                else
                                    stringResource(R.string.flash_on)
                            )
                        }
                    }

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
                            contentDescription = if (uiState.showManualMode)
                                stringResource(R.string.manual_mode_exit)
                            else
                                stringResource(R.string.manual_mode_enter)
                        )
                    }

                    // ── Camera / restart scanner button ──────────────────────
                    if (showCameraFab) {
                        FloatingActionButton(
                            onClick = { viewModel.restartScanner() },
                            containerColor = GreenAccent,
                            contentColor = Color(0xFF002200)
                        ) {
                            Icon(Icons.Filled.PhotoCamera, contentDescription = stringResource(R.string.scan))
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
                            onSearchByName   = { viewModel.openCatalogSearch() },
                            onDismiss        = { viewModel.exitManualMode() },
                            barcodeNotFoundMessage = uiState.scanError,
                            modifier         = Modifier.fillMaxSize()
                        )
                    }
                    !hasCameraPermission -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(stringResource(R.string.camera_permission_required), color = Color.White)
                                Spacer(Modifier.height(8.dp))
                                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                                    Text(stringResource(R.string.grant_permission))
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
                                }) { Text(stringResource(R.string.retry)) }
                            }
                        }
                    }
                    scannerInactive -> {
                        ScannerInactiveOverlay(
                            onClick         = { viewModel.restartScanner() },
                            modifier        = Modifier.fillMaxSize(),
                            detectedBarcode = uiState.detectedBarcode,
                            productName     = pendingDisplayName
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
                            BarcodeScannerOverlay(
                                errorMessage = uiState.scanError,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
                if (uiState.showScanConfirmation && uiState.lastSavedItem != null) {
                    ScanConfirmationCard(
                        item = uiState.lastSavedItem!!,
                        projectName = uiState.activeProjectName,
                        showExpiry = !uiState.isStockMode,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp)
                    )
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
                        showExpiry = !uiState.isStockMode,
                        onEdit = { viewModel.requestEdit(item) },
                        onDelete = { viewModel.requestDelete(item) }
                    )
                }
                if (uiState.recentScans.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = GreenAccent, modifier = Modifier.size(30.dp))
                            Text("Ready for your first scan", style = MaterialTheme.typography.titleSmall, color = GreenAccent)
                            Text("Use the camera or keyboard button to add a product.", style = MaterialTheme.typography.bodySmall, color = SubtleGray)
                        }
                    }
                }
            }
        }
    }

    // ── Dialogs ───────────────────────────────────────────────────────────
    if (uiState.showCatalogSearch) {
        CatalogSearchDialog(
            query = uiState.catalogSearchQuery,
            results = uiState.catalogSearchResults,
            onQueryChange = { viewModel.onCatalogSearchQueryChange(it) },
            onSelect = { viewModel.onCatalogProductSelected(it) },
            onDismiss = { viewModel.dismissCatalogSearch() }
        )
    }

    if (uiState.showExpiryDialog) {
        ExpiryDatePickerDialog(
            initialDate = uiState.initialExpiryDate,
            onDateSelected = { date -> viewModel.onExpiryDateSelected(date) },
            onDismiss = { viewModel.dismissDialog() },
            productName = pendingDisplayName,
            onlineLookupState = uiState.onlineLookupState,
            onlineProductName = uiState.onlineProductName,
            onlineProductNameArabic = uiState.onlineProductNameArabic,
            onSearchOnline = { viewModel.searchOnline() },
            onUseOnlineResult = { saveForFutureScans -> viewModel.useOnlineResult(saveForFutureScans) }
        )
    }

    if (uiState.showQuantityDialog) {
        QuantityInputDialog(
            onQuantityConfirmed = { quantity -> viewModel.onQuantityConfirmed(quantity) },
            onDismiss = { viewModel.dismissQuantityDialog() },
            productName = pendingDisplayName,
            itemCode = uiState.pendingItemCode,
            unit = uiState.pendingUnit
        )
    }

    if (uiState.showDuplicateDialog) {
        DuplicateItemDialog(
            existingQty = uiState.duplicateExistingQty,
            newQty = uiState.duplicateNewQty,
            onResolve = { mode -> viewModel.resolveDuplicate(mode) },
            onDismiss = { viewModel.dismissDuplicateDialog() },
            productName = pendingDisplayName
        )
    }

    if (uiState.showExistingItemDialog) {
        ExistingItemDialog(
            productName = pendingDisplayName,
            itemCode = uiState.pendingItemCode,
            entries = uiState.existingEntries,
            onAddQty = { id -> viewModel.existingChooseQtyAction(id, addMode = true) },
            onReplaceQty = { id -> viewModel.existingChooseQtyAction(id, addMode = false) },
            onAddNewDate = { viewModel.existingAddNewDate() },
            showExpiryActions = !uiState.isStockMode,
            onDismiss = { viewModel.dismissExistingItemDialog() }
        )
    }

    if (uiState.showExistingQtyDialog) {
        QuantityInputDialog(
            onQuantityConfirmed = { quantity -> viewModel.onExistingQtyConfirmed(quantity) },
            onDismiss = { viewModel.dismissExistingQtyDialog() },
            productName = pendingDisplayName,
            itemCode = uiState.pendingItemCode,
            unit = uiState.pendingUnit
        )
    }

    if (uiState.showEditDialog) {
        EditScanItemDialog(
            barcode = uiState.editBarcode,
            productName = editDisplayName,
            expiryDate = uiState.editExpiryDate,
            showExpiry = !uiState.isStockMode,
            quantity = uiState.editQuantity,
            unit = uiState.editUnit,
            onExpiryDateChange = { viewModel.updateEditExpiryDate(it) },
            onQuantityChange = { viewModel.updateEditQuantity(it) },
            onUnitChange = viewModel::updateEditUnit,
            onConfirm = { viewModel.confirmEdit() },
            onDismiss = { viewModel.dismissEditDialog() }
        )
    }

    if (uiState.showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteDialog() },
            title = { Text(stringResource(R.string.delete_item)) },
            text = { Text(stringResource(R.string.delete_scan_confirm)) },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmDelete() }) {
                    Text(stringResource(R.string.delete), color = ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDeleteDialog() }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

// ── Manual barcode input box (replaces camera area in manual mode) ────────

@Composable
private fun ManualBarcodeInputBox(
    onBarcodeEntered: (String) -> Unit,
    onSearchByName: () -> Unit,
    onDismiss: () -> Unit,
    barcodeNotFoundMessage: String? = null,
    modifier: Modifier = Modifier
) {
    // Keep the exact text supplied by the IME while the user is typing.
    // Normalizing inside onValueChange replaces the keyboard's composing text
    // on some OEMs, where the input is accepted but rendered as an almost
    // invisible glyph. Normalization happens only when the barcode is sent.
    var barcodeText by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val pleaseEnterBarcodeMsg = stringResource(R.string.please_enter_barcode)

    // Pop the number pad the moment this enters composition.
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    val handleDone = {
        val normalizedBarcode = normalizeBarcodeDigits(barcodeText)
        if (normalizedBarcode.isEmpty()) {
            error = pleaseEnterBarcodeMsg
        } else {
            keyboardController?.hide()
            onBarcodeEntered(normalizedBarcode)
        }
    }

    // This Text is intentionally rendered independently from Material's
    // internal text painter. Certain OEM Compose/IME combinations accept the
    // input but fail to draw that painter; the overlay guarantees the exact
    // typed barcode remains visible in Manual Mode.
    val visibleBarcodeTextStyle = MaterialTheme.typography.bodyLarge.copy(
        color = Color.White,
        fontFamily = FontFamily.Monospace,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        lineHeight = 32.sp,
        textDirection = TextDirection.Ltr
    )

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
                text = stringResource(R.string.manual_entry),
                style = MaterialTheme.typography.labelMedium.copy(color = CyanAccent),
                fontWeight = FontWeight.Bold
            )
            barcodeNotFoundMessage?.let { message ->
                ScanErrorBanner(
                    message = message,
                    textColor = Color.White,
                    containerColor = Color(0xFF651818)
                )
            }
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = barcodeText,
                    onValueChange = { rawInput ->
                        // Do not transform the value while the IME owns a
                        // composing region. This keeps every typed digit visible
                        // on keyboards that use OEM-specific composition logic.
                        barcodeText = rawInput
                        error = null
                    },
                    label = { Text(stringResource(R.string.barcode_label), color = SubtleGray) },
                    singleLine = true,
                    isError = error != null,
                    supportingText = { error?.let { Text(it, color = ErrorRed) } },
                    // The platform field stays fully active for focus, cursor,
                    // selection and IME behavior. Its OEM-dependent glyph layer
                    // is transparent because the explicit Text below is the
                    // stable, high-contrast visual source of the barcode.
                    textStyle = visibleBarcodeTextStyle.copy(color = Color.Transparent),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { handleDone() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanAccent,
                        unfocusedBorderColor = SubtleGray,
                        focusedTextColor = Color.Transparent,
                        unfocusedTextColor = Color.Transparent,
                        cursorColor = CyanAccent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 72.dp)
                        .focusRequester(focusRequester)
                )
                if (barcodeText.isNotEmpty()) {
                    Text(
                        text = barcodeText,
                        style = visibleBarcodeTextStyle,
                        maxLines = 1,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 16.dp, end = 16.dp)
                    )
                }
            }
            // Search the catalog by product name/POS code when a barcode won't scan.
            TextButton(onClick = {
                keyboardController?.hide()
                onSearchByName()
            }) {
                Text(stringResource(R.string.search_by_name), color = CyanAccent)
            }
            // No Save button — Enter/Done on the number pad is the only way to submit.
            // A Cancel link lets the user go back to camera mode.
            TextButton(onClick = {
                keyboardController?.hide()
                onDismiss()
            }) {
                Text(stringResource(R.string.cancel), color = SubtleGray)
            }
        }
    }
}

/** Normalizes Arabic-Indic digits while preserving a simple String input state. */
internal fun normalizeBarcodeDigits(rawInput: String): String = buildString {
    for (ch in rawInput) {
        when (ch) {
            in '0'..'9' -> append(ch)
            in '\u0660'..'\u0669' -> append('0' + (ch - '\u0660')) // ٠..٩
            in '\u06F0'..'\u06F9' -> append('0' + (ch - '\u06F0')) // ۰..۹
        }
    }
}

/** Full-width in-app feedback shown inside the active scan or manual entry area. */
@Composable
private fun ScanErrorBanner(
    message: String,
    textColor: Color,
    containerColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = containerColor,
        contentColor = textColor,
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 3.dp,
        shadowElevation = 3.dp
    ) {
        Text(
            text = message,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            color = textColor,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

// ── Recent scan card ──────────────────────────────────────────────────────

@Composable
private fun RecentScanCard(
    item: ExpiryItem,
    showExpiry: Boolean = true,
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
                // Line 2: Product name (if known) — in the app's current language
                val productDisplayName = if (LanguageManager.isArabic()) {
                    item.productNameArabic?.takeIf { it.isNotBlank() }
                        ?: item.productName?.takeIf { it.isNotBlank() }
                } else {
                    item.productName?.takeIf { it.isNotBlank() }
                        ?: item.productNameArabic?.takeIf { it.isNotBlank() }
                }
                if (!productDisplayName.isNullOrBlank()) {
                    Text(
                        text = productDisplayName,
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.White),
                        maxLines = 1
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (showExpiry) {
                        Text(
                            text = stringResource(R.string.expiry_format, item.expiryDate),
                            style = MaterialTheme.typography.bodySmall.copy(color = GreenAccent)
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
                        style = MaterialTheme.typography.bodySmall.copy(color = OrangeAccent)
                    )
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit), tint = CyanAccent, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete), tint = ErrorRed, modifier = Modifier.size(20.dp))
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
    showExpiry: Boolean = true,
    quantity: Double,
    unit: String,
    onExpiryDateChange: (String) -> Unit,
    onQuantityChange: (Double) -> Unit,
    onUnitChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var qtyText by remember(quantity) {
        mutableStateOf(QuantityFormatter.format(quantity))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_item), style = MaterialTheme.typography.titleMedium.copy(color = CyanAccent)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!productName.isNullOrBlank()) {
                    Text(text = productName, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                }
                Text(text = stringResource(R.string.barcode_format, barcode), style = MaterialTheme.typography.bodyMedium.copy(color = SubtleGray))
                if (showExpiry) {
                    ExpiryDateField(
                        value = expiryDate,
                        onValueChange = onExpiryDateChange,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedTextField(
                    value = qtyText,
                    onValueChange = { v ->
                        if (v.count { c -> c == '.' } <= 1 && v.all { c -> c.isDigit() || c == '.' }) {
                            qtyText = v
                            v.toDoubleOrNull()?.let { onQuantityChange(it) }
                        }
                    },
                    label = { Text(stringResource(R.string.quantity_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                OutlinedTextField(
                    value = unit,
                    onValueChange = onUnitChange,
                    label = { Text("UOM / Unit Type") },
                    supportingText = { Text("Examples: PCS, CTN, KG, OFR") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !showExpiry || expiryDate.isNotBlank()) {
                Text(stringResource(R.string.save), color = GreenAccent)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

// ── Catalog name-search dialog (manual entry by product name) ──────────────

@Composable
private fun CatalogSearchDialog(
    query: String,
    results: List<com.nearexpiry.manager.domain.model.ProductInfo>,
    onQueryChange: (String) -> Unit,
    onSelect: (com.nearexpiry.manager.domain.model.ProductInfo) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.search_by_name)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    label = { Text(stringResource(R.string.search_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                if (query.trim().length >= 2 && results.isEmpty()) {
                    Text(
                        stringResource(R.string.no_catalog_matches),
                        style = MaterialTheme.typography.bodySmall.copy(color = SubtleGray)
                    )
                }
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(results) { product ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(product) }
                                .padding(vertical = 10.dp)
                        ) {
                            Text(
                                product.name ?: product.nameArabic ?: product.barcode,
                                style = MaterialTheme.typography.titleSmall.copy(color = Color.White)
                            )
                            val sub = listOfNotNull(
                                product.itemCode?.takeIf { it.isNotBlank() }?.let { "POS $it" },
                                product.unit?.takeIf { it.isNotBlank() },
                                product.barcode.takeIf { it.isNotBlank() }
                            ).joinToString(" · ")
                            if (sub.isNotBlank()) {
                                Text(sub, style = MaterialTheme.typography.bodySmall.copy(color = SubtleGray))
                            }
                        }
                        HorizontalDivider(color = SubtleGray.copy(alpha = 0.2f))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun ScanConfirmationCard(
    item: ExpiryItem,
    projectName: String,
    showExpiry: Boolean = true,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = GreenAccent.copy(alpha = 0.96f)),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "SAVED SUCCESSFULLY",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = Color(0xFF003300)
            )
            Text(
                text = item.displayName,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFF003300),
                maxLines = 1
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = buildString {
                    append("Qty ${QuantityFormatter.format(item.quantity)}${item.unit?.let { " $it" }.orEmpty()}")
                    if (showExpiry) append("  •  Expiry ${item.expiryDate}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF003300)
            )
            if (projectName.isNotBlank()) {
                Text(
                    text = projectName,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = Color(0xFF003300)
                )
            }
        }
    }
}
