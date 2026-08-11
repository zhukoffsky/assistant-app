package com.zhukoffsky.magpie.core.llm

import com.zhukoffsky.magpie.feature.reminders.domain.RepeatRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime

class LlmPhraseParserTest {

    private val zone = ZoneId.of("Europe/Moscow")
    private val now = ZonedDateTime.of(2026, 8, 10, 15, 0, 0, 0, zone)

    private val api = FakeOpenAiCompatApi()

    private fun parser(key: String? = "sk-test") = LlmPhraseParser(api, apiKey = { key })

    @Test
    fun `without a key the network is never touched`() = runTest {
        assertNull(parser(key = null).parse("что-нибудь", now))
        assertNull(parser(key = "   ").parse("что-нибудь", now))
        assertEquals(0, api.calls)
    }

    @Test
    fun `a well formed answer becomes a reminder`() = runTest {
        api.reply = """{"title":"забрать посылку","dueAt":"2026-08-13T18:00:00+03:00","repeat":null}"""

        val parsed = parser().parse("посылку бы забрать в четверг вечером", now)!!

        assertEquals("забрать посылку", parsed.title)
        assertEquals(ZonedDateTime.of(2026, 8, 13, 18, 0, 0, 0, zone), parsed.dueAt)
        assertNull(parsed.repeat)
    }

    @Test
    fun `a markdown fence around the json is tolerated`() = runTest {
        api.reply = """
            ```json
            {"title":"полить цветы","dueAt":"2026-08-11T09:00:00+03:00","repeat":"DAILY"}
            ```
        """.trimIndent()

        val parsed = parser().parse("цветы поливать", now)!!

        assertEquals("полить цветы", parsed.title)
        assertEquals(RepeatRule.Daily, parsed.repeat)
    }

    @Test
    fun `weekly repeat comes back as a rule`() = runTest {
        api.reply =
            """{"title":"мусор","dueAt":"2026-08-11T09:00:00+03:00","repeat":"WEEKLY:TUESDAY,FRIDAY"}"""

        val parsed = parser().parse("мусор по вторникам и пятницам", now)!!

        assertEquals(
            RepeatRule.Weekly(setOf(DayOfWeek.TUESDAY, DayOfWeek.FRIDAY)),
            parsed.repeat,
        )
    }

    @Test
    fun `the time is converted into the user zone`() = runTest {
        // Тот же момент, записанный в UTC.
        api.reply = """{"title":"звонок","dueAt":"2026-08-11T06:00:00Z","repeat":null}"""

        val parsed = parser().parse("звонок", now)!!

        assertEquals(ZonedDateTime.of(2026, 8, 11, 9, 0, 0, 0, zone), parsed.dueAt)
    }

    @Test
    fun `an answer without a date is rejected`() = runTest {
        api.reply = """{"title":"что-то","dueAt":null,"repeat":null}"""

        assertNull(parser().parse("что-то", now))
    }

    @Test
    fun `an unparsable answer is rejected rather than guessed`() = runTest {
        api.reply = "Конечно! Вот ваше напоминание."

        assertNull(parser().parse("что-то", now))
    }

    @Test
    fun `a network failure is swallowed so the caller can fall back`() = runTest {
        api.failWith = IOException("offline")

        assertNull(parser().parse("что-то", now))
    }

    @Test
    fun `an answer without choices is rejected`() = runTest {
        api.replyWithoutChoices = true

        assertNull(parser().parse("что-то", now))
    }

    @Test
    fun `the phrase is the user message and the key rides in the bearer header`() = runTest {
        api.reply = """{"title":"звонок","dueAt":"2026-08-11T09:00:00+03:00"}"""

        parser(key = "sk-secret").parse("позвонить завтра", now)

        assertEquals("Bearer sk-secret", api.lastAuthorization)

        val messages = api.lastBody?.messages.orEmpty()
        assertEquals("позвонить завтра", messages.single { it.role == "user" }.content)
        assertTrue(messages.single { it.role == "system" }.content.contains("2026-08-10T15:00"))
    }

    private class FakeOpenAiCompatApi : OpenAiCompatApi {
        var reply: String = ""
        var replyWithoutChoices = false
        var failWith: Exception? = null
        var calls = 0
        var lastAuthorization: String? = null
        var lastBody: ChatRequest? = null

        override suspend fun chatCompletions(
            authorization: String,
            body: ChatRequest,
        ): ChatResponse {
            calls++
            lastAuthorization = authorization
            lastBody = body
            failWith?.let { throw it }
            if (replyWithoutChoices) return ChatResponse()
            return ChatResponse(
                listOf(ChatChoice(ChatMessage(role = "assistant", content = reply))),
            )
        }
    }
}
