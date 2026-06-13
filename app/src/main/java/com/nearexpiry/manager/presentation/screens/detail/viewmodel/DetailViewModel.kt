package com.nearexpiry.manager.presentation.screens.detail.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nearexpiry.manager.data.local.entity.toEntity
import com.nearexpiry.manager.domain.model.ExpiryItem
import com.nearexpiry.manager.domain.repository.ExpiryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val MIN_QUANTITY = 0.01
private const val MAX_QUANTITY = 99_999.0

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val repository: ExpiryRepository
) : ViewModel() {

    data class DetailUiState(
        val item: ExpiryItem? = null,
        val expiryDate: String = "",
        /** Raw text shown in the quantity field, kept separate from the validated value. */
        val quantityText: String = "0",
        /** Non-null while [quantityText] is not a valid 1..99999 quantity; blocks saving. */
        val quantityError: String? = null,
        val error: String? = null,
        val navigateBack: Boolean = false,
        val isLoading: Boolean = false,
        val isSaving: Boolean = false
    )

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    fun loadItem(itemId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val item = repository.getItemById(itemId)
                _uiState.update {
                    it.copy(
                        item = item,
                        expiryDate = item?.expiryDate ?: "",
                        quantityText = (item?.quantity ?: 0.0).let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() },
                        quantityError = null,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    fun updateExpiryDate(date: String) {
        _uiState.update { it.copy(expiryDate = date) }
    }

    /**
     * Accepts raw text from the quantity field, keeps only digits, and
     * validates against the same 1..99999 bound enforced by
     * [com.nearexpiry.manager.presentation.screens.scan.QuantityInputDialog]
     * during scanning. Saving is blocked while [DetailUiState.quantityError]
     * is non-null.
     */
    fun updateQuantity(rawText: String) {
        // Allow digits and at most one decimal point
        if (rawText.count { char -> char == '.' } > 1 || !rawText.all { char -> char.isDigit() || char == '.' }) {
            return
        }
        val parsed = rawText.toDoubleOrNull()
        val error = when {
            rawText.isEmpty() -> "Quantity is required"
            parsed == null || parsed < MIN_QUANTITY || parsed > MAX_QUANTITY ->
                "Quantity must be between $MIN_QUANTITY and $MAX_QUANTITY"
            else -> null
        }
        _uiState.update { it.copy(quantityText = rawText, quantityError = error) }
    }

    fun saveChanges() {
        val current = _uiState.value
        val quantity = current.quantityText.toDoubleOrNull()
        if (current.quantityError != null || quantity == null) {
            _uiState.update {
                it.copy(
                    quantityError = it.quantityError
                        ?: "Quantity must be between $MIN_QUANTITY and $MAX_QUANTITY"
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val updatedItem = current.item?.copy(
                    expiryDate = current.expiryDate,
                    quantity = quantity,
                    updatedAt = System.currentTimeMillis()
                ) ?: return@launch
                repository.updateItem(updatedItem.toEntity())
                _uiState.update { it.copy(navigateBack = true, isSaving = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isSaving = false) }
            }
        }
    }

    fun deleteItem() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                _uiState.value.item?.let {
                    repository.deleteItem(it)
                    _uiState.update { it.copy(navigateBack = true, isSaving = false) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isSaving = false) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
