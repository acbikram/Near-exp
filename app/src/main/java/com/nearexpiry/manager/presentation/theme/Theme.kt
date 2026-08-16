package com.nearexpiry.manager.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── Brand colours (matching the reference screenshot) ──────────────────────
val CyanAccent       = Color(0xFF00E5FF)   // cyan – barcode numbers, headings
val GreenAccent      = Color(0xFF00E676)   // green – scan frame, action buttons
val OrangeAccent     = Color(0xFFFFAB40)   // orange – "Developed by" text
val YellowAccent     = Color(0xFFFFEB3B)   // yellow – flashlight-on button
val DarkBackground   = Color(0xFF0D1117)   // very dark navy background
val SurfaceDark      = Color(0xFF161B22)   // card / surface background
val SurfaceVariant   = Color(0xFF1C2333)   // slightly lighter card variant
val OnSurfaceWhite   = Color(0xFFE6EDF3)   // primary text (near-white)
val SubtleGray       = Color(0xFF8B949E)   // secondary / hint text
val ErrorRed         = Color(0xFFFF5555)   // delete / error colour

// ── Colour scheme (always dark – matches the reference app) ─────────────────
private val LightColorScheme = androidx.compose.material3.lightColorScheme(
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
    background = Color(0xFFF8FBFC),
    onBackground = Color(0xFF191C1D),
    surface = Color(0xFFF8FBFC),
    onSurface = Color(0xFF191C1D),
    surfaceVariant = Color(0xFFDCE5E8),
    onSurfaceVariant = Color(0xFF40484C),
    error = Color(0xFFBA1A1A),
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary            = CyanAccent,
    onPrimary          = Color(0xFF003344),
    primaryContainer   = Color(0xFF004D66),
    onPrimaryContainer = CyanAccent,

    secondary            = GreenAccent,
    onSecondary          = Color(0xFF003300),
    secondaryContainer   = Color(0xFF004D00),
    onSecondaryContainer = GreenAccent,

    tertiary   = OrangeAccent,
    onTertiary = Color(0xFF3A1F00),

    background   = DarkBackground,
    onBackground = OnSurfaceWhite,

    surface          = SurfaceDark,
    onSurface        = OnSurfaceWhite,
    surfaceVariant   = SurfaceVariant,
    onSurfaceVariant = SubtleGray,

    error   = ErrorRed,
    onError = Color.White,

    outline        = Color(0xFF30363D),
    outlineVariant = Color(0xFF21262D),
)

@Composable
fun NearExpiryManagerTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        shapes = AppShapes,
        content = content
    )
}
