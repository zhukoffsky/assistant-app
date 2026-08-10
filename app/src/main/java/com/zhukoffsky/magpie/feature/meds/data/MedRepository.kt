package com.zhukoffsky.magpie.feature.meds.data

import com.zhukoffsky.magpie.core.data.db.IntakeStatus
import com.zhukoffsky.magpie.core.data.db.MedCourseEntity
import com.zhukoffsky.magpie.core.data.db.MedDao
import com.zhukoffsky.magpie.core.data.db.MedIntakeEntity
import com.zhukoffsky.magpie.feature.meds.alarm.MedScheduler
import com.zhukoffsky.magpie.feature.meds.domain.DoseCycle
import com.zhukoffsky.magpie.feature.meds.domain.DoseDay
import com.zhukoffsky.magpie.feature.meds.domain.MedCourse
import com.zhukoffsky.magpie.feature.meds.domain.MedIntake
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class MedRepository(
    private val dao: MedDao,
    private val scheduler: MedScheduler,
    private val clock: Clock = Clock.systemDefaultZone(),
) {

    fun observeCourse(): Flow<MedCourse?> =
        dao.observeActiveCourse().map { it?.toDomain() }

    fun observeIntakes(courseId: Long, since: Instant): Flow<List<MedIntake>> =
        dao.observeIntakes(courseId, since).map { list -> list.map { it.toDomain() } }

    suspend fun activeCourse(): MedCourse? = dao.activeCourse()?.toDomain()

    suspend fun saveCourse(
        id: Long,
        name: String,
        dosesMg: List<Int>,
        timeOfDay: LocalTime,
        startDate: LocalDate,
    ): Boolean {
        val cleanName = name.trim()
        if (cleanName.isEmpty() || dosesMg.isEmpty()) return false

        dao.upsertCourse(
            MedCourseEntity(
                id = id,
                name = cleanName,
                dosesMg = dosesMg,
                timeOfDay = timeOfDay,
                startDate = startDate,
                isActive = true,
                createdAt = clock.instant(),
            ),
        )
        scheduleNext()
        return true
    }

    suspend fun deleteCourse(course: MedCourse) {
        scheduler.cancelAll()
        dao.deleteCourse(
            MedCourseEntity(
                id = course.id,
                name = course.name,
                dosesMg = course.dosesMg,
                timeOfDay = course.timeOfDay,
                startDate = course.startDate,
                isActive = course.isActive,
                createdAt = clock.instant(),
            ),
        )
    }

    /**
     * Ставит будильник на ближайший приём. Идемпотентно.
     *
     * @param notBefore приём, который уже обработан. Отсчёт ведётся строго
     * от него, а не от текущего времени: неточный будильник может сработать
     * на несколько секунд раньше срока, и отсчёт «от сейчас» перевзвёл бы
     * его на тот же момент — получился бы цикл мгновенных повторов.
     */
    suspend fun scheduleNext(notBefore: Instant? = null) {
        val course = dao.activeCourse()?.toDomain()
        if (course == null) {
            scheduler.cancelAll()
            return
        }

        val reference = maxOf(notBefore ?: Instant.MIN, clock.instant())
        val next = DoseCycle.nextIntake(course, reference.atZone(clock.zone))
        scheduler.scheduleDaily(next.toInstant())
    }

    /**
     * Сработал будильник: заводим запись приёма (если её ещё нет) и сразу
     * планируем следующий день, чтобы цепочка не оборвалась.
     *
     * @param scheduledAt время приёма; null для ежедневного будильника —
     * тогда берётся приём на сегодня.
     */
    suspend fun onAlarm(scheduledAt: Instant?): Pair<MedCourse, MedIntake>? {
        val course = dao.activeCourse()?.toDomain() ?: return null
        val today = clock.instant().atZone(clock.zone).toLocalDate()

        val plannedAt = scheduledAt ?: DoseCycle.scheduledAt(course, today, clock.zone)
        val dose = DoseCycle.doseFor(course, plannedAt.atZone(clock.zone).toLocalDate())
            ?: return null

        val intake = dao.intakeAt(course.id, plannedAt)?.toDomain()
            ?: run {
                dao.upsertIntake(
                    MedIntakeEntity(
                        courseId = course.id,
                        scheduledAt = plannedAt,
                        status = IntakeStatus.PENDING,
                        doseMg = dose,
                    ),
                )
                dao.intakeAt(course.id, plannedAt)?.toDomain() ?: return null
            }

        scheduleNext(notBefore = plannedAt)

        /*
         * Отложенный будильник живёт своей жизнью: «Принял» его не снимает,
         * потому что снять чужой PendingIntent из репозитория нельзя, не
         * протащив планировщик через все пути отметки. Поэтому решение
         * принимается здесь — по состоянию приёма, а не по тому, кто разбудил.
         *
         * Заодно закрывается второй случай: доза, отмеченная задним числом
         * заранее, к вечеру всё равно подняла бы уведомление.
         */
        if (intake.status == IntakeStatus.TAKEN) return null

        return course to intake
    }

    suspend fun markTaken(scheduledAt: Instant) {
        val course = dao.activeCourse()?.toDomain() ?: return
        val date = scheduledAt.atZone(clock.zone).toLocalDate()
        val dose = DoseCycle.doseFor(course, date) ?: return
        val existing = dao.intakeAt(course.id, scheduledAt)

        dao.upsertIntake(
            MedIntakeEntity(
                id = existing?.id ?: 0,
                courseId = course.id,
                scheduledAt = scheduledAt,
                takenAt = clock.instant(),
                status = IntakeStatus.TAKEN,
                // Доза берётся из цикла, а не из старой записи: так отметка
                // задним числом не может закрепить неверную дозировку.
                doseMg = dose,
                snoozeCount = existing?.snoozeCount ?: 0,
            ),
        )
    }

    /** Отметка приёма за прошедший день — «выпил, но забыл нажать». */
    suspend fun markTakenOn(date: LocalDate) {
        val course = dao.activeCourse()?.toDomain() ?: return
        markTaken(DoseCycle.scheduledAt(course, date, clock.zone))
    }

    suspend fun snooze(scheduledAt: Instant, delay: Duration) {
        val course = dao.activeCourse()?.toDomain() ?: return
        val existing = dao.intakeAt(course.id, scheduledAt)
        val dose = DoseCycle.doseFor(course, scheduledAt.atZone(clock.zone).toLocalDate()) ?: return

        dao.upsertIntake(
            MedIntakeEntity(
                id = existing?.id ?: 0,
                courseId = course.id,
                scheduledAt = scheduledAt,
                takenAt = null,
                status = IntakeStatus.SNOOZED,
                doseMg = dose,
                snoozeCount = (existing?.snoozeCount ?: 0) + 1,
            ),
        )

        scheduler.scheduleSnooze(clock.instant().plus(delay), scheduledAt)
    }

    /**
     * История: каркас строится из цикла доз, а не из записей в БД. Дни,
     * которые пользователь проигнорировал, записи не создают — без каркаса
     * они бы просто исчезли из истории.
     */
    fun historyFor(course: MedCourse, intakes: List<MedIntake>, days: Long): List<DoseDay> {
        val today = clock.instant().atZone(clock.zone).toLocalDate()
        val byDate = intakes.associateBy { it.scheduledAt.atZone(clock.zone).toLocalDate() }

        return DoseCycle.daysBetween(course, today.minusDays(days - 1), today)
            .map { (date, dose) ->
                val intake = byDate[date]
                DoseDay(
                    date = date,
                    doseMg = intake?.doseMg ?: dose,
                    status = intake?.status ?: defaultStatus(date, today),
                    takenAt = intake?.takenAt,
                )
            }
            .reversed()
    }

    /** Прошедший день без записи считается пропущенным, сегодняшний — ожидающим. */
    private fun defaultStatus(date: LocalDate, today: LocalDate): IntakeStatus =
        if (date.isBefore(today)) IntakeStatus.SKIPPED else IntakeStatus.PENDING
}

private fun MedCourseEntity.toDomain() = MedCourse(
    id = id,
    name = name,
    dosesMg = dosesMg,
    timeOfDay = timeOfDay,
    startDate = startDate,
    isActive = isActive,
)

private fun MedIntakeEntity.toDomain() = MedIntake(
    id = id,
    courseId = courseId,
    scheduledAt = scheduledAt,
    takenAt = takenAt,
    status = status,
    doseMg = doseMg,
    snoozeCount = snoozeCount,
)
