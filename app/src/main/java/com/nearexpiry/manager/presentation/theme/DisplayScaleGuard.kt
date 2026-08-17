package com.nearexpiry.manager.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.math.min

/**
 * Keeps the Compose layout usable when Android's Display size setting is set
 * to a very large value. Android reports a smaller dp viewport in that case,
 * which can make fixed controls and rows appear disproportionately large or
 * fall outside smaller phone screens.
 *
 * The guard restores a practical minimum virtual short side while retaining
 * the system's normal or smaller text preference. It only caps enlargement;
 * it does not disable scrolling, touch targets, orientation changes, or the
 * user's preferred Dark/Light/System appearance.
 */
@Composable
fun NearExpiryDisplayScaleGuard(
    isArabic: Boolean = false,
    content: @Composable () -> Unit
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val shortSide = min(configuration.screenWidthDp, configuration.screenHeightDp).dp

    // A 360dp virtual short side preserves compact controls on small Android
    // phones. The 0.80 floor avoids over-correcting unusually narrow devices.
    val layoutFactor = (shortSide / 360.dp).coerceIn(0.80f, 1f)
    val baseFontScale = min(density.fontScale, 1f)
    // Arabic glyphs need slightly more visual presence than the Latin baseline
    // at the same nominal sp size. This single theme-level adjustment scales
    // every Compose text style while preserving all existing English layouts.
    val guardedFontScale = if (isArabic) {
        (baseFontScale * 1.12f).coerceAtMost(1.14f)
    } else {
        baseFontScale
    }
    val guardedDensity = Density(
        density = density.density * layoutFactor,
        fontScale = guardedFontScale
    )

    CompositionLocalProvider(LocalDensity provides guardedDensity) {
        content()
    }
}
