package com.zhukoffsky.magpie.core.di

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.room.Room
import com.zhukoffsky.magpie.core.data.db.MagpieDatabase
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.zhukoffsky.magpie.core.diagnostics.DiagnosticsInspector
import com.zhukoffsky.magpie.core.diagnostics.TestAlarmScheduler
import com.zhukoffsky.magpie.BuildConfig
import com.zhukoffsky.magpie.core.llm.LlmPhraseParser
import com.zhukoffsky.magpie.core.llm.LlmShoppingParser
import com.zhukoffsky.magpie.core.llm.OpenAiCompatApi
import com.zhukoffsky.magpie.feature.shopping.domain.HybridShoppingParser
import com.zhukoffsky.magpie.feature.shopping.domain.RuleBasedShoppingParser
import com.zhukoffsky.magpie.feature.shopping.domain.ShoppingItemsParser
import com.zhukoffsky.magpie.feature.reminders.domain.HybridPhraseParser
import com.zhukoffsky.magpie.feature.reminders.domain.PhraseParser
import com.zhukoffsky.magpie.feature.reminders.domain.RuleBasedPhraseParser
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
import com.zhukoffsky.magpie.feature.reminders.widget.ReminderVoiceWidget
import com.zhukoffsky.magpie.feature.shopping.data.ShoppingRepository
import com.zhukoffsky.magpie.feature.shopping.widget.ShoppingWidget

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
        ).addMigrations(MagpieDatabase.MIGRATION_1_2).build()
    }

    val shoppingDao by lazy { database.shoppingDao() }
    val reminderDao by lazy { database.reminderDao() }
    val medDao by lazy { database.medDao() }

    val shoppingRepository by lazy {
        // Каждая запись в список толкает виджет: подписки на Room ему хватает
        // только пока жива сессия Glance, то есть пока жив процесс.
        ShoppingRepository(
            dao = shoppingDao,
            onChanged = { ShoppingWidget().updateAll(appContext) },
        )
    }

    val syncPreferences by lazy { SyncPreferences(appContext) }
    val syncTrigger: SyncTrigger by lazy { WorkManagerSyncTrigger(appContext) }

    private val json = Json { ignoreUnknownKeys = true }

    private inline fun <reified T> retrofit(baseUrl: String): T = Retrofit.Builder()
        .baseUrl(baseUrl)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(T::class.java)

    private val googleTasksApi: GoogleTasksApi by lazy { retrofit(GoogleTasksApi.BASE_URL) }

    /**
     * Сначала правила, при неудаче — LLM. Обе реализации за общим
     * интерфейсом и взаимозаменяемы.
     *
     * Ключ один на всех и приезжает из сборки: он лежит в `local.properties`
     * (файл вне git) и попадает в `BuildConfig`. Вводить его в приложении
     * негде — сборка без ключа просто работает на одних правилах.
     */
    /**
     * Разбор фразы покупок. Та же связка «правила → LLM», что и у
     * напоминаний, и по той же причине: распознавание не ставит запятых.
     */
    val shoppingItemsParser: ShoppingItemsParser by lazy {
        HybridShoppingParser(
            rules = RuleBasedShoppingParser(),
            llm = LlmShoppingParser(
                api = retrofit(OpenAiCompatApi.BASE_URL),
                apiKey = { BuildConfig.LLM_API_KEY.takeIf { it.isNotBlank() } },
            ),
        )
    }

    val phraseParser: PhraseParser by lazy {
        val apiKey = BuildConfig.LLM_API_KEY.takeIf { it.isNotBlank() }

        HybridPhraseParser(
            rules = RuleBasedPhraseParser(),
            llm = LlmPhraseParser(
                api = retrofit(OpenAiCompatApi.BASE_URL),
                apiKey = { apiKey },
            ),
        )
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
        // Каждая запись толкает виджет — по той же причине, что и у покупок:
        // подписки на Room ему хватает только пока жив процесс.
        ReminderRepository(
            dao = reminderDao,
            scheduler = reminderScheduler,
            syncTrigger = syncTrigger,
            onChanged = { ReminderVoiceWidget().updateAll(appContext) },
        )
    }

    val medScheduler: MedScheduler by lazy { AlarmManagerMedScheduler(appContext) }
    val medRepository by lazy { MedRepository(medDao, medScheduler) }

    val diagnosticsInspector by lazy { DiagnosticsInspector(appContext) }
    val testAlarmScheduler by lazy { TestAlarmScheduler(appContext) }
}
