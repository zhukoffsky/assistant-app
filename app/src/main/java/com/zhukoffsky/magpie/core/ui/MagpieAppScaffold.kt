package com.zhukoffsky.magpie.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.zhukoffsky.magpie.core.ui.theme.MagpieMotion
import com.zhukoffsky.magpie.core.ui.theme.MagpieRadius
import com.zhukoffsky.magpie.core.ui.theme.MagpieTheme
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

    MagpieScreenBackground {
        Scaffold(
            // Фон рисуется под всем каркасом, поэтому сам Scaffold и его
            // контейнеры прозрачны — иначе градиент окажется закрыт.
            containerColor = Color.Transparent,
            contentColor = MagpieTheme.colors.ink,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                GlassNavigationBar(
                    isSelected = { destination ->
                        currentDestination?.hierarchy?.any { it.route == destination.route } == true
                    },
                    onSelect = navController::navigateToTab,
                )
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
}

/**
 * Нижняя навигация — плавающая стеклянная таблетка, а не сплошная панель.
 *
 * Своя реализация вместо `NavigationBar`: материаловая панель тянет за собой
 * непрозрачный контейнер во всю ширину и собственную индикаторную капсулу,
 * и то и другое пришлось бы перебивать. Здесь нужны всего четыре элемента.
 */
@Composable
private fun GlassNavigationBar(
    isSelected: (MagpieDestination) -> Boolean,
    onSelect: (MagpieDestination) -> Unit,
) {
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            // Инсет жест-полосы, иначе таблетка ложится вплотную к ней и обе
            // читаются как одна деталь: край стекла и белая черта сливаются.
            // Порядок важен — инсет до собственных отступов, иначе он их съест.
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .height(66.dp),
        shape = RoundedCornerShape(MagpieRadius.md),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            MagpieDestination.entries.forEach { destination ->
                val selected = isSelected(destination)
                val tint by animateColorAsState(
                    targetValue = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MagpieTheme.colors.ink2
                    },
                    animationSpec = MagpieMotion.snappy(),
                    label = "navTint",
                )
                val halo by animateColorAsState(
                    targetValue = if (selected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                    } else {
                        Color.Transparent
                    },
                    animationSpec = MagpieMotion.snappy(),
                    label = "navHalo",
                )

                Box(
                    modifier = Modifier
                        .selectable(
                            selected = selected,
                            role = Role.Tab,
                            onClick = { onSelect(destination) },
                        )
                        .background(halo, RoundedCornerShape(MagpieRadius.sm))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.height(20.dp),
                        )
                        Text(
                            text = stringResource(destination.labelRes),
                            style = MaterialTheme.typography.labelSmall,
                            color = tint,
                            maxLines = 1,
                        )
                    }
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
