package com.zhukoffsky.magpie.core.speech

/**
 * Расшифровка, идущая прямо во время записи.
 *
 * Кормят её кусками звука с микрофона ([write]), а после «Готово» забирают
 * текст ([finish]). Реализация — распознаватель на телефоне; интерфейс
 * отдельно, чтобы ViewModel и тесты не зависели от Android 13.
 */
interface LiveTranscript {
    /** Кусок PCM. Не блокирует: поток микрофона ждать не может. */
    fun write(chunk: ByteArray)

    /** Звук кончился: дождаться и отдать текст, null — ничего не распознано. */
    suspend fun finish(): String?

    /** Экран закрыли: бросить сеанс без текста. */
    fun cancel()
}

/** Готовая запись и её живая расшифровка, если она шла. */
class Recording(val pcm: Pcm16, val live: LiveTranscript?)

/** Чем превратить запись в текст. Подменяется в тестах одной лямбдой. */
fun interface RecordingTranscriber {
    suspend fun transcribe(recording: Recording, languageTag: String): String?
}
