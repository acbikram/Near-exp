package com.nearexpiry.manager.presentation.screens.scan

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.nearexpiry.manager.domain.model.MergeMode
import com.nearexpiry.manager.presentation.theme.CyanAccent
import com.nearexpiry.manager.presentation.theme.OrangeAccent

/**
 * Shown when a scan/manual entry matches an existing item (same POS code +
 * expiry date + unit). Offers two resolutions:
 *  • Add     — sum the new quantity onto the existing one.
 *  • Replace — overwrite the existing quantity with the new one.
 */
@Composable
fun DuplicateItemDialog(
    existingQty: Double,
    newQty: Double,
    onResolve: (MergeMode) -> Unit,
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
                val addResultLabel = stringResource(R.string.add_result_qty)
                Text(
                    buildAnnotatedString {
                        append(previousQtyLabel)
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("${formatQty(existingQty)}\n") }
                        append(newQtyLabel)
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("${formatQty(newQty)}\n") }
                        append(addResultLabel)
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = CyanAccent)) { append(formatQty(existingQty + newQty)) }
                    }
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.duplicate_choose_action),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onResolve(MergeMode.ADD) }) {
                Text(stringResource(R.string.merge_add), color = CyanAccent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onResolve(MergeMode.REPLACE) }) {
                    Text(stringResource(R.string.merge_replace), color = OrangeAccent)
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    )
}
