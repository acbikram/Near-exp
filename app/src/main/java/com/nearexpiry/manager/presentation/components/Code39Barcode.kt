package com.nearexpiry.manager.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Renders [value] as a Code39 barcode on a solid white card with black bars,
 * sized close to a normal physical 1D barcode label — meant to be read
 * straight off the phone screen by a USB/wireless hand scanner.
 *
 * Code39 (not EAN-13) is used deliberately: it encodes the text exactly as
 * given, with no padding and no appended check digit, so scanning it back
 * reads precisely [value] — e.g. item code "69051" scans back as "69051",
 * which is required since it's used directly as an Item Code lookup.
 *
 * Only characters Code39 supports are encoded (digits, A-Z, space, and
 * - . $ / + %); anything else is stripped. In practice [value] is always a
 * numeric item code or barcode, so this never matters.
 */
@Composable
fun Code39Barcode(
    value: String,
    modifier: Modifier = Modifier,
    narrowWidthDp: Float = 2.6f,
    heightDp: Float = 90f
) {
    val cleaned = remember(value) { sanitizeForCode39(value) }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .padding(vertical = 14.dp, horizontal = 18.dp)
    ) {
        if (cleaned.isEmpty()) return@Column
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(heightDp.dp)
        ) {
            drawCode39(cleaned, narrowWidthDp * density, size)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = cleaned,
            color = Color.Black,
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

/** Keeps only characters Code39 supports; uppercases letters. Never pads or truncates digits. */
private fun sanitizeForCode39(raw: String): String {
    val allowed = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ-. $/+%"
    return raw.uppercase().filter { it in allowed }
}

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

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCode39(
    text: String,
    narrowWidthPx: Float,
    canvasSize: Size
) {
    val wideWidthPx = narrowWidthPx * 3f
    val fullText = "*$text*"   // Code39 requires start/stop '*' delimiters

    // Total module-width units (narrow=1, wide=3) across all characters +
    // inter-character gaps (1 narrow unit each, between characters only).
    var totalUnits = 0f
    for (ch in fullText) {
        val pattern = CODE39_PATTERNS[ch] ?: continue
        totalUnits += pattern.count { it == '0' } * 1f + pattern.count { it == '1' } * 3f
    }
    totalUnits += (fullText.length - 1) * 1f // inter-character gaps

    val totalContentWidth = totalUnits * narrowWidthPx
    val startX = ((canvasSize.width - totalContentWidth) / 2f).coerceAtLeast(0f)

    var x = startX
    val barHeight = canvasSize.height
    for ((idx, ch) in fullText.withIndex()) {
        val pattern = CODE39_PATTERNS[ch] ?: continue
        for ((i, bit) in pattern.withIndex()) {
            val isBar = i % 2 == 0          // elements alternate bar, space, bar, ...
            val w = if (bit == '1') wideWidthPx else narrowWidthPx
            if (isBar) {
                drawRect(
                    color = Color.Black,
                    topLeft = Offset(x, 0f),
                    size = Size(w, barHeight)
                )
            }
            x += w
        }
        if (idx != fullText.length - 1) x += narrowWidthPx // inter-character gap
    }
}
