package com.zhukoffsky.magpie.core.di

import android.content.Context
import androidx.room.Room
import com.zhukoffsky.magpie.core.data.db.MagpieDatabase
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.zhukoffsky.magpie.core.diagnostics.DiagnosticsInspector
import com.zhukoffsky.magpie.core.diagnostics.TestAlarmScheduler
import com.zhukoffsky.magpie.core.sync.GoogleAuthorization
import com.zhukoffsky.magpie.core.sync.GoogleTasksApi
import com.zhukoffsky.magpie.core.sync.RemindersSyncer
import com.zhukoffsky.magpie.core.sync.SyncPreferences
import com.zhukoffsky.magpie.core.sync.SyncTrigger
import com.zhukoffsky.magpie.core.sync.WorkManagerSyncTrigger
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import com.zhukoffsky.magpie.feature.meds.alarm.AlarmManagerMedScheduler
import com.zhukoffsky.magpie.feature.meds.alarm.MedScheduler
import com.zhukoffsky.magpie.feature.meds.data.MedRepository
import com.zhukoffsky.magpie.feature.reminders.alarm.AlarmManagerReminderScheduler
import com.zhukoffsky.magpie.feature.reminders.alarm.ReminderScheduler
import com.zhukoffsky.magpie.feature.reminders.data.ReminderRepository
import com.zhukoffsky.magpie.feature.shopping.data.ShoppingRepository

/**
 * Ручной контейнер зависимостей. Живёт столько же, сколько процесс.
 *
 * Достаётся из любого места с [Context]:
 * `(context.applicationContext as MagpieApp).container`.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val database: MagpieDatabase by lazy {
        Room.databaseBuilder(
            appContext,
            MagpieDatabase::class.java,
            MagpieDatabase.NAME,
        ).build()
    }

    val shoppingDao by lazy { database.shoppingDao() }
    val reminderDao by lazy { database.reminderDao() }
    val medDao by lazy { database.medDao() }

    val shoppingRepository by lazy { ShoppingRepository(shoppingDao) }

    val syncPreferences by lazy { SyncPreferences(appContext) }
    val syncTrigger: SyncTrigger by lazy { WorkManagerSyncTrigger(appContext) }

    private val googleTasksApi: GoogleTasksApi by lazy {
        val json = Json { ignoreUnknownKeys = true }

        Retrofit.Builder()
            .baseUrl(GoogleTasksApi.BASE_URL)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GoogleTasksApi::class.java)
    }

    val remindersSyncer by lazy {
        RemindersSyncer(
            dao = reminderDao,
            api = googleTasksApi,
            authorizer = GoogleAuthorization(appContext),
            preferences = syncPreferences,
        )
    }

    val reminderScheduler: ReminderScheduler by lazy { AlarmManagerReminderScheduler(appContext) }
    val reminderRepository by lazy {
        ReminderRepository(
            dao = reminderDao,
            scheduler = reminderScheduler,
            syncTrigger = syncTrigger,
        )
    }

    val medScheduler: MedScheduler by lazy { AlarmManagerMedScheduler(appContext) }
    val medRepository by lazy { MedRepository(medDao, medScheduler) }

    val diagnosticsInspector by lazy { DiagnosticsInspector(appContext) }
    val testAlarmScheduler by lazy { TestAlarmScheduler(appContext) }
}
