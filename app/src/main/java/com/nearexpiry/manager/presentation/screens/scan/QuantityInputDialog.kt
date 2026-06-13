package com.nearexpiry.manager.presentation.screens.scan

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun QuantityInputDialog(
    onQuantityConfirmed: (Double) -> Unit,
    onDismiss: () -> Unit,
    productName: String? = null,
    unit: String? = null
) {
    // Start empty so the field shows just a blinking cursor, matching the
    // scan flow's other "ready to type" inputs.
    var quantityText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Auto-focus the field and pop the number pad as soon as the dialog appears.
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    val handleConfirm = {
        val qty = quantityText.toDoubleOrNull()
        if (qty != null && qty > 0 && qty <= 99999) {
            onQuantityConfirmed(qty)
        } else {
            error = "Quantity must be between 0.01 and 99999"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter Quantity") },
        text = {
            Column {
                if (!productName.isNullOrBlank()) {
                    Text(
                        text = if (!unit.isNullOrBlank()) "$productName ($unit)" else productName,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                OutlinedTextField(
                    value = quantityText,
                    onValueChange = {
                        // Allow digits and at most one decimal point
                        if (it.count { char -> char == '.' } <= 1 && it.all { char -> char.isDigit() || char == '.' }) {
                            quantityText = it
                            error = null
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { handleConfirm() }
                    ),
                    label = { Text("Quantity") },
                    isError = error != null,
                    supportingText = { error?.let { Text(it) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = handleConfirm
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
