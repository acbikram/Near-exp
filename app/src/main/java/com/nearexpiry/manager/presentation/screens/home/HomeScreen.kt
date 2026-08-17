package com.nearexpiry.manager.presentation.screens.home

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import kotlin.math.roundToLong

// Filter keys used when navigating to History with a pre-set filter
const val FILTER_ALL       = "ALL"
const val FILTER_UNIQUE    = "UNIQUE"
const val FILTER_EXPIRED   = "EXPIRED"
const val FILTER_TODAY     = "TODAY"
const val FILTER_7D        = "ONE_TO_SEVEN"
const val FILTER_30D       = "EIGHT_TO_THIRTY"
const val FILTER_QUANTITY  = "QUANTITY"

private val NORMAL_DASHBOARD_CARD_HEIGHT = 90.dp

private const val WHATSAPP_DEVELOPER_LINK =
    "https://wa.me/9779860874001?text=Hi%20Bikram,%20I%20reached%20you%20through%20the%20Near%20Expiry%20application%20can%20you%20respond%20me?"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Text(
                        text = stringResource(R.string.developed_by),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontStyle = FontStyle.Italic,
                            color = SubtleGray
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    Row(
                        modifier = Modifier.clickable {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(WHATSAPP_DEVELOPER_LINK))
                                )
                            }
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_whatsapp),
                            contentDescription = stringResource(R.string.whatsapp_contact_bikram),
                            tint = GreenAccent,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Bikram Acharya",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontStyle = FontStyle.Italic,
                                fontWeight = FontWeight.SemiBold,
                                color = OrangeAccent
                            )
                        )
                    }
                }
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
                        label = stringResource(R.string.damage_exp_qty),
                        value = uiState.stockDamageExpiryQuantity,
                        accentColor = OrangeAccent,
                        modifier = Modifier.weight(1f),
                        onClick = { navController.navigate("${Screen.History.BASE}?filter=$FILTER_ALL&sort=QUANTITY") }
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
                        RecentItemCard(
                            item = item,
                            showExpiry = !uiState.isStockMode,
                            stockTotalQuantity = uiState.stockTotalQuantityByItem[item.id],
                            onClick = {
                                viewModel.prepareItemNavigation()
                                navController.navigate(Screen.Detail.passId(item.id))
                            }
                        )
                    }
                    if (uiState.recentScanItems.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = GreenAccent, modifier = Modifier.size(34.dp))
                                Text(stringResource(R.string.no_stock_scans_yet), style = MaterialTheme.typography.titleSmall, color = GreenAccent)
                                Text(stringResource(R.string.stock_scan_empty_description), style = MaterialTheme.typography.bodySmall, color = SubtleGray)
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
                    text = stringResource(R.string.expiry_action_dashboard),
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
                    ClickableStatCard(label = stringResource(R.string.expired), value = uiState.expiredCount, accentColor = Color(0xFFE53935), modifier = Modifier.weight(1f), fixedHeight = NORMAL_DASHBOARD_CARD_HEIGHT, onClick = { navController.navigate("${Screen.History.BASE}?filter=$FILTER_EXPIRED&sort=EXPIRY_DATE") })
                    ClickableStatCard(label = stringResource(R.string.today), value = uiState.expiringToday, accentColor = Color(0xFFFF7043), modifier = Modifier.weight(1f), fixedHeight = NORMAL_DASHBOARD_CARD_HEIGHT, onClick = { navController.navigate("${Screen.History.BASE}?filter=$FILTER_TODAY&sort=EXPIRY_DATE") })
                    ClickableStatCard(label = stringResource(R.string.total_items), value = uiState.totalRecords, accentColor = CyanAccent, modifier = Modifier.weight(1f), fixedHeight = NORMAL_DASHBOARD_CARD_HEIGHT, onClick = { navController.navigate("${Screen.History.BASE}?filter=$FILTER_ALL&sort=EXPIRY_DATE") })
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ClickableStatCard(label = stringResource(R.string.one_to_seven_days), value = uiState.expiring1to7Days, accentColor = Color(0xFFFFC107), modifier = Modifier.weight(1f), fixedHeight = NORMAL_DASHBOARD_CARD_HEIGHT, onClick = { navController.navigate("${Screen.History.BASE}?filter=$FILTER_7D&sort=EXPIRY_DATE") })
                    ClickableStatCard(label = stringResource(R.string.eight_to_thirty_days), value = uiState.expiring8to30Days, accentColor = Color(0xFF42A5F5), modifier = Modifier.weight(1f), fixedHeight = NORMAL_DASHBOARD_CARD_HEIGHT, onClick = { navController.navigate("${Screen.History.BASE}?filter=$FILTER_30D&sort=EXPIRY_DATE") })
                    ClickableStatCard(label = stringResource(R.string.total_quantity), value = uiState.totalQuantity, accentColor = GreenAccent, modifier = Modifier.weight(1f), fixedHeight = NORMAL_DASHBOARD_CARD_HEIGHT, compactLongDecimal = true, onClick = { navController.navigate("${Screen.History.BASE}?filter=$FILTER_ALL&sort=QUANTITY") })
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
                                Text(stringResource(R.string.nothing_needs_attention), style = MaterialTheme.typography.titleSmall, color = GreenAccent)
                                Text(stringResource(R.string.scan_products_tracking_description), style = MaterialTheme.typography.bodySmall, color = SubtleGray)
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
    fixedHeight: Dp? = null,
    compactLongDecimal: Boolean = false,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .then(if (fixedHeight != null) Modifier.height(fixedHeight) else Modifier)
            .border(1.dp, accentColor.copy(alpha = 0.48f), MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = AppDimens.CardPadding, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height(42.dp)
                    .background(accentColor, RoundedCornerShape(50))
            )
            Column(modifier = Modifier.weight(1f)) {
                val rawDecimalValue = when (value) {
                    is Double -> value
                    is Float -> value.toDouble()
                    else -> null
                }
                val displayValue = if (compactLongDecimal && rawDecimalValue != null && rawDecimalValue.toLong().toString().length >= 5) {
                    rawDecimalValue.roundToLong().toString()
                } else {
                    rawDecimalValue?.let(QuantityFormatter::format) ?: value.toString()
                }
                val valueFontSize = when {
                    displayValue.length >= 8 -> 17.sp
                    displayValue.length >= 6 -> 20.sp
                    else -> 24.sp
                }
                Text(
                    text = displayValue,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        color = accentColor,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = valueFontSize
                    ),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip
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
    /** Non-null in Stock Mode: entered Physical Qty plus template Damage/Exp Qty. */
    stockTotalQuantity: Double? = null,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CyanAccent.copy(alpha = 0.30f), MaterialTheme.shapes.medium),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
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
                        color = GreenAccent.copy(alpha = 0.16f),
                        shape = MaterialTheme.shapes.extraSmall,
                        border = androidx.compose.foundation.BorderStroke(1.dp, GreenAccent.copy(alpha = 0.38f))
                    ) {
                        Text(
                            text = stringResource(R.string.expiry_format, item.expiryDate),
                            style = MaterialTheme.typography.labelMedium.copy(color = GreenAccent),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                Surface(
                    color = OrangeAccent.copy(alpha = 0.16f),
                    shape = MaterialTheme.shapes.extraSmall,
                    border = androidx.compose.foundation.BorderStroke(1.dp, OrangeAccent.copy(alpha = 0.38f))
                ) {
                    val displayQuantity = stockTotalQuantity ?: item.quantity
                    Text(
                        text = if (stockTotalQuantity != null && item.unit != null) {
                            stringResource(
                                R.string.total_qty_unit_format,
                                QuantityFormatter.format(displayQuantity),
                                item.unit
                            )
                        } else if (stockTotalQuantity != null) {
                            stringResource(R.string.total_qty_format, QuantityFormatter.format(displayQuantity))
                        } else if (item.unit != null) {
                            stringResource(
                                R.string.qty_unit_format,
                                QuantityFormatter.format(displayQuantity),
                                item.unit
                            )
                        } else {
                            stringResource(R.string.qty_format, QuantityFormatter.format(displayQuantity))
                        },
                        style = MaterialTheme.typography.labelMedium.copy(color = OrangeAccent),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}
