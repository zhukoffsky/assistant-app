package com.zhukoffsky.magpie.feature.reminders.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderPhraseParserTest {

    private val zone = ZoneId.of("Europe/Moscow")

    /** Понедельник, 15:00 — днём, чтобы «в 9» уезжало на завтра. */
    private val now = ZonedDateTime.of(2026, 8, 10, 15, 0, 0, 0, zone)

    private fun parse(phrase: String) = ReminderPhraseParser.parse(phrase, now)

    private fun at(day: Int, hour: Int, minute: Int = 0) =
        ZonedDateTime.of(2026, 8, day, hour, minute, 0, 0, zone)

    @Test
    fun `tomorrow with explicit time`() {
        val result = parse("завтра в 9 позвонить в поликлинику")

        assertEquals("позвонить в поликлинику", result.title)
        assertEquals(at(11, 9), result.dueAt)
        assertNull(result.repeat)
    }

    @Test
    fun `leading remind-me verb is dropped`() {
        assertEquals("выкинуть ёлку", parse("напомни мне завтра выкинуть ёлку").title)
    }

    @Test
    fun `weekly repeat picks the nearest matching day`() {
        val result = parse("каждый вторник вынести мусор")

        assertEquals("вынести мусор", result.title)
        assertEquals(RepeatRule.Weekly(setOf(DayOfWeek.TUESDAY)), result.repeat)
        assertEquals(at(11, 9), result.dueAt)
    }

    @Test
    fun `daily repeat skips today when the time has passed`() {
        val result = parse("каждый день пить витамины")

        assertEquals(RepeatRule.Daily, result.repeat)
        assertEquals(at(11, 9), result.dueAt)
    }

    @Test
    fun `relative offset in minutes`() {
        val result = parse("через 20 минут снять с плиты")

        assertEquals("снять с плиты", result.title)
        assertEquals(at(10, 15, 20), result.dueAt)
    }

    @Test
    fun `relative offset in hours`() {
        assertEquals(at(10, 17), parse("через 2 часа выйти").dueAt)
    }

    @Test
    fun `time already passed today moves to tomorrow`() {
        assertEquals(at(11, 9), parse("в 9 позвонить").dueAt)
    }

    @Test
    fun `time still ahead stays today`() {
        assertEquals(at(10, 18, 30), parse("в 18:30 забрать посылку").dueAt)
    }

    @Test
    fun `evening qualifier shifts to the afternoon clock`() {
        assertEquals(at(10, 19), parse("в 7 вечера позвонить маме").dueAt)
    }

    @Test
    fun `named weekday resolves to the coming one`() {
        val result = parse("в пятницу в 10 к врачу")

        assertEquals("к врачу", result.title)
        assertEquals(at(14, 10), result.dueAt)
    }

    @Test
    fun `day after tomorrow falls back to the default time`() {
        assertEquals(at(12, 9), parse("послезавтра забрать документы").dueAt)
    }

    @Test
    fun `phrase without any time still becomes a reminder`() {
        val result = parse("полить цветы")

        assertEquals("полить цветы", result.title)
        assertEquals(at(11, 9), result.dueAt)
        assertNull(result.repeat)
    }

    @Test
    fun `title never ends up empty`() {
        assertEquals("завтра в 9", parse("завтра в 9").title)
    }

    /**
     * Регрессия 12 августа 2026: распознаватель поставил после «напомни»
     * неразрывный пробел, `\s` его не считает пробелом, и вводный глагол
     * остался в заголовке — «напомни съездить на рынок». На глаз от обычного
     * пробела неотличимо, поэтому случай и записан тестом.
     */
    @Test
    fun `a non-breaking space after the verb does not keep it in the title`() {
        val parsed = ReminderPhraseParser.parse(
            "напомни\u00A0съездить на рынок завтра в 15:00",
            now,
        )

        assertEquals("съездить на рынок", parsed.title)
    }

    @Test
    fun `a comma after the verb does not keep it in the title`() {
        assertEquals(
            "съездить на рынок",
            ReminderPhraseParser.parse("напомни, съездить на рынок завтра в 15:00", now).title,
        )
    }

    @Test
    fun `the noun form of the request is dropped too`() {
        assertEquals(
            "съездить на рынок",
            ReminderPhraseParser.parse("напоминание съездить на рынок завтра в 15:00", now).title,
        )
    }
}
