package com.zhukoffsky.magpie.core.ui.theme

import android.content.Context
import android.content.res.Configuration
// Тип и функция-строитель лежат в разных пакетах, поэтому импорта два.
import androidx.glance.color.ColorProviders
import androidx.glance.material3.ColorProviders
import com.zhukoffsky.magpie.core.settings.AppearancePreferences
import com.zhukoffsky.magpie.core.settings.ThemeMode
import kotlinx.coroutines.flow.first

/**
 * Палитра для виджетов.
 *
 * Те же цвета, что и в приложении, но **без стекла и без градиента**: Glance
 * не умеет ни размытия подложки, ни произвольных кистей — ему доступна
 * плоская заливка со скруглением. Поэтому виджет держит узнаваемость на
 * тёплом фоне, акценте и типографике, а не на материале.
 *
 * Тема берётся из настройки приложения, а не из системы. Раньше было
 * наоборот — рассуждение было, что виджет живёт на домашнем экране и должен
 * совпадать с ним. На практике это выглядит поломкой: выбрал светлую тему, а
 * виджеты остались тёмными, и объяснить это невозможно.
 *
 * Цена решения: при светлой теме на тёмных обоях виджет будет светлым пятном.
 * Это осознанно — предсказуемость дороже.
 */
suspend fun magpieGlanceColors(context: Context): ColorProviders {
    val dark = when (AppearancePreferences(context).themeMode.first()) {
        ThemeMode.SYSTEM -> context.isSystemDark()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    // Одна схема на оба слота, а не пара light/dark: пару Glance разбирает
    // сам по системной теме, и выбор пользователя тогда снова теряется.
    return ColorProviders(if (dark) DarkColors else LightColors)
}

private fun Context.isSystemDark(): Boolean =
    resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
        Configuration.UI_MODE_NIGHT_YES
