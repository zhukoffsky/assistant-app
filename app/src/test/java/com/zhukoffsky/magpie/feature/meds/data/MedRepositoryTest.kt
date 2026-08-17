package com.zhukoffsky.magpie.feature.meds.data

import com.zhukoffsky.magpie.core.data.db.FakeMedDao
import com.zhukoffsky.magpie.core.data.db.IntakeStatus
import com.zhukoffsky.magpie.feature.meds.alarm.MedScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class MedRepositoryTest {

    private val zone = ZoneId.of("Europe/Moscow")

    /** 5 августа 2026, 08:00 по Москве — до времени приёма (09:00). */
    private val now: Instant = Instant.parse("2026-08-05T05:00:00Z")

    private val startDate = LocalDate.of(2026, 8, 1)
    private val doseTime = LocalTime.of(9, 0)

    private val dao = FakeMedDao()
    private val scheduler = RecordingMedScheduler()
    private val repository = MedRepository(dao, scheduler, Clock.fixed(now, zone))

    private fun at(day: Int, hour: Int = 9): Instant =
        ZonedDateTime.of(2026, 8, day, hour, 0, 0, 0, zone).toInstant()

    @Before
    fun setUp() = runTest {
        repository.saveCourse(
            id = 0,
            name = "Тестовое",
            dosesMg = listOf(100, 75),
            timeOfDay = doseTime,
            startDate = startDate,
        )
    }

    @Test
    fun `saving a course arms the alarm for the next intake`() {
        assertEquals(at(day = 5), scheduler.daily)
    }

    @Test
    fun `a course without doses is rejected`() = runTest {
        val saved = repository.saveCourse(0, "Пустое", emptyList(), doseTime, startDate)

        assertFalse(saved)
    }

    @Test
    fun `the alarm creates a pending intake with the dose for that day`() = runTest {
        val result = repository.onAlarm(scheduledAt = null)

        assertNotNull(result)
        val intake = result!!.second
        assertEquals(at(day = 5), intake.scheduledAt)
        // От старта прошло 4 дня, цикл [100, 75] — значит снова 100 мг.
        assertEquals(100, intake.doseMg)
        assertEquals(IntakeStatus.PENDING, intake.status)
    }

    @Test
    fun `the alarm immediately arms the next day`() = runTest {
        repository.onAlarm(scheduledAt = null)

        assertEquals(at(day = 6), scheduler.daily)
    }

    @Test
    fun `firing twice does not create a second record for the same day`() = runTest {
        repository.onAlarm(scheduledAt = null)
        repository.onAlarm(scheduledAt = null)

        assertEquals(1, dao.intakes.value.size)
    }

    @Test
    fun `an alarm for a dose already taken does not ask for it again`() = runTest {
        repository.onAlarm(scheduledAt = null)
        repository.markTaken(at(day = 5))

        // Отложенный будильник переживает отметку «принял»: он поставлен на
        // своё время и никем не снимается. Сработав, он не должен требовать
        // принять дозу, которая уже принята.
        val result = repository.onAlarm(at(day = 5))

        assertNull(result)
    }

    @Test
    fun `an alarm for a dose already taken still arms the next day`() = runTest {
        repository.onAlarm(scheduledAt = null)
        repository.markTaken(at(day = 5))

        repository.onAlarm(at(day = 5))

        assertEquals(at(day = 6), scheduler.daily)
    }

    @Test
    fun `marking taken records the time`() = runTest {
        repository.onAlarm(scheduledAt = null)

        repository.markTaken(at(day = 5))

        val intake = dao.intakes.value.single()
        assertEquals(IntakeStatus.TAKEN, intake.status)
        assertEquals(now, intake.takenAt)
    }

    @Test
    fun `back-dating an intake uses the dose from the cycle, not from today`() = runTest {
        // 4 августа — второй день цикла, 75 мг, в отличие от сегодняшних 100.
        repository.markTakenOn(LocalDate.of(2026, 8, 4))

        val intake = dao.intakes.value.single()
        assertEquals(75, intake.doseMg)
        assertEquals(IntakeStatus.TAKEN, intake.status)
    }

    @Test
    fun `snoozing counts the delays and arms a separate alarm`() = runTest {
        repository.onAlarm(scheduledAt = null)

        repository.snooze(at(day = 5), Duration.ofMinutes(15))
        repository.snooze(at(day = 5), Duration.ofMinutes(15))

        val intake = dao.intakes.value.single()
        assertEquals(IntakeStatus.SNOOZED, intake.status)
        assertEquals(2, intake.snoozeCount)
        assertEquals(now.plus(Duration.ofMinutes(15)) to at(day = 5), scheduler.snooze)
        // Ежедневный будильник отсрочка не трогает.
        assertEquals(at(day = 6), scheduler.daily)
    }

    /**
     * Отложенный приём обязан вернуться после перезагрузки.
     *
     * Цена потери здесь выше, чем у напоминания: отложил дозу, телефон
     * перезагрузился — и о ней больше ничто не напомнит, а следующий
     * будильник только завтра.
     */
    @Test
    fun `a snoozed dose comes back after a reboot`() = runTest {
        repository.onAlarm(scheduledAt = null)
        repository.snooze(at(day = 5), Duration.ofMinutes(15))
        scheduler.snooze = null

        repository.rescheduleAll()

        assertEquals(now.plus(Duration.ofMinutes(15)) to at(day = 5), scheduler.snooze)
    }

    @Test
    fun `a snoozed dose whose time passed is dropped, not fired`() = runTest {
        repository.onAlarm(scheduledAt = null)
        repository.snooze(at(day = 5), Duration.ofMinutes(15))
        scheduler.snooze = null

        // Телефон включили через сутки: показывать вчерашнюю отсрочку поздно.
        val afterReboot = MedRepository(
            dao,
            scheduler,
            Clock.fixed(now.plus(Duration.ofDays(1)), zone),
        )
        afterReboot.rescheduleAll()

        assertNull(scheduler.snooze)
        assertNull(dao.intakes.value.single().snoozedUntil)
    }

    /** Приняли — отсрочке неоткуда взяться после перезагрузки. */
    @Test
    fun `taking the dose clears a pending snooze`() = runTest {
        repository.onAlarm(scheduledAt = null)
        repository.snooze(at(day = 5), Duration.ofMinutes(15))

        repository.markTaken(at(day = 5))

        assertNull(dao.intakes.value.single().snoozedUntil)
    }

    @Test
    fun `history shows every day of the course, not only the recorded ones`() = runTest {
        val course = repository.activeCourse()!!
        val intakes = repository.observeIntakes(course.id, at(day = 1)).first()

        val history = repository.historyFor(course, intakes, days = 30)

        // 1–5 августа: пять дней, новейший сверху.
        assertEquals(5, history.size)
        assertEquals(LocalDate.of(2026, 8, 5), history.first().date)
        assertEquals(IntakeStatus.PENDING, history.first().status)
        assertTrue(history.drop(1).all { it.status == IntakeStatus.SKIPPED })
        assertEquals(listOf(100, 75, 100, 75, 100), history.map { it.doseMg })
    }

    @Test
    fun `deleting the course cancels the alarms`() = runTest {
        repository.deleteCourse(repository.activeCourse()!!)

        assertTrue(scheduler.cancelled)
        assertNull(repository.activeCourse())
    }

    private class RecordingMedScheduler : MedScheduler {
        var daily: Instant? = null
        var snooze: Pair<Instant, Instant>? = null
        var cancelled = false

        override fun scheduleDaily(at: Instant) {
            daily = at
        }

        override fun scheduleSnooze(at: Instant, scheduledAt: Instant) {
            snooze = at to scheduledAt
        }

        override fun cancelAll() {
            cancelled = true
        }
    }
}
