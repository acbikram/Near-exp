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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nearexpiry.manager.R
import java.time.YearMonth

/**
 * Edits an expiry date strictly in the stored "yyyy-MM-dd" format.
 *
 * Three numeric fields (Year / Month / Day) with fixed "-" separators, so the
 * separators can't be deleted or mistyped. Each field is digits-only and
 * range-validated:
 *  - year  : 4 digits
 *  - month : 1-12
 *  - day   : 1-(last real day of that month/year, leap-aware)
 *
 * IMPORTANT: the three fields are the single source of truth while editing.
 * The local state is seeded ONCE from the initial [value] and is NOT
 * re-synced when the parent echoes a new value back through [onValueChange]
 * - re-syncing on every keystroke is what made the field fight the user.
 * [onValueChange] is fired with a full "yyyy-MM-dd" only when all three parts
 * form a real date, and with "" whenever the date is incomplete/invalid so
 * the parent can disable Save.
 */
@Composable
fun ExpiryDateField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    // Seed ONCE from the initial value. rememberSaveable (no value key) keeps
    // these stable across recompositions and config changes, so the parent's
    // echoed value never resets what the user is typing.
    val initial = remember { splitDate(value) }
    var year by rememberSaveable { mutableStateOf(initial[0]) }
    var month by rememberSaveable { mutableStateOf(initial[1]) }
    var day by rememberSaveable { mutableStateOf(initial[2]) }

    // Derived validity for the CURRENT field contents.
    val y = year.toIntOrNull()
    val m = month.toIntOrNull()
    val maxDay = if (y != null && m != null && m in 1..12)
        runCatching { YearMonth.of(y, m).lengthOfMonth() }.getOrDefault(31) else 31
    val d = day.toIntOrNull()

    val yearOk = year.length == 4 && y != null
    val monthOk = m != null && m in 1..12
    val dayOk = d != null && d in 1..maxDay
    val allValid = yearOk && monthOk && dayOk

    // Push the combined result up. Emits a full date when valid, otherwise ""
    // so the parent knows it's not yet savable.
    fun emit() {
        if (yearOk && monthOk && dayOk) {
            onValueChange("%04d-%02d-%02d".format(y, m, d))
        } else {
            onValueChange("")
        }
    }

    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.expiry_date_label),
            style = MaterialTheme.typography.labelMedium
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = year,
                onValueChange = { input ->
                    year = input.filter { it.isDigit() }.take(4)
                    emit()
                },
                label = { Text(stringResource(R.string.year)) },
                singleLine = true,
                isError = year.isNotEmpty() && !yearOk,
                enabled = enabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(104.dp)
            )
            Text("-", modifier = Modifier.padding(horizontal = 6.dp), style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = month,
                onValueChange = { input ->
                    val digits = input.filter { it.isDigit() }.take(2)
                    val v = digits.toIntOrNull()
                    // Allow empty or anything that can still become 1-12.
                    if (digits.isEmpty() || (v != null && v <= 12)) {
                        month = digits
                        // If the day now exceeds the new month's length, trim it.
                        val newMax = if (y != null && v != null && v in 1..12)
                            runCatching { YearMonth.of(y, v).lengthOfMonth() }.getOrDefault(31) else 31
                        if ((day.toIntOrNull() ?: 0) > newMax) day = newMax.toString()
                        emit()
                    }
                },
                label = { Text(stringResource(R.string.month_label)) },
                singleLine = true,
                isError = month.isNotEmpty() && !monthOk,
                enabled = enabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(80.dp)
            )
            Text("-", modifier = Modifier.padding(horizontal = 6.dp), style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = day,
                onValueChange = { input ->
                    val digits = input.filter { it.isDigit() }.take(2)
                    val v = digits.toIntOrNull()
                    if (digits.isEmpty() || (v != null && v <= maxDay)) {
                        day = digits
                        emit()
                    }
                },
                label = { Text(stringResource(R.string.day_label)) },
                singleLine = true,
                isError = day.isNotEmpty() && !dayOk,
                enabled = enabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(80.dp)
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
