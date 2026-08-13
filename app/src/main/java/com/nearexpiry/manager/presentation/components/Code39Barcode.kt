package com.nearexpiry.manager.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Renders [value] as a Code39 barcode on a solid white card with black bars,
 * sized like a normal physical 1D barcode label — meant to be read straight
 * off the phone screen by a hand scanner or barcode-reading software.
 *
 * Code39 (not EAN-13) is used deliberately: it encodes the text exactly as
 * given, with no padding and no appended check digit, so scanning it back
 * reads precisely [value] — e.g. item code "69051" scans back as "69051".
 *
 * The card preserves the preferred module width whenever possible. If a long
 * code would exceed the available screen width, it scales the module width
 * down just enough to keep every bar and both ANSI/AIM quiet zones inside the
 * white card; no bars are clipped off the right edge.
 * Bar edges are pixel-snapped for crisp, non-blurred lines, which matters
 * for both laser scanners and software decoders.
 *
 * [onSwipeLeft]/[onSwipeRight], if given, fire only for a horizontal drag
 * that starts and stays on the visible white card itself — not the empty
 * space around it (this component centers the card in whatever width
 * [modifier] gives it, so that surrounding space is otherwise inert).
 */
@Composable
fun Code39Barcode(
    value: String,
    modifier: Modifier = Modifier,
    narrowWidthDp: Float = 2.2f,
    heightDp: Float = 80f,
    onSwipeLeft: (() -> Unit)? = null,
    onSwipeRight: (() -> Unit)? = null
) {
    val cleaned = remember(value) { sanitizeForCode39(value) }
    val density = LocalDensity.current

    var dragAccum by remember(value) { mutableStateOf(0f) }
    val swipeModifier = if (onSwipeLeft != null || onSwipeRight != null) {
        Modifier.pointerInput(value) {
            detectHorizontalDragGestures(
                onDragEnd = {
                    val threshold = 96f
                    when {
                        dragAccum <= -threshold -> onSwipeLeft?.invoke()
                        dragAccum >= threshold -> onSwipeRight?.invoke()
                    }
                    dragAccum = 0f
                },
                onDragCancel = { dragAccum = 0f }
            ) { change, dragAmount ->
                change.consume()
                dragAccum += dragAmount
            }
        }
    } else Modifier

    BoxWithConstraints(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        // The card has 10.dp padding on each side. Fit the full barcode inside
        // that space instead of letting a long Code39 value overflow its parent.
        val availableBarcodeWidthDp = (maxWidth.value - 20f).coerceAtLeast(0f)
        val effectiveNarrowWidthDp = remember(cleaned, narrowWidthDp, availableBarcodeWidthDp) {
            if (cleaned.isEmpty() || availableBarcodeWidthDp == 0f) 0f
            else min(narrowWidthDp, availableBarcodeWidthDp / totalUnits(cleaned))
        }
        val contentWidthDp = remember(cleaned, effectiveNarrowWidthDp) {
            if (cleaned.isEmpty()) 0f else barcodeWidthDp(cleaned, effectiveNarrowWidthDp)
        }

        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White)
                .then(swipeModifier)
                .padding(vertical = 14.dp, horizontal = 10.dp)
        ) {
            if (cleaned.isEmpty()) return@Column
            Canvas(
                modifier = Modifier
                    .width(contentWidthDp.dp)
                    .height(heightDp.dp)
            ) {
                val narrowWidthPx = with(density) { effectiveNarrowWidthDp.dp.toPx() }
                drawCode39(cleaned, narrowWidthPx, size)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = cleaned,
                color = Color.Black,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.width(contentWidthDp.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

/** Keeps only characters Code39 supports; uppercases letters. Never pads or truncates digits. */
private fun sanitizeForCode39(raw: String): String {
    val allowed = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ-. $/+%"
    return raw.uppercase().filter { it in allowed }
}

/** ANSI/AIM Code39 spec minimum quiet zone: 10 narrow-module widths on each side. */
private const val QUIET_ZONE_UNITS = 10f

/** Total module-width units (narrow=1, wide=3) for the *-wrapped text, incl. quiet zones. */
private fun totalUnits(text: String): Float {
    val fullText = "*$text*"
    var units = 0f
    for (ch in fullText) {
        val pattern = CODE39_PATTERNS[ch] ?: continue
        units += pattern.count { it == '0' } * 1f + pattern.count { it == '1' } * 3f
    }
    units += (fullText.length - 1) * 1f // inter-character gaps
    units += QUIET_ZONE_UNITS * 2f       // left + right quiet zones
    return units
}

private fun barcodeWidthDp(text: String, narrowWidthDp: Float): Float =
    totalUnits(text) * narrowWidthDp

/** width pattern per character: 9 elements (bar,space,bar,space,bar,space,bar,space,bar), 0=narrow 1=wide. */
private val CODE39_PATTERNS: Map<Char, String> = mapOf(
    '0' to "000110100", '1' to "100100001", '2' to "001100001", '3' to "101100000",
    '4' to "000110001", '5' to "100110000", '6' to "001110000", '7' to "000100101",
    '8' to "100100100", '9' to "001100100",
    'A' to "100001001", 'B' to "001001001", 'C' to "101001000", 'D' to "000011001",
    'E' to "100011000", 'F' to "001011000", 'G' to "000001101", 'H' to "100001100",
    'I' to "001001100", 'J' to "000011100", 'K' to "100000011", 'L' to "001000011",
    'M' to "101000010", 'N' to "000010011", 'O' to "100010010", 'P' to "001010010",
    'Q' to "000000111", 'R' to "100000110", 'S' to "001000110", 'T' to "000010110",
    'U' to "110000001", 'V' to "011000001", 'W' to "111000000", 'X' to "010010001",
    'Y' to "110010000", 'Z' to "011010000",
    '-' to "010000101", '.' to "110000100", ' ' to "011000100", '$' to "010101000",
    '/' to "010100010", '+' to "010001010", '%' to "000101010", '*' to "010010100"
)

/**
 * Draws the bars starting after the left quiet zone. Because the Canvas is
 * sized to exactly [size] = content width (computed the same way as here),
 * the right quiet zone falls out naturally with no clipping and no leftover
 * gap. Bar x-positions and widths are rounded to whole pixels so edges stay
 * crisp under Compose's anti-aliasing — soft/blurred edges are a common
 * cause of scan failures, especially for software decoders.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCode39(
    text: String,
    narrowWidthPx: Float,
    canvasSize: Size
) {
    val wideWidthPx = narrowWidthPx * 3f
    val fullText = "*$text*"
    val barHeight = canvasSize.height

    var xF = QUIET_ZONE_UNITS * narrowWidthPx
    for ((idx, ch) in fullText.withIndex()) {
        val pattern = CODE39_PATTERNS[ch] ?: continue
        for ((i, bit) in pattern.withIndex()) {
            val isBar = i % 2 == 0
            val wF = if (bit == '1') wideWidthPx else narrowWidthPx
            if (isBar) {
                val x0 = xF.roundToInt().toFloat()
                val x1 = (xF + wF).roundToInt().toFloat()
                drawRect(
                    color = Color.Black,
                    topLeft = Offset(x0, 0f),
                    size = Size(x1 - x0, barHeight)
                )
            }
            xF += wF
        }
        if (idx != fullText.length - 1) xF += narrowWidthPx
    }
}
