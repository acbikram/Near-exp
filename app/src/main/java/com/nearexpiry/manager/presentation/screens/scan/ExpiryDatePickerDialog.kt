package com.nearexpiry.manager.presentation.screens.scan

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.nearexpiry.manager.presentation.theme.CyanAccent
import com.nearexpiry.manager.presentation.theme.SubtleGray
import com.nearexpiry.manager.presentation.theme.SurfaceDark
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

// ─────────────────────────────────────────────────────────────────────────────
// Constants
// ─────────────────────────────────────────────────────────────────────────────

private val ITEM_HEIGHT: Dp = 48.dp
private const val VISIBLE_ITEMS = 5          // must be odd
private const val PADDING_ITEMS = VISIBLE_ITEMS / 2   // = 2

private val MONTH_SHORT = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
)

// ─────────────────────────────────────────────────────────────────────────────
// Date range: today → end of month that is 4 months ahead
// All dates in this range are selectable; today is shown first.
// ─────────────────────────────────────────────────────────────────────────────

private data class PickerRange(val min: LocalDate, val max: LocalDate)

private fun buildRange(): PickerRange {
    val today = LocalDate.now()
    val maxMonth = today.plusMonths(4)
    val max = YearMonth.of(maxMonth.year, maxMonth.month).atEndOfMonth()
    return PickerRange(min = today, max = max)
}

// ─────────────────────────────────────────────────────────────────────────────
// Helper: days in a given month/year (full month, 1..lastDay)
// ─────────────────────────────────────────────────────────────────────────────

private fun daysInMonth(month: Int, year: Int): Int =
    YearMonth.of(year, month).lengthOfMonth()

// ─────────────────────────────────────────────────────────────────────────────
// Build flat list of all selectable dates
// ─────────────────────────────────────────────────────────────────────────────

private fun buildAllDates(range: PickerRange): List<LocalDate> {
    val dates = mutableListOf<LocalDate>()
    var d = range.min
    while (!d.isAfter(range.max)) {
        dates.add(d)
        d = d.plusDays(1)
    }
    return dates
}

// ─────────────────────────────────────────────────────────────────────────────
// Main dialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ExpiryDatePickerDialog(
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    productName: String? = null
) {
    val range  = remember { buildRange() }
    val months = remember(range) { buildMonthList(range) }   // list of (year, month)
    val scope  = rememberCoroutineScope()

    // ── State: selected month index and day index ────────────────────────────
    // Start at today: month index 0 (the month containing today), and the day
    // index corresponding to today's day-of-month within that month's full
    // 1..lastDay list (buildDayList returns every day of the month, so index
    // = dayOfMonth - 1). Previously this defaulted to 0 (the 1st of the
    // month) instead of today.
    val today = remember { LocalDate.now() }
    var selectedMonthIdx by remember { mutableIntStateOf(0) }
    var selectedDayIdx   by remember { mutableIntStateOf(today.dayOfMonth - 1) }

    // Days available for the currently selected month
    val currentMonthYear = months[selectedMonthIdx]
    val daysForMonth = remember(currentMonthYear) {
        buildDayList(currentMonthYear.first, currentMonthYear.second, range)
    }

    // Clamp day index when month changes
    LaunchedEffect(selectedMonthIdx) {
        val days = buildDayList(months[selectedMonthIdx].first, months[selectedMonthIdx].second, range)
        if (selectedDayIdx >= days.size) selectedDayIdx = days.size - 1
    }

    val selectedYear = currentMonthYear.first

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape         = RoundedCornerShape(18.dp),
            color         = SurfaceDark,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier              = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                horizontalAlignment   = Alignment.CenterHorizontally
            ) {
                // ── Title ──────────────────────────────────────────────────
                Text(
                    text  = "Select Expiry Date",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color      = CyanAccent,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                // ── Resolved product name (from local catalog), if any ───────
                if (!productName.isNullOrBlank()) {
                    Text(
                        text = productName,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                // ── Selected date preview ──────────────────────────────────
                val previewDay = if (daysForMonth.isNotEmpty()) daysForMonth[selectedDayIdx.coerceIn(0, daysForMonth.lastIndex)] else 1
                val previewMonth = currentMonthYear.second
                val previewMonthStr = MONTH_SHORT[previewMonth - 1]
                val previewMonthNum = previewMonth.toString().padStart(2, '0')
                Text(
                    text  = "${previewDay.toString().padStart(2, '0')}, $previewMonthStr ($previewMonthNum), $selectedYear",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color      = Color.White,
                        fontWeight = FontWeight.SemiBold
                    ),
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // ── Wheel pickers row ──────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ITEM_HEIGHT * VISIBLE_ITEMS)
                ) {
                    // Centre highlight band
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth()
                            .height(ITEM_HEIGHT)
                            .background(
                                color = CyanAccent.copy(alpha = 0.10f),
                                shape = RoundedCornerShape(10.dp)
                            )
                    )

                    Row(
                        modifier            = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(0.dp),
                        verticalAlignment   = Alignment.CenterVertically
                    ) {
                        // Day wheel
                        WheelPicker(
                            items           = daysForMonth.map { it.toString().padStart(2, '0') },
                            selectedIndex   = selectedDayIdx.coerceIn(0, maxOf(0, daysForMonth.lastIndex)),
                            onSelectionChange = { idx -> selectedDayIdx = idx },
                            modifier        = Modifier.weight(1f)
                        )

                        // Month wheel  (label: "Jan (01)")
                        WheelPicker(
                            items           = months.map { (_, m) ->
                                "${MONTH_SHORT[m - 1]} (${m.toString().padStart(2, '0')})"
                            },
                            selectedIndex   = selectedMonthIdx,
                            onSelectionChange = { idx -> selectedMonthIdx = idx },
                            modifier        = Modifier.weight(2f)
                        )

                        // Year (display only, auto-updates)
                        Column(
                            modifier              = Modifier
                                .weight(1f)
                                .height(ITEM_HEIGHT * VISIBLE_ITEMS),
                            horizontalAlignment   = Alignment.CenterHorizontally,
                            verticalArrangement   = Arrangement.Center
                        ) {
                            Text(
                                text       = selectedYear.toString(),
                                style      = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color      = Color.White
                                ),
                                textAlign  = TextAlign.Center
                            )
                            Text(
                                text      = "Year",
                                style     = MaterialTheme.typography.labelSmall.copy(color = SubtleGray),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    // Top + bottom fade gradients (iOS feel)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(ITEM_HEIGHT * PADDING_ITEMS)
                            .align(Alignment.TopCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(SurfaceDark, Color.Transparent)
                                )
                            )
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(ITEM_HEIGHT * PADDING_ITEMS)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, SurfaceDark)
                                )
                            )
                    )
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = Color(0xFF30363D))
                Spacer(Modifier.height(8.dp))

                // ── Action buttons ─────────────────────────────────────────
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = SubtleGray)
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            val day   = if (daysForMonth.isNotEmpty())
                                daysForMonth[selectedDayIdx.coerceIn(0, daysForMonth.lastIndex)]
                            else 1
                            val month = currentMonthYear.second
                            val year  = currentMonthYear.first
                            val date  = runCatching { LocalDate.of(year, month, day) }.getOrNull()
                            if (date != null) onDateSelected(date) else onDismiss()
                        }
                    ) {
                        Text("Done", color = CyanAccent, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Build month list: list of (year, month) pairs from range.min to range.max
// ─────────────────────────────────────────────────────────────────────────────

private fun buildMonthList(range: PickerRange): List<Pair<Int, Int>> {
    val list = mutableListOf<Pair<Int, Int>>()
    var ym = YearMonth.of(range.min.year, range.min.monthValue)
    val end = YearMonth.of(range.max.year, range.max.monthValue)
    while (!ym.isAfter(end)) {
        list.add(ym.year to ym.monthValue)
        ym = ym.plusMonths(1)
    }
    return list
}

// ─────────────────────────────────────────────────────────────────────────────
// Build day list for a given month/year within the range
// ─────────────────────────────────────────────────────────────────────────────

private fun buildDayList(year: Int, month: Int, range: PickerRange): List<Int> {
    val lastDay = daysInMonth(month, year)
    // All days 1..lastDay are selectable (no restriction per day)
    return (1..lastDay).toList()
}

// ─────────────────────────────────────────────────────────────────────────────
// iOS-style wheel picker using LazyColumn + snap fling
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WheelPicker(
    items: List<String>,
    selectedIndex: Int,
    onSelectionChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return

    val listState   = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val snapFling   = rememberSnapFlingBehavior(lazyListState = listState)
    val scope       = rememberCoroutineScope()
    var wasScrolling by remember { mutableStateOf(false) }

    // Snap + report when scroll settles
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            wasScrolling = true
        } else if (wasScrolling) {
            wasScrolling = false
            // The snap fling already snapped; just read the settled index
            val settled = listState.firstVisibleItemIndex
            onSelectionChange(settled)
        }
    }

    // Sync when parent changes selectedIndex programmatically
    LaunchedEffect(selectedIndex) {
        if (!listState.isScrollInProgress &&
            listState.firstVisibleItemIndex != selectedIndex
        ) {
            scope.launch {
                listState.animateScrollToItem(selectedIndex)
            }
        }
    }

    LazyColumn(
        state          = listState,
        flingBehavior  = snapFling,
        contentPadding = PaddingValues(vertical = ITEM_HEIGHT * PADDING_ITEMS),
        modifier       = modifier.height(ITEM_HEIGHT * VISIBLE_ITEMS)
    ) {
        items(items.size) { idx ->
            val distance = abs(listState.firstVisibleItemIndex - idx).coerceAtMost(PADDING_ITEMS + 1)

            val alpha = when (distance) {
                0    -> 1.00f
                1    -> 0.55f
                2    -> 0.25f
                else -> 0.08f
            }
            val scale = when (distance) {
                0    -> 1.00f
                1    -> 0.86f
                2    -> 0.74f
                else -> 0.62f
            }

            Box(
                modifier         = Modifier
                    .height(ITEM_HEIGHT)
                    .fillMaxWidth()
                    .alpha(alpha)
                    .scale(scale),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text       = items[idx],
                    fontSize   = 18.sp,
                    fontWeight = if (distance == 0) FontWeight.Bold else FontWeight.Normal,
                    color      = if (distance == 0) Color.White else SubtleGray,
                    textAlign  = TextAlign.Center,
                    maxLines   = 1
                )
            }
        }
    }
}
