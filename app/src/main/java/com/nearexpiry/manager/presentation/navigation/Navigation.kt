package com.nearexpiry.manager.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nearexpiry.manager.presentation.screens.export.ExportScreen
import com.nearexpiry.manager.presentation.screens.history.HistoryScreen
import com.nearexpiry.manager.presentation.screens.home.HomeScreen
import com.nearexpiry.manager.presentation.screens.scan.ScanScreen
import com.nearexpiry.manager.presentation.screens.settings.SettingsScreen
import com.nearexpiry.manager.presentation.screens.detail.DetailScreen
import com.nearexpiry.manager.presentation.screens.backup.BackupRestoreScreen

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Scan : Screen("scan")
    // History accepts optional filter and sort query params from dashboard clicks
    object History : Screen("history?filter={filter}&sort={sort}") {
        const val BASE = "history"
    }
    object Export : Screen("export")
    object Settings : Screen("settings")
    object Detail : Screen("detail/{itemId}") {
        fun passId(id: Long): String = "detail/$id"
    }
    object BackupRestore : Screen("backup_restore")
}

@Composable
fun NearExpiryNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier
    ) {
        composable(Screen.Home.route) {
            HomeScreen(navController)
        }
        composable(Screen.Scan.route) {
            ScanScreen(navController)
        }
        composable(
            route = Screen.History.route,
            arguments = listOf(
                navArgument("filter") {
                    type = NavType.StringType
                    defaultValue = "ALL"
                    nullable = true
                },
                navArgument("sort") {
                    type = NavType.StringType
                    defaultValue = "NEWEST"
                    nullable = true
                }
            )
        ) { backStackEntry ->
            val filter = backStackEntry.arguments?.getString("filter") ?: "ALL"
            val sort   = backStackEntry.arguments?.getString("sort")   ?: "NEWEST"
            HistoryScreen(navController, initialFilter = filter, initialSort = sort)
        }
        composable(Screen.Export.route) {
            ExportScreen(navController)
        }
        composable(Screen.Settings.route) {
            SettingsScreen(navController)
        }
        composable(
            route = Screen.Detail.route,
            arguments = listOf(navArgument("itemId") { type = NavType.LongType })
        ) { backStackEntry ->
            val itemId = backStackEntry.arguments?.getLong("itemId") ?: -1L
            if (itemId != -1L) {
                DetailScreen(navController, itemId)
            }
        }
        composable(Screen.BackupRestore.route) {
            BackupRestoreScreen(navController)
        }
    }
}
