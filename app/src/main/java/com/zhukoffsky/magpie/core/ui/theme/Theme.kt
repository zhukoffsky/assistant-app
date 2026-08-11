package com.zhukoffsky.magpie.core.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Window
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

internal val LightColors = lightColorScheme(
    primary = LightAccent,
    onPrimary = LightOnAccent,
    primaryContainer = LightAccent,
    onPrimaryContainer = LightOnAccent,
    secondary = LightAccentPressed,
    onSecondary = LightOnAccent,
    background = LightBg,
    onBackground = LightInk,
    surface = LightBg,
    onSurface = LightInk,
    surfaceVariant = LightBg,
    onSurfaceVariant = LightInk2,
    outline = LightInk3,
    error = LightAccentPressed,
)

internal val DarkColors = darkColorScheme(
    primary = DarkAccent,
    onPrimary = DarkOnAccent,
    primaryContainer = DarkAccent,
    onPrimaryContainer = DarkOnAccent,
    secondary = DarkAccentPressed,
    onSecondary = DarkOnAccent,
    background = DarkBg,
    onBackground = DarkInk,
    surface = DarkBg,
    onSurface = DarkInk,
    surfaceVariant = DarkBg,
    onSurfaceVariant = DarkInk2,
    outline = DarkInk3,
    error = DarkAccentPressed,
)

/**
 * Тема приложения — «тёплое стекло».
 *
 * **Динамическая палитра выключена намеренно.** Material You перекрасил бы
 * схему под обои, а весь дизайн держится на одном тёплом акценте поверх
 * органического градиента: с чужим цветом фон и акцент разъезжаются, и
 * узнавать становится нечего. Обои — не наш источник правды.
 */
@Composable
fun MagpieTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val magpieColors = if (darkTheme) DarkMagpieColors else LightMagpieColors

    /*
     * Светлота значков статус-бара и жест-полосы — следом за темой приложения.
     *
     * `enableEdgeToEdge()` в активности решает это один раз при создании и по
     * СИСТЕМНОЙ теме. Пока приложение всегда шло за системой, этого хватало;
     * с появлением выбора темы внутри приложения светлая тема на тёмной
     * системе давала белые часы на светлом фоне — не читалось совсем.
     *
     * Отсюда же `SideEffect`, а не `LaunchedEffect`: правка должна лечь в тот
     * же кадр, что и смена цветов, иначе панель мигает старым.
     */
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivityWindow() ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalMagpieColors provides magpieColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MagpieTypography,
            shapes = MagpieShapes,
            content = content,
        )
    }
}

/**
 * Окно активности из контекста представления.
 *
 * Перебор `ContextWrapper` обязателен: `MagpieLanguage` оборачивает контекст
 * композиции ради подмены локали, поэтому напрямую активностью он не является.
 */
private tailrec fun Context.findActivityWindow(): Window? = when (this) {
    is Activity -> window
    is ContextWrapper -> baseContext.findActivityWindow()
    else -> null
}

/** Короткий доступ к токенам, которых нет в схеме Material. */
object MagpieTheme {
    val colors: MagpieColors
        @Composable get() = LocalMagpieColors.current
}
