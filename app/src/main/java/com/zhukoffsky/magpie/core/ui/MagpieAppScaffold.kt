package com.zhukoffsky.magpie.core.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.zhukoffsky.magpie.core.diagnostics.DiagnosticFix
import com.zhukoffsky.magpie.core.voice.VoiceTarget
import com.zhukoffsky.magpie.feature.meds.ui.MedsScreen
import com.zhukoffsky.magpie.feature.reminders.ui.RemindersScreen
import com.zhukoffsky.magpie.feature.settings.ui.SettingsScreen
import com.zhukoffsky.magpie.feature.shopping.ui.ShoppingScreen

@Composable
fun MagpieAppScaffold(
    onVoiceCapture: (VoiceTarget) -> Unit,
    onOpenFix: (DiagnosticFix) -> Unit,
    onShareText: (String) -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                MagpieDestination.entries.forEach { destination ->
                    val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = { navController.navigateToTab(destination) },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(stringResource(destination.labelRes)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
            NavHost(
                navController = navController,
                startDestination = MagpieDestination.START.route,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                composable(MagpieDestination.Shopping.route) {
                    ShoppingScreen(onVoiceInput = { onVoiceCapture(VoiceTarget.SHOPPING) })
                }
                composable(MagpieDestination.Reminders.route) {
                    RemindersScreen(onVoiceInput = { onVoiceCapture(VoiceTarget.REMINDER) })
                }
                // Экран таблеток голосового ввода не имеет: курс заводится
                // один раз руками, диктовать там нечего.
                composable(MagpieDestination.Meds.route) {
                    MedsScreen(onShareText = onShareText)
                }
                composable(MagpieDestination.Settings.route) {
                    SettingsScreen(onOpenFix = onOpenFix)
                }
            }
        }
    }
}

/**
 * Переход между вкладками: без наращивания стека и с сохранением состояния
 * каждой вкладки — стандартное поведение нижней навигации.
 */
private fun androidx.navigation.NavHostController.navigateToTab(destination: MagpieDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
