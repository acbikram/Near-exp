package com.nearexpiry.manager.presentation.screens.scan.viewmodel

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nearexpiry.manager.data.local.entity.ExpiryItemEntity
import com.nearexpiry.manager.data.local.entity.toEntity
import com.nearexpiry.manager.domain.model.ExpiryItem
import com.nearexpiry.manager.domain.repository.ExpiryRepository
import com.nearexpiry.manager.domain.repository.ProductCatalogRepository
import com.nearexpiry.manager.utils.PreferencesManager
import com.nearexpiry.manager.utils.SoundManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val repository: ExpiryRepository,
    private val productCatalogRepository: ProductCatalogRepository,
    private val preferencesManager: PreferencesManager,
    private val soundManager: SoundManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    data class ScanUiState(
        val recentScans: List<ExpiryItem> = emptyList(),
        val scannerInactive: Boolean = false,
        /** Barcode shown on the camera overlay immediately after detection. */
        val detectedBarcode: String = "",
        val showExpiryDialog: Boolean = false,
        val showQuantityDialog: Boolean = false,
        val showDuplicateDialog: Boolean = false,
        val pendingBarcode: String = "",
        val pendingExpiryDate: String = "",
        // Product info resolved from the local catalog for pendingBarcode (if found).
        val pendingProductName: String? = null,
        val pendingProductNameArabic: String? = null,
        val pendingUnit: String? = null,
        val duplicateExistingQty: Double = 0.0,
        val duplicateNewQty: Double = 0.0,
        val duplicateItemId: Long = 0,
        // Edit dialog state
        val showEditDialog: Boolean = false,
        val editItemId: Long = 0,
        val editBarcode: String = "",
        val editProductName: String? = null,
        val editExpiryDate: String = "",
        val editQuantity: Double = 1.0,
        // Delete confirm dialog state
        val showDeleteConfirmDialog: Boolean = false,
        val deleteItemId: Long = 0,
        // Manual barcode entry mode
        val showManualMode: Boolean = false
    )

    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    private var scanTimeoutJob = viewModelScope.launch { }

    init {
        loadRecentScans()
        startInactivityTimer()
    }

    fun startScanner() {
        _uiState.update { it.copy(scannerInactive = false) }
        startInactivityTimer()
    }

    fun stopScanner() {
        scanTimeoutJob.cancel()
    }

    private fun startInactivityTimer() {
        scanTimeoutJob.cancel()
        scanTimeoutJob = viewModelScope.launch {
            delay(10000)
            _uiState.update { it.copy(scannerInactive = true) }
        }
    }

    fun restartScanner() {
        _uiState.update { it.copy(scannerInactive = false) }
        startInactivityTimer()
    }

    // ── Manual barcode entry mode ─────────────────────────────────────────────

    fun enterManualMode() {
        stopScanner()
        _uiState.update { it.copy(showManualMode = true, scannerInactive = true) }
    }

    fun exitManualMode() {
        _uiState.update { it.copy(showManualMode = false) }
        restartScanner()
    }

    /** Called when the user submits a manually typed barcode. */
    fun onManualBarcodeEntered(barcode: String) {
        val trimmed = barcode.trim()
        if (trimmed.isEmpty()) return
        _uiState.update { it.copy(showManualMode = false) }
        onBarcodeScanned(trimmed)
    }

    // ── Scan flow ────────────────────────────────────────────────────────────

    fun onBarcodeScanned(barcode: String) {
        if (_uiState.value.scannerInactive) return
        if (_uiState.value.pendingBarcode.isNotEmpty()) return  // already processing one
        stopScanner()

        // ── Instant feedback on detection ───────────────────────────────────
        // We don't know yet if it's new or duplicate (that requires a DB lookup),
        // so we play a single "detected" beep immediately, then correct to double
        // if it turns out to be a duplicate once the user confirms quantity.
        if (preferencesManager.isScanSoundEnabled()) soundManager.playSingleBeep()
        if (preferencesManager.isVibrationEnabled()) vibrateSingle()

        // ── Show barcode on camera screen + go standby immediately ──────────
        _uiState.update {
            it.copy(
                detectedBarcode  = barcode,
                scannerInactive  = true,   // camera goes to standby immediately
                pendingBarcode   = barcode,
                pendingProductName = null,
                pendingProductNameArabic = null,
                pendingUnit = null,
                showExpiryDialog = true
            )
        }

        // ── Resolve product name from the local catalog (offline lookup) ────
        // This runs async and updates the UI state when (if) a match is found;
        // the dialogs/overlay simply fall back to the raw barcode until then.
        viewModelScope.launch {
            val product = productCatalogRepository.lookup(barcode)
            // Make sure we're still looking at the same scan before applying it.
            if (_uiState.value.pendingBarcode == barcode) {
                _uiState.update {
                    it.copy(
                        pendingProductName = product?.name,
                        pendingProductNameArabic = product?.nameArabic,
                        pendingUnit = product?.unit
                    )
                }
            }
        }
    }

    fun onExpiryDateSelected(date: LocalDate) {
        val formatted = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        _uiState.update {
            it.copy(
                pendingExpiryDate = formatted,
                showExpiryDialog = false,
                showQuantityDialog = true
            )
        }
    }

    fun dismissDialog() {
        _uiState.update {
            it.copy(
                showExpiryDialog    = false,
                showQuantityDialog  = false,
                showDuplicateDialog = false,
                showManualMode      = false,
                pendingBarcode      = "",
                pendingExpiryDate   = "",
                pendingProductName = null,
                pendingProductNameArabic = null,
                pendingUnit = null,
                detectedBarcode     = "",
                scannerInactive     = false
            )
        }
        startInactivityTimer()
    }

    fun dismissQuantityDialog() {
        _uiState.update {
            it.copy(
                showQuantityDialog = false,
                showManualMode     = false,
                pendingBarcode     = "",
                pendingExpiryDate  = "",
                pendingProductName = null,
                pendingProductNameArabic = null,
                pendingUnit = null,
                detectedBarcode    = "",
                scannerInactive    = false
            )
        }
        startInactivityTimer()
    }

    fun dismissDuplicateDialog() {
        _uiState.update {
            it.copy(
                showDuplicateDialog = false,
                showManualMode      = false,
                pendingBarcode      = "",
                pendingExpiryDate   = "",
                pendingProductName = null,
                pendingProductNameArabic = null,
                pendingUnit = null,
                detectedBarcode     = "",
                scannerInactive     = false
            )
        }
        startInactivityTimer()
    }

    fun onQuantityConfirmed(quantity: Double) {
        viewModelScope.launch {
            val currentState = _uiState.value
            val barcode = currentState.pendingBarcode
            val expiry  = currentState.pendingExpiryDate
            val existing = repository.findByBarcodeAndExpiry(barcode, expiry)
            if (existing != null) {
                // ── Duplicate: play extra beep + haptic (single was already played on detection)
                if (preferencesManager.isScanSoundEnabled()) soundManager.playDoubleBeep()
                if (preferencesManager.isVibrationEnabled()) vibrateDouble()
                _uiState.update {
                    it.copy(
                        duplicateExistingQty = existing.quantity,
                        duplicateNewQty      = quantity,
                        duplicateItemId      = existing.id,
                        showDuplicateDialog  = true,
                        showQuantityDialog   = false
                    )
                }
            } else {
                // New item – feedback was already given on detection; just save
                val newItem = ExpiryItemEntity(
                    barcode    = barcode,
                    expiryDate = expiry,
                    quantity   = quantity,
                    createdAt  = System.currentTimeMillis(),
                    updatedAt  = System.currentTimeMillis(),
                    productName = currentState.pendingProductName,
                    productNameArabic = currentState.pendingProductNameArabic,
                    unit = currentState.pendingUnit
                )
                repository.insertItem(newItem)
                loadRecentScans()
                _uiState.update {
                    it.copy(
                        showQuantityDialog = false,
                        pendingBarcode     = "",
                        pendingExpiryDate  = "",
                        pendingProductName = null,
                        pendingProductNameArabic = null,
                        pendingUnit = null,
                        detectedBarcode    = "",
                        scannerInactive    = false  // Start scanning instantly
                    )
                }
                startInactivityTimer()
            }
        }
    }

    fun mergeDuplicateItem() {
        viewModelScope.launch {
            val state = _uiState.value
            val existing = repository.getItemById(state.duplicateItemId) ?: return@launch
            val newQuantity = existing.quantity + state.duplicateNewQty
            val updatedItem = existing.copy(
                quantity = newQuantity,
                updatedAt = System.currentTimeMillis(),
                // Backfill product info if the existing record predates the
                // catalog lookup (e.g. items created before this feature, or
                // before products.db had this barcode).
                productName = existing.productName ?: state.pendingProductName,
                productNameArabic = existing.productNameArabic ?: state.pendingProductNameArabic,
                unit = existing.unit ?: state.pendingUnit
            )
            repository.updateItem(updatedItem.toEntity())
            loadRecentScans()
            _uiState.update {
                it.copy(
                    showDuplicateDialog = false,
                    pendingBarcode      = "",
                    pendingExpiryDate   = "",
                    pendingProductName = null,
                    pendingProductNameArabic = null,
                    pendingUnit = null,
                    detectedBarcode     = "",
                    scannerInactive     = false  // Start scanning instantly
                )
            }
            startInactivityTimer()
        }
    }

    // ── Edit / Delete for recent scan items ──────────────────────────────────

    fun requestEdit(item: ExpiryItem) {
        _uiState.update {
            it.copy(
                showEditDialog = true,
                editItemId = item.id,
                editBarcode = item.barcode,
                editProductName = item.productName,
                editExpiryDate = item.expiryDate,
                editQuantity = item.quantity
            )
        }
    }

    fun updateEditExpiryDate(date: String) {
        _uiState.update { it.copy(editExpiryDate = date) }
    }

    fun updateEditQuantity(qty: Double) {
        _uiState.update { it.copy(editQuantity = qty) }
    }

    fun confirmEdit() {
        viewModelScope.launch {
            val state = _uiState.value
            val existing = repository.getItemById(state.editItemId) ?: return@launch
            val updated = existing.copy(
                expiryDate = state.editExpiryDate,
                quantity = state.editQuantity,
                updatedAt = System.currentTimeMillis()
            )
            repository.updateItem(updated.toEntity())
            loadRecentScans()
            _uiState.update { it.copy(showEditDialog = false) }
        }
    }

    fun dismissEditDialog() {
        _uiState.update { it.copy(showEditDialog = false) }
    }

    fun requestDelete(item: ExpiryItem) {
        _uiState.update {
            it.copy(showDeleteConfirmDialog = true, deleteItemId = item.id)
        }
    }

    fun confirmDelete() {
        viewModelScope.launch {
            val state = _uiState.value
            val item = repository.getItemById(state.deleteItemId) ?: return@launch
            repository.deleteItem(item)
            loadRecentScans()
            _uiState.update { it.copy(showDeleteConfirmDialog = false, deleteItemId = 0) }
        }
    }

    fun dismissDeleteDialog() {
        _uiState.update { it.copy(showDeleteConfirmDialog = false, deleteItemId = 0) }
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    private fun loadRecentScans() {
        viewModelScope.launch {
            val allItems = repository.getAllItems().first()
            val items = allItems.sortedByDescending { it.createdAt }.take(20)
            _uiState.update { it.copy(recentScans = items) }
        }
    }

    /** Single short vibration (new barcode). */
    private fun vibrateSingle() {
        val vibrator = getVibrator()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(120)
        }
    }

    /** Two short pulses (duplicate barcode). */
    private fun vibrateDouble() {
        val vibrator = getVibrator()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // pattern: [delay, on, off, on] in ms
            val pattern = longArrayOf(0, 120, 100, 120)
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            val pattern = longArrayOf(0, 120, 100, 120)
            vibrator?.vibrate(pattern, -1)
        }
    }

    private fun getVibrator(): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ContextCompat.getSystemService(context, Vibrator::class.java)
        }
    }
}
