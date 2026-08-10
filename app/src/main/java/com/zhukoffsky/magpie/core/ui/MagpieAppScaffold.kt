package com.zhukoffsky.magpie.core.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.zhukoffsky.magpie.core.voice.VoiceTarget
import com.zhukoffsky.magpie.feature.meds.ui.MedsScreen
import com.zhukoffsky.magpie.feature.reminders.ui.RemindersScreen
import com.zhukoffsky.magpie.feature.shopping.ui.ShoppingScreen

@Composable
fun MagpieAppScaffold(onVoiceCapture: (VoiceTarget) -> Unit) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
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
            composable(MagpieDestination.Meds.route) { MedsScreen() }
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
