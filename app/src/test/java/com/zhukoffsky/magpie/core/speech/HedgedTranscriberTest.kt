package com.zhukoffsky.magpie.core.speech

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.EmptyCoroutineContext

class HedgedTranscriberTest {

    private class FakeLive(private val text: String?) : LiveTranscript {
        var finished = false
        override fun write(chunk: ByteArray) = Unit
        override suspend fun finish(): String? = text.also { finished = true }
        override fun cancel() = Unit
    }

    /** 15 секунд тишины: срок Whisper для такой записи — 5 с. */
    private val pcm = Pcm16(ByteArray(16_000 * 2 * 15), 16_000, 1)

    private fun transcriber(
        whisper: SpeechTranscriber?,
        offline: Boolean = false,
        onDevice: (suspend (Pcm16, String) -> String?)? = null,
    ) = HedgedTranscriber(
        whisper, onDevice, offlineOnly = { offline }, encode = { ByteArray(8) }, encodeContext = EmptyCoroutineContext,
    )

    /** Решение 4 ноября: текст телефона уже готов — Whisper не ждём и не зовём. */
    @Test
    fun `live text wins without calling whisper`() = runTest {
        var called = false
        val live = FakeLive("молоко хлеб")

        val text = transcriber(whisper = { _, _ -> called = true; "облако" })
            .transcribe(Recording(pcm, live), "ru-RU")

        assertEquals("молоко хлеб", text)
        assertFalse(called)
        assertTrue(live.finished)
    }

    @Test
    fun `empty live text falls back to whisper`() = runTest {
        val text = transcriber(whisper = { _, _ -> "средство для мытья посуды" })
            .transcribe(Recording(pcm, FakeLive("")), "ru-RU")

        assertEquals("средство для мытья посуды", text)
    }

    @Test
    fun `late whisper without live text gives way to the recording on the phone`() = runTest {
        val text = transcriber(whisper = { _, _ -> delay(60_000); "поздно" }, onDevice = { _, _ -> "из записи" })
            .transcribe(Recording(pcm, live = null), "ru-RU")

        assertEquals("из записи", text)
    }

    @Test
    fun `offline mode never calls whisper`() = runTest {
        var called = false
        val text = transcriber(whisper = { _, _ -> called = true; "облако" }, offline = true, onDevice = { _, _ -> "из записи" })
            .transcribe(Recording(pcm, live = null), "ru-RU")

        assertEquals("из записи", text)
        assertFalse(called)
    }

    @Test
    fun `without a live text the whole recording goes to the phone`() = runTest {
        val text = transcriber(whisper = null, onDevice = { _, _ -> "из записи" })
            .transcribe(Recording(pcm, live = null), "ru-RU")

        assertEquals("из записи", text)
    }
}
