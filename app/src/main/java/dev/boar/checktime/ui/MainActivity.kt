package dev.boar.checktime.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.boar.checktime.R
import dev.boar.checktime.appContainer
import dev.boar.checktime.scheduler.TailNotifier
import dev.boar.checktime.ui.categories.CategoriesRoute
import dev.boar.checktime.ui.categories.CategoriesViewModel
import dev.boar.checktime.ui.day.DayRoute
import dev.boar.checktime.ui.day.DayViewModel
import dev.boar.checktime.ui.settings.SettingsRoute
import dev.boar.checktime.ui.settings.SettingsViewModel
import dev.boar.checktime.ui.theme.CheckTimeTheme
import kotlinx.coroutines.launch

private object Routes {
    const val DAY = "day"
    const val SETTINGS = "settings"
    const val CATEGORIES = "categories"
}

class MainActivity : ComponentActivity() {
    private val dayViewModel: DayViewModel by viewModels { DayViewModel.factory(appContainer) }
    private val settingsViewModel: SettingsViewModel by viewModels { SettingsViewModel.factory(appContainer) }
    private val categoriesViewModel: CategoriesViewModel by viewModels { CategoriesViewModel.factory(appContainer) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CheckTimeTheme {
                MainScreen(dayViewModel, settingsViewModel, categoriesViewModel)
            }
        }
    }

    /** Заход в приложение — уведомление о хвосте должно соответствовать реальности (спека 3.2). */
    override fun onResume() {
        super.onResume()
        lifecycleScope.launch { TailNotifier.sync(applicationContext, appContainer) }
    }
}

@Composable
private fun MainScreen(
    dayViewModel: DayViewModel,
    settingsViewModel: SettingsViewModel,
    categoriesViewModel: CategoriesViewModel,
) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBar = route == Routes.DAY || route == Routes.SETTINGS

    Scaffold(
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    NavigationBarItem(
                        selected = route == Routes.DAY,
                        onClick = { navController.switchTab(Routes.DAY) },
                        icon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                        label = { Text(stringResource(R.string.tab_day)) },
                    )
                    NavigationBarItem(
                        selected = route == Routes.SETTINGS,
                        onClick = { navController.switchTab(Routes.SETTINGS) },
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        label = { Text(stringResource(R.string.tab_settings)) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(navController, startDestination = Routes.DAY, modifier = Modifier.padding(padding)) {
            composable(Routes.DAY) { DayRoute(dayViewModel) }
            composable(Routes.SETTINGS) {
                SettingsRoute(settingsViewModel, onOpenCategories = { navController.navigate(Routes.CATEGORIES) })
            }
            composable(Routes.CATEGORIES) {
                CategoriesRoute(categoriesViewModel, onBack = { navController.popBackStack() })
            }
        }
    }
}

private fun androidx.navigation.NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
