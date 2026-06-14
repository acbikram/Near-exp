package com.nearexpiry.manager.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── Brand colours (matching the reference screenshot) ──────────────────────
val CyanAccent       = Color(0xFF00E5FF)   // cyan – barcode numbers, headings
val GreenAccent      = Color(0xFF00E676)   // green – scan frame, action buttons
val OrangeAccent     = Color(0xFFFFAB40)   // orange – "Developed by" text
val DarkBackground   = Color(0xFF0D1117)   // very dark navy background
val SurfaceDark      = Color(0xFF161B22)   // card / surface background
val SurfaceVariant   = Color(0xFF1C2333)   // slightly lighter card variant
val OnSurfaceWhite   = Color(0xFFE6EDF3)   // primary text (near-white)
val SubtleGray       = Color(0xFF8B949E)   // secondary / hint text
val ErrorRed         = Color(0xFFFF5555)   // delete / error colour

// ── Colour scheme (always dark – matches the reference app) ─────────────────
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
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
