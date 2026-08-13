package com.zhukoffsky.magpie.feature.reminders.data

import com.zhukoffsky.magpie.core.data.db.FakeReminderDao
import com.zhukoffsky.magpie.core.data.db.ReminderEntity
import com.zhukoffsky.magpie.feature.reminders.alarm.ReminderScheduler
import com.zhukoffsky.magpie.feature.reminders.domain.RepeatRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

class ReminderRepositoryTest {

    private val zone = ZoneId.of("Europe/Moscow")

    /** Понедельник, 10 августа 2026, 15:00 по Москве. */
    private val now: Instant = Instant.parse("2026-08-10T12:00:00Z")

    private val dao = FakeReminderDao()
    private val scheduler = RecordingScheduler()
    private val repository = ReminderRepository(dao, scheduler, Clock.fixed(now, zone))

    @Test
    fun `adding a timed reminder schedules an alarm`() = runTest {
        val dueAt = now.plus(Duration.ofHours(2))

        val id = repository.add("позвонить", dueAt, repeat = null)

        assertEquals(mapOf(id to dueAt), scheduler.scheduled)
    }

    @Test
    fun `blank title is rejected`() = runTest {
        assertNull(repository.add("   ", now, repeat = null))
        assertTrue(dao.items.value.isEmpty())
    }

    @Test
    fun `marking done cancels the alarm`() = runTest {
        val id = repository.add("позвонить", now.plus(Duration.ofHours(2)), repeat = null)!!

        repository.setDone(id, true)

        assertEquals(setOf(id), scheduler.cancelled)
    }

    @Test
    fun `un-marking done schedules the alarm again`() = runTest {
        val dueAt = now.plus(Duration.ofHours(2))
        val id = repository.add("позвонить", dueAt, repeat = null)!!
        repository.setDone(id, true)
        scheduler.scheduled.clear()

        repository.setDone(id, false)

        assertEquals(mapOf(id to dueAt), scheduler.scheduled)
    }

    @Test
    fun `deleting cancels the alarm`() = runTest {
        val id = repository.add("позвонить", now.plus(Duration.ofHours(2)), repeat = null)!!

        repository.delete(id)

        assertEquals(setOf(id), scheduler.cancelled)
        assertTrue(dao.items.value.isEmpty())
    }

    @Test
    fun `a fired daily reminder moves to the next day`() = runTest {
        val dueAt = now.minus(Duration.ofMinutes(1))
        val id = repository.add("витамины", dueAt, RepeatRule.Daily)!!
        scheduler.scheduled.clear()

        repository.onFired(repository.byId(id)!!)

        val expected = dueAt.plus(Duration.ofDays(1))
        assertEquals(expected, dao.items.value.single().dueAt)
        assertEquals(mapOf(id to expected), scheduler.scheduled)
    }

    @Test
    fun `a fired one-off reminder is not rescheduled`() = runTest {
        val id = repository.add("позвонить", now, repeat = null)!!
        scheduler.scheduled.clear()

        repository.onFired(repository.byId(id)!!)

        assertTrue(scheduler.scheduled.isEmpty())
    }

    @Test
    fun `snoozing sets a separate alarm and leaves the schedule alone`() = runTest {
        val dueAt = now.minus(Duration.ofMinutes(1))
        val id = repository.add("вынести мусор", dueAt, RepeatRule.Daily)!!
        scheduler.scheduled.clear()

        repository.snooze(id, Duration.ofMinutes(10))

        // Отложили — значит, срабатывание через десять минут и ни одного
        // изменения в самой записи: серия повтора остаётся на месте.
        assertEquals(mapOf(id to now.plus(Duration.ofMinutes(10))), scheduler.snoozed)
        assertTrue(scheduler.scheduled.isEmpty())
        assertEquals(dueAt, dao.items.value.single().dueAt)
    }

    @Test
    fun `a done reminder is not snoozed`() = runTest {
        val id = repository.add("позвонить", now, repeat = null)!!
        repository.setDone(id, true)

        repository.snooze(id, Duration.ofMinutes(10))

        assertTrue(scheduler.snoozed.isEmpty())
    }

    @Test
    fun `editing drops a pending snooze`() = runTest {
        val id = repository.add("позвонить", now.plus(Duration.ofHours(2)), repeat = null)!!
        repository.snooze(id, Duration.ofMinutes(10))

        repository.update(id, "позвонить в поликлинику", now.plus(Duration.ofHours(5)))

        // cancel снимает оба будильника, иначе отсрочка сработала бы по
        // старому времени уже после правки.
        assertEquals(setOf(id), scheduler.cancelled)
    }

    @Test
    fun `reboot reschedules future reminders and skips missed one-offs`() = runTest {
        val future = now.plus(Duration.ofHours(3))
        dao.insert(entity(title = "будущее", dueAt = future))
        dao.insert(entity(title = "просрочено", dueAt = now.minus(Duration.ofDays(1))))
        dao.insert(entity(title = "без времени", dueAt = null))
        scheduler.scheduled.clear()

        repository.rescheduleAll()

        assertEquals(mapOf(1L to future), scheduler.scheduled)
    }

    @Test
    fun `reboot moves an overdue repeating reminder to the next occurrence`() = runTest {
        val missed = now.minus(Duration.ofDays(3))
        dao.insert(entity(title = "витамины", dueAt = missed, repeat = RepeatRule.Daily))
        scheduler.scheduled.clear()

        repository.rescheduleAll()

        // Ровно одно будущее срабатывание, а не три пропущенных подряд.
        assertEquals(1, scheduler.scheduled.size)
        assertTrue(scheduler.scheduled.values.single().isAfter(now))
    }

    private fun entity(title: String, dueAt: Instant?, repeat: RepeatRule? = null) = ReminderEntity(
        title = title,
        dueAt = dueAt,
        repeatRule = repeat?.serialize(),
        createdAt = now,
        updatedAt = now,
    )

    private class RecordingScheduler : ReminderScheduler {
        val scheduled = mutableMapOf<Long, Instant>()
        val snoozed = mutableMapOf<Long, Instant>()
        val cancelled = mutableSetOf<Long>()

        override fun schedule(id: Long, at: Instant) {
            scheduled[id] = at
        }

        override fun scheduleSnooze(id: Long, at: Instant) {
            snoozed[id] = at
        }

        override fun cancel(id: Long) {
            cancelled += id
        }
    }
}
