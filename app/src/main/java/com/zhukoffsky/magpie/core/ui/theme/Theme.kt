package com.zhukoffsky.magpie.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

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

    CompositionLocalProvider(LocalMagpieColors provides magpieColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MagpieTypography,
            shapes = MagpieShapes,
            content = content,
        )
    }
}

/** Короткий доступ к токенам, которых нет в схеме Material. */
object MagpieTheme {
    val colors: MagpieColors
        @Composable get() = LocalMagpieColors.current
}
