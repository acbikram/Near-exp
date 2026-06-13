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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
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
const val FILTER_7D        = "SEVEN_DAYS"
const val FILTER_30D       = "THIRTY_DAYS"
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
                    text = "NEAR EXPIRY MANAGER",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                        color = CyanAccent
                    )
                )
                Text(
                    text = buildAnnotatedString {
                        append("Developed by ")
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

            Spacer(modifier = Modifier.height(16.dp))

            // ── Dashboard card ──────────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Dashboard",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = CyanAccent,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ClickableStatCard(
                            label = "Total Records",
                            value = uiState.totalRecords,
                            onClick = {
                                navController.navigate(
                                    "${Screen.History.BASE}?filter=$FILTER_ALL&sort=EXPIRY_DATE"
                                )
                            }
                        )
                        ClickableStatCard(
                            label = "Unique Products",
                            value = uiState.uniqueProducts,
                            onClick = {
                                navController.navigate(
                                    "${Screen.History.BASE}?filter=$FILTER_ALL&sort=EXPIRY_DATE"
                                )
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ClickableStatCard(
                            label = "Expiring in 7d",
                            value = uiState.expiringIn7Days,
                            accentColor = if (uiState.expiringIn7Days > 0) Color(0xFFFF7043) else CyanAccent,
                            onClick = {
                                navController.navigate(
                                    "${Screen.History.BASE}?filter=$FILTER_7D&sort=EXPIRY_DATE"
                                )
                            }
                        )
                        ClickableStatCard(
                            label = "Expiring in 30d",
                            value = uiState.expiringIn30Days,
                            accentColor = if (uiState.expiringIn30Days > 0) Color(0xFFFFCA28) else CyanAccent,
                            onClick = {
                                navController.navigate(
                                    "${Screen.History.BASE}?filter=$FILTER_30D&sort=EXPIRY_DATE"
                                )
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        ClickableStatCard(
                            label = "Total Quantity",
                            value = uiState.totalQuantity,
                            onClick = {
                                navController.navigate(
                                    "${Screen.History.BASE}?filter=$FILTER_ALL&sort=QUANTITY"
                                )
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Recent scans section header (fixed, doesn't scroll) ──────────
            Text(
                text = "Recent Scans",
                style = MaterialTheme.typography.titleMedium.copy(
                    color = CyanAccent,
                    fontWeight = FontWeight.Bold
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ── Recent scans list — only this part scrolls ───────────────────
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(uiState.recentItems) { item ->
                    RecentItemCard(
                        item = item,
                        onClick = { navController.navigate(Screen.Detail.passId(item.id)) }
                    )
                }
                if (uiState.recentItems.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No recent items", style = MaterialTheme.typography.bodyMedium)
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
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(140.dp)
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
                text = item.productName ?: item.barcode,
                style = MaterialTheme.typography.titleMedium.copy(
                    color = CyanAccent,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
            if (item.productName != null) {
                Text(
                    text = item.barcode,
                    style = MaterialTheme.typography.bodySmall.copy(color = SubtleGray)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Expiry: ${item.expiryDate}",
                    style = MaterialTheme.typography.bodyMedium.copy(color = GreenAccent)
                )
                Text(
                    text = "Qty: ${if (item.quantity % 1.0 == 0.0) item.quantity.toInt().toString() else item.quantity.toString()}",
                    style = MaterialTheme.typography.bodyMedium.copy(color = OrangeAccent)
                )
            }
            Text(
                text = "Scanned: ${formatTimestamp(item.createdAt)}",
                style = MaterialTheme.typography.bodySmall.copy(color = SubtleGray)
            )
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .format(LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault()))
}
