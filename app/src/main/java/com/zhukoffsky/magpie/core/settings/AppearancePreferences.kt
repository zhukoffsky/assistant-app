package com.zhukoffsky.magpie.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appearanceDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "appearance")

/** Тема интерфейса. По умолчанию — как в системе. */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    ;

    companion object {
        fun fromName(value: String?): ThemeMode =
            entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

/**
 * Язык интерфейса. По умолчанию — как в системе.
 *
 * Список намеренно закрытый и совпадает с `res/xml/locales_config.xml`:
 * переводы есть только на эти два языка, а предлагать выбор без перевода
 * значит показать пользователю пустой интерфейс.
 */
enum class AppLanguage(val tag: String?) {
    SYSTEM(null),
    RUSSIAN("ru"),
    ENGLISH("en"),
    ;

    companion object {
        fun fromName(value: String?): AppLanguage =
            entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

/**
 * Тема и язык, выбранные в приложении.
 *
 * Отдельное хранилище, а не общее с ключом LLM: настройки внешнего вида
 * читаются на старте каждой композиции, а ключ — только при разборе фразы.
 */
class AppearancePreferences(context: Context) {

    private val dataStore = context.applicationContext.appearanceDataStore

    val themeMode: Flow<ThemeMode> =
        dataStore.data.map { ThemeMode.fromName(it[THEME_MODE]) }

    val language: Flow<AppLanguage> =
        dataStore.data.map { AppLanguage.fromName(it[LANGUAGE]) }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME_MODE] = mode.name }
    }

    suspend fun setLanguage(language: AppLanguage) {
        dataStore.edit { it[LANGUAGE] = language.name }
    }

    private companion object {
        val THEME_MODE = stringPreferencesKey("themeMode")
        val LANGUAGE = stringPreferencesKey("language")
    }
}
