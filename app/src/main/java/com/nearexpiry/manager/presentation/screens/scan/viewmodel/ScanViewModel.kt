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
import com.nearexpiry.manager.domain.model.ProductInfo
import com.nearexpiry.manager.domain.repository.ExpiryRepository
import com.nearexpiry.manager.domain.repository.ProductCatalogRepository
import com.nearexpiry.manager.utils.EmbeddedBarcode
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

/** Tracks the user-initiated "Search Online" flow for unmatched barcodes. */
enum class OnlineLookupState { IDLE, LOADING, FOUND, NOT_FOUND }

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val repository: ExpiryRepository,
    private val productCatalogRepository: ProductCatalogRepository,
    private val projectRepository: com.nearexpiry.manager.domain.repository.ProjectRepository,
    private val preferencesManager: PreferencesManager,
    private val activeProjectManager: com.nearexpiry.manager.utils.ActiveProjectManager,
    private val soundManager: SoundManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    data class ScanUiState(
        val recentScans: List<ExpiryItem> = emptyList(),
        val activeProjectName: String = "",
        val scannerInactive: Boolean = false,
        /** Barcode shown on the camera overlay immediately after detection. */
        val detectedBarcode: String = "",
        val showExpiryDialog: Boolean = false,
        val showQuantityDialog: Boolean = false,
        val showDuplicateDialog: Boolean = false,
        /** Shows the "item already exists" chooser (all existing entries for the item). */
        val showExistingItemDialog: Boolean = false,
        /** Existing entries for the just-scanned item in this project (all expiry dates). */
        val existingEntries: List<com.nearexpiry.manager.domain.model.ExpiryItem> = emptyList(),
        /** When adding/replacing qty on a chosen existing entry, the entry's id. */
        val existingTargetId: Long = 0,
        /** Shows a quantity prompt for add/replace on an existing entry. */
        val showExistingQtyDialog: Boolean = false,
        /** True = add to existing qty; false = replace. */
        val existingQtyAddMode: Boolean = true,
        val pendingBarcode: String = "",
        val pendingExpiryDate: String = "",
        /** Pre-fill date for the expiry picker: last-picked date earlier today, else "" (today). */
        val initialExpiryDate: String = "",
        val pendingProductName: String? = null,
        val pendingProductNameArabic: String? = null,
        val pendingUnit: String? = null,
        val pendingItemCode: String? = null,
        /** For "22" embedded barcodes: the quantity parsed from the barcode, so
         *  the quantity dialog is skipped and only expiry is asked. Null otherwise. */
        val pendingEmbeddedQty: Double? = null,
        // Online fallback lookup (Open Food Facts), for barcodes not in the
        // bundled catalog or saved custom products.
        val onlineLookupState: OnlineLookupState = OnlineLookupState.IDLE,
        val onlineProductName: String? = null,
        val onlineProductNameArabic: String? = null,
        // ── Catalog name-search (manual entry by product name) ──────────────
        val showCatalogSearch: Boolean = false,
        val catalogSearchQuery: String = "",
        val catalogSearchResults: List<com.nearexpiry.manager.domain.model.ProductInfo> = emptyList(),
        val duplicateExistingQty: Double = 0.0,
        val duplicateNewQty: Double = 0.0,
        val duplicateItemId: Long = 0,
        // Edit dialog state
        val showEditDialog: Boolean = false,
        val editItemId: Long = 0,
        val editBarcode: String = "",
        val editProductName: String? = null,
        val editProductNameArabic: String? = null,
        val editExpiryDate: String = "",
        val editQuantity: Double = 1.0,
        // Delete confirm dialog state
        val showDeleteConfirmDialog: Boolean = false,
        val deleteItemId: Long = 0,
        // Manual barcode entry mode
        val showManualMode: Boolean = false,
        /** Transient error to surface (e.g. embedded barcode item not in catalog). */
        val scanError: String? = null
    )

    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    private var scanTimeoutJob = viewModelScope.launch { }

    init {
        loadRecentScans()
        startInactivityTimer()
        refreshInitialExpiry()
        // Reload the recent-scans list whenever the user switches projects.
        viewModelScope.launch {
            activeProjectManager.activeProjectIdFlow.collect { id ->
                loadRecentScans()
                val name = projectRepository.getProjectById(id)?.name ?: ""
                _uiState.update { it.copy(activeProjectName = name) }
            }
        }
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
        // Clear manual mode AND scannerInactive so onBarcodeScanned's guard
        // doesn't block us (manual mode sets scannerInactive = true).
        _uiState.update { it.copy(showManualMode = false, scannerInactive = false) }
        onBarcodeScanned(trimmed)
    }

    // ── Catalog name-search (manual entry by product name) ──────────────────

    fun openCatalogSearch() {
        _uiState.update {
            it.copy(showCatalogSearch = true, catalogSearchQuery = "", catalogSearchResults = emptyList())
        }
    }

    fun dismissCatalogSearch() {
        _uiState.update { it.copy(showCatalogSearch = false) }
    }

    fun onCatalogSearchQueryChange(query: String) {
        _uiState.update { it.copy(catalogSearchQuery = query) }
        viewModelScope.launch {
            // Ignore stale results if the query changed while searching.
            val results = if (query.trim().length < 2) emptyList()
                          else productCatalogRepository.searchCatalog(query)
            if (_uiState.value.catalogSearchQuery == query) {
                _uiState.update { it.copy(catalogSearchResults = results) }
            }
        }
    }

    /** User picked a product from the name-search; proceed as if it was scanned. */
    fun onCatalogProductSelected(product: com.nearexpiry.manager.domain.model.ProductInfo) {
        stopScanner()
        _uiState.update {
            it.copy(
                showCatalogSearch = false,
                showManualMode = false,
                detectedBarcode = product.barcode,
                scannerInactive = true,
                pendingBarcode = product.barcode,
                pendingProductName = product.name,
                pendingProductNameArabic = product.nameArabic,
                pendingUnit = product.unit,
                pendingItemCode = product.itemCode,
                onlineLookupState = OnlineLookupState.IDLE,
                onlineProductName = null,
                onlineProductNameArabic = null,
                showExpiryDialog = true
            )
        }
        refreshInitialExpiry()
    }

    // ── Scan flow ────────────────────────────────────────────────────────────

    fun onBarcodeScanned(barcode: String) {
        if (_uiState.value.scannerInactive) return
        if (_uiState.value.pendingBarcode.isNotEmpty()) return  // already processing one
        stopScanner()

        // Beep + vibrate on scan are always on.
        soundManager.playSingleBeep()
        vibrateSingle()

        // ── "22" embedded-weight barcode → parse item code + quantity ───────
        val embedded = EmbeddedBarcode.parse(barcode)
        if (embedded != null) {
            handleEmbeddedBarcode(embedded)
            return
        }

        // ── Resolve catalog + check for existing entries before asking expiry ─
        _uiState.update {
            it.copy(
                detectedBarcode  = barcode,
                scannerInactive  = true,
                pendingBarcode   = barcode,
                pendingProductName = null,
                pendingProductNameArabic = null,
                pendingUnit = null,
                pendingItemCode = null,
                pendingEmbeddedQty = null,
                onlineLookupState = OnlineLookupState.IDLE,
                onlineProductName = null,
                onlineProductNameArabic = null
            )
        }

        viewModelScope.launch {
            val product = productCatalogRepository.lookup(barcode)
            if (_uiState.value.pendingBarcode != barcode) return@launch
            _uiState.update {
                it.copy(
                    pendingProductName = product?.name,
                    pendingProductNameArabic = product?.nameArabic,
                    pendingUnit = product?.unit,
                    pendingItemCode = product?.itemCode
                )
            }
            // Same item already in this project? (by item code, else barcode)
            val projectId = activeProjectManager.getActiveProjectId()
            val existing = repository.findAllForItem(projectId, product?.itemCode, barcode)
            if (existing.isNotEmpty()) {
                soundManager.playDoubleBeep()
                vibrateDouble()
                _uiState.update { it.copy(existingEntries = existing, showExistingItemDialog = true) }
            } else {
                _uiState.update { it.copy(showExpiryDialog = true) }
                refreshInitialExpiry()
            }
        }
    }

    /**
     * Handles a parsed "22" embedded-weight barcode: looks the item code up in
     * the catalog (same as a normal scan). If found, pre-fills name/unit/code
     * and the barcode's quantity, then asks only for the expiry date. If the
     * item code isn't in the catalog, warns the user and does not create it.
     */
    private fun handleEmbeddedBarcode(parsed: EmbeddedBarcode.Parsed) {
        viewModelScope.launch {
            val product = productCatalogRepository.lookup(parsed.itemCode)
            if (product == null) {
                // Not in catalog → warn, reset, resume scanning.
                _uiState.update {
                    it.copy(
                        scanError = "Item code ${parsed.itemCode} not found in catalog.",
                        pendingBarcode = "",
                        detectedBarcode = "",
                        scannerInactive = false
                    )
                }
                startInactivityTimer()
                return@launch
            }
            _uiState.update {
                it.copy(
                    detectedBarcode = parsed.itemCode,
                    scannerInactive = true,
                    pendingBarcode = parsed.itemCode,
                    pendingProductName = product.name,
                    pendingProductNameArabic = product.nameArabic,
                    pendingUnit = product.unit,
                    pendingItemCode = product.itemCode ?: parsed.itemCode,
                    pendingEmbeddedQty = parsed.quantity,
                    onlineLookupState = OnlineLookupState.IDLE,
                    onlineProductName = null,
                    onlineProductNameArabic = null
                )
            }
            // Same item already in this project? Show existing-entry chooser.
            val projectId = activeProjectManager.getActiveProjectId()
            val existing = repository.findAllForItem(
                projectId, product.itemCode ?: parsed.itemCode, parsed.itemCode
            )
            if (existing.isNotEmpty()) {
                soundManager.playDoubleBeep()
                vibrateDouble()
                _uiState.update { it.copy(existingEntries = existing, showExistingItemDialog = true) }
            } else {
                _uiState.update { it.copy(showExpiryDialog = true) }
                refreshInitialExpiry()
            }
        }
    }

    // ── Existing-item chooser actions ──────────────────────────────────────

    /** User chose "add another expiry date" → proceed to the normal expiry flow. */
    fun existingAddNewDate() {
        _uiState.update { it.copy(showExistingItemDialog = false, showExpiryDialog = true) }
        refreshInitialExpiry()
    }

    /**
     * User chose Add or Replace quantity on an existing entry [entryId].
     * For embedded "22" barcodes the quantity is known, so apply immediately;
     * otherwise prompt for the quantity to add/replace.
     */
    fun existingChooseQtyAction(entryId: Long, addMode: Boolean) {
        val embeddedQty = _uiState.value.pendingEmbeddedQty
        if (embeddedQty != null) {
            applyExistingQty(entryId, addMode, embeddedQty)
        } else {
            _uiState.update {
                it.copy(
                    showExistingItemDialog = false,
                    showExistingQtyDialog = true,
                    existingTargetId = entryId,
                    existingQtyAddMode = addMode
                )
            }
        }
    }

    /** Confirms the typed quantity for the add/replace on the chosen entry. */
    fun onExistingQtyConfirmed(quantity: Double) {
        val state = _uiState.value
        applyExistingQty(state.existingTargetId, state.existingQtyAddMode, quantity)
    }

    private fun applyExistingQty(entryId: Long, addMode: Boolean, quantity: Double) {
        viewModelScope.launch {
            val existing = repository.getItemById(entryId) ?: return@launch
            val newQty = if (addMode) existing.quantity + quantity else quantity
            repository.updateItem(
                existing.copy(quantity = newQty, updatedAt = System.currentTimeMillis()).toEntity()
            )
            loadRecentScans()
            resetAfterScan()
        }
    }

    /** Clears all pending scan state and resumes scanning. */
    private fun resetAfterScan() {
        _uiState.update {
            it.copy(
                showExistingItemDialog = false,
                showExistingQtyDialog = false,
                showExpiryDialog = false,
                showQuantityDialog = false,
                showDuplicateDialog = false,
                existingEntries = emptyList(),
                existingTargetId = 0,
                pendingBarcode = "",
                pendingExpiryDate = "",
                pendingProductName = null,
                pendingProductNameArabic = null,
                pendingUnit = null,
                pendingItemCode = null,
                pendingEmbeddedQty = null,
                detectedBarcode = "",
                scannerInactive = false
            )
        }
        startInactivityTimer()
    }

    fun dismissExistingItemDialog() = resetAfterScan()
    fun dismissExistingQtyDialog() = resetAfterScan()

    /**
     * Triggers a one-shot online lookup (Open Food Facts) for the currently
     * pending barcode. Only meaningful when the local catalog/custom-products
     * lookup didn't resolve a name (pendingProductName/Arabic are both null).
     */
    fun searchOnline() {
        val barcode = _uiState.value.pendingBarcode
        if (barcode.isEmpty()) return
        _uiState.update { it.copy(onlineLookupState = OnlineLookupState.LOADING) }
        viewModelScope.launch {
            val result = productCatalogRepository.lookupOnline(barcode)
            // Bail out if the user moved on to a different scan in the meantime.
            if (_uiState.value.pendingBarcode != barcode) return@launch
            if (result != null && (result.name != null || result.nameArabic != null)) {
                _uiState.update {
                    it.copy(
                        onlineLookupState = OnlineLookupState.FOUND,
                        onlineProductName = result.name,
                        onlineProductNameArabic = result.nameArabic
                    )
                }
            } else {
                _uiState.update { it.copy(onlineLookupState = OnlineLookupState.NOT_FOUND) }
            }
        }
    }

    /**
     * Applies the result of [searchOnline] to the item currently being
     * scanned. If [saveForFutureScans] is true, also stores it in the
     * custom-products table so the next scan of this barcode resolves
     * offline without another network call.
     */
    fun useOnlineResult(saveForFutureScans: Boolean) {
        val state = _uiState.value
        val name = state.onlineProductName
        val nameAr = state.onlineProductNameArabic
        if (name == null && nameAr == null) return

        _uiState.update {
            it.copy(
                pendingProductName = name,
                pendingProductNameArabic = nameAr,
                onlineLookupState = OnlineLookupState.IDLE
            )
        }

        if (saveForFutureScans && state.pendingBarcode.isNotEmpty()) {
            viewModelScope.launch {
                productCatalogRepository.saveCustomProduct(
                    ProductInfo(
                        barcode = state.pendingBarcode,
                        name = name,
                        nameArabic = nameAr,
                        unit = state.pendingUnit,
                        itemCode = state.pendingItemCode
                    )
                )
            }
        }
    }

    /**
     * Loads the expiry date to pre-fill the picker with: the last date picked
     * earlier *today*, or "" (meaning default to today) on the first scan of a
     * new day. Call right before showing the expiry dialog.
     */
    private fun refreshInitialExpiry() {
        viewModelScope.launch {
            val todayIso = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            val remembered = preferencesManager.getLastExpiryForToday(todayIso) ?: ""
            _uiState.update { it.copy(initialExpiryDate = remembered) }
        }
    }

    fun onExpiryDateSelected(date: LocalDate) {
        val formatted = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        // Remember this pick for the rest of today so the next scan pre-fills it.
        viewModelScope.launch {
            val todayIso = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            preferencesManager.setLastExpiry(formatted, todayIso)
        }
        val embeddedQty = _uiState.value.pendingEmbeddedQty
        if (embeddedQty != null) {
            // "22" barcode: quantity came from the barcode → skip the quantity
            // dialog and save directly.
            _uiState.update {
                it.copy(pendingExpiryDate = formatted, initialExpiryDate = formatted, showExpiryDialog = false)
            }
            onQuantityConfirmed(embeddedQty)
            return
        }
        _uiState.update {
            it.copy(
                pendingExpiryDate = formatted,
                initialExpiryDate = formatted,
                showExpiryDialog = false,
                showQuantityDialog = true
            )
        }
    }

    fun clearScanError() {
        _uiState.update { it.copy(scanError = null) }
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
                pendingItemCode = null,
                pendingEmbeddedQty = null,
                onlineLookupState = OnlineLookupState.IDLE,
                onlineProductName = null,
                onlineProductNameArabic = null,
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
                pendingItemCode = null,
                pendingEmbeddedQty = null,
                onlineLookupState = OnlineLookupState.IDLE,
                onlineProductName = null,
                onlineProductNameArabic = null,
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
                pendingItemCode = null,
                pendingEmbeddedQty = null,
                onlineLookupState = OnlineLookupState.IDLE,
                onlineProductName = null,
                onlineProductNameArabic = null,
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
            val projectId = activeProjectManager.getActiveProjectId()
            // Duplicate identity: POS/item code (if any) else barcode, AND unit AND expiry.
            val existing = repository.findDuplicate(
                projectId, currentState.pendingItemCode, barcode, expiry, currentState.pendingUnit
            )
            if (existing != null) {
                // ── Duplicate: play extra beep + haptic (single was already played on detection)
                soundManager.playDoubleBeep()
                vibrateDouble()
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
                    unit = currentState.pendingUnit,
                    itemCode = currentState.pendingItemCode,
                    projectId = projectId
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
                        pendingItemCode = null,
                        pendingEmbeddedQty = null,
                        detectedBarcode    = "",
                        scannerInactive    = false
                    )
                }
                startInactivityTimer()
            }
        }
    }

    /**
     * Resolves the duplicate dialog. [mergeMode] ADD sums the new quantity
     * onto the existing item; REPLACE overwrites it with the new quantity.
     */
    fun resolveDuplicate(mergeMode: com.nearexpiry.manager.domain.model.MergeMode) {
        viewModelScope.launch {
            val state = _uiState.value
            val existing = repository.getItemById(state.duplicateItemId) ?: return@launch
            val newQuantity = if (mergeMode == com.nearexpiry.manager.domain.model.MergeMode.ADD)
                existing.quantity + state.duplicateNewQty
            else
                state.duplicateNewQty
            val updatedItem = existing.copy(
                quantity = newQuantity,
                updatedAt = System.currentTimeMillis(),
                productName = existing.productName ?: state.pendingProductName,
                productNameArabic = existing.productNameArabic ?: state.pendingProductNameArabic,
                unit = existing.unit ?: state.pendingUnit,
                itemCode = existing.itemCode ?: state.pendingItemCode
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
                    pendingItemCode = null,
                    pendingEmbeddedQty = null,
                    detectedBarcode     = "",
                    scannerInactive     = false
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
                editProductNameArabic = item.productNameArabic,
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
            val projectId = activeProjectManager.getActiveProjectId()
            val allItems = repository.getItemsOnce(projectId)
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
