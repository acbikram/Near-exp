package com.nearexpiry.manager.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.nearexpiry.manager.R
import com.nearexpiry.manager.presentation.theme.CyanAccent

/**
 * Required second step of first-startup onboarding. This is presented only
 * after language selection, and the selected preference is persisted before
 * the user continues into the app.
 */
@Composable
fun FirstLaunchThemeDialog(
    initialMode: String,
    onConfirm: (String) -> Unit
) {
    var selectedMode by remember(initialMode) { mutableStateOf(initialMode) }
    val options = listOf(
        "dark" to R.string.theme_dark,
        "light" to R.string.theme_light,
        "system" to R.string.theme_system
    )

    AlertDialog(
        onDismissRequest = { /* must choose; not dismissible by tapping outside */ },
        title = {
            Text(
                stringResource(R.string.appearance),
                style = MaterialTheme.typography.titleMedium.copy(color = CyanAccent)
            )
        },
        text = {
            Column {
                Text(
                    stringResource(R.string.theme_selection_description),
                    style = MaterialTheme.typography.bodySmall
                )
                options.forEach { (mode, labelRes) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedMode = mode },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedMode == mode,
                            onClick = { selectedMode = mode },
                            colors = RadioButtonDefaults.colors(selectedColor = CyanAccent)
                        )
                        Text(stringResource(labelRes))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedMode) }) {
                Text(stringResource(R.string.ok), color = CyanAccent)
            }
        }
    )
}
