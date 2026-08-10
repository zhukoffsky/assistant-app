package com.zhukoffsky.magpie.feature.meds.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * Вычисление дозы по календарю.
 *
 * **Индекс дозы считается из даты, а не хранится счётчиком.** Счётчик
 * «следующая доза» разъезжается при пропуске приёма, перезагрузке телефона
 * или правке истории задним числом — и человек начинает пить не ту
 * дозировку, не замечая этого. Здесь же результат зависит только от
 * `startDate` и текущей даты, поэтому разъехаться не может в принципе.
 *
 * Это требование архитектуры, а не деталь реализации: если однажды
 * захочется «просто прибавлять единицу при каждом приёме» — нельзя.
 */
object DoseCycle {

    /** Позиция дня в цикле: 0 для дня старта, дальше по кругу. */
    fun doseIndex(startDate: LocalDate, date: LocalDate, cycleSize: Int): Int {
        require(cycleSize > 0) { "Цикл доз не может быть пустым" }

        val days = ChronoUnit.DAYS.between(startDate, date)
        val remainder = days % cycleSize
        // Дни до старта дают отрицательный остаток — приводим к диапазону.
        return (if (remainder < 0) remainder + cycleSize else remainder).toInt()
    }

    /** @return null для дат до начала курса — дозы там просто нет. */
    fun doseFor(course: MedCourse, date: LocalDate): Int? {
        if (course.dosesMg.isEmpty() || date.isBefore(course.startDate)) return null
        return course.dosesMg[doseIndex(course.startDate, date, course.dosesMg.size)]
    }

    fun scheduledAt(course: MedCourse, date: LocalDate, zone: ZoneId): Instant =
        date.atTime(course.timeOfDay).atZone(zone).toInstant()

    /**
     * Ближайший приём строго после [now] — то, на что ставится будильник.
     * Считается от текущего момента, поэтому длительный простой телефона не
     * приводит к очереди пропущенных срабатываний.
     */
    fun nextIntake(course: MedCourse, now: ZonedDateTime): ZonedDateTime {
        val firstPossibleDay = maxOf(now.toLocalDate(), course.startDate)
        val candidate = firstPossibleDay.atTime(course.timeOfDay).atZone(now.zone)

        return if (candidate.isAfter(now)) candidate else candidate.plusDays(1)
    }

    /** Дни курса от [from] до [to] включительно — каркас для истории приёмов. */
    fun daysBetween(course: MedCourse, from: LocalDate, to: LocalDate): List<Pair<LocalDate, Int>> {
        val start = maxOf(from, course.startDate)
        if (start.isAfter(to)) return emptyList()

        return generateSequence(start) { it.plusDays(1) }
            .takeWhile { !it.isAfter(to) }
            .mapNotNull { date -> doseFor(course, date)?.let { date to it } }
            .toList()
    }
}
