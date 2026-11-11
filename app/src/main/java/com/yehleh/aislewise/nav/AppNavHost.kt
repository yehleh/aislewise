package com.yehleh.aislewise.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.yehleh.aislewise.feature.catalog.CatalogScreen
import com.yehleh.aislewise.feature.catalog.ItemEditorScreen
import com.yehleh.aislewise.feature.list.ListScreen
import com.yehleh.aislewise.feature.scan.ScanScreen
import com.yehleh.aislewise.feature.settings.LocalShowPrices
import com.yehleh.aislewise.feature.settings.SettingsScreen
import com.yehleh.aislewise.feature.settings.SettingsViewModel
import com.yehleh.aislewise.feature.stores.SectionOrderScreen
import com.yehleh.aislewise.feature.stores.StoresScreen

object Routes {
    const val LIST = "list"
    const val CATALOG = "catalog"
    const val STORES = "stores"
    const val SCAN = "scan"
    const val SETTINGS = "settings"
    const val ITEM_EDITOR = "item/{itemId}"
    const val SECTION_ORDER = "store/{storeId}/sections"

    fun itemEditor(itemId: Long) = "item/$itemId"
    fun sectionOrder(storeId: Long) = "store/$storeId/sections"
}

private data class TopLevel(val route: String, val label: String, val icon: ImageVector)

private val topLevel = listOf(
    TopLevel(Routes.LIST, "List", Icons.AutoMirrored.Filled.List),
    TopLevel(Routes.CATALOG, "Items", Icons.Default.Inventory2),
    TopLevel(Routes.STORES, "Stores", Icons.Default.Storefront),
)

@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
) {
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = topLevel.any { it.route == currentRoute }
    val showPrices by settingsViewModel.showPrices.collectAsStateWithLifecycle()

    CompositionLocalProvider(LocalShowPrices provides showPrices) {
        Scaffold(
            bottomBar = {
                if (showBottomBar) {
                    NavigationBar {
                        topLevel.forEach { dest ->
                            NavigationBarItem(
                                selected = currentRoute == dest.route,
                                onClick = {
                                    navController.navigate(dest.route) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(dest.icon, contentDescription = null) },
                                label = { Text(dest.label) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = Routes.LIST,
                // Each screen draws its own top bar and handles the status bar inset itself.
                modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
            ) {
                composable(Routes.LIST) {
                    ListScreen(
                        onScan = { navController.navigate(Routes.SCAN) },
                        onEditItem = { navController.navigate(Routes.itemEditor(it)) },
                        onSettings = { navController.navigate(Routes.SETTINGS) },
                    )
                }
                composable(Routes.CATALOG) {
                    CatalogScreen(onEditItem = { navController.navigate(Routes.itemEditor(it)) })
                }
                composable(Routes.STORES) {
                    StoresScreen(onEditSectionOrder = { navController.navigate(Routes.sectionOrder(it)) })
                }
                composable(Routes.SCAN) {
                    ScanScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.ITEM_EDITOR, arguments = listOf(navArgument("itemId") { type = NavType.LongType })) {
                    ItemEditorScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.SECTION_ORDER, arguments = listOf(navArgument("storeId") { type = NavType.LongType })) {
                    SectionOrderScreen(onBack = { navController.popBackStack() })
                }
            }
        }
    }
}
