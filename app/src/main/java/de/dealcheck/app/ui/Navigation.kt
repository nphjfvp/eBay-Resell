package de.dealcheck.app.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import de.dealcheck.app.DealCheckApp
import de.dealcheck.app.ui.screens.HistoryScreen
import de.dealcheck.app.ui.screens.NewAnalysisScreen
import de.dealcheck.app.ui.screens.ResultScreen
import de.dealcheck.app.ui.screens.SettingsScreen

object Routes {
    const val NEW = "neu"
    const val HISTORY = "verlauf"
    const val SETTINGS = "einstellungen"
    const val RESULT = "ergebnis/{id}"
    fun result(id: Long) = "ergebnis/$id"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.NEW, "Analyse", Icons.Default.Search),
    Tab(Routes.HISTORY, "Verlauf", Icons.AutoMirrored.Filled.List),
    Tab(Routes.SETTINGS, "Einstellungen", Icons.Default.Settings),
)

@Composable
inline fun <reified VM : ViewModel> appViewModel(key: String? = null, crossinline create: (DealCheckApp) -> VM): VM {
    val app = LocalContext.current.applicationContext as DealCheckApp
    return viewModel(key = key, factory = viewModelFactory { initializer { create(app) } })
}

@Composable
fun DealCheckNav(newAnalysisViewModel: NewAnalysisViewModel) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    LaunchedEffect(Unit) {
        newAnalysisViewModel.sharedImages.collect { nav.switchTab(Routes.NEW) }
    }
    LaunchedEffect(Unit) {
        newAnalysisViewModel.finished.collect { id -> nav.navigate(Routes.result(id)) }
    }

    Scaffold(
        // Die Statusleiste behandelt die TopAppBar jedes Screens selbst
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = { nav.switchTab(tab.route) },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            nav,
            startDestination = Routes.NEW,
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding(),
        ) {
            composable(Routes.NEW) {
                NewAnalysisScreen(
                    viewModel = newAnalysisViewModel,
                    onOpenSettings = { nav.switchTab(Routes.SETTINGS) },
                )
            }
            composable(Routes.HISTORY) {
                HistoryScreen(
                    viewModel = appViewModel { HistoryViewModel(it.repository) },
                    onOpen = { id -> nav.navigate(Routes.result(id)) },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(appViewModel { SettingsViewModel(it.settings) }, (LocalContext.current.applicationContext as DealCheckApp).updates)
            }
            composable(
                Routes.RESULT,
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { entry ->
                val id = entry.arguments?.getLong("id") ?: 0L
                ResultScreen(
                    viewModel = appViewModel(key = "result-$id") { ResultViewModel(it.repository, id) },
                    onBack = { nav.popBackStack() },
                )
            }
        }
    }
}

private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
