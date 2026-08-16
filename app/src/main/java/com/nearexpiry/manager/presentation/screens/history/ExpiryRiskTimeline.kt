package com.nearexpiry.manager.presentation.screens.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nearexpiry.manager.domain.model.ExpiryItem
import com.nearexpiry.manager.presentation.theme.AppDimens
import com.nearexpiry.manager.presentation.theme.ErrorRed
import com.nearexpiry.manager.presentation.theme.GreenAccent
import com.nearexpiry.manager.presentation.theme.OrangeAccent
import com.nearexpiry.manager.presentation.theme.SubtleGray
import com.nearexpiry.manager.presentation.theme.SurfaceVariant
import com.nearexpiry.manager.utils.ExpiryDateUtils
import java.time.LocalDate

@Composable
fun ExpiryRiskTimeline(items: List<ExpiryItem>) {
    val today = LocalDate.now()
    val counts = items.groupingBy { item ->
        when {
            ExpiryDateUtils.parseOrNull(item.expiryDate)?.isBefore(today) == true -> "Expired"
            ExpiryDateUtils.parseOrNull(item.expiryDate) == today -> "Today"
            ExpiryDateUtils.parseOrNull(item.expiryDate)?.isBefore(today.plusDays(8)) == true -> "1-7 days"
            else -> "Safe"
        }
    }.eachCount()
    val segments = listOf(
        "Expired" to ErrorRed,
        "Today" to OrangeAccent,
        "1-7 days" to Color(0xFFFFC107),
        "Safe" to GreenAccent
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.ScreenPadding, vertical = 8.dp)
            .background(SurfaceVariant, RoundedCornerShape(AppDimens.ControlRadius)),
    ) {
        Text(
            text = "Expiry risk overview",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = SubtleGray,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            segments.forEach { (label, color) ->
                val count = counts[label] ?: 0
                Column(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .background(color, RoundedCornerShape(50))
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = "$count",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = color
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = SubtleGray,
                        maxLines = 1
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}
