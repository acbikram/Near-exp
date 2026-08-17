package com.nearexpiry.manager.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nearexpiry.manager.R
import com.nearexpiry.manager.presentation.theme.CyanAccent
import com.nearexpiry.manager.utils.LanguageManager

/**
 * Shown once on first launch so the user can pick their preferred app
 * language before using the app. Selecting an option applies it
 * immediately (recreating the activity, same as the Settings toggle).
 */
@Composable
fun FirstLaunchLanguageDialog(
    onLanguageSelected: (LanguageManager.AppLanguage) -> Unit
) {
    var selected by remember { mutableStateOf(LanguageManager.getCurrentLanguage()) }

    AlertDialog(
        onDismissRequest = { /* must choose; not dismissible by tapping outside */ },
        title = {
            Text(
                stringResource(R.string.language),
                style = MaterialTheme.typography.titleMedium.copy(color = CyanAccent)
            )
        },
        text = {
            Column {
                Text(
                    stringResource(R.string.language_description),
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(10.dp))
                GlassSelectableOption(
                    label = stringResource(R.string.language_system_default),
                    selected = selected == LanguageManager.AppLanguage.SYSTEM_DEFAULT,
                    onClick = { selected = LanguageManager.AppLanguage.SYSTEM_DEFAULT }
                )
                Spacer(Modifier.height(8.dp))
                GlassSelectableOption(
                    label = stringResource(R.string.language_english),
                    selected = selected == LanguageManager.AppLanguage.ENGLISH,
                    onClick = { selected = LanguageManager.AppLanguage.ENGLISH }
                )
                Spacer(Modifier.height(8.dp))
                GlassSelectableOption(
                    label = stringResource(R.string.language_arabic),
                    selected = selected == LanguageManager.AppLanguage.ARABIC,
                    onClick = { selected = LanguageManager.AppLanguage.ARABIC }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onLanguageSelected(selected) }) {
                Text(stringResource(R.string.ok), color = CyanAccent)
            }
        }
    )
}
