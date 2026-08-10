package com.zhukoffsky.magpie.feature.reminders.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime

class RepeatRuleTest {

    private val zone = ZoneId.of("Europe/Moscow")
    private fun at(day: Int, hour: Int) = ZonedDateTime.of(2026, 8, day, hour, 0, 0, 0, zone)

    @Test
    fun `daily survives serialisation`() {
        assertEquals(RepeatRule.Daily, RepeatRule.parse(RepeatRule.Daily.serialize()))
    }

    @Test
    fun `weekly survives serialisation`() {
        val rule = RepeatRule.Weekly(setOf(DayOfWeek.TUESDAY, DayOfWeek.FRIDAY))
        assertEquals(rule, RepeatRule.parse(rule.serialize()))
    }

    @Test
    fun `absent rule means one-off`() {
        assertNull(RepeatRule.parse(null))
        assertNull(RepeatRule.parse(""))
        assertNull(RepeatRule.parse("WEEKLY:"))
        assertNull(RepeatRule.parse("нечто непонятное"))
    }

    @Test
    fun `daily advances by one day when today has passed`() {
        // 10 августа 15:00, время приёма — 09:00.
        val next = RepeatRule.Daily.nextAfter(after = at(10, 15), timeOfDay = at(10, 9))
        assertEquals(at(11, 9), next)
    }

    @Test
    fun `daily stays today when the time is still ahead`() {
        val next = RepeatRule.Daily.nextAfter(after = at(10, 8), timeOfDay = at(10, 9))
        assertEquals(at(10, 9), next)
    }

    @Test
    fun `weekly finds the next listed day`() {
        // 10 августа 2026 — понедельник.
        val rule = RepeatRule.Weekly(setOf(DayOfWeek.WEDNESDAY, DayOfWeek.SATURDAY))

        assertEquals(at(12, 9), rule.nextAfter(after = at(10, 15), timeOfDay = at(10, 9)))
        assertEquals(at(15, 9), rule.nextAfter(after = at(12, 15), timeOfDay = at(12, 9)))
    }

    @Test
    fun `weekly wraps around to the following week`() {
        val rule = RepeatRule.Weekly(setOf(DayOfWeek.MONDAY))
        assertEquals(at(17, 9), rule.nextAfter(after = at(10, 15), timeOfDay = at(10, 9)))
    }

    @Test
    fun `a long outage does not replay every missed occurrence`() {
        // Телефон был выключен неделю: следующее срабатывание считается от
        // «сейчас», а не от последнего пропущенного.
        val rule = RepeatRule.Daily
        assertEquals(at(18, 9), rule.nextAfter(after = at(17, 15), timeOfDay = at(10, 9)))
    }
}
