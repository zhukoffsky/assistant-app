package com.zhukoffsky.magpie.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val BrandBlue = Color(0xFF1F3A5F)
private val BrandBlueLight = Color(0xFF9DC2F0)
private val BrandAccent = Color(0xFF3E7CB1)

private val LightColors = lightColorScheme(
    primary = BrandBlue,
    secondary = BrandAccent,
)

private val DarkColors = darkColorScheme(
    primary = BrandBlueLight,
    secondary = BrandAccent,
)

/**
 * Тема приложения.
 *
 * На Android 12+ по умолчанию берётся системная динамическая палитра —
 * приложение подстраивается под обои. Фирменные цвета выше используются
 * как запасной вариант.
 */
@Composable
fun MagpieTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MagpieTypography,
        content = content,
    )
}
