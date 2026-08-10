package com.zhukoffsky.magpie.feature.reminders.domain

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class HybridPhraseParserTest {

    private val zone = ZoneId.of("Europe/Moscow")
    private val now = ZonedDateTime.of(2026, 8, 10, 15, 0, 0, 0, zone)

    private val rules = RuleBasedPhraseParser()

    @Test
    fun `rules recognise an explicit time and report confidence`() {
        val parsed = ReminderPhraseParser.parse("завтра в 9 позвонить", now)

        assertTrue(parsed.isConfident)
    }

    @Test
    fun `a phrase without any time marker is not confident`() {
        val parsed = ReminderPhraseParser.parse("разобраться с налогами когда-нибудь", now)

        assertFalse(parsed.isConfident)
    }

    @Test
    fun `a confident rule result short-circuits the LLM`() = runTest {
        val llm = CountingParser(result = null)
        val hybrid = HybridPhraseParser(rules, llm)

        val parsed = hybrid.parse("завтра в 9 позвонить", now)

        assertEquals("позвонить", parsed.title)
        assertEquals(0, llm.calls)
    }

    @Test
    fun `an unclear phrase goes to the LLM`() = runTest {
        val fromLlm = ParsedReminder(
            title = "забрать посылку",
            dueAt = now.plusDays(3),
            repeat = null,
        )
        val llm = CountingParser(result = fromLlm)
        val hybrid = HybridPhraseParser(rules, llm)

        val parsed = hybrid.parse("посылку бы забрать на неделе", now)

        assertEquals(fromLlm, parsed)
        assertEquals(1, llm.calls)
    }

    @Test
    fun `a failing LLM falls back to the rules result instead of losing the note`() = runTest {
        val llm = CountingParser(result = null)
        val hybrid = HybridPhraseParser(rules, llm)

        val parsed = hybrid.parse("посылку бы забрать на неделе", now)

        assertEquals("посылку бы забрать на неделе", parsed.title)
        assertEquals(1, llm.calls)
    }

    @Test
    fun `without an LLM the rules result is still returned`() = runTest {
        val hybrid = HybridPhraseParser(rules, llm = null)

        val parsed = hybrid.parse("посылку бы забрать на неделе", now)

        assertEquals("посылку бы забрать на неделе", parsed.title)
    }

    private class CountingParser(private val result: ParsedReminder?) : PhraseParser {
        var calls = 0

        override suspend fun parse(phrase: String, now: ZonedDateTime): ParsedReminder? {
            calls++
            return result
        }
    }
}
