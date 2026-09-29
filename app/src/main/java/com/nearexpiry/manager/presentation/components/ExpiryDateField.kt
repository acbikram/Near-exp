package com.nearexpiry.manager.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.nearexpiry.manager.R
import java.time.YearMonth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Edits an expiry date strictly in the stored "yyyy-MM-dd" format.
 *
 * Each part is a numeric field with a fixed separator. TextFieldValue is used
 * deliberately instead of String so the field selection is preserved. When a
 * part receives focus, its existing value is selected once; typing "11" over
 * "10" therefore replaces the complete month instead of allowing the IME to
 * replace only one digit. The same behavior applies to the day and year.
 */
@Composable
fun ExpiryDateField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val initial = remember { splitDate(value) }
    val scope = rememberCoroutineScope()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    var year by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initial[0]))
    }
    var month by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initial[1]))
    }
    var day by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initial[2]))
    }

    val y = year.text.toIntOrNull()
    val m = month.text.toIntOrNull()
    val maxDay = if (y != null && m != null && m in 1..12) {
        runCatching { YearMonth.of(y, m).lengthOfMonth() }.getOrDefault(31)
    } else 31
    val d = day.text.toIntOrNull()
    val yearOk = year.text.length == 4 && y != null
    val monthOk = m != null && m in 1..12
    val dayOk = d != null && d in 1..maxDay
    val allValid = yearOk && monthOk && dayOk

    fun emit() {
        if (yearOk && monthOk && dayOk) {
            onValueChange("%04d-%02d-%02d".format(y, m, d))
        } else {
            onValueChange("")
        }
    }

    fun requestEditorIntoView(focusState: FocusState) {
        if (focusState.isFocused) {
            scope.launch {
                // Wait for the IME/layout pass, then reveal the complete editor.
                delay(150)
                bringIntoViewRequester.bringIntoView()
            }
        }
    }

    fun selectAllOnFocus(current: TextFieldValue): TextFieldValue =
        if (current.text.isNotEmpty() && current.selection.collapsed) {
            current.copy(selection = TextRange(0, current.text.length))
        } else current

    fun digits(input: String, maxLength: Int): String =
        input.filter(Char::isDigit).take(maxLength)

    fun normalizedValue(input: TextFieldValue, maxLength: Int): TextFieldValue {
        val text = digits(input.text, maxLength)
        val start = input.selection.start.coerceIn(0, text.length)
        val end = input.selection.end.coerceIn(0, text.length)
        return input.copy(text = text, selection = TextRange(start, end))
    }

    Column(
        modifier = modifier.bringIntoViewRequester(bringIntoViewRequester),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = stringResource(R.string.expiry_date_label),
            style = MaterialTheme.typography.labelMedium
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = year,
                onValueChange = { input ->
                    year = normalizedValue(input, 4)
                    emit()
                },
                label = { Text(stringResource(R.string.year)) },
                singleLine = true,
                isError = year.text.isNotEmpty() && !yearOk,
                enabled = enabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .width(104.dp)
                    .onFocusChanged { focus ->
                        year = selectAllOnFocus(year)
                        requestEditorIntoView(focus)
                    }
            )
            Text("-", modifier = Modifier.padding(horizontal = 6.dp), style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = month,
                onValueChange = { input ->
                    val text = digits(input.text, 2)
                    val v = text.toIntOrNull()
                    if (text.isEmpty() || (v != null && v <= 12)) {
                        month = normalizedValue(input, 2)
                        val newMax = if (y != null && v != null && v in 1..12) {
                            runCatching { YearMonth.of(y, v).lengthOfMonth() }.getOrDefault(31)
                        } else 31
                        if ((day.text.toIntOrNull() ?: 0) > newMax) {
                            day = TextFieldValue(newMax.toString())
                        }
                        emit()
                    }
                },
                label = { Text(stringResource(R.string.month_label)) },
                singleLine = true,
                isError = month.text.isNotEmpty() && !monthOk,
                enabled = enabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .width(80.dp)
                    .onFocusChanged { focus ->
                        month = selectAllOnFocus(month)
                        requestEditorIntoView(focus)
                    }
            )
            Text("-", modifier = Modifier.padding(horizontal = 6.dp), style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = day,
                onValueChange = { input ->
                    val text = digits(input.text, 2)
                    val v = text.toIntOrNull()
                    if (text.isEmpty() || (v != null && v <= maxDay)) {
                        day = normalizedValue(input, 2)
                        emit()
                    }
                },
                label = { Text(stringResource(R.string.day_label)) },
                singleLine = true,
                isError = day.text.isNotEmpty() && !dayOk,
                enabled = enabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .width(80.dp)
                    .onFocusChanged { focus ->
                        day = selectAllOnFocus(day)
                        requestEditorIntoView(focus)
                    }
            )
        }
        if (!allValid && (year.text.isNotEmpty() || month.text.isNotEmpty() || day.text.isNotEmpty())) {
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
    val year = segs.getOrNull(0)?.filter(Char::isDigit)?.take(4) ?: ""
    val month = segs.getOrNull(1)?.filter(Char::isDigit)?.take(2) ?: ""
    val day = segs.getOrNull(2)?.filter(Char::isDigit)?.take(2) ?: ""
    return listOf(year, month, day)
}
