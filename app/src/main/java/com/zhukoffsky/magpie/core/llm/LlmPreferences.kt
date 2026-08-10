package com.zhukoffsky.magpie.core.llm

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.llmDataStore: DataStore<Preferences> by preferencesDataStore(name = "llm")

/**
 * Хранилище ключа Anthropic.
 *
 * Ключ вводится пользователем и живёт только здесь. В коде, в
 * `local.properties` и в репозитории его нет ни при каких обстоятельствах —
 * иначе он уехал бы в git и в каждый розданный APK.
 */
class LlmPreferences(context: Context) {

    private val dataStore = context.applicationContext.llmDataStore

    /** Только признак наличия: сам ключ в UI не показывается. */
    val hasApiKey: Flow<Boolean> = dataStore.data.map { !it[API_KEY].isNullOrBlank() }

    suspend fun apiKey(): String? = dataStore.data.first()[API_KEY]

    suspend fun setApiKey(key: String) {
        dataStore.edit { prefs ->
            val clean = key.trim()
            if (clean.isEmpty()) prefs.remove(API_KEY) else prefs[API_KEY] = clean
        }
    }

    suspend fun clear() {
        dataStore.edit { it.remove(API_KEY) }
    }

    private companion object {
        val API_KEY = stringPreferencesKey("anthropicApiKey")
    }
}
