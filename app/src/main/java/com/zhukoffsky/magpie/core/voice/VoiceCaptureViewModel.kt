package com.zhukoffsky.magpie.core.voice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zhukoffsky.magpie.MagpieApp
import com.zhukoffsky.magpie.feature.shopping.data.ShoppingRepository
import com.zhukoffsky.magpie.feature.shopping.domain.ShoppingPhraseParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Почему голосовой ввод не удался. Влияет только на текст сообщения. */
enum class VoiceFailure {
    /** На устройстве нет приложения, умеющего распознавать речь. */
    NO_RECOGNIZER,

    /** Пользователь закрыл диалог распознавания или ничего не сказал. */
    NOTHING_RECOGNIZED,
}

sealed interface VoiceCaptureUiState {
    /** Открыт системный диалог распознавания, своего интерфейса не показываем. */
    data object Listening : VoiceCaptureUiState

    /** Распознано; ждём подтверждения и правок. */
    data class Confirming(val items: List<String>) : VoiceCaptureUiState

    data class Failed(val reason: VoiceFailure) : VoiceCaptureUiState

    /** Сохранено, экран пора закрывать. */
    data object Done : VoiceCaptureUiState
}

class VoiceCaptureViewModel(
    private val target: VoiceTarget,
    private val shoppingRepository: ShoppingRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<VoiceCaptureUiState>(VoiceCaptureUiState.Listening)
    val uiState: StateFlow<VoiceCaptureUiState> = _uiState.asStateFlow()

    private var recognitionLaunched = false

    /**
     * Разрешает запуск диалога распознавания ровно один раз на попытку.
     *
     * Флаг живёт в ViewModel, а не в активности: поворот экрана, пока
     * системный диалог поверх, пересоздаёт активность — без этой защиты
     * распознавание запустилось бы вторым окном.
     */
    fun shouldStartRecognition(): Boolean {
        if (recognitionLaunched || _uiState.value !is VoiceCaptureUiState.Listening) return false
        recognitionLaunched = true
        return true
    }

    /** @param spoken распознанная фраза; null — отмена, ошибка или тишина. */
    fun onRecognitionResult(spoken: String?) {
        val items = when (target) {
            VoiceTarget.SHOPPING -> spoken?.let(ShoppingPhraseParser::parse).orEmpty()
        }
        _uiState.value = if (items.isEmpty()) {
            VoiceCaptureUiState.Failed(VoiceFailure.NOTHING_RECOGNIZED)
        } else {
            VoiceCaptureUiState.Confirming(items)
        }
    }

    fun onRecognizerMissing() {
        _uiState.value = VoiceCaptureUiState.Failed(VoiceFailure.NO_RECOGNIZER)
    }

    fun onRetry() {
        recognitionLaunched = false
        _uiState.value = VoiceCaptureUiState.Listening
    }

    fun onItemChange(index: Int, value: String) = updateItems { items ->
        items.mapIndexed { i, item -> if (i == index) value else item }
    }

    fun onItemRemove(index: Int) = updateItems { items ->
        items.filterIndexed { i, _ -> i != index }
    }

    fun onConfirm() {
        val items = (_uiState.value as? VoiceCaptureUiState.Confirming)?.items ?: return
        // Состояние меняем сразу: повторный тап по «Сохранить» до завершения
        // записи иначе продублирует позиции.
        _uiState.value = VoiceCaptureUiState.Done
        viewModelScope.launch {
            when (target) {
                VoiceTarget.SHOPPING -> items.forEach { shoppingRepository.add(it) }
            }
        }
    }

    private fun updateItems(transform: (List<String>) -> List<String>) {
        val current = _uiState.value as? VoiceCaptureUiState.Confirming ?: return
        val updated = transform(current.items)
        _uiState.value = if (updated.isEmpty()) {
            VoiceCaptureUiState.Done
        } else {
            VoiceCaptureUiState.Confirming(updated)
        }
    }

    companion object {
        fun factory(target: VoiceTarget): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MagpieApp
                VoiceCaptureViewModel(target, app.container.shoppingRepository)
            }
        }
    }
}
