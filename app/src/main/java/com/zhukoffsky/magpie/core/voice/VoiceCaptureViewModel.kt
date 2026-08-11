package com.zhukoffsky.magpie.core.voice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zhukoffsky.magpie.MagpieApp
import com.zhukoffsky.magpie.feature.reminders.data.ReminderRepository
import com.zhukoffsky.magpie.feature.reminders.domain.PhraseParser
import com.zhukoffsky.magpie.feature.reminders.domain.RepeatRule
import com.zhukoffsky.magpie.feature.shopping.data.ShoppingRepository
import com.zhukoffsky.magpie.feature.shopping.domain.ShoppingItemsParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.ZonedDateTime

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

    /** Фраза распознана, идёт разбор. Может занять секунду-две, если зовём LLM. */
    data object Parsing : VoiceCaptureUiState

    /** Распознан список покупок; ждём подтверждения и правок. */
    data class ConfirmingItems(val items: List<String>) : VoiceCaptureUiState

    /** Распознано напоминание; ждём подтверждения. */
    data class ConfirmingReminder(
        val title: String,
        val dueAt: ZonedDateTime,
        val repeat: RepeatRule?,
    ) : VoiceCaptureUiState

    data class Failed(val reason: VoiceFailure) : VoiceCaptureUiState

    /** Сохранено, экран пора закрывать. */
    data object Done : VoiceCaptureUiState
}

class VoiceCaptureViewModel(
    private val target: VoiceTarget,
    private val shoppingRepository: ShoppingRepository,
    private val reminderRepository: ReminderRepository,
    private val parser: PhraseParser,
    private val shoppingParser: ShoppingItemsParser,
    private val clock: Clock = Clock.systemDefaultZone(),
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
        when (target) {
            VoiceTarget.SHOPPING -> {
                val phrase = spoken?.trim().orEmpty()
                if (phrase.isEmpty()) {
                    _uiState.value = failure()
                    return
                }

                // Как и у напоминаний, разбор асинхронный: правила отвечают
                // мгновенно, но фраза без разделителей уходит в сеть.
                _uiState.value = VoiceCaptureUiState.Parsing
                viewModelScope.launch {
                    val items = shoppingParser.parse(phrase)
                    _uiState.value = if (items.isEmpty()) {
                        failure()
                    } else {
                        VoiceCaptureUiState.ConfirmingItems(items)
                    }
                }
            }

            VoiceTarget.REMINDER -> {
                val phrase = spoken?.trim().orEmpty()
                if (phrase.isEmpty()) {
                    _uiState.value = failure()
                    return
                }

                // Разбор стал асинхронным: правила отвечают мгновенно, но
                // при откате на LLM это сетевой вызов.
                _uiState.value = VoiceCaptureUiState.Parsing
                viewModelScope.launch {
                    val parsed = parser.parse(phrase, ZonedDateTime.now(clock))
                    _uiState.value = if (parsed == null) {
                        failure()
                    } else {
                        VoiceCaptureUiState.ConfirmingReminder(
                            title = parsed.title,
                            dueAt = parsed.dueAt,
                            repeat = parsed.repeat,
                        )
                    }
                }
            }
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

    fun onTitleChange(value: String) {
        val current = _uiState.value as? VoiceCaptureUiState.ConfirmingReminder ?: return
        _uiState.value = current.copy(title = value)
    }

    /**
     * Защита от повторного тапа по «Сохранить».
     *
     * Раньше эту роль играл перевод состояния в `Done` перед записью — и
     * стоил потери данных: на `Done` активность закрывается, вместе с ней
     * отменяется `viewModelScope`, и цикл записи обрывался на середине.
     * Пока записей было мало и они были быстрыми, это проскакивало; стоило
     * записи подорожать, как из пяти покупок сохранялись три.
     *
     * Теперь `Done` выставляется после записи, а от второго тапа защищает
     * этот флаг.
     */
    private var saving = false

    fun onConfirm() {
        if (saving) return

        when (val current = _uiState.value) {
            is VoiceCaptureUiState.ConfirmingItems -> {
                saving = true
                viewModelScope.launch {
                    shoppingRepository.addAll(current.items)
                    _uiState.value = VoiceCaptureUiState.Done
                }
            }

            is VoiceCaptureUiState.ConfirmingReminder -> {
                saving = true
                viewModelScope.launch {
                    reminderRepository.add(
                        title = current.title,
                        dueAt = current.dueAt.toInstant(),
                        repeat = current.repeat,
                    )
                    _uiState.value = VoiceCaptureUiState.Done
                }
            }

            else -> Unit
        }
    }

    private fun failure() = VoiceCaptureUiState.Failed(VoiceFailure.NOTHING_RECOGNIZED)

    private fun updateItems(transform: (List<String>) -> List<String>) {
        val current = _uiState.value as? VoiceCaptureUiState.ConfirmingItems ?: return
        val updated = transform(current.items)
        _uiState.value = if (updated.isEmpty()) {
            VoiceCaptureUiState.Done
        } else {
            VoiceCaptureUiState.ConfirmingItems(updated)
        }
    }

    companion object {
        fun factory(target: VoiceTarget): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container =
                    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MagpieApp).container
                VoiceCaptureViewModel(
                    target = target,
                    shoppingRepository = container.shoppingRepository,
                    reminderRepository = container.reminderRepository,
                    parser = container.phraseParser,
                    shoppingParser = container.shoppingItemsParser,
                )
            }
        }
    }
}
