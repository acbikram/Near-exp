package com.nearexpiry.manager.presentation.theme

import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Shared dimensions for the modern inventory-dashboard visual system. */
object AppDimens {
    val ScreenPadding = 16.dp
    val CompactScreenPadding = 12.dp
    val CardPadding = 16.dp
    val CardRadius = 20.dp
    val ControlRadius = 16.dp
    val ChipRadius = 14.dp
    val SectionGap = 20.dp
    val ItemGap = 10.dp
    val MinimumTouchTarget = 48.dp
}

val AppShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(30.dp)
)
