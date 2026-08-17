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
private val DarkCyanAccent = Color(0xFF16D9F5)
private val DarkGreenAccent = Color(0xFF3CE5A5)
private val DarkOrangeAccent = Color(0xFFFFB35C)
private val DarkYellowAccent = Color(0xFFFFE36E)
private val DarkBackgroundColor = Color(0xFF09131B)
private val DarkSurfaceColor = Color(0xFF101E29)
private val DarkSurfaceVariantColor = Color(0xFF162A37)
private val DarkOnSurfaceColor = Color(0xFFEAF6FA)
private val DarkSubtleColor = Color(0xFF9FB5BF)
private val DarkErrorColor = Color(0xFFFF6B72)

// ── Material color schemes ─────────────────────────────────────────────────
// The light palette uses deeper, WCAG-friendly hues for readable text, icons,
// and borders on bright surfaces while retaining the app's cyan/green/orange
// visual language.
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF006C86),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8F4FC),
    onPrimaryContainer = Color(0xFF00212A),
    secondary = Color(0xFF087654),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB2F5D6),
    onSecondaryContainer = Color(0xFF002117),
    tertiary = Color(0xFF995600),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDDB7),
    onTertiaryContainer = Color(0xFF301900),
    background = Color(0xFFF1F8FC),
    onBackground = Color(0xFF14242C),
    surface = Color(0xFFFBFDFF),
    onSurface = Color(0xFF14242C),
    surfaceVariant = Color(0xFFDCECF3),
    onSurfaceVariant = Color(0xFF3D515A),
    error = Color(0xFFB32638),
    onError = Color.White,
    outline = Color(0xFF6697A7),
    outlineVariant = Color(0xFFBCDCE7)
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkCyanAccent,
    onPrimary = Color(0xFF003344),
    primaryContainer = Color(0xFF074B5C),
    onPrimaryContainer = DarkCyanAccent,
    secondary = DarkGreenAccent,
    onSecondary = Color(0xFF003300),
    secondaryContainer = Color(0xFF064A36),
    onSecondaryContainer = DarkGreenAccent,
    tertiary = DarkOrangeAccent,
    onTertiary = Color(0xFF3A1F00),
    tertiaryContainer = Color(0xFF6A3D08),
    onTertiaryContainer = DarkOrangeAccent,
    background = DarkBackgroundColor,
    onBackground = DarkOnSurfaceColor,
    surface = DarkSurfaceColor,
    onSurface = DarkOnSurfaceColor,
    surfaceVariant = DarkSurfaceVariantColor,
    onSurfaceVariant = DarkSubtleColor,
    error = DarkErrorColor,
    onError = Color.White,
    outline = Color(0xFF2A6A7B),
    outlineVariant = Color(0xFF1B3C49)
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
