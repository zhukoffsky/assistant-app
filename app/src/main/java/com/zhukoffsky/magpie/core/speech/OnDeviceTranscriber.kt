package com.zhukoffsky.magpie.core.speech

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.MainThread
import androidx.annotation.RequiresApi
import androidx.core.content.edit
import com.zhukoffsky.magpie.core.util.MagpieLog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executor
import java.util.concurrent.LinkedBlockingQueue
import kotlin.coroutines.resume

/**
 * Расшифровка распознавателем на самом телефоне — без сети и без чужих
 * облаков.
 *
 * **Главный режим — на лету** ([startLive]): звук идёт распознавателю
 * кусками прямо во время записи, и к нажатию «Готово» текст уже готов.
 * Замер 4 ноября на Pixel 8: сеанс заканчивается через ~0,1 с после
 * последнего байта звука, а паузы по 10 секунд посреди диктовки ничего не
 * ломают — каждая просто закрывает сегмент.
 *
 * **Почему это не тот путь, что провалился в августе.** Тогда микрофоном
 * владел распознаватель и сам решал, когда закончить (раздел 3 `CLAUDE.md`,
 * «Диктовка»). Здесь микрофон держит наш `VoiceRecorder`, распознаватель
 * получает поток через `EXTRA_AUDIO_SOURCE` (Android 13+), а сегментированный
 * сеанс со значением `EXTRA_AUDIO_SOURCE` длится, пока не закончится поток, —
 * то есть пока человек не нажмёт «Готово».
 *
 * [transcribe] — тот же сеанс, которому звук подают из готовой записи:
 * запасной путь, если живой сеанс не дал текста.
 *
 * Работает, только если на устройстве есть распознаватель с моделью нужного
 * языка; иначе отдаёт null, и вызывающий берёт другой путь.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class OnDeviceTranscriber(
    private val context: Context,
    /**
     * Во сколько раз быстрее реального времени подавать готовую запись.
     *
     * Распознаватель рассчитан на живой микрофон, и поток быстрее реального
     * отбрасывает. Замер 4 ноября на Pixel 8, 15 секунд речи с паузами
     * по 3,5–4 секунды: всё сразу — потеряна середина; 1× — дословно за
     * 17 с; 3×, 4×, 6× — дословно за 7, 6 и 5 с; 10× — снова потерян кусок.
     * Берём 4× с запасом до порога. На лету этот множитель не нужен:
     * микрофон и так отдаёт звук с реальной скоростью.
     */
    private val speedup: Double = 4.0,
) : SpeechTranscriber {

    private val main = Handler(Looper.getMainLooper())

    /**
     * Начать расшифровку на лету. Вызывать с главного потока: распознаватель
     * живёт на нём.
     *
     * @return сеанс или null, если на телефоне распознавать нечем.
     */
    @MainThread
    fun startLive(languageTag: String, sampleRate: Int, channels: Int): LiveTranscript? {
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            MagpieLog.w("on-device: no recognizer on this device")
            return null
        }
        return runCatching { LiveSession(languageTag, sampleRate, channels) }
            .onFailure { MagpieLog.w("on-device: live session failed to start", it) }
            .getOrNull()
    }

    override suspend fun transcribe(audio: ByteArray, languageTag: String): String? {
        val pcm = withContext(Dispatchers.IO) {
            val file = File(context.cacheDir, "on-device-input.m4a")
            try {
                file.writeBytes(audio)
                runCatching { PcmDecoder.decode(file) }
                    .onFailure { MagpieLog.w("on-device: decode failed", it) }
                    .getOrNull()
            } finally {
                file.delete()
            }
        } ?: return null
        return transcribe(pcm, languageTag)
    }

    /** Готовая запись через тот же сеанс, с подачей в [speedup] раз быстрее реальной. */
    suspend fun transcribe(pcm: Pcm16, languageTag: String): String? {
        MagpieLog.i("on-device: ${pcm.bytes.size} bytes pcm, ${pcm.sampleRate} Hz, ${pcm.channels} ch")
        val session = withContext(Dispatchers.Main) {
            startLive(languageTag, pcm.sampleRate, pcm.channels)
        } ?: return null

        withContext(Dispatchers.Default) {
            val chunk = pcm.sampleRate * pcm.channels * 2 * CHUNK_MILLIS / 1_000
            var offset = 0
            while (offset < pcm.bytes.size) {
                val size = minOf(chunk, pcm.bytes.size - offset)
                session.write(pcm.bytes.copyOfRange(offset, offset + size))
                offset += size
                Thread.sleep((CHUNK_MILLIS / speedup).toLong())
            }
        }
        return session.finish()
    }

    /** Есть ли модель языка, скачанная на телефон. Для диагностики и замера. */
    suspend fun support(languageTag: String): RecognitionSupport? = withContext(Dispatchers.Main) {
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) return@withContext null
        val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        try {
            suspendCancellableCoroutine { continuation ->
                recognizer.checkRecognitionSupport(
                    intent(languageTag),
                    MainExecutor,
                    object : RecognitionSupportCallback {
                        override fun onSupportResult(support: RecognitionSupport) {
                            if (continuation.isActive) continuation.resume(support)
                        }

                        override fun onError(error: Int) {
                            MagpieLog.w("on-device: support check error=$error")
                            if (continuation.isActive) continuation.resume(null)
                        }
                    },
                )
            }
        } finally {
            recognizer.destroy()
        }
    }

    /**
     * Попросить систему скачать модель языка, если её ещё нет на телефоне.
     *
     * Без модели распознаватель на устройстве молча ничего не распознаёт.
     * Скачивание подтверждает человек в системном диалоге (около 50 МБ).
     *
     * **Найденная модель запоминается** ([cached]), и следующая проверка
     * обходится без распознавателя. Дело не в скорости вопроса, а в том, что
     * идёт после него: проверка создаёт свой распознаватель и закрывает его,
     * а живой сеанс диктовки, открытый сразу следом, падает с `error=11`
     * (замер 4 ноября на Pixel 8, три раза из трёх). Та же ловушка, что
     * 13 августа: мгновенное переоткрытие после `destroy` не стартует. Пауза
     * в [RELEASE_MILLIS] после настоящей проверки это лечит, а память о
     * результате убирает и паузу, и проверку со второй диктовки.
     *
     * @param cached верить запомненному «модель есть». Настройки передают
     *        false: включая «Без облака», человек ждёт настоящей проверки.
     * @return true, если модель уже стоит; false — запрос на скачивание
     *         отправлен; null — узнать не удалось (распознаватель не ответил),
     *         и тогда мешать диктовке незачем.
     */
    suspend fun ensureModel(languageTag: String, cached: Boolean = true): Boolean? {
        val key = "modelReady:$languageTag"
        if (cached && prefs.getBoolean(key, false)) return true

        val support = support(languageTag) ?: return null
        if (support.installedOnDeviceLanguages.any { it.equals(languageTag, ignoreCase = true) }) {
            prefs.edit { putBoolean(key, true) }
            delay(RELEASE_MILLIS)
            return true
        }
        prefs.edit { remove(key) }
        MagpieLog.i("on-device: requesting model for $languageTag")
        withContext(Dispatchers.Main) {
            val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            recognizer.triggerModelDownload(intent(languageTag))
            recognizer.destroy()
        }
        return false
    }

    private val prefs by lazy { context.getSharedPreferences("speech", Context.MODE_PRIVATE) }

    /**
     * Один сеанс распознавания поверх канала.
     *
     * Запись в канал идёт из своего потока через очередь, а не из потока
     * микрофона: если распознаватель замешкается, канал заполнится и запись
     * в него встанет — а микрофон ждать не может, его буфер переполнится, и
     * звук пропадёт. Очередь держит отставание в памяти, а не в потерях.
     */
    private inner class LiveSession(languageTag: String, sampleRate: Int, channels: Int) : LiveTranscript {

        private val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        private val pipe = ParcelFileDescriptor.createPipe()
        private val queue = LinkedBlockingQueue<ByteArray>()
        private val segments = mutableListOf<String>()
        private val result = CompletableDeferred<String?>()

        private val writer = Thread {
            runCatching {
                FileOutputStream(pipe[1].fileDescriptor).use { out ->
                    while (true) {
                        val chunk = queue.take()
                        if (chunk.isEmpty()) break
                        out.write(chunk)
                    }
                }
            }.onFailure { MagpieLog.w("on-device: pipe write failed", it) }
            runCatching { pipe[1].close() }
        }

        init {
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onSegmentResults(results: Bundle) {
                    val segment = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    MagpieLog.i("on-device: segment «$segment»")
                    segment?.takeIf { it.isNotBlank() }?.let(segments::add)
                }

                override fun onEndOfSegmentedSession() = complete("end of session")

                override fun onResults(results: Bundle) {
                    results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()?.takeIf { it.isNotBlank() }
                        ?.let(segments::add)
                    complete("results")
                }

                override fun onError(error: Int) {
                    MagpieLog.w("on-device: error=$error")
                    complete("error $error")
                }

                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })

            recognizer.startListening(
                intent(languageTag).apply {
                    putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, pipe[0])
                    putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, channels)
                    putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                    putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, sampleRate)
                    putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_AUDIO_SOURCE)
                },
            )
            writer.start()
        }

        private fun complete(reason: String) {
            if (result.isCompleted) return
            val text = dropRecognizerCommas(segments.joinToString(" ") { it.trim() }.trim())
            MagpieLog.i("on-device: $reason, ${segments.size} segment(s), ${text.length} chars")
            result.complete(text.ifEmpty { null })
        }

        override fun write(chunk: ByteArray) {
            if (chunk.isNotEmpty() && !result.isCompleted) queue.offer(chunk)
        }

        override suspend fun finish(): String? {
            queue.offer(END_OF_STREAM)
            // Отставание распознавателя — это то, что ещё лежит в очереди.
            // На живой диктовке его нет; предел — на случай, если сеанс
            // так и не сообщил о конце.
            val text = withTimeoutOrNull(FINISH_TIMEOUT_MILLIS) { result.await() }
            release()
            return text
        }

        override fun cancel() {
            queue.clear()
            queue.offer(END_OF_STREAM)
            result.complete(null)
            release()
        }

        private fun release() {
            main.post {
                runCatching { recognizer.cancel() }
                runCatching { recognizer.destroy() }
            }
            runCatching { pipe[0].close() }
        }
    }

    private fun intent(languageTag: String) = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        // Ради точек на паузах и заглавных; запятые потом снимаются, см. dropRecognizerCommas.
        putExtra(RecognizerIntent.EXTRA_ENABLE_FORMATTING, RecognizerIntent.FORMATTING_OPTIMIZE_QUALITY)
    }

    private companion object {
        const val CHUNK_MILLIS = 100

        /** Сколько ждать после закрытия распознавателя, прежде чем открыть следующий. */
        const val RELEASE_MILLIS = 500L
        const val FINISH_TIMEOUT_MILLIS = 10_000L
        val END_OF_STREAM = ByteArray(0)
    }

    private object MainExecutor : Executor {
        private val handler = Handler(Looper.getMainLooper())
        override fun execute(command: Runnable) {
            handler.post(command)
        }
    }
}
