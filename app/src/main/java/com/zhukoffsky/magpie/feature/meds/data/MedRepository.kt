package com.zhukoffsky.magpie.feature.meds.data

import com.zhukoffsky.magpie.core.data.db.DoseUnit
import com.zhukoffsky.magpie.core.data.db.IntakeStatus
import com.zhukoffsky.magpie.core.data.db.MedCourseEntity
import com.zhukoffsky.magpie.core.data.db.MedDao
import com.zhukoffsky.magpie.core.data.db.MedIntakeEntity
import com.zhukoffsky.magpie.feature.meds.alarm.MedScheduler
import com.zhukoffsky.magpie.feature.meds.domain.DoseCycle
import com.zhukoffsky.magpie.feature.meds.domain.DoseDay
import com.zhukoffsky.magpie.feature.meds.domain.MedCourse
import com.zhukoffsky.magpie.feature.meds.domain.MedIntake
import com.zhukoffsky.magpie.feature.meds.domain.TodayDose
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * [onChanged] вызывается после каждой записи и служит одному: растолкать
 * виджет. Причина та же, что у покупок и напоминаний: подписка на Room
 * обновляет виджет, только пока жива сессия Glance, а она умирает вместе с
 * процессом.
 */
class MedRepository(
    private val dao: MedDao,
    private val scheduler: MedScheduler,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val onChanged: suspend () -> Unit = {},
) {

    /** Действующие курсы по времени приёма — в том порядке, в каком их пьют. */
    fun observeCourses(): Flow<List<MedCourse>> =
        dao.observeActiveCourses().map { list -> list.map { it.toDomain() } }

    /**
     * Сегодняшние приёмы всех курсов одним потоком — то, что показывает
     * виджет.
     *
     * Доза считается из даты, а не берётся из записи: записи может не быть
     * вовсе, пока человек не отреагировал. Это то же архитектурное
     * требование, что и везде в таблетках, — счётчик «следующая доза»
     * разъезжается при первом же пропуске. Курс, который ещё не начался,
     * в список не попадает: сегодня у него дозы нет.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeToday(): Flow<List<TodayDose>> = observeCourses().flatMapLatest { courses ->
        if (courses.isEmpty()) return@flatMapLatest flowOf(emptyList())

        val today = clock.instant().atZone(clock.zone).toLocalDate()
        val since = startOfDay(today)
        combine(
            courses.map { course ->
                observeIntakes(course.id, since).map { intakes -> todayDose(course, today, intakes) }
            },
        ) { doses -> doses.filterNotNull() }
    }

    private fun todayDose(course: MedCourse, today: LocalDate, intakes: List<MedIntake>): TodayDose? {
        val dose = DoseCycle.doseFor(course, today) ?: return null
        val intake = intakes.firstOrNull { it.scheduledAt.atZone(clock.zone).toLocalDate() == today }
        return TodayDose(
            course = course,
            doseMg = intake?.doseMg ?: dose,
            status = intake?.status ?: IntakeStatus.PENDING,
        )
    }

    private fun startOfDay(date: LocalDate): Instant =
        date.atStartOfDay(clock.zone).toInstant()

    fun observeIntakes(courseId: Long, since: Instant): Flow<List<MedIntake>> =
        dao.observeIntakes(courseId, since).map { list -> list.map { it.toDomain() } }

    suspend fun courses(): List<MedCourse> = dao.activeCourses().map { it.toDomain() }

    suspend fun course(id: Long): MedCourse? = dao.activeCourse(id)?.toDomain()

    /**
     * Создать курс ([id] = 0) или изменить существующий.
     *
     * @return id сохранённого курса; null, если сохранять нечего.
     */
    suspend fun saveCourse(
        id: Long,
        name: String,
        dosesMg: List<Int>,
        timeOfDay: LocalTime,
        startDate: LocalDate,
        unit: DoseUnit = DoseUnit.MG,
    ): Long? {
        val cleanName = name.trim()
        if (cleanName.isEmpty() || dosesMg.isEmpty()) return null

        val rowId = dao.upsertCourse(
            MedCourseEntity(
                id = id,
                name = cleanName,
                dosesMg = dosesMg,
                unit = unit,
                timeOfDay = timeOfDay,
                startDate = startDate,
                isActive = true,
                createdAt = clock.instant(),
            ),
        )
        // `@Upsert` отдаёт номер строки только при вставке, при обновлении — -1.
        val savedId = if (id == 0L) rowId else id

        // Будильник переставляется сразу: у правленого курса могло смениться
        // время, а прежний будильник иначе сработал бы по старому.
        scheduleNext(savedId)
        onChanged()
        return savedId
    }

    suspend fun deleteCourse(course: MedCourse) {
        scheduler.cancel(course.id)
        dao.deleteCourse(
            MedCourseEntity(
                id = course.id,
                name = course.name,
                dosesMg = course.dosesMg,
                unit = course.unit,
                timeOfDay = course.timeOfDay,
                startDate = course.startDate,
                isActive = course.isActive,
                createdAt = clock.instant(),
            ),
        )
        onChanged()
    }

    /**
     * Ставит будильник на ближайший приём курса. Идемпотентно.
     *
     * @param notBefore приём, который уже обработан. Отсчёт ведётся строго
     * от него, а не от текущего времени: неточный будильник может сработать
     * на несколько секунд раньше срока, и отсчёт «от сейчас» перевзвёл бы
     * его на тот же момент — получился бы цикл мгновенных повторов.
     */
    suspend fun scheduleNext(courseId: Long, notBefore: Instant? = null) {
        val course = course(courseId)
        if (course == null) {
            scheduler.cancel(courseId)
            return
        }

        val reference = maxOf(notBefore ?: Instant.MIN, clock.instant())
        val next = DoseCycle.nextIntake(course, reference.atZone(clock.zone))
        scheduler.scheduleDaily(course.id, next.toInstant())
    }

    /**
     * Сработал будильник курса: заводим запись приёма (если её ещё нет) и
     * сразу планируем следующий день, чтобы цепочка не оборвалась.
     *
     * @param scheduledAt время приёма; null для ежедневного будильника —
     * тогда берётся приём на сегодня.
     */
    suspend fun onAlarm(courseId: Long, scheduledAt: Instant?): Pair<MedCourse, MedIntake>? {
        // Курс удалили, а будильник успел сработать: показывать нечего.
        val course = course(courseId) ?: return null
        val today = clock.instant().atZone(clock.zone).toLocalDate()

        val plannedAt = scheduledAt ?: DoseCycle.scheduledAt(course, today, clock.zone)
        val dose = DoseCycle.doseFor(course, plannedAt.atZone(clock.zone).toLocalDate())
            ?: return null

        val existing = dao.intakeAt(course.id, plannedAt)

        // Будильник отработал — срок отсрочки отслужил своё. Оставленный, он
        // вернул бы уведомление после ближайшей перезагрузки.
        if (existing?.snoozedUntil != null) dao.upsertIntake(existing.copy(snoozedUntil = null))

        val intake = existing?.toDomain()
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

        scheduleNext(course.id, notBefore = plannedAt)
        onChanged()

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

    suspend fun markTaken(courseId: Long, scheduledAt: Instant) {
        val course = course(courseId) ?: return
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
        onChanged()
    }

    /** Отметка приёма за прошедший день — «выпил, но забыл нажать». */
    suspend fun markTakenOn(courseId: Long, date: LocalDate) {
        val course = course(courseId) ?: return
        markTaken(course.id, DoseCycle.scheduledAt(course, date, clock.zone))
    }

    suspend fun snooze(courseId: Long, scheduledAt: Instant, delay: Duration) {
        val course = course(courseId) ?: return
        val existing = dao.intakeAt(course.id, scheduledAt)
        val dose = DoseCycle.doseFor(course, scheduledAt.atZone(clock.zone).toLocalDate()) ?: return

        val at = clock.instant().plus(delay)
        dao.upsertIntake(
            MedIntakeEntity(
                id = existing?.id ?: 0,
                courseId = course.id,
                scheduledAt = scheduledAt,
                takenAt = null,
                status = IntakeStatus.SNOOZED,
                doseMg = dose,
                snoozeCount = (existing?.snoozeCount ?: 0) + 1,
                // Статус говорил только о факте отсрочки; срок нужен,
                // чтобы вернуть будильник после перезагрузки.
                snoozedUntil = at,
            ),
        )

        scheduler.scheduleSnooze(course.id, at, scheduledAt)
        onChanged()
    }

    /**
     * Восстановить будильники всех курсов после перезагрузки: ежедневные и
     * отсрочки.
     *
     * Отдельный метод, а не `scheduleNext`: тот зовётся из каждой отметки
     * приёма, и восстановление отсрочки в нём воскрешало бы уже отработавшую.
     *
     * Просроченная отсрочка стирается, а не срабатывает: телефон мог
     * пролежать выключенным, и напоминание о вчерашней дозе сегодня утром
     * скорее собьёт с толку, чем поможет. Сама доза при этом не теряется —
     * она осталась в истории со своим статусом.
     */
    suspend fun rescheduleAll() {
        scheduler.cancelLegacy()

        val courses = courses()
        courses.forEach { scheduleNext(it.id) }

        val courseIds = courses.map { it.id }.toSet()
        val snoozed = dao.snoozedIntakes()
        // Действующая отсрочка курса — самая поздняя, остальные отслужили.
        val current = snoozed
            .groupBy { it.courseId }
            .mapValues { (_, list) -> list.maxBy { it.snoozedUntil!! } }

        snoozed.forEach { intake ->
            val at = intake.snoozedUntil ?: return@forEach
            val isCurrent = current[intake.courseId]?.id == intake.id

            if (isCurrent && intake.courseId in courseIds && at.isAfter(clock.instant())) {
                scheduler.scheduleSnooze(intake.courseId, at, intake.scheduledAt)
            } else {
                dao.upsertIntake(intake.copy(snoozedUntil = null))
            }
        }
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
    unit = unit,
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
