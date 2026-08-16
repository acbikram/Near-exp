package com.nearexpiry.manager.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nearexpiry.manager.domain.model.Project
import com.nearexpiry.manager.domain.repository.ProjectRepository
import com.nearexpiry.manager.presentation.theme.AppDimens
import com.nearexpiry.manager.presentation.theme.CyanAccent
import com.nearexpiry.manager.presentation.theme.SurfaceVariant
import com.nearexpiry.manager.presentation.theme.SubtleGray
import com.nearexpiry.manager.utils.ActiveProjectManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ActiveProjectHeaderViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val activeProjectManager: ActiveProjectManager
) : ViewModel() {
    data class UiState(val activeId: Long = 0, val projects: List<Project> = emptyList())

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(projectRepository.getAllProjects(), activeProjectManager.activeProjectIdFlow) { projects, activeId ->
                UiState(activeId, projects)
            }
                // This header is rendered on Home immediately. A legacy Room
                // row or transient preference failure must leave the empty,
                // non-interactive header visible instead of crashing the
                // activity after first render.
                .catch { _uiState.value = UiState() }
                .collect { _uiState.value = it }
        }
    }

    fun switchProject(id: Long) {
        viewModelScope.launch {
            runCatching { activeProjectManager.setActiveProject(id) }
        }
    }
}

/** Compact project switcher used at the top of the app's primary work screens. */
@Composable
fun ActiveProjectHeader(
    modifier: Modifier = Modifier,
    viewModel: ActiveProjectHeaderViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val active = state.projects.firstOrNull { it.id == state.activeId }
    var expanded by remember { mutableStateOf(false) }
    val fallbackProjectColor = CyanAccent
    val color = remember(active?.colorHex, fallbackProjectColor) {
        runCatching { Color(android.graphics.Color.parseColor(active?.colorHex ?: "#26C6DA")) }
            .getOrDefault(fallbackProjectColor)
    }

    Box(modifier = modifier) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .clickable(enabled = state.projects.size > 1) { expanded = true },
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
            elevation = CardDefaults.cardElevation(3.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = AppDimens.CardPadding, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(50)).padding(0.dp)) {
                        Card(modifier = Modifier.size(10.dp), colors = CardDefaults.cardColors(containerColor = color)) {}
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("ACTIVE PROJECT", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = SubtleGray)
                        Text(active?.name ?: "Project", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = color)
                    }
                }
                if (state.projects.size > 1) Icon(Icons.Default.ExpandMore, contentDescription = "Switch project", tint = CyanAccent)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            state.projects.forEach { project ->
                val projectColor = runCatching { Color(android.graphics.Color.parseColor(project.colorHex)) }.getOrDefault(CyanAccent)
                DropdownMenuItem(
                    text = { Text(project.name, fontWeight = if (project.id == state.activeId) FontWeight.Bold else FontWeight.Normal) },
                    leadingIcon = { Card(modifier = Modifier.size(10.dp), colors = CardDefaults.cardColors(containerColor = projectColor)) {} },
                    trailingIcon = if (project.id == state.activeId) ({ Icon(Icons.Default.Check, contentDescription = null, tint = CyanAccent) }) else null,
                    onClick = { viewModel.switchProject(project.id); expanded = false }
                )
            }
        }
    }
}
