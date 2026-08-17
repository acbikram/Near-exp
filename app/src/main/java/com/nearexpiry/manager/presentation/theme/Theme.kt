package com.nearexpiry.manager.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── Dark-mode brand palette ────────────────────────────────────────────────
// These values are used only to construct the dark Material color scheme.
// Screens consume the semantic accessors below, so all UI automatically adapts
// when the user chooses Light or System appearance.
private val DarkCyanAccent = Color(0xFF00E5FF)
private val DarkGreenAccent = Color(0xFF00E676)
private val DarkOrangeAccent = Color(0xFFFFAB40)
private val DarkYellowAccent = Color(0xFFFFEB3B)
private val DarkBackgroundColor = Color(0xFF0D1117)
private val DarkSurfaceColor = Color(0xFF161B22)
private val DarkSurfaceVariantColor = Color(0xFF1C2333)
private val DarkOnSurfaceColor = Color(0xFFE6EDF3)
private val DarkSubtleColor = Color(0xFF8B949E)
private val DarkErrorColor = Color(0xFFFF5555)

// ── Material color schemes ─────────────────────────────────────────────────
// The light palette uses deeper, WCAG-friendly hues for readable text, icons,
// and borders on bright surfaces while retaining the app's cyan/green/orange
// visual language.
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF006778),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFAEEDFA),
    onPrimaryContainer = Color(0xFF001F26),
    secondary = Color(0xFF006E1C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB5F2AE),
    onSecondaryContainer = Color(0xFF002106),
    tertiary = Color(0xFF8A5100),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDDB8),
    onTertiaryContainer = Color(0xFF2C1700),
    background = Color(0xFFF8FBFC),
    onBackground = Color(0xFF191C1D),
    surface = Color(0xFFF8FBFC),
    onSurface = Color(0xFF191C1D),
    surfaceVariant = Color(0xFFDCE5E8),
    onSurfaceVariant = Color(0xFF40484C),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    outline = Color(0xFF70787C),
    outlineVariant = Color(0xFFC0C8CC)
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkCyanAccent,
    onPrimary = Color(0xFF003344),
    primaryContainer = Color(0xFF004D66),
    onPrimaryContainer = DarkCyanAccent,
    secondary = DarkGreenAccent,
    onSecondary = Color(0xFF003300),
    secondaryContainer = Color(0xFF004D00),
    onSecondaryContainer = DarkGreenAccent,
    tertiary = DarkOrangeAccent,
    onTertiary = Color(0xFF3A1F00),
    tertiaryContainer = Color(0xFF663600),
    onTertiaryContainer = DarkOrangeAccent,
    background = DarkBackgroundColor,
    onBackground = DarkOnSurfaceColor,
    surface = DarkSurfaceColor,
    onSurface = DarkOnSurfaceColor,
    surfaceVariant = DarkSurfaceVariantColor,
    onSurfaceVariant = DarkSubtleColor,
    error = DarkErrorColor,
    onError = Color.White,
    outline = Color(0xFF30363D),
    outlineVariant = Color(0xFF21262D)
)

// ── Appearance-aware UI palette ────────────────────────────────────────────
// Keep these established names so all existing screens transparently receive
// the correct Material colors in Dark, Light, and System appearance modes.
val CyanAccent: Color
    @Composable get() = MaterialTheme.colorScheme.primary

val GreenAccent: Color
    @Composable get() = MaterialTheme.colorScheme.secondary

val OrangeAccent: Color
    @Composable get() = MaterialTheme.colorScheme.tertiary

val YellowAccent: Color
    @Composable get() = if (MaterialTheme.colorScheme.background == DarkBackgroundColor) {
        DarkYellowAccent
    } else {
        // Gold preserves the filled-action emphasis in Light Theme while
        // keeping black button content crisp and legible.
        Color(0xFFE3B400)
    }

val DarkBackground: Color
    @Composable get() = MaterialTheme.colorScheme.background

val SurfaceDark: Color
    @Composable get() = MaterialTheme.colorScheme.surface

val SurfaceVariant: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceVariant

val OnSurfaceWhite: Color
    @Composable get() = MaterialTheme.colorScheme.onSurface

val SubtleGray: Color
    @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

val ErrorRed: Color
    @Composable get() = MaterialTheme.colorScheme.error

@Composable
fun NearExpiryManagerTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        shapes = AppShapes
    ) {
        NearExpiryDisplayScaleGuard(content)
    }
}
