package com.nearexpiry.manager.presentation.screens.scan.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp

private val ScannerGlowColor = Color(0xFF00E676)
private val MaskColor        = Color(0xCC000000)

/**
 * Draws an animated glowing scanner frame on top of the camera preview.
 *
 * The area OUTSIDE the glowing box is covered with a dark mask so the
 * camera preview appears ONLY inside the frame — the black box stays
 * strictly inside the glowing border.
 */
@Composable
fun BarcodeScannerOverlay(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "barcode_scanner_overlay")

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.40f,
        targetValue  = 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    val scanLineProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue  = 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scan_line_progress"
    )

    val frameColor = ScannerGlowColor.copy(alpha = glowAlpha)

    BoxWithConstraints(
        modifier         = modifier,
        contentAlignment = Alignment.Center
    ) {
        val frameWidth  = maxWidth  * 0.92f
        val frameHeight = maxHeight * 0.80f
        val scanLineHeight = 3.dp
        val scanLineOffset = (frameHeight - scanLineHeight) * scanLineProgress

        // ── Dark mask outside the scan frame ─────────────────────────────
        Canvas(modifier = Modifier.fillMaxSize()) {
            val fw   = frameWidth.toPx()
            val fh   = frameHeight.toPx()
            val left = (size.width  - fw) / 2f
            val top  = (size.height - fh) / 2f

            // Top strip
            drawRect(MaskColor, topLeft = Offset(0f, 0f),
                size = Size(size.width, top))
            // Bottom strip
            drawRect(MaskColor, topLeft = Offset(0f, top + fh),
                size = Size(size.width, size.height - top - fh))
            // Left strip
            drawRect(MaskColor, topLeft = Offset(0f, top),
                size = Size(left, fh))
            // Right strip
            drawRect(MaskColor, topLeft = Offset(left + fw, top),
                size = Size(size.width - left - fw, fh))
        }

        // ── Glowing border + corner brackets + scan line ──────────────────
        Box(
            modifier = Modifier
                .width(frameWidth)
                .height(frameHeight)
                .shadow(
                    elevation    = 14.dp,
                    shape        = RoundedCornerShape(14.dp),
                    ambientColor = frameColor,
                    spotColor    = frameColor
                )
                .border(
                    width = 2.dp,
                    color = frameColor,
                    shape = RoundedCornerShape(14.dp)
                )
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cl  = size.minDimension * 0.20f
                val sw  = 5.dp.toPx()
                val cap = StrokeCap.Round
                // Top-left
                drawLine(frameColor, Offset(0f, cl),              Offset(0f, 0f),              sw, cap)
                drawLine(frameColor, Offset(0f, 0f),              Offset(cl, 0f),              sw, cap)
                // Top-right
                drawLine(frameColor, Offset(size.width - cl, 0f), Offset(size.width, 0f),      sw, cap)
                drawLine(frameColor, Offset(size.width, 0f),      Offset(size.width, cl),      sw, cap)
                // Bottom-left
                drawLine(frameColor, Offset(0f, size.height - cl),Offset(0f, size.height),     sw, cap)
                drawLine(frameColor, Offset(0f, size.height),     Offset(cl, size.height),     sw, cap)
                // Bottom-right
                drawLine(frameColor, Offset(size.width - cl, size.height), Offset(size.width, size.height), sw, cap)
                drawLine(frameColor, Offset(size.width, size.height - cl), Offset(size.width, size.height), sw, cap)
            }

            // Animated scan line sweeping top → bottom inside the frame
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(scanLineHeight)
                    .offset(y = scanLineOffset)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                ScannerGlowColor.copy(alpha = 0.95f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }
    }
}

