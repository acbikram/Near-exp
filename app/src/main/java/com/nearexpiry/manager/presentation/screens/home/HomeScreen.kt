package com.nearexpiry.manager.presentation.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.nearexpiry.manager.R
import com.nearexpiry.manager.presentation.components.ActiveProjectHeader
import com.nearexpiry.manager.presentation.components.BottomNavigationBar
import com.nearexpiry.manager.presentation.navigation.Screen
import com.nearexpiry.manager.presentation.theme.AppDimens
import com.nearexpiry.manager.presentation.theme.CyanAccent
import com.nearexpiry.manager.presentation.theme.GreenAccent
import com.nearexpiry.manager.presentation.theme.OrangeAccent
import com.nearexpiry.manager.presentation.theme.SurfaceDark
import com.nearexpiry.manager.utils.QuantityFormatter
import com.nearexpiry.manager.presentation.theme.SurfaceVariant
import com.nearexpiry.manager.presentation.theme.SubtleGray
import com.nearexpiry.manager.utils.ExpiryDateUtils
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// Filter keys used when navigating to History with a pre-set filter
const val FILTER_ALL       = "ALL"
const val FILTER_UNIQUE    = "UNIQUE"
const val FILTER_EXPIRED   = "EXPIRED"
const val FILTER_TODAY     = "TODAY"
const val FILTER_7D        = "ONE_TO_SEVEN"
const val FILTER_30D       = "EIGHT_TO_THIRTY"
const val FILTER_QUANTITY  = "QUANTITY"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val expiryListState = rememberLazyListState()
    var hasPositionedToday by remember(uiState.activeProjectName) { mutableStateOf(false) }
    val todayExpiryIndex = uiState.expiringSoonItems.indexOfFirst { item ->
        ExpiryDateUtils.parseOrNull(item.expiryDate) == LocalDate.now()
    }

    // The Home expiry queue opens at today's first item when present. Expired
    // entries remain directly above it, and the upcoming 1-7 day entries stay
    // below it for a natural two-direction work flow.
    LaunchedEffect(todayExpiryIndex, uiState.expiringSoonItems.size, uiState.isStockMode) {
        if (!uiState.isStockMode && !hasPositionedToday && todayExpiryIndex >= 0) {
            expiryListState.scrollToItem(todayExpiryIndex)
            hasPositionedToday = true
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { BottomNavigationBar(navController) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { navController.navigate(Screen.Scan.route) },
                icon = { Icon(Icons.Default.QrCodeScanner, contentDescription = null) },
                text = { Text(stringResource(R.string.scan)) },
                containerColor = GreenAccent,
                contentColor = MaterialTheme.colorScheme.onSecondary,
                shape = MaterialTheme.shapes.large
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        // ── Fixed (non-scrolling) header + dashboard, then a scrollable
        // "Recent Scans" list that takes the remaining space. ─────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = AppDimens.ScreenPadding)
        ) {
            // ── App header ──────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .padding(top = 16.dp, bottom = 4.dp)
            ) {
                Text(
                    text = stringResource(R.string.app_title_header),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                        color = CyanAccent
                    )
                )
                Text(
                    text = buildAnnotatedString {
                        append(stringResource(R.string.developed_by))
                        withStyle(SpanStyle(color = OrangeAccent, fontWeight = FontWeight.SemiBold)) {
                            append("Bikram Acharya")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontStyle = FontStyle.Italic,
                        color = SubtleGray
                    )
                )
            }

            ActiveProjectHeader()

            Spacer(modifier = Modifier.height(16.dp))

            if (uiState.isStockMode) {
                Text(
                    text = "STOCK CHECK DASHBOARD",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = CyanAccent,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ClickableStatCard(
                        label = "Total Items",
                        value = uiState.totalRecords,
                        accentColor = CyanAccent,
                        modifier = Modifier.weight(1f),
                        onClick = { navController.navigate("${Screen.History.BASE}?filter=$FILTER_ALL&sort=SCAN_ORDER") }
                    )
                    ClickableStatCard(
                        label = "Total Quantity",
                        value = uiState.totalQuantity,
                        accentColor = GreenAccent,
                        modifier = Modifier.weight(1f),
                        onClick = { navController.navigate("${Screen.History.BASE}?filter=$FILTER_ALL&sort=QUANTITY") }
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "RECENT SCANS",
                    style = MaterialTheme.typography.titleMedium.copy(color = CyanAccent, fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(uiState.recentScanItems) { item ->
                        RecentItemCard(item = item, showExpiry = !uiState.isStockMode, onClick = {
                            viewModel.prepareItemNavigation()
                            navController.navigate(Screen.Detail.passId(item.id))
                        })
                    }
                    if (uiState.recentScanItems.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = GreenAccent, modifier = Modifier.size(34.dp))
                                Text("No stock scans yet", style = MaterialTheme.typography.titleSmall, color = GreenAccent)
                                Text("Scan catalog products to build this stock check.", style = MaterialTheme.typography.bodySmall, color = SubtleGray)
                                Button(
                                    onClick = { navController.navigate(Screen.Scan.route) },
                                    shape = MaterialTheme.shapes.small,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = GreenAccent,
                                        contentColor = MaterialTheme.colorScheme.onSecondary
                                    )
                                ) { Text(stringResource(R.string.scan)) }
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = "EXPIRY ACTION DASHBOARD",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = CyanAccent,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ClickableStatCard(label = stringResource(R.string.expired), value = uiState.expiredCount, accentColor = Color(0xFFE53935), modifier = Modifier.weight(1f), onClick = { navController.navigate("${Screen.History.BASE}?filter=$FILTER_EXPIRED&sort=EXPIRY_DATE") })
                    ClickableStatCard(label = "Today", value = uiState.expiringToday, accentColor = Color(0xFFFF7043), modifier = Modifier.weight(1f), onClick = { navController.navigate("${Screen.History.BASE}?filter=$FILTER_TODAY&sort=EXPIRY_DATE") })
                    ClickableStatCard(label = "Total Items", value = uiState.totalRecords, accentColor = CyanAccent, modifier = Modifier.weight(1f), onClick = { navController.navigate("${Screen.History.BASE}?filter=$FILTER_ALL&sort=EXPIRY_DATE") })
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ClickableStatCard(label = "1-7 Days", value = uiState.expiring1to7Days, accentColor = Color(0xFFFFC107), modifier = Modifier.weight(1f), onClick = { navController.navigate("${Screen.History.BASE}?filter=$FILTER_7D&sort=EXPIRY_DATE") })
                    ClickableStatCard(label = "8-30 Days", value = uiState.expiring8to30Days, accentColor = Color(0xFF42A5F5), modifier = Modifier.weight(1f), onClick = { navController.navigate("${Screen.History.BASE}?filter=$FILTER_30D&sort=EXPIRY_DATE") })
                    ClickableStatCard(label = "Total Quantity", value = uiState.totalQuantity, accentColor = GreenAccent, modifier = Modifier.weight(1f), onClick = { navController.navigate("${Screen.History.BASE}?filter=$FILTER_ALL&sort=QUANTITY") })
                }
                val today = LocalDate.now()
                val firstVisibleExpiryItem = uiState.expiringSoonItems
                    .getOrNull(expiryListState.firstVisibleItemIndex)
                val firstVisibleExpiryDate = firstVisibleExpiryItem?.let {
                    ExpiryDateUtils.parseOrNull(it.expiryDate)
                }
                val expirySectionLabel = when {
                    firstVisibleExpiryDate == null -> stringResource(R.string.expiring_in_7_days)
                    firstVisibleExpiryDate.isBefore(today) -> stringResource(R.string.expired)
                    firstVisibleExpiryDate == today -> stringResource(R.string.expire_today)
                    !firstVisibleExpiryDate.isAfter(today.plusDays(3)) -> stringResource(R.string.expiring_in_3_days)
                    else -> stringResource(R.string.expiring_in_7_days)
                }
                val expirySectionColor = when {
                    firstVisibleExpiryDate == null -> Color(0xFFFFC107)
                    firstVisibleExpiryDate.isBefore(today) -> Color(0xFFE53935)
                    firstVisibleExpiryDate == today -> Color(0xFFFF7043)
                    else -> Color(0xFFFFC107)
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = expirySectionLabel,
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = expirySectionColor,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(
                    state = expiryListState,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(uiState.expiringSoonItems) { item ->
                        RecentItemCard(item = item, showExpiry = !uiState.isStockMode, onClick = {
                            viewModel.prepareItemNavigation()
                            navController.navigate(Screen.Detail.passId(item.id))
                        })
                    }
                    if (uiState.expiringSoonItems.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = GreenAccent, modifier = Modifier.size(34.dp))
                                Text("Nothing needs attention right now", style = MaterialTheme.typography.titleSmall, color = GreenAccent)
                                Text("Scan products to begin tracking expiry dates.", style = MaterialTheme.typography.bodySmall, color = SubtleGray)
                                Button(
                                    onClick = { navController.navigate(Screen.Scan.route) },
                                    shape = MaterialTheme.shapes.small,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = CyanAccent,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                ) { Text(stringResource(R.string.scan)) }
                            }
                        }
                    }
                }
            }
        }
    }

    if (uiState.error != null) {
        LaunchedEffect(uiState.error) {
            scope.launch {
                snackbarHostState.showSnackbar(uiState.error!!)
                viewModel.clearError()
            }
        }
    }
}

@Composable
fun ClickableStatCard(
    label: String,
    value: Number,
    accentColor: Color = CyanAccent,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(
            modifier = Modifier.padding(AppDimens.CardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height(42.dp)
                    .background(accentColor, RoundedCornerShape(50))
            )
            Column {
                val displayValue = when (value) {
                    is Double -> QuantityFormatter.format(value)
                    is Float -> QuantityFormatter.format(value.toDouble())
                    else -> value.toString()
                }
                Text(
                    text = displayValue,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        color = accentColor,
                        fontWeight = FontWeight.ExtraBold
                    )
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium.copy(color = SubtleGray),
                    maxLines = 2
                )
            }
        }
    }
}

@Composable
fun RecentItemCard(
    item: com.nearexpiry.manager.domain.model.ExpiryItem,
    showExpiry: Boolean = true,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
            Column(modifier = Modifier.padding(AppDimens.CardPadding)) {
            Text(
                text = item.displayName,
                style = MaterialTheme.typography.titleMedium.copy(
                    color = CyanAccent,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
            // Item Code (show if we have a product name, or if barcode is the title)
            if (item.itemCode != null) {
                Text(
                    text = stringResource(R.string.item_code_format, item.itemCode),
                    style = MaterialTheme.typography.bodyMedium.copy(color = OrangeAccent)
                )
            }
            // Barcode — always shown as a secondary line when name/itemCode exist
            if (item.productName != null || item.itemCode != null) {
                Text(
                    text = stringResource(R.string.barcode_format, item.barcode),
                    style = MaterialTheme.typography.bodySmall.copy(color = SubtleGray)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (showExpiry) {
                    Surface(
                        color = GreenAccent.copy(alpha = 0.12f),
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Text(
                            text = stringResource(R.string.expiry_format, item.expiryDate),
                            style = MaterialTheme.typography.labelMedium.copy(color = GreenAccent),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                Surface(
                    color = OrangeAccent.copy(alpha = 0.14f),
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Text(
                        text = if (item.unit != null)
                            stringResource(
                                R.string.qty_unit_format,
                                QuantityFormatter.format(item.quantity),
                                item.unit
                            )
                        else
                            stringResource(
                                R.string.qty_format,
                                QuantityFormatter.format(item.quantity)
                            ),
                        style = MaterialTheme.typography.labelMedium.copy(color = OrangeAccent),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}
