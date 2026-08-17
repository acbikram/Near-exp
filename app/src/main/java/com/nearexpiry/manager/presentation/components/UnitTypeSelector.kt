package com.nearexpiry.manager.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nearexpiry.manager.R
import com.nearexpiry.manager.presentation.theme.CyanAccent

/** The only Unit Type values selectable while editing an item. */
object EditableUnitTypes {
    val values = listOf("PCS", "KG", "CTN", "OFR")

    fun normalizedOrNull(value: String): String? = when (value.trim().uppercase()) {
        "KGS" -> "KG"
        in values -> value.trim().uppercase()
        else -> null
    }
}

/**
 * Fixed Unit Type picker used by item editing surfaces. Free-text UOM entry is
 * intentionally unavailable so saved edits are limited to [EditableUnitTypes].
 */
@Composable
fun UnitTypeSelector(
    selectedUnit: String,
    onUnitSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val normalizedSelection = EditableUnitTypes.normalizedOrNull(selectedUnit)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.edit_uom_unit_type),
            style = MaterialTheme.typography.labelLarge.copy(color = CyanAccent, fontWeight = FontWeight.Bold)
        )
        EditableUnitTypes.values.chunked(2).forEach { rowOptions ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowOptions.forEach { option ->
                    GlassSelectableOption(
                        label = option,
                        selected = normalizedSelection == option,
                        onClick = { onUnitSelected(option) },
                        modifier = Modifier.weight(1f),
                        trailingContent = {}
                    )
                }
            }
        }
        Text(
            text = stringResource(R.string.choose_supported_uom),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
