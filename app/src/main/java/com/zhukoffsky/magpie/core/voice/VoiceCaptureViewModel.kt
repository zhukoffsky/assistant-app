package com.zhukoffsky.magpie.core.voice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zhukoffsky.magpie.MagpieApp
import com.zhukoffsky.magpie.core.speech.SpeechTranscriber
import com.zhukoffsky.magpie.feature.reminders.data.ReminderRepository
import com.zhukoffsky.magpie.feature.reminders.domain.PhraseParser
import com.zhukoffsky.magpie.feature.reminders.domain.RepeatRule
import com.zhukoffsky.magpie.feature.shopping.data.ShoppingRepository
import com.zhukoffsky.magpie.feature.shopping.domain.ShoppingItemsParser
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

    /** Без микрофона своя запись невозможна. */
    NO_PERMISSION,

    /** Микрофон занят: звонок или запись в другом приложении. */
    NO_MICROPHONE,

    /**
     * Записали, но расшифровать не смогли: нет сети, нет ключей в сборке,
     * сервис отказал. Запись при этом уже не вернуть, поэтому сообщение
     * предлагает продиктовать заново.
     */
    TRANSCRIPTION_FAILED,
}

sealed interface VoiceCaptureUiState {
    /** Открыт системный диалог распознавания, своего интерфейса не показываем. */
    data object Listening : VoiceCaptureUiState

    /**
     * Идёт своя запись, и закончить её может только человек.
     *
     * [seconds] и [level] нужны, чтобы карточка не выглядела застывшей:
     * без обратной связи непонятно, слышат ли вообще, и люди начинают
     * говорить громче вместо того, чтобы продолжать.
     */
    data class Recording(val seconds: Int = 0, val level: Float = 0f) : VoiceCaptureUiState

    /** Запись ушла на расшифровку. */
    data object Transcribing : VoiceCaptureUiState

    /** Фраза распознана, идёт разбор. Может занять секунду-две, если зовём LLM. */
    data object Parsing : VoiceCaptureUiState

    /**
     * Покупки уже в списке; карточка показывает, что записалось, и три
     * секунды ждёт отмены.
     */
    data class SavedItems(val titles: List<String>, val ids: List<Long>) : VoiceCaptureUiState

    /** Напоминание уже стоит; карточка показывает время и ждёт отмены. */
    data class SavedReminder(
        val id: Long,
        val title: String,
        val dueAt: ZonedDateTime,
        val repeat: RepeatRule?,
    ) : VoiceCaptureUiState

    data class Failed(val reason: VoiceFailure) : VoiceCaptureUiState

    /** Экран пора закрывать. */
    data object Done : VoiceCaptureUiState
}

class VoiceCaptureViewModel(
    private val target: VoiceTarget,
    private val shoppingRepository: ShoppingRepository,
    private val reminderRepository: ReminderRepository,
    private val parser: PhraseParser,
    private val shoppingParser: ShoppingItemsParser,
    private val transcriber: SpeechTranscriber,
    private val canTranscribe: Boolean = true,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {

    /**
     * Два входа различаются с самого начала.
     *
     * Список покупок пишем сами: его диктуют с паузами, и заканчивать
     * должен человек. Напоминание — одна короткая фраза, там системный
     * диалог быстрее всего, и городить свою запись ради него незачем.
     *
     * Если расшифровывать нечем (сборка без ключей), покупки тоже уходят в
     * диалог: своя запись без расшифровки — это просто потерянный звук.
     */
    private fun initialState(): VoiceCaptureUiState = when {
        target == VoiceTarget.SHOPPING && canTranscribe -> VoiceCaptureUiState.Recording()
        else -> VoiceCaptureUiState.Listening
    }

    private val _uiState = MutableStateFlow(initialState())
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

    /** То же самое для своей записи: один раз на попытку. */
    fun shouldStartRecording(): Boolean {
        if (recognitionLaunched || _uiState.value !is VoiceCaptureUiState.Recording) return false
        recognitionLaunched = true
        return true
    }

    /** Секунды и уровень с работающего рекордера. */
    fun onRecordingTick(seconds: Int, level: Float) {
        if (_uiState.value !is VoiceCaptureUiState.Recording) return
        _uiState.value = VoiceCaptureUiState.Recording(seconds = seconds, level = level)
    }

    fun onPermissionDenied() {
        _uiState.value = VoiceCaptureUiState.Failed(VoiceFailure.NO_PERMISSION)
    }

    fun onMicrophoneUnavailable() {
        _uiState.value = VoiceCaptureUiState.Failed(VoiceFailure.NO_MICROPHONE)
    }

    /**
     * Запись закончена — расшифровываем и дальше идём общим путём.
     *
     * @param audio файл целиком; null — записать не удалось или в записи
     *        не оказалось звука (нажали «Готово» мгновенно).
     */
    fun onAudioRecorded(audio: ByteArray?, languageTag: String) {
        if (audio == null) {
            _uiState.value = failure()
            return
        }
        if (transcribing) return
        transcribing = true
        _uiState.value = VoiceCaptureUiState.Transcribing

        viewModelScope.launch {
            val text = transcriber.transcribe(audio, languageTag)
            transcribing = false

            if (text.isNullOrBlank()) {
                _uiState.value = VoiceCaptureUiState.Failed(VoiceFailure.TRANSCRIPTION_FAILED)
                return@launch
            }
            onRecognitionResult(text)
        }
    }

    /** @param spoken распознанная фраза; null — отмена, ошибка или тишина. */
    fun onRecognitionResult(spoken: String?) {
        val phrase = spoken?.trim().orEmpty()
        if (phrase.isEmpty()) {
            _uiState.value = failure()
            return
        }
        if (saving) return
        saving = true

        // Разбор асинхронный: правила отвечают мгновенно, но фраза, которой
        // они не осилили, уходит в сеть к модели.
        _uiState.value = VoiceCaptureUiState.Parsing

        viewModelScope.launch {
            val saved = when (target) {
                VoiceTarget.SHOPPING -> saveItems(phrase)
                VoiceTarget.REMINDER -> saveReminder(phrase)
            }

            if (saved == null) {
                saving = false
                _uiState.value = failure()
                return@launch
            }

            _uiState.value = saved
            startAutoClose()
        }
    }

    private suspend fun saveItems(phrase: String): VoiceCaptureUiState? {
        val items = shoppingParser.parse(phrase)
        if (items.isEmpty()) return null

        val ids = shoppingRepository.addAll(items)
        if (ids.isEmpty()) return null

        return VoiceCaptureUiState.SavedItems(titles = items.map { it.title }, ids = ids)
    }

    private suspend fun saveReminder(phrase: String): VoiceCaptureUiState? {
        val parsed = parser.parse(phrase, ZonedDateTime.now(clock)) ?: return null
        val id = reminderRepository.add(
            title = parsed.title,
            dueAt = parsed.dueAt.toInstant(),
            repeat = parsed.repeat,
        ) ?: return null

        return VoiceCaptureUiState.SavedReminder(
            id = id,
            title = parsed.title,
            dueAt = parsed.dueAt,
            repeat = parsed.repeat,
        )
    }

    /**
     * Отмена откатывает по-настоящему: покупки удаляются, у напоминания
     * снимается ещё и будильник — это делает `delete` в репозитории.
     *
     * Удаление обязано закончиться **до** `Done`: на нём активность
     * закрывается и отменяет `viewModelScope`. Ровно на этом порядке уже
     * теряли данные при сохранении.
     */
    fun onUndo() {
        if (undoing) return
        undoing = true
        autoClose?.cancel()

        val current = _uiState.value
        viewModelScope.launch {
            when (current) {
                is VoiceCaptureUiState.SavedItems -> shoppingRepository.deleteAll(current.ids)
                is VoiceCaptureUiState.SavedReminder -> reminderRepository.delete(current.id)
                else -> Unit
            }
            _uiState.value = VoiceCaptureUiState.Done
        }
    }

    private fun startAutoClose() {
        autoClose = viewModelScope.launch {
            delay(AUTO_CLOSE_MILLIS)
            _uiState.value = VoiceCaptureUiState.Done
        }
    }

    fun onRecognizerMissing() {
        _uiState.value = VoiceCaptureUiState.Failed(VoiceFailure.NO_RECOGNIZER)
    }

    fun onRetry() {
        recognitionLaunched = false
        saving = false
        transcribing = false
        _uiState.value = initialState()
    }

    /**
     * Флаги, а не состояние.
     *
     * Роль защиты когда-то играл перевод состояния в `Done` перед записью — и
     * стоил данных: на `Done` активность закрывается, вместе с ней
     * отменяется `viewModelScope`, и цикл записи обрывался на середине. Пока
     * записей было мало, это проскакивало; стоило записи подорожать, как из
     * пяти покупок сохранялись три.
     */
    private var saving = false
    private var undoing = false
    private var transcribing = false

    private var autoClose: Job? = null

    private fun failure() = VoiceCaptureUiState.Failed(VoiceFailure.NOTHING_RECOGNIZED)

    companion object {

        /**
         * Сколько карточка висит перед закрытием.
         *
         * Три секунды — компромисс: хватает прочитать разобранное время и
         * дотянуться до «Отменить», но не настолько долго, чтобы экран
         * ощущался как ещё один шаг. Смысл приложения — «тап по виджету →
         * сразу микрофон», и лишний тап по «Сохранить» этот смысл и съедал.
         *
         * Публичная, потому что ту же длительность отсчитывает полоса на
         * карточке: две константы разъехались бы.
         */
        const val AUTO_CLOSE_MILLIS = 3_000L
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
                    transcriber = container.speechTranscriber,
                    canTranscribe = container.speechAvailable,
                )
            }
        }
    }
}
