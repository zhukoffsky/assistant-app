package com.zhukoffsky.magpie.core.voice

import com.zhukoffsky.magpie.core.data.db.FakeReminderDao
import com.zhukoffsky.magpie.core.data.db.FakeShoppingDao
import com.zhukoffsky.magpie.feature.reminders.alarm.ReminderScheduler
import com.zhukoffsky.magpie.feature.reminders.data.ReminderRepository
import com.zhukoffsky.magpie.feature.reminders.domain.HybridPhraseParser
import com.zhukoffsky.magpie.feature.reminders.domain.RuleBasedPhraseParser
import com.zhukoffsky.magpie.feature.reminders.domain.RepeatRule
import com.zhukoffsky.magpie.feature.shopping.data.ShoppingRepository
import com.zhukoffsky.magpie.feature.shopping.domain.RuleBasedShoppingParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import kotlinx.coroutines.flow.first
import org.junit.Test
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceCaptureViewModelTest {

    private val zone = ZoneId.of("Europe/Moscow")

    /** Понедельник, 10 августа 2026, 15:00 по Москве. */
    private val clock = Clock.fixed(Instant.parse("2026-08-10T12:00:00Z"), zone)

    private val shoppingDao = FakeShoppingDao()
    private val reminderDao = FakeReminderDao()

    private fun viewModel(
        target: VoiceTarget,
        scheduler: ReminderScheduler = RecordingScheduler(),
        transcript: String? = null,
        canTranscribe: Boolean = true,
    ) = VoiceCaptureViewModel(
        target = target,
        shoppingRepository = ShoppingRepository(shoppingDao, clock),
        reminderRepository = ReminderRepository(reminderDao, scheduler, clock),
        parser = HybridPhraseParser(rules = RuleBasedPhraseParser(), llm = null),
        // Без LLM: в тестах сети нет, а правил хватает для фраз с запятыми.
        shoppingParser = RuleBasedShoppingParser(),
        // Расшифровка подменяется целиком: сети в тестах нет, а проверяем мы
        // то, что происходит с текстом после неё.
        transcriber = { _, _ -> transcript },
        canTranscribe = canTranscribe,
        clock = clock,
    )

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `a recognised phrase is written immediately, without waiting for a tap`() = runTest {
        val vm = viewModel(VoiceTarget.SHOPPING)

        vm.onRecognitionResult("молоко, хлеб и яйца")

        val state = vm.uiState.value as VoiceCaptureUiState.SavedItems
        assertEquals(listOf("молоко", "хлеб", "яйца"), state.titles)
        assertEquals(
            listOf("молоко", "хлеб", "яйца"),
            shoppingDao.observeAll().first().map { it.title },
        )
    }

    /**
     * Регрессия: `Done` выставлялся до записи, активность на него
     * закрывалась и отменяла `viewModelScope` посреди цикла — из пяти
     * покупок сохранялись три. Порядок «сначала запись, потом закрытие»
     * с автозакрытием стал ещё важнее: тапа, который его удерживал, больше нет.
     */
    @Test
    fun `every dictated item is saved before the card appears`() = runTest {
        val vm = viewModel(VoiceTarget.SHOPPING)

        vm.onRecognitionResult("молоко, хлеб, яйца, масло и сыр")

        assertEquals(
            listOf("молоко", "хлеб", "яйца", "масло", "сыр"),
            shoppingDao.observeAll().first().map { it.title },
        )
    }

    @Test
    fun `undo removes everything the dictation added`() = runTest {
        val vm = viewModel(VoiceTarget.SHOPPING)
        vm.onRecognitionResult("молоко, хлеб")

        vm.onUndo()

        assertEquals(emptyList<String>(), shoppingDao.items.value.map { it.title })
        assertEquals(VoiceCaptureUiState.Done, vm.uiState.value)
    }

    @Test
    fun `undo leaves items dictated earlier alone`() = runTest {
        val vm = viewModel(VoiceTarget.SHOPPING)
        vm.onRecognitionResult("молоко")
        viewModel(VoiceTarget.SHOPPING).onRecognitionResult("хлеб, яйца")

        vm.onUndo()

        assertEquals(listOf("хлеб", "яйца"), shoppingDao.items.value.map { it.title })
    }

    @Test
    fun `a second recognition result does not write twice`() = runTest {
        val vm = viewModel(VoiceTarget.SHOPPING)

        vm.onRecognitionResult("молоко")
        vm.onRecognitionResult("молоко")

        assertEquals(listOf("молоко"), shoppingDao.items.value.map { it.title })
    }

    @Test
    fun `cancelled recognition reports a failure instead of saving nothing silently`() {
        val vm = viewModel(VoiceTarget.SHOPPING)

        vm.onRecognitionResult(null)

        assertEquals(
            VoiceCaptureUiState.Failed(VoiceFailure.NOTHING_RECOGNIZED),
            vm.uiState.value,
        )
    }

    @Test
    fun `phrase with no usable words is a failure and writes nothing`() {
        val vm = viewModel(VoiceTarget.SHOPPING)

        vm.onRecognitionResult("  ,  , ")

        assertEquals(
            VoiceCaptureUiState.Failed(VoiceFailure.NOTHING_RECOGNIZED),
            vm.uiState.value,
        )
        assertEquals(emptyList<String>(), shoppingDao.items.value.map { it.title })
    }

    @Test
    fun `reminder is stored with the parsed date and repeat`() = runTest {
        val vm = viewModel(VoiceTarget.REMINDER)

        vm.onRecognitionResult("каждый вторник вынести мусор")

        val state = vm.uiState.value as VoiceCaptureUiState.SavedReminder
        assertEquals("вынести мусор", state.title)
        assertEquals(ZonedDateTime.of(2026, 8, 11, 9, 0, 0, 0, zone), state.dueAt)
        assertEquals(RepeatRule.Weekly(setOf(DayOfWeek.TUESDAY)), state.repeat)

        val stored = reminderDao.items.value.single()
        assertEquals("вынести мусор", stored.title)
        assertEquals(ZonedDateTime.of(2026, 8, 11, 9, 0, 0, 0, zone).toInstant(), stored.dueAt)
    }

    @Test
    fun `undo removes the reminder and takes its alarm down with it`() = runTest {
        val scheduler = RecordingScheduler()
        val vm = viewModel(VoiceTarget.REMINDER, scheduler)
        vm.onRecognitionResult("завтра в 9 позвонить")
        val id = (vm.uiState.value as VoiceCaptureUiState.SavedReminder).id

        vm.onUndo()

        assertEquals(emptyList<String>(), reminderDao.items.value.map { it.title })
        assertEquals(setOf(id), scheduler.cancelled)
    }

    @Test
    fun `retry returns a reminder to the dialog and re-arms it`() {
        val vm = viewModel(VoiceTarget.REMINDER)
        vm.onRecognitionResult(null)

        vm.onRetry()

        assertEquals(VoiceCaptureUiState.Listening, vm.uiState.value)
        assertEquals(true, vm.shouldStartRecognition())
    }

    /**
     * У покупок другая точка старта, и это не деталь: список пишет само
     * приложение, потому что чужая сессия заканчивается на первой паузе.
     */
    @Test
    fun `retry returns shopping to recording and re-arms it`() {
        val vm = viewModel(VoiceTarget.SHOPPING)
        vm.onRecognitionResult(null)

        vm.onRetry()

        assertEquals(VoiceCaptureUiState.Recording(), vm.uiState.value)
        assertEquals(true, vm.shouldStartRecording())
    }

    /**
     * Сборка без ключей расшифровки обязана остаться рабочей: покупки
     * уходят в системный диалог, а не в запись, которую некуда деть.
     */
    @Test
    fun `without transcription shopping falls back to the dialog`() {
        val vm = viewModel(VoiceTarget.SHOPPING, canTranscribe = false)

        assertEquals(VoiceCaptureUiState.Listening, vm.uiState.value)
        assertEquals(true, vm.shouldStartRecognition())
        assertEquals(false, vm.shouldStartRecording())
    }

    @Test
    fun `recording starts only once per attempt`() {
        val vm = viewModel(VoiceTarget.SHOPPING)

        assertEquals(true, vm.shouldStartRecording())
        assertEquals(false, vm.shouldStartRecording())
    }

    @Test
    fun `a transcribed recording becomes items in the list`() = runTest {
        val vm = viewModel(VoiceTarget.SHOPPING, transcript = "молоко, хлеб, яйца")

        vm.onAudioRecorded(ByteArray(16), "ru-RU")

        assertEquals(
            listOf("молоко", "хлеб", "яйца"),
            shoppingDao.items.value.map { it.title },
        )
    }

    /**
     * Запись, которую не расшифровали, обязана сказать об этом.
     *
     * Тихий откат здесь хуже всего: звука уже нет, повторить его нечем, и
     * молчание выглядит как «приложение просто не сработало».
     */
    @Test
    fun `a recording that cannot be transcribed is reported`() = runTest {
        val vm = viewModel(VoiceTarget.SHOPPING, transcript = null)

        vm.onAudioRecorded(ByteArray(16), "ru-RU")

        assertEquals(
            VoiceCaptureUiState.Failed(VoiceFailure.TRANSCRIPTION_FAILED),
            vm.uiState.value,
        )
    }

    private class RecordingScheduler : ReminderScheduler {
        val cancelled = mutableSetOf<Long>()

        override fun schedule(id: Long, at: Instant) = Unit
        override fun scheduleSnooze(id: Long, at: Instant) = Unit
        override fun cancel(id: Long) {
            cancelled += id
        }
    }
}
