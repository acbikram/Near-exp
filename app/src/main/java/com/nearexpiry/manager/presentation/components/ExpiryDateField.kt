package com.nearexpiry.manager.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nearexpiry.manager.R
import androidx.compose.ui.res.stringResource
import java.time.YearMonth

/**
 * Edits an expiry date strictly in the stored "yyyy-MM-dd" format.
 *
 * Three separate numeric fields (Year / Month / Day) with fixed "-"
 * separators between them, so the separators can never be deleted or
 * mistyped. Each field accepts digits only and is range-validated:
 *  • year  : 4 digits, clamped to a sane window
 *  • month : 1–12
 *  • day   : 1–(last day of that month/year, leap-aware)
 *
 * The combined value is reported via [onValueChange] only when all three
 * parts form a real calendar date; otherwise [isError] is surfaced and the
 * parent keeps the previous valid value.
 */
@Composable
fun ExpiryDateField(
    value: String,                 // current stored value "yyyy-MM-dd" (may be blank)
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    // Split the incoming value once into editable parts.
    val parts = remember(value) { splitDate(value) }
    var year by remember(value) { mutableStateOf(parts[0]) }
    var month by remember(value) { mutableStateOf(parts[1]) }
    var day by remember(value) { mutableStateOf(parts[2]) }

    fun pushIfValid() {
        val y = year.toIntOrNull()
        val m = month.toIntOrNull()
        val d = day.toIntOrNull()
        if (y != null && m != null && d != null &&
            year.length == 4 && m in 1..12
        ) {
            val maxDay = runCatching { YearMonth.of(y, m).lengthOfMonth() }.getOrDefault(31)
            if (d in 1..maxDay) {
                onValueChange("%04d-%02d-%02d".format(y, m, d))
            }
        }
    }

    val y = year.toIntOrNull()
    val m = month.toIntOrNull()
    val d = day.toIntOrNull()
    val maxDayForState = if (y != null && m != null && m in 1..12)
        runCatching { YearMonth.of(y, m).lengthOfMonth() }.getOrDefault(31) else 31
    val yearOk = year.length == 4 && y != null
    val monthOk = m != null && m in 1..12
    val dayOk = d != null && d in 1..maxDayForState
    val allValid = yearOk && monthOk && dayOk

    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.expiry_date_label),
            style = MaterialTheme.typography.labelMedium
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Year (4 digits)
            OutlinedTextField(
                value = year,
                onValueChange = { input ->
                    val digits = input.filter { it.isDigit() }.take(4)
                    year = digits
                    pushIfValid()
                },
                label = { Text(stringResource(R.string.year)) },
                singleLine = true,
                isError = year.isNotEmpty() && !yearOk,
                enabled = enabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(96.dp)
            )
            Text("-", modifier = Modifier.padding(horizontal = 6.dp), style = MaterialTheme.typography.titleLarge)
            // Month (01–12)
            OutlinedTextField(
                value = month,
                onValueChange = { input ->
                    val digits = input.filter { it.isDigit() }.take(2)
                    // Reject out-of-range months as they're typed.
                    val v = digits.toIntOrNull()
                    if (digits.isEmpty() || (v != null && v <= 12)) {
                        month = digits
                        // Day might now be invalid for the new month → re-clamp on push.
                        pushIfValid()
                    }
                },
                label = { Text(stringResource(R.string.month_label)) },
                singleLine = true,
                isError = month.isNotEmpty() && !monthOk,
                enabled = enabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(72.dp)
            )
            Text("-", modifier = Modifier.padding(horizontal = 6.dp), style = MaterialTheme.typography.titleLarge)
            // Day (01–lastDayOfMonth)
            OutlinedTextField(
                value = day,
                onValueChange = { input ->
                    val digits = input.filter { it.isDigit() }.take(2)
                    val v = digits.toIntOrNull()
                    if (digits.isEmpty() || (v != null && v <= maxDayForState)) {
                        day = digits
                        pushIfValid()
                    }
                },
                label = { Text(stringResource(R.string.day_label)) },
                singleLine = true,
                isError = day.isNotEmpty() && !dayOk,
                enabled = enabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(72.dp)
            )
        }
        if (!allValid && (year.isNotEmpty() || month.isNotEmpty() || day.isNotEmpty())) {
            Text(
                text = stringResource(R.string.invalid_date_message),
                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.error)
            )
        }
    }
}

/** Splits "yyyy-MM-dd" into [year, month, day]; tolerates blanks/partials. */
private fun splitDate(value: String): List<String> {
    val segs = value.split("-")
    val year = segs.getOrNull(0)?.filter { it.isDigit() }?.take(4) ?: ""
    val month = segs.getOrNull(1)?.filter { it.isDigit() }?.take(2) ?: ""
    val day = segs.getOrNull(2)?.filter { it.isDigit() }?.take(2) ?: ""
    return listOf(year, month, day)
}
