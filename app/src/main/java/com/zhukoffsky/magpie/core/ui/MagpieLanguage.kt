package com.zhukoffsky.magpie.core.ui

import android.content.res.Configuration
import android.view.ContextThemeWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
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
    val context = LocalContext.current
    val base = LocalConfiguration.current

    /*
     * Ранний выход при `tag == null` здесь был, и он ломал навигацию.
     *
     * `content()` вызывался из ДРУГОГО места дерева, чем ветка с
     * `CompositionLocalProvider`. Переключение «Как в системе» ⇄ «English»
     * переносило поддерево между двумя разными позициями композиции, Compose
     * выбрасывал старое целиком, и вместе с ним пропадало состояние `NavHost`:
     * пользователь менял язык в «Настройках» и оказывался в «Покупках».
     *
     * Теперь вызов один на оба случая, а «как в системе» просто отдаёт
     * исходные значения.
     */
    val configuration = remember(base, tag) {
        if (tag == null) base else Configuration(base).apply {
            setLocale(Locale.forLanguageTag(tag))
        }
    }

    /*
     * `ContextThemeWrapper`, а не `createConfigurationContext`.
     *
     * Последний возвращает самостоятельный контекст, в цепочке которого
     * активности нет вовсе. А всё, что ищет владельца подъёмом по
     * `ContextWrapper.baseContext` — `rememberLauncherForActivityResult` и
     * прочие `LocalActivity*` — на таком контексте не находит ничего и падает.
     * Экран «Настройки» с его запросом согласия Google так и уронил
     * приложение: `No ActivityResultRegistryOwner was provided`.
     *
     * Обёртка оставляет активность на месте и при этом подменяет конфигурацию.
     */
    val localized = remember(context, configuration) {
        if (configuration === base) {
            context
        } else {
            ContextThemeWrapper(context, 0).apply { applyOverrideConfiguration(configuration) }
        }
    }

    CompositionLocalProvider(
        LocalConfiguration provides configuration,
        LocalContext provides localized,
        content = content,
    )
}
