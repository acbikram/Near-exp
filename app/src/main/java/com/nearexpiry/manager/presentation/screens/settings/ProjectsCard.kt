package com.nearexpiry.manager.presentation.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nearexpiry.manager.R
import com.nearexpiry.manager.presentation.theme.CyanAccent
import com.nearexpiry.manager.presentation.theme.ErrorRed
import com.nearexpiry.manager.presentation.theme.SubtleGray
import com.nearexpiry.manager.presentation.theme.SurfaceVariant

/**
 * The "Projects" section shown in Settings: a selectable list of projects
 * (each with a colour tag, item count and nearest expiry), plus create,
 * rename, recolor, clone, delete, and switch actions.
 */
@Composable
fun ProjectsSection(
    projects: List<SettingsViewModel.ProjectSummary>,
    activeProjectId: Long,
    onSwitch: (Long) -> Unit,
    onCreate: (String, String) -> Unit,
    onRename: (Long, String) -> Unit,
    onRecolor: (Long, String) -> Unit,
    onClone: (Long, String, String) -> Unit,
    onDelete: (Long) -> Unit
) {
    var showCreate by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Long?>(null) }
    var cloneTarget by remember { mutableStateOf<Long?>(null) }
    var deleteTarget by remember { mutableStateOf<Long?>(null) }
    var colorTarget by remember { mutableStateOf<Long?>(null) }
    var expanded by rememberSaveable { mutableStateOf(false) }

    Column(modifier = Modifier.padding(16.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.projects),
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = CyanAccent,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    if (expanded) stringResource(R.string.projects_description)
                    else "${projects.size} project${if (projects.size == 1) "" else "s"} • Tap to select or manage",
                    style = MaterialTheme.typography.bodySmall.copy(color = SubtleGray)
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse projects" else "Expand projects",
                tint = CyanAccent
            )
        }

        if (expanded) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { showCreate = true }) {
                Icon(Icons.Default.Add, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.project_create), color = CyanAccent)
            }
            projects.forEach { summary ->
                ProjectRow(
                    summary = summary,
                    isActive = summary.project.id == activeProjectId,
                    onSwitch = { onSwitch(summary.project.id) },
                    onRename = { renameTarget = summary.project.id },
                    onClone = { cloneTarget = summary.project.id },
                    onRecolor = { colorTarget = summary.project.id },
                    onDelete = { deleteTarget = summary.project.id }
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    // ── Create ─────────────────────────────────────────────────────────────
    if (showCreate) {
        ProjectNameColorDialog(
            title = stringResource(R.string.project_create),
            initialName = "",
            initialColor = PROJECT_COLORS.first(),
            confirmLabel = stringResource(R.string.project_create),
            onConfirm = { name, color -> onCreate(name, color); showCreate = false },
            onDismiss = { showCreate = false }
        )
    }

    // ── Rename ─────────────────────────────────────────────────────────────
    renameTarget?.let { id ->
        val current = projects.firstOrNull { it.project.id == id }
        ProjectNameColorDialog(
            title = stringResource(R.string.project_rename),
            initialName = current?.project?.name ?: "",
            initialColor = current?.project?.colorHex ?: PROJECT_COLORS.first(),
            confirmLabel = stringResource(R.string.save),
            showColor = false,
            onConfirm = { name, _ -> onRename(id, name); renameTarget = null },
            onDismiss = { renameTarget = null }
        )
    }

    // ── Recolor ────────────────────────────────────────────────────────────
    colorTarget?.let { id ->
        val current = projects.firstOrNull { it.project.id == id }
        ColorPickerDialog(
            initialColor = current?.project?.colorHex ?: PROJECT_COLORS.first(),
            onConfirm = { color -> onRecolor(id, color); colorTarget = null },
            onDismiss = { colorTarget = null }
        )
    }

    // ── Clone ──────────────────────────────────────────────────────────────
    cloneTarget?.let { id ->
        val current = projects.firstOrNull { it.project.id == id }
        ProjectNameColorDialog(
            title = stringResource(R.string.project_clone),
            initialName = (current?.project?.name ?: "") + " (Copy)",
            initialColor = current?.project?.colorHex ?: PROJECT_COLORS.first(),
            confirmLabel = stringResource(R.string.project_clone),
            onConfirm = { name, color -> onClone(id, name, color); cloneTarget = null },
            onDismiss = { cloneTarget = null }
        )
    }

    // ── Delete ─────────────────────────────────────────────────────────────
    deleteTarget?.let { id ->
        val current = projects.firstOrNull { it.project.id == id }
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.project_delete)) },
            text = { Text(stringResource(R.string.project_delete_confirm, current?.project?.name ?: "")) },
            confirmButton = {
                TextButton(onClick = { onDelete(id); deleteTarget = null }) {
                    Text(stringResource(R.string.delete), color = ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun ProjectRow(
    summary: SettingsViewModel.ProjectSummary,
    isActive: Boolean,
    onSwitch: () -> Unit,
    onRename: () -> Unit,
    onClone: () -> Unit,
    onRecolor: () -> Unit,
    onDelete: () -> Unit
) {
    val color = runCatching { Color(android.graphics.Color.parseColor(summary.project.colorHex)) }
        .getOrDefault(CyanAccent)

    Surface(
        color = if (isActive) CyanAccent.copy(alpha = 0.12f) else SurfaceVariant,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isActive) Modifier.border(1.dp, CyanAccent, RoundedCornerShape(10.dp)) else Modifier
            )
            .clickable { onSwitch() }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Colour tag (tap to recolor)
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(color, CircleShape)
                    .clickable { onRecolor() }
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        summary.project.name,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    if (isActive) {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Default.Check, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(16.dp))
                    }
                }
                val summaryText = if (summary.nearestExpiry != null) {
                    stringResource(R.string.project_summary_format, summary.itemCount, summary.nearestExpiry)
                } else {
                    stringResource(R.string.project_summary_no_expiry_format, summary.itemCount)
                }
                Text(summaryText, style = MaterialTheme.typography.bodySmall.copy(color = SubtleGray))
            }
            IconButton(onClick = onRename) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.project_rename), tint = CyanAccent, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onClone) {
                Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.project_clone), tint = CyanAccent, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.project_delete), tint = ErrorRed, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun ProjectNameColorDialog(
    title: String,
    initialName: String,
    initialColor: String,
    confirmLabel: String,
    showColor: Boolean = true,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var color by remember { mutableStateOf(initialColor) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.project_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (showColor) {
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.project_color_label), style = MaterialTheme.typography.labelMedium.copy(color = SubtleGray))
                    Spacer(Modifier.height(6.dp))
                    ColorSwatchRow(selected = color, onSelect = { color = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, color) }, enabled = name.isNotBlank()) {
                Text(confirmLabel, color = CyanAccent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun ColorPickerDialog(
    initialColor: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var color by remember { mutableStateOf(initialColor) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.project_color_label)) },
        text = { ColorSwatchRow(selected = color, onSelect = { color = it }) },
        confirmButton = {
            TextButton(onClick = { onConfirm(color) }) { Text(stringResource(R.string.save), color = CyanAccent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun ColorSwatchRow(selected: String, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PROJECT_COLORS.forEach { hex ->
            val c = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(CyanAccent)
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(c, CircleShape)
                    .then(
                        if (hex == selected) Modifier.border(2.dp, Color.White, CircleShape) else Modifier
                    )
                    .clickable { onSelect(hex) }
            )
        }
    }
}
