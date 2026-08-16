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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.nearexpiry.manager.utils.ExpiryDateUtils
import java.time.LocalDate

@Composable
fun ExpiryRiskTimeline(items: List<ExpiryItem>) {
    val today = LocalDate.now()
    val counts = items.groupingBy { item ->
        val date = ExpiryDateUtils.parseOrNull(item.expiryDate)
        when {
            date?.isBefore(today) == true -> "Expired"
            date == today -> "Today"
            date?.isBefore(today.plusDays(8)) == true -> "1-7 days"
            else -> "Safe"
        }
    }.eachCount()
    val segments = listOf(
        "Expired" to ErrorRed,
        "Today" to OrangeAccent,
        "1-7 days" to Color(0xFFFFC107),
        "Safe" to GreenAccent
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.ScreenPadding, vertical = 3.dp),
        color = com.nearexpiry.manager.presentation.theme.SurfaceVariant,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Expiry",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = SubtleGray
            )
            segments.forEach { (label, color) ->
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(5.dp)
                            .height(18.dp)
                            .background(color, RoundedCornerShape(50))
                    )
                    Column {
                        Text(
                            text = "${counts[label] ?: 0}",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
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
        }
    }
}
