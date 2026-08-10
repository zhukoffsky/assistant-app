package com.zhukoffsky.magpie.core.voice

import com.zhukoffsky.magpie.core.data.db.FakeReminderDao
import com.zhukoffsky.magpie.core.data.db.FakeShoppingDao
import com.zhukoffsky.magpie.feature.reminders.alarm.ReminderScheduler
import com.zhukoffsky.magpie.feature.reminders.data.ReminderRepository
import com.zhukoffsky.magpie.feature.reminders.domain.RepeatRule
import com.zhukoffsky.magpie.feature.shopping.data.ShoppingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
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

    private fun viewModel(target: VoiceTarget) = VoiceCaptureViewModel(
        target = target,
        shoppingRepository = ShoppingRepository(shoppingDao, clock),
        reminderRepository = ReminderRepository(reminderDao, NoopScheduler, clock),
        clock = clock,
    )

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `recognised phrase becomes an editable list`() {
        val vm = viewModel(VoiceTarget.SHOPPING)

        vm.onRecognitionResult("молоко, хлеб и яйца")

        assertEquals(
            VoiceCaptureUiState.ConfirmingItems(listOf("молоко", "хлеб", "яйца")),
            vm.uiState.value,
        )
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
    fun `phrase with no usable words is a failure too`() {
        val vm = viewModel(VoiceTarget.SHOPPING)

        vm.onRecognitionResult("  ,  , ")

        assertEquals(
            VoiceCaptureUiState.Failed(VoiceFailure.NOTHING_RECOGNIZED),
            vm.uiState.value,
        )
    }

    @Test
    fun `editing replaces a single item`() {
        val vm = viewModel(VoiceTarget.SHOPPING)
        vm.onRecognitionResult("молоко и хлеб")

        vm.onItemChange(1, "хлеб бородинский")

        assertEquals(
            VoiceCaptureUiState.ConfirmingItems(listOf("молоко", "хлеб бородинский")),
            vm.uiState.value,
        )
    }

    @Test
    fun `removing the last item closes the screen`() {
        val vm = viewModel(VoiceTarget.SHOPPING)
        vm.onRecognitionResult("молоко")

        vm.onItemRemove(0)

        assertEquals(VoiceCaptureUiState.Done, vm.uiState.value)
    }

    @Test
    fun `confirm writes every item to the repository`() = runTest {
        val vm = viewModel(VoiceTarget.SHOPPING)
        vm.onRecognitionResult("молоко, хлеб")

        vm.onConfirm()

        assertEquals(listOf("молоко", "хлеб"), shoppingDao.items.value.map { it.title })
        assertEquals(VoiceCaptureUiState.Done, vm.uiState.value)
    }

    @Test
    fun `double tap on save does not duplicate items`() = runTest {
        val vm = viewModel(VoiceTarget.SHOPPING)
        vm.onRecognitionResult("молоко")

        vm.onConfirm()
        vm.onConfirm()

        assertEquals(listOf("молоко"), shoppingDao.items.value.map { it.title })
    }

    @Test
    fun `reminder target parses date and repeat out of the phrase`() {
        val vm = viewModel(VoiceTarget.REMINDER)

        vm.onRecognitionResult("каждый вторник вынести мусор")

        assertEquals(
            VoiceCaptureUiState.ConfirmingReminder(
                title = "вынести мусор",
                dueAt = ZonedDateTime.of(2026, 8, 11, 9, 0, 0, 0, zone),
                repeat = RepeatRule.Weekly(setOf(DayOfWeek.TUESDAY)),
            ),
            vm.uiState.value,
        )
    }

    @Test
    fun `editing the reminder title keeps the parsed time`() {
        val vm = viewModel(VoiceTarget.REMINDER)
        vm.onRecognitionResult("завтра в 9 позвонить")

        vm.onTitleChange("позвонить в поликлинику")

        val state = vm.uiState.value as VoiceCaptureUiState.ConfirmingReminder
        assertEquals("позвонить в поликлинику", state.title)
        assertEquals(ZonedDateTime.of(2026, 8, 11, 9, 0, 0, 0, zone), state.dueAt)
    }

    @Test
    fun `confirming a reminder stores it`() = runTest {
        val vm = viewModel(VoiceTarget.REMINDER)
        vm.onRecognitionResult("завтра в 9 позвонить")

        vm.onConfirm()

        val stored = reminderDao.items.value.single()
        assertEquals("позвонить", stored.title)
        assertEquals(
            ZonedDateTime.of(2026, 8, 11, 9, 0, 0, 0, zone).toInstant(),
            stored.dueAt,
        )
    }

    @Test
    fun `retry returns to listening and re-arms the recogniser`() {
        val vm = viewModel(VoiceTarget.SHOPPING)
        vm.onRecognitionResult(null)

        vm.onRetry()

        assertEquals(VoiceCaptureUiState.Listening, vm.uiState.value)
        assertEquals(true, vm.shouldStartRecognition())
    }

    @Test
    fun `recogniser is launched only once per attempt`() {
        val vm = viewModel(VoiceTarget.SHOPPING)

        assertEquals(true, vm.shouldStartRecognition())
        assertEquals(false, vm.shouldStartRecognition())
    }

    private object NoopScheduler : ReminderScheduler {
        override fun schedule(id: Long, at: Instant) = Unit
        override fun cancel(id: Long) = Unit
    }
}
