package com.nearexpiry.manager.presentation.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nearexpiry.manager.presentation.theme.AppDimens
import com.nearexpiry.manager.presentation.theme.CyanAccent
import com.nearexpiry.manager.presentation.theme.ErrorRed
import com.nearexpiry.manager.presentation.theme.GreenAccent
import com.nearexpiry.manager.presentation.theme.OrangeAccent
import com.nearexpiry.manager.presentation.theme.SubtleGray

/**
 * Shared visual primitives for the app's liquid-glass design language.
 *
 * The treatment intentionally uses opaque-enough frosted surfaces and clear
 * outlines instead of relying on transparency alone. This keeps text contrast
 * and selection states accessible in both the dark and light themes.
 */
private val GlassCardShape = RoundedCornerShape(20.dp)
private val GlassControlShape = RoundedCornerShape(16.dp)

@Composable
private fun isLightAppearance(): Boolean = MaterialTheme.colorScheme.background.luminance() > 0.5f

@Composable
private fun glassSurfaceColor(selected: Boolean, accent: Color): Color {
    val light = isLightAppearance()
    // Use one opaque tinted surface instead of a translucent gradient behind a
    // transparent Material Surface. This prevents Light theme from showing a
    // rectangular white inset inside an otherwise rounded glass card.
    val base = if (light) Color(0xFFE6F3F8) else Color(0xFF132632)
    return if (selected) {
        accent.copy(alpha = if (light) 0.13f else 0.20f).compositeOver(base)
    } else {
        base
    }
}

@Composable
private fun glassOutline(selected: Boolean, accent: Color): Color {
    val light = isLightAppearance()
    return when {
        selected -> accent.copy(alpha = if (light) 0.90f else 0.96f)
        light -> accent.copy(alpha = 0.34f)
        else -> accent.copy(alpha = 0.46f)
    }
}

/** A frosted app section surface with a subtle blue border. */
@Composable
fun GlassSectionCard(
    modifier: Modifier = Modifier,
    accent: Color = CyanAccent,
    content: @Composable () -> Unit
) {
    val shape = GlassCardShape
    Surface(
        modifier = modifier
            .clip(shape)
            .border(1.dp, glassOutline(selected = false, accent = accent), shape),
        color = glassSurfaceColor(selected = false, accent = accent),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 2.dp,
        tonalElevation = 0.dp,
        shape = shape
    ) {
        content()
    }
}

/**
 * Full-width, accessible project-selector-style option. The entire row is a
 * 48dp+ touch target; selected rows receive a cyan border, frosted accent fill,
 * and a compact check indicator.
 */
@Composable
fun GlassSelectableOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    accent: Color = CyanAccent,
    leadingIcon: ImageVector? = null,
    trailingContent: (@Composable () -> Unit)? = null
) {
    val shape = GlassControlShape
    val interactionSource = remember { MutableInteractionSource() }
    val animatedElevation = animateDpAsState(
        targetValue = if (selected) 4.dp else 0.dp,
        animationSpec = tween(durationMillis = 160),
        label = "glassOptionElevation"
    )
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AppDimens.MinimumTouchTarget)
            .clip(shape)
            .border(if (selected) 1.5.dp else 1.dp, glassOutline(selected, accent), shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.RadioButton,
                onClick = onClick
            ),
        color = glassSurfaceColor(selected, accent),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = animatedElevation.value,
        tonalElevation = 0.dp,
        shape = shape
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = if (selected) accent else SubtleGray,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (selected) accent else MaterialTheme.colorScheme.onSurface
                    )
                )
                if (detail != null) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = SubtleGray
                    )
                }
            }
            if (trailingContent != null) {
                trailingContent()
            } else {
                GlassSelectionIndicator(selected = selected, accent = accent)
            }
        }
    }
}

@Composable
fun GlassSelectionIndicator(selected: Boolean, accent: Color = CyanAccent) {
    Surface(
        modifier = Modifier.size(24.dp),
        shape = CircleShape,
        color = if (selected) accent.copy(alpha = 0.18f) else Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(
            if (selected) 2.dp else 1.5.dp,
            if (selected) accent else SubtleGray.copy(alpha = 0.75f)
        )
    ) {
        if (selected) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/** A compact glass action row for navigation, import/export, and settings actions. */
@Composable
fun GlassActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    icon: ImageVector? = null,
    tone: GlassActionTone = GlassActionTone.Primary,
    enabled: Boolean = true
) {
    val accent = when (tone) {
        GlassActionTone.Primary -> CyanAccent
        GlassActionTone.Success -> GreenAccent
        GlassActionTone.Warning -> OrangeAccent
        GlassActionTone.Destructive -> ErrorRed
        GlassActionTone.Neutral -> SubtleGray
    }
    val shape = GlassControlShape
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AppDimens.MinimumTouchTarget)
            .clip(shape)
            .border(1.dp, glassOutline(selected = tone != GlassActionTone.Neutral, accent = accent), shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        color = glassSurfaceColor(selected = tone != GlassActionTone.Neutral, accent = accent),
        contentColor = accent,
        shadowElevation = if (enabled) 1.dp else 0.dp,
        tonalElevation = 0.dp,
        shape = shape
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = if (enabled) accent else SubtleGray)
                Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = if (enabled) accent else SubtleGray
                )
                if (supportingText != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(supportingText, style = MaterialTheme.typography.bodySmall, color = SubtleGray)
                }
            }
        }
    }
}

enum class GlassActionTone { Primary, Success, Warning, Destructive, Neutral }
