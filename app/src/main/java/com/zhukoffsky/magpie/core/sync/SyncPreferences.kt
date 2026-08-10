package com.zhukoffsky.magpie.core.sync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant

private val Context.syncDataStore: DataStore<Preferences> by preferencesDataStore(name = "sync")

data class SyncSettings(
    val isEnabled: Boolean = false,
    val taskListId: String = GoogleTasksApi.DEFAULT_LIST,
    val lastSyncAt: Instant? = null,
    /** Текст последней ошибки; null — последняя попытка прошла успешно. */
    val lastError: String? = null,
)

/**
 * Хранилище настроек синхронизации за интерфейсом: без него логика выгрузки
 * тянула бы за собой DataStore и Context и не проверялась бы юнит-тестами.
 */
interface SyncSettingsStore {
    val settings: Flow<SyncSettings>
    suspend fun current(): SyncSettings
    suspend fun setEnabled(enabled: Boolean)
    suspend fun setTaskList(id: String)
    suspend fun recordSuccess(at: Instant)
    suspend fun recordError(message: String)
}

class SyncPreferences(context: Context) : SyncSettingsStore {

    private val dataStore = context.applicationContext.syncDataStore

    override val settings: Flow<SyncSettings> = dataStore.data.map { prefs ->
        SyncSettings(
            isEnabled = prefs[ENABLED] ?: false,
            taskListId = prefs[LIST_ID] ?: GoogleTasksApi.DEFAULT_LIST,
            lastSyncAt = prefs[LAST_SYNC]?.let(Instant::ofEpochMilli),
            lastError = prefs[LAST_ERROR],
        )
    }

    override suspend fun current(): SyncSettings = settings.first()

    override suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[ENABLED] = enabled
            if (!enabled) prefs.remove(LAST_ERROR)
        }
    }

    override suspend fun setTaskList(id: String) {
        dataStore.edit { it[LIST_ID] = id }
    }

    override suspend fun recordSuccess(at: Instant) {
        dataStore.edit { prefs ->
            prefs[LAST_SYNC] = at.toEpochMilli()
            prefs.remove(LAST_ERROR)
        }
    }

    override suspend fun recordError(message: String) {
        dataStore.edit { it[LAST_ERROR] = message }
    }

    private companion object {
        val ENABLED = booleanPreferencesKey("enabled")
        val LIST_ID = stringPreferencesKey("taskListId")
        val LAST_SYNC = longPreferencesKey("lastSyncAt")
        val LAST_ERROR = stringPreferencesKey("lastError")
    }
}
