package com.nearexpiry.manager.presentation.screens.scan

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.nearexpiry.manager.R

@Composable
fun CartonNumberDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val (value, setValue) = remember { mutableStateOf("") }
    val requester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { requester.requestFocus(); keyboard?.show() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.enter_carton_number)) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { text -> if (text.all(Char::isDigit)) setValue(text) },
                    label = { Text(stringResource(R.string.ctn_no)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().focusRequester(requester)
                )
            }
        },
        confirmButton = { Button(onClick = { if (value.isNotBlank()) onConfirm(value) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { Button(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
