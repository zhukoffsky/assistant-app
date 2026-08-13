package com.zhukoffsky.magpie.core.settings

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import kotlinx.coroutines.flow.first
import java.util.Locale

/**
 * Контекст, чьи строки берутся на выбранном в приложении языке.
 *
 * Нужен всему, что рисует не Compose: уведомлениям и виджетам. Язык
 * интерфейса меняется подменой конфигурации композиции, и до строк, которые
 * достаёт `context.getString`, эта подмена не доходит — они остаются на языке
 * системы.
 *
 * Здесь `createConfigurationContext` **уместен**, хотя раздел 9 `CLAUDE.md`
 * предостерегает против него. Тот запрет касается Compose: по цепочке
 * `baseContext` там ищут активность, и самостоятельный контекст ронял
 * `rememberLauncherForActivityResult`. Строителю уведомления активность не
 * нужна вовсе — ему нужны только ресурсы.
 */
// lint ждёт здесь вызовов Play Core: при доставке через app bundle язык
// может быть просто не скачан. Приложение раздаётся цельным APK со всеми
// языками внутри, скачивать нечего.
@SuppressLint("AppBundleLocaleChanges")
fun Context.forLanguage(language: AppLanguage): Context {
    val tag = language.tag ?: return this

    val configuration = Configuration(resources.configuration).apply {
        setLocales(LocaleList(Locale.forLanguageTag(tag)))
    }
    return createConfigurationContext(configuration)
}

/**
 * То же, но язык читается из настроек.
 *
 * Приостановка здесь не мешает: все вызывающие — фоновые точки входа, они и
 * так работают в корутине под `goAsync`.
 */
suspend fun Context.forSelectedLanguage(): Context =
    forLanguage(AppearancePreferences(this).language.first())
