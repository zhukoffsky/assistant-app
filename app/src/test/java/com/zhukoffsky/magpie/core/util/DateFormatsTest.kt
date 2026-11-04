package com.zhukoffsky.magpie.core.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale

class DateFormatsTest {

    private val ru = Locale.forLanguageTag("ru")
    private val en = Locale.ENGLISH
    private val today = LocalDate.of(2026, 11, 4)

    @Test
    fun `this year omits the year`() {
        val moment = LocalDateTime.of(2026, 12, 15, 10, 17)
        assertEquals("15 дек., 10:17", DateFormats.dateTime(moment, ru, today))
        assertEquals("15 Dec, 10:17", DateFormats.dateTime(moment, en, today))
    }

    @Test
    fun `another year is written out, so January of next year differs from this one`() {
        val moment = LocalDateTime.of(2027, 1, 15, 10, 17)
        assertEquals("15 янв. 2027, 10:17", DateFormats.dateTime(moment, ru, today))
        assertEquals("15 Jan 2027, 10:17", DateFormats.dateTime(moment, en, today))
    }

    @Test
    fun `plain date always carries the year and no trailing year word`() {
        val date = LocalDate.of(2026, 11, 4)
        assertEquals("4 нояб. 2026", DateFormats.date(date, ru))
        assertEquals("4 Nov 2026", DateFormats.date(date, en))
    }
}
