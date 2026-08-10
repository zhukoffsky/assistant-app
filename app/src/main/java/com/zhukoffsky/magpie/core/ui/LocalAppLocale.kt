package com.zhukoffsky.magpie.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale

/**
 * Язык, которым надо форматировать даты и время на экранах.
 *
 * `Locale.getDefault()` здесь не годится: он отдаёт язык системы, а
 * [MagpieLanguage] меняет только конфигурацию композиции. Из-за этого при
 * английском интерфейсе даты оставались русскими — «10 авг.» рядом с
 * «Reminders». Берём язык из той же конфигурации, что и строки.
 */
@Composable
@ReadOnlyComposable
fun appLocale(): Locale = LocalConfiguration.current.locales[0]
