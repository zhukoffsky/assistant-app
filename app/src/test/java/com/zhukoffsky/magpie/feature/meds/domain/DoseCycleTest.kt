package com.zhukoffsky.magpie.feature.meds.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Обязательные тесты: ошибка здесь означает неверную дозировку лекарства,
 * причём тихую — приложение будет уверенно показывать не то число.
 */
class DoseCycleTest {

    private val zone = ZoneId.of("Europe/Moscow")
    private val start = LocalDate.of(2026, 8, 1)

    private fun course(vararg doses: Int, startDate: LocalDate = start) = MedCourse(
        id = 1,
        name = "Тестовое",
        dosesMg = doses.toList(),
        timeOfDay = LocalTime.of(9, 0),
        startDate = startDate,
        isActive = true,
    )

    @Test
    fun `first day of the course is the first dose in the cycle`() {
        assertEquals(0, DoseCycle.doseIndex(start, start, cycleSize = 2))
        assertEquals(100, DoseCycle.doseFor(course(100, 75), start))
    }

    @Test
    fun `two-dose cycle alternates day by day`() {
        val course = course(100, 75)

        val doses = (0L..5L).map { DoseCycle.doseFor(course, start.plusDays(it)) }

        assertEquals(listOf(100, 75, 100, 75, 100, 75), doses)
    }

    @Test
    fun `cycle of arbitrary length repeats in order`() {
        val course = course(100, 75, 100, 50)

        val doses = (0L..8L).map { DoseCycle.doseFor(course, start.plusDays(it)) }

        assertEquals(listOf(100, 75, 100, 50, 100, 75, 100, 50, 100), doses)
    }

    @Test
    fun `a single dose course always gives the same dose`() {
        val course = course(50)

        assertEquals(50, DoseCycle.doseFor(course, start))
        assertEquals(50, DoseCycle.doseFor(course, start.plusDays(37)))
    }

    @Test
    fun `dose depends only on the date, so a missed day shifts nothing`() {
        val course = course(100, 75)

        // Пропуск 2 и 3 августа не сдвигает 4-е: доза считается от даты,
        // а не «следующая после прошлой принятой».
        assertEquals(100, DoseCycle.doseFor(course, LocalDate.of(2026, 8, 3)))
        assertEquals(75, DoseCycle.doseFor(course, LocalDate.of(2026, 8, 4)))
    }

    @Test
    fun `cycle survives a month boundary`() {
        val course = course(100, 75, 50)

        assertEquals(0, DoseCycle.doseIndex(start, LocalDate.of(2026, 8, 31), cycleSize = 3))
        assertEquals(100, DoseCycle.doseFor(course, LocalDate.of(2026, 8, 31)))
        assertEquals(75, DoseCycle.doseFor(course, LocalDate.of(2026, 9, 1)))
    }

    @Test
    fun `a long gap does not desynchronise the cycle`() {
        val course = course(100, 75)
        val oneYearLater = start.plusDays(365)

        // 365 нечётно, значит вторая доза цикла.
        assertEquals(75, DoseCycle.doseFor(course, oneYearLater))
    }

    @Test
    fun `dates before the course start have no dose`() {
        assertNull(DoseCycle.doseFor(course(100, 75), start.minusDays(1)))
    }

    @Test
    fun `empty cycle is rejected rather than silently guessed`() {
        assertNull(DoseCycle.doseFor(course(), start))
    }

    @Test
    fun `next intake is today when the time has not come yet`() {
        val now = ZonedDateTime.of(2026, 8, 5, 7, 0, 0, 0, zone)

        assertEquals(
            ZonedDateTime.of(2026, 8, 5, 9, 0, 0, 0, zone),
            DoseCycle.nextIntake(course(100, 75), now),
        )
    }

    @Test
    fun `next intake rolls over to tomorrow once the time has passed`() {
        val now = ZonedDateTime.of(2026, 8, 5, 15, 0, 0, 0, zone)

        assertEquals(
            ZonedDateTime.of(2026, 8, 6, 9, 0, 0, 0, zone),
            DoseCycle.nextIntake(course(100, 75), now),
        )
    }

    @Test
    fun `next intake never lands before the course starts`() {
        val now = ZonedDateTime.of(2026, 7, 20, 15, 0, 0, 0, zone)

        assertEquals(
            ZonedDateTime.of(2026, 8, 1, 9, 0, 0, 0, zone),
            DoseCycle.nextIntake(course(100, 75), now),
        )
    }

    @Test
    fun `history frame skips days before the course started`() {
        val course = course(100, 75)

        val days = DoseCycle.daysBetween(
            course = course,
            from = LocalDate.of(2026, 7, 30),
            to = LocalDate.of(2026, 8, 2),
        )

        assertEquals(
            listOf(
                LocalDate.of(2026, 8, 1) to 100,
                LocalDate.of(2026, 8, 2) to 75,
            ),
            days,
        )
    }
}
