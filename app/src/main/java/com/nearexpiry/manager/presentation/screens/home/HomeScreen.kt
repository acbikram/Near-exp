package com.nearexpiry.manager.presentation.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.nearexpiry.manager.presentation.theme.CyanAccent
import com.nearexpiry.manager.presentation.theme.GreenAccent
import com.nearexpiry.manager.presentation.theme.OrangeAccent
import com.nearexpiry.manager.presentation.theme.SurfaceDark
import com.nearexpiry.manager.presentation.theme.SurfaceVariant
import com.nearexpiry.manager.presentation.theme.SubtleGray
import kotlinx.coroutines.launch
import java.time.Instant
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { BottomNavigationBar(navController) },

        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        // ── Fixed (non-scrolling) header + dashboard, then a scrollable
        // "Recent Scans" list that takes the remaining space. ─────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
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
                ClickableStatCard(
                    label = stringResource(R.string.expired),
                    value = uiState.expiredCount,
                    accentColor = Color(0xFFE53935),
                    modifier = Modifier.weight(1f),
                    onClick = { navController.navigate("${Screen.History.BASE}?filter=$FILTER_EXPIRED&sort=EXPIRY_DATE") }
                )
                ClickableStatCard(
                    label = "Today",
                    value = uiState.expiringToday,
                    accentColor = Color(0xFFFF7043),
                    modifier = Modifier.weight(1f),
                    onClick = { navController.navigate("${Screen.History.BASE}?filter=$FILTER_TODAY&sort=EXPIRY_DATE") }
                )
                ClickableStatCard(
                    label = "Total Items",
                    value = uiState.totalRecords,
                    accentColor = CyanAccent,
                    modifier = Modifier.weight(1f),
                    onClick = { navController.navigate("${Screen.History.BASE}?filter=$FILTER_ALL&sort=EXPIRY_DATE") }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ClickableStatCard(
                    label = "1-7 Days",
                    value = uiState.expiring1to7Days,
                    accentColor = Color(0xFFFFC107),
                    modifier = Modifier.weight(1f),
                    onClick = { navController.navigate("${Screen.History.BASE}?filter=$FILTER_7D&sort=EXPIRY_DATE") }
                )
                ClickableStatCard(
                    label = "8-30 Days",
                    value = uiState.expiring8to30Days,
                    accentColor = Color(0xFF42A5F5),
                    modifier = Modifier.weight(1f),
                    onClick = { navController.navigate("${Screen.History.BASE}?filter=$FILTER_30D&sort=EXPIRY_DATE") }
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

            // ── "Expiring in 3 Days" section header (fixed, doesn't scroll) ──
            Text(
                text = stringResource(R.string.expiring_in_3_days),
                style = MaterialTheme.typography.titleMedium.copy(
                    color = CyanAccent,
                    fontWeight = FontWeight.Bold
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ── Expiring-soon list — only this part scrolls ──────────────────
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(uiState.expiringSoonItems) { item ->
                    RecentItemCard(
                        item = item,
                        onClick = {
                            viewModel.prepareItemNavigation()
                            navController.navigate(Screen.Detail.passId(item.id))
                        }
                    )
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
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val displayValue = if (value is Double) {
                if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
            } else {
                value.toString()
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
                style = MaterialTheme.typography.bodySmall.copy(color = SubtleGray),
                maxLines = 2
            )
        }
    }
}

@Composable
fun RecentItemCard(item: com.nearexpiry.manager.domain.model.ExpiryItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
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
                    style = MaterialTheme.typography.bodySmall.copy(color = SubtleGray)
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
                Text(
                    text = stringResource(R.string.expiry_format, item.expiryDate),
                    style = MaterialTheme.typography.bodyMedium.copy(color = GreenAccent)
                )
                Text(
                    text = if (item.unit != null)
                        stringResource(
                            R.string.qty_unit_format,
                            if (item.quantity % 1.0 == 0.0) item.quantity.toInt().toString() else item.quantity.toString(),
                            item.unit
                        )
                    else
                        stringResource(
                            R.string.qty_format,
                            if (item.quantity % 1.0 == 0.0) item.quantity.toInt().toString() else item.quantity.toString()
                        ),
                    style = MaterialTheme.typography.bodyMedium.copy(color = OrangeAccent)
                )
            }
        }
    }
}
