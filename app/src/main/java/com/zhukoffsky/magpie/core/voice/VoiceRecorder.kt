package com.zhukoffsky.magpie.core.voice

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import com.zhukoffsky.magpie.core.util.MagpieLog
import java.io.File

/**
 * Запись голоса в файл, который заканчивает **только человек**.
 *
 * Это вся суть перехода с `SpeechRecognizer`. Там микрофоном владел чужой
 * процесс и он же решал, когда закончить: пауза заканчивала сессию, а
 * непрерывная сессия на Pixel 8 падала посреди диктовки. Здесь микрофон
 * держит приложение, и остановить запись нечему, кроме кнопки «Готово»,
 * предела длительности и закрытия экрана.
 *
 * Формат — OggOpus: Android умеет его с API 29 без единой зависимости, а
 * расшифровка принимает без конвертации. Полминуты речи весят около сотни
 * килобайт, то есть уходят одним запросом даже на слабой связи.
 */
class VoiceRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var file: File? = null

    val isRecording: Boolean get() = recorder != null

    /**
     * @return true, если запись пошла. false — микрофон занят другим
     *         приложением или кодек отказался; звонок и запись голоса в
     *         другой программе выглядят именно так.
     */
    fun start(): Boolean {
        stopQuietly()

        val target = File(context.cacheDir, FILE_NAME)
        val created = newRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.OGG)
            setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
            setAudioChannels(1)
            setMaxDuration(MAX_DURATION_MILLIS)
            setOutputFile(target.absolutePath)
        }

        // Частоту и битрейт задаём отдельно и не считаем обязательными:
        // набор допустимых значений у Opus зависит от устройства, а
        // отклонённое значение роняет `prepare` целиком. Речь важнее
        // экономии, поэтому при отказе просто берём умолчания.
        val prepared = runCatching {
            created.setAudioSamplingRate(SAMPLE_RATE)
            created.setAudioEncodingBitRate(BIT_RATE)
            created.prepare()
        }.recoverCatching {
            MagpieLog.w("recorder: falling back to default opus settings", it)
            created.reset()
            created.setAudioSource(MediaRecorder.AudioSource.MIC)
            created.setOutputFormat(MediaRecorder.OutputFormat.OGG)
            created.setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
            created.setAudioChannels(1)
            created.setMaxDuration(MAX_DURATION_MILLIS)
            created.setOutputFile(target.absolutePath)
            created.prepare()
        }

        if (prepared.isFailure) {
            MagpieLog.w("recorder: prepare failed", prepared.exceptionOrNull())
            created.release()
            return false
        }

        return runCatching { created.start() }
            .onFailure {
                MagpieLog.w("recorder: start failed", it)
                created.release()
            }
            .onSuccess {
                recorder = created
                file = target
                MagpieLog.i("recorder: recording to ${target.name}")
            }
            .isSuccess
    }

    /** Текущая громкость, 0..1 — для полоски уровня на карточке. */
    fun level(): Float {
        val peak = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)
        return (peak / MAX_AMPLITUDE).coerceIn(0f, 1f)
    }

    /**
     * Остановить и отдать записанное.
     *
     * `stop` бросается, если записи фактически не было — так бывает, когда
     * «Готово» нажали в первую же долю секунды. Это не ошибка, а пустая
     * запись, и обрабатывается она как «ничего не расслышали».
     */
    fun stop(): ByteArray? {
        val current = recorder ?: return null
        recorder = null

        val stopped = runCatching { current.stop() }
        current.release()

        val recorded = file
        file = null

        if (stopped.isFailure) {
            MagpieLog.w("recorder: nothing recorded", stopped.exceptionOrNull())
            recorded?.delete()
            return null
        }

        val bytes = recorded?.takeIf { it.exists() }?.readBytes()
        recorded?.delete()

        MagpieLog.i("recorder: ${bytes?.size ?: 0} bytes")
        return bytes?.takeIf { it.isNotEmpty() }
    }

    /** Бросить запись и стереть файл: экран закрыли, звук никому не нужен. */
    fun cancel() {
        stopQuietly()
        file?.delete()
        file = null
    }

    private fun stopQuietly() {
        recorder?.let { active ->
            runCatching { active.stop() }
            active.release()
        }
        recorder = null
    }

    private fun newRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

    private companion object {
        const val FILE_NAME = "dictation.ogg"

        /** Больше распознаванию не нужно: речь укладывается в 16 кГц. */
        const val SAMPLE_RATE = 16_000
        const val BIT_RATE = 24_000

        /**
         * Предел длительности.
         *
         * Не про удобство, а про размер запроса: расшифровка принимает файл
         * целиком, и полутора минут речи (около 270 КБ, в base64 треть
         * сверху) достаточно с запасом для списка покупок. Упёрлись —
         * запись закончится сама и уйдёт в разбор, ничего не потеряв.
         */
        const val MAX_DURATION_MILLIS = 90_000

        /** Потолок шкалы `maxAmplitude` у 16-битного звука. */
        const val MAX_AMPLITUDE = 32_767f
    }
}
