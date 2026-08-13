package com.zhukoffsky.magpie.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.shoppingDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "shopping")

/**
 * Настройки списка покупок.
 *
 * Отдельное хранилище, а не общее с внешним видом: то читается на старте
 * каждой композиции, это — только на экране покупок и в настройках.
 */
class ShoppingPreferences(context: Context) {

    private val dataStore = context.applicationContext.shoppingDataStore

    /**
     * Группировать ли список по отделам магазина.
     *
     * По умолчанию выключено: владелец просил именно переключатель, а не
     * безусловное поведение, и плоский список остаётся тем, что было.
     */
    val groupByCategory: Flow<Boolean> =
        dataStore.data.map { it[GROUP_BY_CATEGORY] ?: false }

    suspend fun setGroupByCategory(enabled: Boolean) {
        dataStore.edit { it[GROUP_BY_CATEGORY] = enabled }
    }

    private companion object {
        val GROUP_BY_CATEGORY = booleanPreferencesKey("groupByCategory")
    }
}
