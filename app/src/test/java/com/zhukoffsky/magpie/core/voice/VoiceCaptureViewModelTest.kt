package com.zhukoffsky.magpie.core.voice

import com.zhukoffsky.magpie.core.data.db.FakeShoppingDao
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

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceCaptureViewModelTest {

    private val dao = FakeShoppingDao()
    private val viewModel = VoiceCaptureViewModel(
        target = VoiceTarget.SHOPPING,
        shoppingRepository = ShoppingRepository(dao),
    )

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `recognised phrase becomes an editable list`() {
        viewModel.onRecognitionResult("молоко, хлеб и яйца")

        assertEquals(
            VoiceCaptureUiState.Confirming(listOf("молоко", "хлеб", "яйца")),
            viewModel.uiState.value,
        )
    }

    @Test
    fun `cancelled recognition reports a failure instead of saving nothing silently`() {
        viewModel.onRecognitionResult(null)

        assertEquals(
            VoiceCaptureUiState.Failed(VoiceFailure.NOTHING_RECOGNIZED),
            viewModel.uiState.value,
        )
    }

    @Test
    fun `phrase with no usable words is a failure too`() {
        viewModel.onRecognitionResult("  ,  , ")

        assertEquals(
            VoiceCaptureUiState.Failed(VoiceFailure.NOTHING_RECOGNIZED),
            viewModel.uiState.value,
        )
    }

    @Test
    fun `editing replaces a single item`() {
        viewModel.onRecognitionResult("молоко и хлеб")

        viewModel.onItemChange(1, "хлеб бородинский")

        assertEquals(
            VoiceCaptureUiState.Confirming(listOf("молоко", "хлеб бородинский")),
            viewModel.uiState.value,
        )
    }

    @Test
    fun `removing the last item closes the screen`() {
        viewModel.onRecognitionResult("молоко")

        viewModel.onItemRemove(0)

        assertEquals(VoiceCaptureUiState.Done, viewModel.uiState.value)
    }

    @Test
    fun `confirm writes every item to the repository`() = runTest {
        viewModel.onRecognitionResult("молоко, хлеб")

        viewModel.onConfirm()

        assertEquals(listOf("молоко", "хлеб"), dao.items.value.map { it.title })
        assertEquals(VoiceCaptureUiState.Done, viewModel.uiState.value)
    }

    @Test
    fun `double tap on save does not duplicate items`() = runTest {
        viewModel.onRecognitionResult("молоко")

        viewModel.onConfirm()
        viewModel.onConfirm()

        assertEquals(listOf("молоко"), dao.items.value.map { it.title })
    }

    @Test
    fun `retry returns to listening and re-arms the recogniser`() {
        viewModel.onRecognitionResult(null)

        viewModel.onRetry()

        assertEquals(VoiceCaptureUiState.Listening, viewModel.uiState.value)
        assertEquals(true, viewModel.shouldStartRecognition())
    }

    @Test
    fun `recogniser is launched only once per attempt`() {
        assertEquals(true, viewModel.shouldStartRecognition())
        assertEquals(false, viewModel.shouldStartRecognition())
    }
}
