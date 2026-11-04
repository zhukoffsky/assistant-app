package com.zhukoffsky.magpie.feature.meds.data

import com.zhukoffsky.magpie.core.data.db.FakeMedDao
import com.zhukoffsky.magpie.core.data.db.IntakeStatus
import com.zhukoffsky.magpie.feature.meds.alarm.MedScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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

    /** id первого курса, заведённого в [setUp]. */
    private var course = 0L

    @Before
    fun setUp() = runTest {
        course = repository.saveCourse(
            id = 0,
            name = "Тестовое",
            dosesMg = listOf(100, 75),
            timeOfDay = doseTime,
            startDate = startDate,
        )!!
    }

    @Test
    fun `saving a course arms the alarm for the next intake`() {
        assertEquals(at(day = 5), scheduler.daily[course])
    }

    @Test
    fun `a course without doses is rejected`() = runTest {
        val saved = repository.saveCourse(0, "Пустое", emptyList(), doseTime, startDate)

        assertNull(saved)
    }

    @Test
    fun `the alarm creates a pending intake with the dose for that day`() = runTest {
        val result = repository.onAlarm(course, scheduledAt = null)

        assertNotNull(result)
        val intake = result!!.second
        assertEquals(at(day = 5), intake.scheduledAt)
        // От старта прошло 4 дня, цикл [100, 75] — значит снова 100 мг.
        assertEquals(100, intake.doseMg)
        assertEquals(IntakeStatus.PENDING, intake.status)
    }

    @Test
    fun `the alarm immediately arms the next day`() = runTest {
        repository.onAlarm(course, scheduledAt = null)

        assertEquals(at(day = 6), scheduler.daily[course])
    }

    @Test
    fun `firing twice does not create a second record for the same day`() = runTest {
        repository.onAlarm(course, scheduledAt = null)
        repository.onAlarm(course, scheduledAt = null)

        assertEquals(1, dao.intakes.value.size)
    }

    @Test
    fun `an alarm for a dose already taken does not ask for it again`() = runTest {
        repository.onAlarm(course, scheduledAt = null)
        repository.markTaken(course, at(day = 5))

        // Отложенный будильник переживает отметку «принял»: он поставлен на
        // своё время и никем не снимается. Сработав, он не должен требовать
        // принять дозу, которая уже принята.
        val result = repository.onAlarm(course, at(day = 5))

        assertNull(result)
    }

    @Test
    fun `an alarm for a dose already taken still arms the next day`() = runTest {
        repository.onAlarm(course, scheduledAt = null)
        repository.markTaken(course, at(day = 5))

        repository.onAlarm(course, at(day = 5))

        assertEquals(at(day = 6), scheduler.daily[course])
    }

    @Test
    fun `marking taken records the time`() = runTest {
        repository.onAlarm(course, scheduledAt = null)

        repository.markTaken(course, at(day = 5))

        val intake = dao.intakes.value.single()
        assertEquals(IntakeStatus.TAKEN, intake.status)
        assertEquals(now, intake.takenAt)
    }

    @Test
    fun `back-dating an intake uses the dose from the cycle, not from today`() = runTest {
        // 4 августа — второй день цикла, 75 мг, в отличие от сегодняшних 100.
        repository.markTakenOn(course, LocalDate.of(2026, 8, 4))

        val intake = dao.intakes.value.single()
        assertEquals(75, intake.doseMg)
        assertEquals(IntakeStatus.TAKEN, intake.status)
    }

    @Test
    fun `snoozing counts the delays and arms a separate alarm`() = runTest {
        repository.onAlarm(course, scheduledAt = null)

        repository.snooze(course, at(day = 5), Duration.ofMinutes(15))
        repository.snooze(course, at(day = 5), Duration.ofMinutes(15))

        val intake = dao.intakes.value.single()
        assertEquals(IntakeStatus.SNOOZED, intake.status)
        assertEquals(2, intake.snoozeCount)
        assertEquals(now.plus(Duration.ofMinutes(15)) to at(day = 5), scheduler.snooze[course])
        // Ежедневный будильник отсрочка не трогает.
        assertEquals(at(day = 6), scheduler.daily[course])
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
        repository.onAlarm(course, scheduledAt = null)
        repository.snooze(course, at(day = 5), Duration.ofMinutes(15))
        scheduler.snooze.clear()

        repository.rescheduleAll()

        assertEquals(now.plus(Duration.ofMinutes(15)) to at(day = 5), scheduler.snooze[course])
    }

    @Test
    fun `a snoozed dose whose time passed is dropped, not fired`() = runTest {
        repository.onAlarm(course, scheduledAt = null)
        repository.snooze(course, at(day = 5), Duration.ofMinutes(15))
        scheduler.snooze.clear()

        // Телефон включили через сутки: показывать вчерашнюю отсрочку поздно.
        val afterReboot = MedRepository(
            dao,
            scheduler,
            Clock.fixed(now.plus(Duration.ofDays(1)), zone),
        )
        afterReboot.rescheduleAll()

        assertNull(scheduler.snooze[course])
        assertNull(dao.intakes.value.single().snoozedUntil)
    }

    /** Приняли — отсрочке неоткуда взяться после перезагрузки. */
    @Test
    fun `taking the dose clears a pending snooze`() = runTest {
        repository.onAlarm(course, scheduledAt = null)
        repository.snooze(course, at(day = 5), Duration.ofMinutes(15))

        repository.markTaken(course, at(day = 5))

        assertNull(dao.intakes.value.single().snoozedUntil)
    }

    @Test
    fun `history shows every day of the course, not only the recorded ones`() = runTest {
        val active = repository.course(course)!!
        val intakes = repository.observeIntakes(active.id, at(day = 1)).first()

        val history = repository.historyFor(active, intakes, days = 30)

        // 1–5 августа: пять дней, новейший сверху.
        assertEquals(5, history.size)
        assertEquals(LocalDate.of(2026, 8, 5), history.first().date)
        assertEquals(IntakeStatus.PENDING, history.first().status)
        assertTrue(history.drop(1).all { it.status == IntakeStatus.SKIPPED })
        assertEquals(listOf(100, 75, 100, 75, 100), history.map { it.doseMg })
    }

    @Test
    fun `deleting the course cancels its alarms`() = runTest {
        repository.deleteCourse(repository.course(course)!!)

        assertTrue(course in scheduler.cancelled)
        assertNull(repository.course(course))
    }

    @Test
    fun `editing a course keeps its id and moves its alarm`() = runTest {
        val saved = repository.saveCourse(course, "Тестовое", listOf(100, 75), LocalTime.of(20, 0), startDate)

        assertEquals(course, saved)
        assertEquals(1, dao.courses.value.size)
        assertEquals(at(day = 5, hour = 20), scheduler.daily[course])
    }

    // --- Несколько курсов -------------------------------------------------

    private suspend fun secondCourse(): Long = repository.saveCourse(
        id = 0,
        name = "Витамин D",
        dosesMg = listOf(25),
        timeOfDay = LocalTime.of(21, 0),
        startDate = startDate,
    )!!

    @Test
    fun `every course gets its own alarm`() = runTest {
        val second = secondCourse()

        assertEquals(at(day = 5), scheduler.daily[course])
        assertEquals(at(day = 5, hour = 21), scheduler.daily[second])
    }

    @Test
    fun `an alarm fires the dose of its own course`() = runTest {
        val second = secondCourse()

        val (fired, intake) = repository.onAlarm(second, scheduledAt = null)!!

        assertEquals(second, fired.id)
        assertEquals(25, intake.doseMg)
        assertEquals(at(day = 5, hour = 21), intake.scheduledAt)
        // Будильник первого курса чужое срабатывание не трогает.
        assertEquals(at(day = 5), scheduler.daily[course])
        assertEquals(at(day = 6, hour = 21), scheduler.daily[second])
    }

    @Test
    fun `taking one medication does not mark the other`() = runTest {
        val second = secondCourse()
        repository.onAlarm(course, scheduledAt = null)
        repository.onAlarm(second, scheduledAt = null)

        repository.markTaken(course, at(day = 5))

        val today = repository.observeToday().first().associateBy { it.course.id }
        assertEquals(IntakeStatus.TAKEN, today.getValue(course).status)
        assertEquals(IntakeStatus.PENDING, today.getValue(second).status)
    }

    @Test
    fun `today lists the courses in the order they are taken`() = runTest {
        // Заведён позже, но пьётся раньше — и в списке должен быть первым.
        val morning = repository.saveCourse(0, "Утреннее", listOf(5), LocalTime.of(7, 0), startDate)!!

        val today = repository.observeToday().first()

        assertEquals(listOf(morning, course), today.map { it.course.id })
    }

    @Test
    fun `a course that has not started yet is not in today`() = runTest {
        repository.saveCourse(0, "Будущее", listOf(5), doseTime, LocalDate.of(2026, 9, 1))

        val today = repository.observeToday().first()

        assertEquals(listOf(course), today.map { it.course.id })
    }

    @Test
    fun `deleting one course leaves the other armed`() = runTest {
        val second = secondCourse()

        repository.deleteCourse(repository.course(course)!!)

        assertEquals(listOf(course), scheduler.cancelled)
        assertEquals(at(day = 5, hour = 21), scheduler.daily[second])
        assertNotNull(repository.course(second))
    }

    @Test
    fun `an alarm of a deleted course shows nothing`() = runTest {
        repository.deleteCourse(repository.course(course)!!)

        assertNull(repository.onAlarm(course, scheduledAt = null))
    }

    @Test
    fun `snoozes of different courses do not replace each other`() = runTest {
        val second = secondCourse()

        repository.snooze(course, at(day = 5), Duration.ofMinutes(15))
        repository.snooze(second, at(day = 5, hour = 21), Duration.ofMinutes(30))

        assertEquals(now.plus(Duration.ofMinutes(15)) to at(day = 5), scheduler.snooze[course])
        assertEquals(now.plus(Duration.ofMinutes(30)) to at(day = 5, hour = 21), scheduler.snooze[second])
    }

    @Test
    fun `a reboot brings back the alarms and snoozes of every course`() = runTest {
        val second = secondCourse()
        repository.snooze(course, at(day = 5), Duration.ofMinutes(15))
        repository.snooze(second, at(day = 5, hour = 21), Duration.ofMinutes(30))
        scheduler.daily.clear()
        scheduler.snooze.clear()

        repository.rescheduleAll()

        assertTrue(scheduler.legacyCancelled)
        assertEquals(at(day = 5), scheduler.daily[course])
        assertEquals(at(day = 5, hour = 21), scheduler.daily[second])
        assertEquals(now.plus(Duration.ofMinutes(15)) to at(day = 5), scheduler.snooze[course])
        assertEquals(now.plus(Duration.ofMinutes(30)) to at(day = 5, hour = 21), scheduler.snooze[second])
    }

    private class RecordingMedScheduler : MedScheduler {
        val daily = mutableMapOf<Long, Instant>()
        val snooze = mutableMapOf<Long, Pair<Instant, Instant>>()
        val cancelled = mutableListOf<Long>()
        var legacyCancelled = false

        override fun scheduleDaily(courseId: Long, at: Instant) {
            daily[courseId] = at
        }

        override fun scheduleSnooze(courseId: Long, at: Instant, scheduledAt: Instant) {
            snooze[courseId] = at to scheduledAt
        }

        override fun cancel(courseId: Long) {
            cancelled += courseId
            daily.remove(courseId)
            snooze.remove(courseId)
        }

        override fun cancelLegacy() {
            legacyCancelled = true
        }
    }
}
