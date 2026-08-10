package com.zhukoffsky.magpie.core.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.zhukoffsky.magpie.core.settings.AppLanguage
import java.util.Locale

/**
 * Применяет выбранный в приложении язык, подменяя конфигурацию композиции.
 *
 * **Почему не `LocaleManager`.** Системный механизм смены языка приложения
 * (Android 13+) выглядит правильнее: его слушаются и уведомления, и подпись
 * под иконкой. Но запись `applicationLocales` **пересоздаёт активность**, а
 * значит вызывать её из эффекта композиции нельзя: эффект запускается заново
 * на новой активности. Защита «сравнить с текущим значением» держится на том,
 * что система действительно вернёт записанное, — и если не вернёт (а на
 * Android 17 не вернула), получается бесконечный цикл пересозданий. Экран
 * мигает, приложение недоступно.
 *
 * Подмена конфигурации ничего не пересоздаёт и потому не может зациклиться.
 * Цена — переводится только то, что рисует Compose: **уведомления и подпись
 * под иконкой остаются на языке системы**. Кому нужно сменить и их, у того на
 * Android 13+ есть системный экран «Язык приложения»: он работает благодаря
 * `android:localeConfig` в манифесте и от этой настройки не зависит.
 */
@Composable
fun MagpieLanguage(language: AppLanguage, content: @Composable () -> Unit) {
    val tag = language.tag
    if (tag == null) {
        content()
        return
    }

    val context = LocalContext.current
    val configuration = Configuration(LocalConfiguration.current).apply {
        setLocale(Locale.forLanguageTag(tag))
    }

    CompositionLocalProvider(
        LocalConfiguration provides configuration,
        LocalContext provides context.createConfigurationContext(configuration),
        content = content,
    )
}
