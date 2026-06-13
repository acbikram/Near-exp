package com.nearexpiry.manager.presentation.screens.scan

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.nearexpiry.manager.R

@Composable
fun DuplicateItemDialog(
    existingQty: Double,
    newQty: Double,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    productName: String? = null
) {
    val formatQty: (Double) -> String = { qty ->
        if (qty % 1.0 == 0.0) qty.toInt().toString() else qty.toString()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.same_item_found)) },
        text = {
            Column {
                if (!productName.isNullOrBlank()) {
                    Text(
                        text = productName,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                val previousQtyLabel = stringResource(R.string.previous_qty)
                val newQtyLabel = stringResource(R.string.new_qty)
                val finalQtyLabel = stringResource(R.string.final_qty)
                val confirmSaveLabel = stringResource(R.string.confirm_save_question)
                Text(
                    buildAnnotatedString {
                        append(previousQtyLabel)
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("${formatQty(existingQty)}\n") }
                        append(newQtyLabel)
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("${formatQty(newQty)}\n") }
                        append(finalQtyLabel)
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("${formatQty(existingQty + newQty)}\n") }
                        append(confirmSaveLabel)
                    }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
