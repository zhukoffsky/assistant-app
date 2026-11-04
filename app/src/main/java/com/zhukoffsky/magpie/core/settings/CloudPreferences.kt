package com.zhukoffsky.magpie.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.cloudDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "cloud")

/**
 * «Без облака»: голос и разбор — только на телефоне.
 *
 * Включённый режим выключает всё, что отправляет диктовку наружу: Whisper
 * на Cloudflare, модели Z.ai и Workers AI. Работают распознаватель на
 * телефоне, правила и словарь отделов. Синхронизация с Google Tasks сюда не
 * относится: это не ИИ, и выключается она своей кнопкой.
 *
 * По умолчанию выключено: облако разбирает точнее, и менять поведение
 * без просьбы незачем.
 */
class CloudPreferences(context: Context) {

    private val dataStore = context.applicationContext.cloudDataStore

    val offlineOnly: Flow<Boolean> = dataStore.data.map { it[OFFLINE_ONLY] ?: false }

    /** Для тех, кто решает в моменте: расшифровка, разбор, старт диктовки. */
    suspend fun isOfflineOnly(): Boolean = offlineOnly.first()

    suspend fun setOfflineOnly(enabled: Boolean) {
        dataStore.edit { it[OFFLINE_ONLY] = enabled }
    }

    private companion object {
        val OFFLINE_ONLY = booleanPreferencesKey("offlineOnly")
    }
}
