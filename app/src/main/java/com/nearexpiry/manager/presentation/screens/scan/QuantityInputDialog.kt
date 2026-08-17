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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nearexpiry.manager.R
import com.nearexpiry.manager.presentation.theme.CyanAccent
import com.nearexpiry.manager.presentation.theme.OrangeAccent

@Composable
fun QuantityInputDialog(
    onQuantityConfirmed: (Double) -> Unit,
    onDismiss: () -> Unit,
    productName: String? = null,
    itemCode: String? = null,
    unit: String? = null
) {
    // Start empty so the field shows just a blinking cursor, matching the
    // scan flow's other "ready to type" inputs.
    var quantityText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val quantityRangeError = stringResource(R.string.quantity_range_error)

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
            error = quantityRangeError
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = CyanAccent,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        title = { Text(stringResource(R.string.enter_quantity), fontWeight = FontWeight.Bold) },
        text = {
            Column {
                itemCode?.takeIf { it.isNotBlank() }?.let { code ->
                    Text(
                        text = stringResource(R.string.item_code_format, code),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = OrangeAccent,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
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
                    label = { Text(stringResource(R.string.quantity_label)) },
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
                Text(stringResource(R.string.save), color = CyanAccent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
