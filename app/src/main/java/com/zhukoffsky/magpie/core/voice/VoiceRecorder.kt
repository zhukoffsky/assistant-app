package com.zhukoffsky.magpie.core.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import com.zhukoffsky.magpie.core.speech.Pcm16
import com.zhukoffsky.magpie.core.util.MagpieLog
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

/**
 * Запись голоса, которую заканчивает **только человек**.
 *
 * Это вся суть перехода с `SpeechRecognizer`. Там микрофоном владел чужой
 * процесс и он же решал, когда закончить: пауза заканчивала сессию, а
 * непрерывная сессия на Pixel 8 падала посреди диктовки. Здесь микрофон
 * держит приложение, и остановить запись нечему, кроме кнопки «Готово»,
 * предела длительности и закрытия экрана.
 *
 * **Сырой звук, а не сразу AAC** (с 4 ноября 2026). Прежде писал
 * `MediaRecorder` — сжатый файл, к которому до конца записи не подступиться.
 * Теперь `AudioRecord` отдаёт куски по 100 мс, и каждый кусок сразу идёт
 * распознавателю на телефоне ([onChunk]): к «Готово» текст уже распознан.
 * Для Whisper запись сжимается в AAC после остановки — тот же формат, что
 * был.
 *
 * Источник — `VOICE_RECOGNITION`, а не `MIC`: Android настраивает его под
 * распознавание речи и не крутит на нём автоусиление, которое на паузах
 * поднимает шум.
 */
class VoiceRecorder {

    private var record: AudioRecord? = null
    private var thread: Thread? = null
    private val buffer = ByteArrayOutputStream()
    private val peak = AtomicInteger(0)

    @Volatile
    private var capturing = false

    val isRecording: Boolean get() = record != null && capturing

    /**
     * @param onChunk куда отдавать звук по ходу записи; вызывается из потока
     *        микрофона и обязан не блокироваться.
     * @return true, если запись пошла. false — микрофон занят другим
     *         приложением; звонок и запись голоса в другой программе
     *         выглядят именно так.
     */
    // Разрешение на микрофон проверяет и запрашивает экран диктовки до вызова.
    @SuppressLint("MissingPermission")
    fun start(onChunk: (ByteArray) -> Unit = {}): Boolean {
        stopQuietly()
        buffer.reset()

        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        val created = runCatching {
            AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE, CHANNEL, ENCODING, maxOf(minBuffer, CHUNK_BYTES * 4))
        }.getOrNull()

        if (created == null || created.state != AudioRecord.STATE_INITIALIZED) {
            MagpieLog.w("recorder: microphone unavailable")
            created?.release()
            return false
        }
        val started = runCatching { created.startRecording() }.isSuccess &&
            created.recordingState == AudioRecord.RECORDSTATE_RECORDING
        if (!started) {
            MagpieLog.w("recorder: start failed")
            created.release()
            return false
        }

        record = created
        capturing = true
        thread = Thread({ capture(created, onChunk) }, "dictation-mic").apply { start() }
        MagpieLog.i("recorder: recording pcm ${SAMPLE_RATE} Hz")
        return true
    }

    private fun capture(source: AudioRecord, onChunk: (ByteArray) -> Unit) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val chunk = ByteArray(CHUNK_BYTES)
        while (capturing) {
            val read = source.read(chunk, 0, chunk.size)
            if (read <= 0) continue
            val piece = chunk.copyOf(read)
            synchronized(buffer) { buffer.write(piece) }
            peak.accumulateAndGet(peakOf(piece)) { a, b -> maxOf(a, b) }
            onChunk(piece)

            // Предел длительности: запись замолкает, но экран ждёт «Готово»,
            // как и раньше с `MediaRecorder`, — ничего не теряется.
            if (synchronized(buffer) { buffer.size() } >= MAX_BYTES) {
                MagpieLog.i("recorder: reached the length limit")
                capturing = false
            }
        }
    }

    /** Громкость с прошлого вызова, 0..1 — для полоски уровня на карточке. */
    fun level(): Float = (peak.getAndSet(0) / MAX_AMPLITUDE).coerceIn(0f, 1f)

    /**
     * Остановить и отдать записанное. Пустая запись — «Готово» в первую же
     * долю секунды — это null, «ничего не расслышали».
     */
    fun stop(): Pcm16? {
        val current = record ?: return null
        stopQuietly()
        val bytes = synchronized(buffer) { buffer.toByteArray() }
        buffer.reset()
        MagpieLog.i("recorder: ${bytes.size} bytes pcm")
        return bytes.takeIf { it.isNotEmpty() }?.let { Pcm16(it, current.sampleRate, current.channelCount) }
    }

    /** Бросить запись: экран закрыли, звук никому не нужен. */
    fun cancel() {
        stopQuietly()
        buffer.reset()
    }

    private fun stopQuietly() {
        capturing = false
        thread?.join(JOIN_TIMEOUT_MILLIS)
        thread = null
        record?.let { active ->
            runCatching { active.stop() }
            active.release()
        }
        record = null
    }

    private fun peakOf(piece: ByteArray): Int {
        var max = 0
        var i = 0
        while (i + 1 < piece.size) {
            val sample = (piece[i].toInt() and 0xFF) or (piece[i + 1].toInt() shl 8)
            max = maxOf(max, abs(sample.toShort().toInt()))
            i += 2
        }
        return max
    }

    private companion object {
        /** Больше распознаванию не нужно: речь укладывается в 16 кГц. */
        const val SAMPLE_RATE = 16_000
        const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT

        /** 100 мс звука: 16 000 сэмплов × 2 байта / 10. */
        const val CHUNK_BYTES = SAMPLE_RATE * 2 / 10

        /**
         * Предел длительности — полторы минуты.
         *
         * Не про удобство, а про размер запроса к Whisper: сжатыми это около
         * 360 КБ, в base64 на треть больше. Для списка покупок с запасом.
         */
        const val MAX_BYTES = SAMPLE_RATE * 2 * 90

        const val MAX_AMPLITUDE = 32_767f
        const val JOIN_TIMEOUT_MILLIS = 500L
    }
}
