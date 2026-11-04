package com.nearexpiry.manager.presentation.screens.scan

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nearexpiry.manager.R

@Composable
fun DamageChoiceDialog(
    onExpiry: () -> Unit,
    onReason: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val reasons = listOf(
        R.string.damage_pack_open to "Pack Open",
        R.string.damage_slow_moving to "Slow Moving",
        R.string.damage_broken to "Broken",
        R.string.damage_customer_complaint to "Customer Complaint",
        R.string.damage_label_missing to "Label Missing",
        R.string.damage_defrost to "Defrost",
        R.string.damage_customer_open to "Customer Open",
        R.string.damage_others to "Others"
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.damage_item_type)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onExpiry,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFCDD2), contentColor = Color(0xFFB71C1C)),
                    border = BorderStroke(1.dp, Color(0xFFE57373))
                ) { Text(stringResource(R.string.expiry_item)) }
                Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    reasons.forEach { (label, value) ->
                        Button(
                            onClick = { onReason(value) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                        ) { Text(stringResource(label)) }
                    }
                }
            }
        },
        confirmButton = {}
    )
}
