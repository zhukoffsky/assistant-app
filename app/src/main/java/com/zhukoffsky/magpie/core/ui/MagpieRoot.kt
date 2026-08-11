package com.zhukoffsky.magpie.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zhukoffsky.magpie.core.settings.AppLanguage
import com.zhukoffsky.magpie.core.settings.AppearancePreferences
import com.zhukoffsky.magpie.core.settings.ThemeMode
import com.zhukoffsky.magpie.core.ui.theme.MagpieTheme

/**
 * Общая обёртка для всех точек входа с интерфейсом.
 *
 * Тема и язык читаются здесь, а не в каждой активности: экран диктовки
 * запускается с виджета и плитки в обход главного экрана, и без общей обёртки
 * он выглядел бы в системной теме, пока остальное приложение — в выбранной.
 */
@Composable
fun MagpieRoot(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val preferences = remember(context) { AppearancePreferences(context) }

    val themeMode by preferences.themeMode.collectAsStateWithLifecycle(ThemeMode.SYSTEM)
    val language by preferences.language.collectAsStateWithLifecycle(AppLanguage.SYSTEM)

    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    MagpieLanguage(language) {
        MagpieTheme(darkTheme = darkTheme, content = content)
    }
}
