package com.nearexpiry.manager.presentation.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.nearexpiry.manager.R
import com.nearexpiry.manager.presentation.navigation.Screen
import com.nearexpiry.manager.presentation.theme.CyanAccent
import com.nearexpiry.manager.presentation.theme.SurfaceDark
import com.nearexpiry.manager.presentation.theme.SubtleGray

private data class NavItem(
    val screen: Screen,
    val labelRes: Int,
    val icon: ImageVector
)

@Composable
fun BottomNavigationBar(navController: NavController) {

    val items = listOf(
        NavItem(Screen.Home,     R.string.home,     Icons.Default.Home),
        NavItem(Screen.Scan,     R.string.scan,     Icons.Default.QrCodeScanner),
        NavItem(Screen.History,  R.string.history,  Icons.Default.History),
        NavItem(Screen.Export,   R.string.export,   Icons.Default.FileUpload),
        NavItem(Screen.Settings, R.string.settings, Icons.Default.Settings)
    )

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val railShape = RoundedCornerShape(28.dp)
    val railColor = if (isLight) {
        Color(0xFFEAF6FA).copy(alpha = 0.94f)
    } else {
        Color(0xFF0D1D28).copy(alpha = 0.94f)
    }
    val railBorder = CyanAccent.copy(alpha = if (isLight) 0.38f else 0.54f)
    val selectedPill = CyanAccent.copy(alpha = if (isLight) 0.20f else 0.28f)

    NavigationBar(
        modifier = Modifier
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .clip(railShape)
            .border(1.dp, railBorder, railShape),
        containerColor = railColor,
        tonalElevation = 0.dp
    ) {
        items.forEach { (screen, labelRes, icon) ->
            // Strip query-param template so History with ?filter=... is still matched
            val baseRoute  = screen.route.substringBefore('?')
            val isSelected = currentRoute?.substringBefore('?') == baseRoute

            NavigationBarItem(
                selected = isSelected,
                onClick  = {
                    if (isSelected) return@NavigationBarItem   // already on this tab

                    navController.navigate(baseRoute) {
                        // Pop everything back to Home (inclusive=false keeps Home alive).
                        // saveState=false ensures we always get a fresh screen, which
                        // fixes the "Home button sometimes does nothing" bug.
                        popUpTo(Screen.Home.route) {
                            saveState = false
                            inclusive = false
                        }
                        launchSingleTop = true
                        restoreState    = false
                    }
                },
                icon  = {
                    Icon(
                        imageVector        = icon,
                        contentDescription = stringResource(labelRes)
                    )
                },
                label  = { Text(stringResource(labelRes)) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor   = CyanAccent,
                    selectedTextColor   = CyanAccent,
                    unselectedIconColor = SubtleGray,
                    unselectedTextColor = SubtleGray,
                    indicatorColor      = selectedPill
                )
            )
        }
    }
}
