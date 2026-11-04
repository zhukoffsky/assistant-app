package com.zhukoffsky.magpie.core.speech

import com.zhukoffsky.magpie.core.util.MagpieLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.CoroutineContext

/**
 * Текст, распознанный на телефоне во время записи, а Whisper — только если
 * телефон ничего не дал.
 *
 * **Почему не наоборот.** До 4 ноября первым шёл Whisper со сроком: он
 * точнее (телефон однажды расслышал «средство для мытья посуды» как
 * «непосредственно для натя посуды»). Замер на живой диктовке показал цену:
 * текст телефона был готов через 0,33 с после «Готово», а Whisper не
 * уложился в свои 5,5 с — и эти секунды человек просто ждал над карточкой.
 * Обе живые диктовки того же дня телефон расслышал почти дословно, а та
 * ошибка была на ускоренной подаче готовой записи, не на живой. Владелец
 * выбрал скорость.
 *
 * Whisper остаётся запасным: живой сеанс не стартовал (Android ниже 13, нет
 * модели языка) или вернул пустоту. В режиме «Без облака» он не зовётся вовсе.
 *
 * @param whisper облачная расшифровка; null — ключей в сборке нет.
 * @param onDevice расшифровка готовой записи на телефоне — последний запас,
 *        если не дали текста ни живой сеанс, ни Whisper; null — распознавателя нет.
 */
class HedgedTranscriber(
    private val whisper: SpeechTranscriber?,
    private val onDevice: (suspend (Pcm16, String) -> String?)?,
    private val offlineOnly: suspend () -> Boolean,
    /** Сжатие для Whisper; подменяется в тестах — `MediaCodec` есть только на устройстве. */
    private val encode: (Pcm16) -> ByteArray? = PcmEncoder::encodeAac,
    /**
     * Где сжимать. Тесты оставляют сжатие на своём потоке: срок там идёт по
     * виртуальным часам, и пока настоящий поток занят, они проскакивают срок.
     */
    private val encodeContext: CoroutineContext = Dispatchers.Default,
) : RecordingTranscriber {

    override suspend fun transcribe(recording: Recording, languageTag: String): String? {
        recording.live?.finish()?.takeIf { it.isNotBlank() }?.let {
            MagpieLog.i("speech: transcribed live on the device")
            return it
        }

        if (whisper != null && !offlineOnly()) {
            val cloud = withTimeoutOrNull(whisperDeadline(recording.pcm)) {
                val aac = withContext(encodeContext) { encode(recording.pcm) }
                aac?.let { whisper.transcribe(it, languageTag) }
            }
            if (!cloud.isNullOrBlank()) {
                MagpieLog.i("speech: transcribed by whisper")
                return cloud
            }
        }

        // Ни живого текста, ни облака. Последняя попытка — та же запись
        // целиком, распознавателем на телефоне.
        return onDevice?.invoke(recording.pcm, languageTag)?.takeIf { it.isNotBlank() }?.also {
            MagpieLog.i("speech: transcribed by on-device from the recording")
        }
    }

    /**
     * Сколько ждать Whisper: 4 с плюс секунда на каждые 15 с записи.
     * Обычная диктовка в 15–20 с — около 5 с, предельные полторы минуты — 10 с.
     */
    private fun whisperDeadline(pcm: Pcm16): Long {
        val seconds = pcm.bytes.size / (2L * pcm.channels * pcm.sampleRate)
        return 4_000L + seconds * 1_000L / 15
    }
}
