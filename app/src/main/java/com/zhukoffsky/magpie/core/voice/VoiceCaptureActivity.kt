package com.zhukoffsky.magpie.core.voice

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zhukoffsky.magpie.R
import com.zhukoffsky.magpie.core.ui.MagpieRoot
import java.util.Locale

/**
 * Точка входа голосового ввода: виджет, плитка или кнопка на экране ведут
 * сюда, активность немедленно начинает слушать.
 *
 * **Два входа устроены по-разному, и это главное решение здесь.**
 *
 * Напоминание — одна короткая фраза, его берёт системный диалог: он
 * заканчивает по тишине, что для одной фразы и требуется, и не стоит
 * приложению ни разрешения на микрофон, ни трафика.
 *
 * Список покупок пишем сами. Его диктуют с паузами — назвал три позиции,
 * вспоминаешь четвёртую, — а любой чужой распознаватель на паузе сессию
 * заканчивает. Продержать её не вышло ни системным диалогом, ни своим
 * `SpeechRecognizer`, ни непрерывной сессией API 31+: последняя на Pixel 8
 * падает посреди диктовки с `ERROR_CLIENT`. Поэтому микрофон держит
 * приложение, останавливает запись только человек, а расшифровка идёт
 * одним запросом на весь файл.
 *
 * Активность прозрачная и живёт в отдельной задаче — она не должна
 * появляться в списке недавних и подмешиваться в стек главного экрана.
 */
class VoiceCaptureActivity : ComponentActivity() {

    private val target: VoiceTarget by lazy { targetOf(intent) }

    private val viewModel: VoiceCaptureViewModel by viewModels {
        VoiceCaptureViewModel.factory(target)
    }

    private val recorder by lazy { VoiceRecorder(this) }
    private val handler = Handler(Looper.getMainLooper())
    private var startedAt = 0L

    private val recognition = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val spoken = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
        viewModel.onRecognitionResult(spoken.takeIf { result.resultCode == RESULT_OK })
    }

    private val microphone = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) startRecording() else viewModel.onPermissionDenied()
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
                    if (viewModel.shouldStartRecognition()) startRecognition()
                    if (viewModel.shouldStartRecording()) requestMicrophoneThenRecord()
                }

                VoiceCaptureScreen(
                    state = state,
                    target = target,
                    onUndo = viewModel::onUndo,
                    onCancel = ::finish,
                    onRetry = viewModel::onRetry,
                    onFinishRecording = ::finishRecording,
                )
            }
        }
    }

    /**
     * Экран закрыли, не дослушав: звук больше никому не нужен.
     *
     * Именно `cancel`, а не `stop`: незаконченная запись не должна
     * оставаться в кэше, а расшифровывать её уже некуда — ViewModel уходит
     * вместе с активностью.
     */
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        recorder.cancel()
        super.onDestroy()
    }

    private fun requestMicrophoneThenRecord() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

        if (granted) startRecording() else microphone.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun startRecording() {
        if (!recorder.start()) {
            viewModel.onMicrophoneUnavailable()
            return
        }
        startedAt = SystemClock.elapsedRealtime()
        handler.post(tick)
    }

    /**
     * Секунды и уровень звука на карточку.
     *
     * Не украшение: без обратной связи непонятно, идёт ли запись вообще, а
     * доверия к экрану, который молчит, ровно ноль — именно так выглядела
     * та поломка, из-за которой от чужой сессии и ушли.
     */
    private val tick = object : Runnable {
        override fun run() {
            if (!recorder.isRecording) return
            val seconds = ((SystemClock.elapsedRealtime() - startedAt) / 1_000L).toInt()
            viewModel.onRecordingTick(seconds, recorder.level())
            handler.postDelayed(this, TICK_MILLIS)
        }
    }

    /** «Готово»: единственное, что заканчивает запись. */
    private fun finishRecording() {
        handler.removeCallbacksAndMessages(null)
        viewModel.onAudioRecorded(recorder.stop(), Locale.getDefault().toLanguageTag())
    }

    private fun startRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.voice_prompt_reminder))
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        try {
            recognition.launch(intent)
        } catch (_: ActivityNotFoundException) {
            viewModel.onRecognizerMissing()
        }
    }

    companion object {
        /** Десять раз в секунду: полоска уровня должна выглядеть живой. */
        private const val TICK_MILLIS = 100L

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
