package com.zhukoffsky.magpie.core.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zhukoffsky.magpie.core.ui.MagpieRoot
import com.zhukoffsky.magpie.core.util.MagpieLog
import java.util.Locale

/**
 * Точка входа голосового ввода: виджет, плитка или кнопка на экране ведут
 * сюда, активность немедленно начинает слушать.
 *
 * **Запись своя, а не системным диалогом.** Чужой диалог обрывал фразу на
 * первой же паузе, а список покупок диктуют именно с паузами — назвал три
 * позиции, вспоминаешь четвёртую. Просить его подождать бесполезно:
 * `EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS` документация называет
 * подсказкой, и Google её игнорирует — проверено 12 августа на Pixel 8.
 *
 * Цена — разрешение `RECORD_AUDIO`, которого у приложения раньше не было
 * вовсе, и своя обработка ошибок вместо чужой.
 *
 * Активность прозрачная и живёт в отдельной задаче — она не должна
 * появляться в списке недавних и подмешиваться в стек главного экрана.
 */
class VoiceCaptureActivity : ComponentActivity() {

    private val target: VoiceTarget by lazy { targetOf(intent) }

    private val viewModel: VoiceCaptureViewModel by viewModels {
        VoiceCaptureViewModel.factory(target)
    }

    private var recognizer: SpeechRecognizer? = null

    /** Человек нажал «Готово»: следующий результат — последний. */
    private var finishing = false

    /**
     * Сколько раз запись начиналась заново после паузы.
     *
     * Ограничение — страховка от кручения вхолостую: если распознаватель
     * начнёт возвращать ошибку мгновенно, перезапуск без счётчика съест
     * батарею при открытом экране. Тридцати кусков хватает на список,
     * который невозможно надиктовать.
     */
    private var restarts = 0

    private val microphone = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) startListening() else viewModel.onPermissionDenied()
    }

    /**
     * Активность живёт в отдельной задаче с `singleTask`, поэтому вторая
     * точка входа может попасть в уже открытый экран. Если цель другая —
     * перезапускаемся: цикла не будет, у новой копии цель совпадёт.
     *
     * Перезапуск идёт **свежим** интентом из [intent], а не полученным. Ради
     * шорткатов активность объявлена `exported`, то есть прислать сюда интент
     * может любое приложение, и пересылать его дальше как есть — значит
     * запустить от своего имени чужую полезную нагрузку: флаги, данные,
     * выданные права на URI. Наружу нам нужна ровно одна величина — цель,
     * и она пересобирается заново.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val requested = targetOf(intent)
        if (requested == target) return

        finish()
        startActivity(intent(this, requested))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MagpieRoot {
                val state by viewModel.uiState.collectAsStateWithLifecycle()

                LaunchedEffect(state) {
                    if (state == VoiceCaptureUiState.Done) finish()
                    if (viewModel.shouldStartRecognition()) requestMicrophoneThenListen()
                }

                VoiceCaptureScreen(
                    state = state,
                    target = target,
                    onUndo = viewModel::onUndo,
                    onCancel = ::finish,
                    onRetry = viewModel::onRetry,
                    onFinishListening = ::finishListening,
                )
            }
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        // Распознаватель держит микрофон и переживёт активность, если его не
        // отпустить: следующий запуск получит ERROR_RECOGNIZER_BUSY.
        recognizer?.destroy()
        recognizer = null
        super.onDestroy()
    }

    private fun requestMicrophoneThenListen() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

        if (granted) startListening() else microphone.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            viewModel.onRecognizerMissing()
            return
        }

        finishing = false
        restarts = 0

        // Сюда приходят дважды: первый раз при открытии экрана, второй — по
        // кнопке «Повторить». Прежний распознаватель к этому моменту жив и
        // держит микрофон, и новый на том же микрофоне получил бы
        // ERROR_RECOGNIZER_BUSY — то есть «Повторить» не работало бы вовсе.
        handler.removeCallbacksAndMessages(null)
        recognizer?.destroy()

        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(listener)
            startListening(recognizerIntent())
        }
    }

    /**
     * Непрерывная запись одной сессией — только для покупок и только с
     * Android 12.
     *
     * Без неё после каждой паузы приходится начинать распознавание заново, а
     * это слышно и заметно: играет звук включения микрофона, и слова на стыке
     * теряются. В сегментированной сессии микрофон не отпускается вовсе,
     * куски приезжают в `onSegmentResults`, а заканчиваем мы сами по кнопке.
     *
     * Напоминанию она не нужна: там одна короткая фраза и конец по тишине.
     */
    private val segmented: Boolean
        get() = target == VoiceTarget.SHOPPING && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /**
     * Кнопка «Готово» у покупок.
     *
     * `stopListening` не бросает запись, а просит закончить: последний кусок
     * ещё приедет в `onResults`, и только тогда фраза уходит в разбор.
     */
    private fun finishListening() {
        finishing = true
        recognizer?.stopListening()
    }

    /**
     * Запись у покупок заканчивает **только человек**.
     *
     * Всё остальное — пауза, конец куска, оборванная сессия, ошибка сети — не
     * конец диктовки, а повод слушать дальше: сказанное уже накоплено в
     * ViewModel и не теряется. Иначе получается то, на что владелец и
     * пожаловался: «остановилась запись ни с того ни с сего».
     *
     * У напоминания наоборот: там одна короткая фраза, и конец по тишине —
     * это и есть задуманное поведение.
     */
    private fun listenAgainOrFinish() {
        when {
            finishing -> viewModel.onListeningFinished()
            target == VoiceTarget.REMINDER -> viewModel.onListeningFinished()
            restarts >= MAX_RESTARTS -> viewModel.onListeningFinished()
            else -> restartListening()
        }
    }

    /**
     * Пауза перед перезапуском.
     *
     * Мгновенный `startListening` после ошибки нередко получает
     * `ERROR_RECOGNIZER_BUSY` — распознаватель ещё не отпустил микрофон.
     * Треть секунды глазу незаметна, а цикл ошибок разрывает.
     */
    private fun restartListening() {
        restarts++
        handler.postDelayed(
            {
                if (!isFinishing && !finishing) {
                    recognizer?.cancel()
                    recognizer?.startListening(recognizerIntent())
                }
            },
            RESTART_DELAY_MILLIS,
        )
    }

    private val handler = Handler(Looper.getMainLooper())

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onPartialResults(partialResults: Bundle) {
            firstResult(partialResults)?.let(viewModel::onPartial)
        }

        override fun onResults(results: Bundle) {
            firstResult(results)?.let(viewModel::onSegment)

            // В непрерывной сессии это конец куска, а не записи: её закроет
            // `onEndOfSegmentedSession`.
            if (!segmented) listenAgainOrFinish()
        }

        /** Кусок непрерывной сессии: микрофон при этом остаётся открытым. */
        override fun onSegmentResults(segmentResults: Bundle) {
            firstResult(segmentResults)?.let(viewModel::onSegment)
        }

        /**
         * Сессия закончилась. Если не по кнопке — начинаем заново.
         *
         * Кончиться она может и сама: распознаватель имеет право закрыть
         * сессию по своим причинам, и молча вставший микрофон выглядит
         * поломкой.
         */
        override fun onEndOfSegmentedSession() = listenAgainOrFinish()

        override fun onError(error: Int) {
            MagpieLog.i("voice: recognizer error=$error")

            /*
             * Ошибка у покупок — не конец диктовки.
             *
             * Сеть моргнула, распознаватель занят, ничего не расслышал —
             * человек этого не заказывал и продолжает говорить. Сказанное
             * накоплено, поэтому просто слушаем дальше; заканчивает кнопка.
             */
            if (segmented && !finishing) {
                // Тишину в непрерывной сессии игнорируем совсем: сессия жива.
                val silence = error == SpeechRecognizer.ERROR_NO_MATCH ||
                    error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                if (!silence) restartListening()
                return
            }

            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                -> listenAgainOrFinish()

                else -> viewModel.onListeningFinished()
            }
        }

        private fun firstResult(bundle: Bundle): String? = bundle
            .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    private fun recognizerIntent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            if (segmented) {
                // Значение — имя того extra, которым задаётся длина куска.
                // Сессию оно не заканчивает: её закрывает `stopListening`.
                putExtra(
                    RecognizerIntent.EXTRA_SEGMENTED_SESSION,
                    RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                )
            }
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)

            // Живой текст на карточке: без него непонятно, слышат ли вообще.
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)

            // Те же подсказки, что и раньше. Google их игнорирует, но другие
            // распознаватели — нет, а стоят они ничего. Настоящую защиту от
            // обрыва даёт перезапуск после паузы, а не эти значения.
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                SILENCE_MILLIS,
            )
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                SILENCE_MILLIS,
            )
        }

    companion object {
        /** Сколько тишины ждать, прежде чем считать кусок законченным. */
        private const val SILENCE_MILLIS = 3_000L

        private const val MAX_RESTARTS = 30
        private const val RESTART_DELAY_MILLIS = 300L

        fun intent(context: Context, target: VoiceTarget): Intent =
            Intent(context, VoiceCaptureActivity::class.java)
                .setAction(target.action)
                .putExtra(VoiceTarget.EXTRA_TARGET, target.name)

        private fun targetOf(intent: Intent): VoiceTarget =
            VoiceTarget.fromAction(intent.action)
                ?: VoiceTarget.fromName(intent.getStringExtra(VoiceTarget.EXTRA_TARGET))
                ?: VoiceTarget.SHOPPING
    }
}
