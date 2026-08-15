package com.nearexpiry.manager.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
            text = "UOM / Unit Type",
            style = MaterialTheme.typography.labelLarge.copy(color = CyanAccent, fontWeight = FontWeight.Bold)
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            EditableUnitTypes.values.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = normalizedSelection == option,
                    onClick = { onUnitSelected(option) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = EditableUnitTypes.values.size),
                    label = { Text(option) }
                )
            }
        }
        Text(
            text = "Allowed: PCS, KG, CTN, OFR",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
