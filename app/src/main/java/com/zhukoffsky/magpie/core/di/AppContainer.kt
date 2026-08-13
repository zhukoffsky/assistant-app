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
import com.zhukoffsky.magpie.core.speech.CloudflareWhisperApi
import com.zhukoffsky.magpie.core.speech.CloudflareWhisperTranscriber
import com.zhukoffsky.magpie.core.speech.SpeechTranscriber
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
import okhttp3.OkHttpClient
import okhttp3.Response
import java.time.Duration
import retrofit2.Retrofit
import com.zhukoffsky.magpie.feature.meds.alarm.AlarmManagerMedScheduler
import com.zhukoffsky.magpie.feature.meds.alarm.MedScheduler
import com.zhukoffsky.magpie.feature.meds.data.MedRepository
import com.zhukoffsky.magpie.feature.meds.widget.MedWidget
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

    /**
     * Клиент с запасом по времени и одним повтором.
     *
     * Умолчания OkHttp — десять секунд, и их не хватает: 12 августа разбор
     * покупок упал по сети, и весь список приехал одной строкой, потому что
     * гибрид откатился на правила. Владелец ходит до z.ai из России без VPN,
     * маршрут длинный и нестабильный, так что одна потерянная попытка — это
     * не «редкий случай», а обычный день.
     *
     * **Повторяется не только исключение, но и ответ.** Первая версия ловила
     * только брошенное — и в тот же вечер пропустила настоящую причину:
     * z.ai ответил `429 Too Many Requests`, то есть штатным ответом с кодом,
     * а не обрывом. Для OkHttp это успех, повтора не было, и весь список
     * снова приехал одной строкой.
     *
     * Повтор ровно один: запрос идёт, пока человек смотрит на карточку
     * «Разбираю список…», и растягивать ожидание вдвое ради третьей попытки
     * хуже, чем разобрать правилами. Пауза перед ним нужна именно из-за
     * `429`: мгновенная попытка упрётся в тот же лимит.
     */
    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(HTTP_TIMEOUT)
            .readTimeout(HTTP_TIMEOUT)
            .writeTimeout(HTTP_TIMEOUT)
            .addInterceptor { chain ->
                val request = chain.request()
                val first = runCatching { chain.proceed(request) }.getOrNull()

                if (first != null && !first.isTemporaryFailure()) {
                    first
                } else {
                    // Тело обязано быть закрыто до повтора: иначе соединение
                    // остаётся занятым и утекает из пула.
                    first?.close()
                    Thread.sleep(RETRY_DELAY_MILLIS)
                    chain.proceed(request)
                }
            }
            .build()
    }

    /**
     * Стоит ли пробовать ещё раз.
     *
     * `429` — лимит частоты, он проходит сам. `5xx` — беда на той стороне,
     * тоже нередко разовая. Всё остальное (`401` с чужим ключом, `404` не по
     * тому адресу) повтором не лечится и только тратит время человека.
     */
    private fun Response.isTemporaryFailure(): Boolean = code == 429 || code >= 500

    private inline fun <reified T> retrofit(baseUrl: String): T = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(httpClient)
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

    /**
     * Расшифровка записанного голоса.
     *
     * Ключи из сборки, как и у LLM, и по той же причине: в репозитории их
     * нет, а вводить их в приложении негде. Пустые — диктовка покупок
     * скажет, что не может расшифровать, и предложит продиктовать заново.
     */
    /**
     * Есть ли чем расшифровывать.
     *
     * Сборка без ключей — штатный случай (CI, чужая машина), и ломать в ней
     * диктовку нельзя. Своя запись без расшифровки бесполезна: звук взять
     * некуда, поэтому покупки в такой сборке идут через системный диалог —
     * с обрывом на паузе, но рабочие.
     */
    val speechAvailable: Boolean
        get() = BuildConfig.SPEECH_ACCOUNT_ID.isNotBlank() &&
            BuildConfig.SPEECH_API_TOKEN.isNotBlank()

    val speechTranscriber: SpeechTranscriber by lazy {
        CloudflareWhisperTranscriber(
            api = retrofit(CloudflareWhisperApi.BASE_URL),
            accountId = BuildConfig.SPEECH_ACCOUNT_ID,
            apiToken = BuildConfig.SPEECH_API_TOKEN,
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
    val medRepository by lazy {
        // Как у покупок и напоминаний: подписки на Room виджету хватает
        // только пока жив процесс.
        MedRepository(
            dao = medDao,
            scheduler = medScheduler,
            onChanged = { MedWidget().updateAll(appContext) },
        )
    }

    val diagnosticsInspector by lazy { DiagnosticsInspector(appContext) }
    val testAlarmScheduler by lazy { TestAlarmScheduler(appContext) }

    private companion object {
        val HTTP_TIMEOUT: Duration = Duration.ofSeconds(30)

        /**
         * Пауза перед единственным повтором.
         *
         * Полторы секунды — компромисс: лимит частоты за это время нередко
         * отпускает, а человек всё ещё смотрит на карточку разбора и не
         * успевает решить, что приложение зависло.
         */
        const val RETRY_DELAY_MILLIS = 1_500L
    }
}
