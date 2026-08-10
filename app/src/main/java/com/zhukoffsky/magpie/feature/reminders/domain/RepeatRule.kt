package com.zhukoffsky.magpie.feature.reminders.domain

import java.time.DayOfWeek
import java.time.ZonedDateTime

/**
 * Правило повтора напоминания.
 *
 * Намеренно беднее RRULE из iCalendar: поддерживаются только сценарии,
 * которые владелец действительно диктует. Расширять — когда появится
 * реальная нужда, а не «на всякий случай».
 */
sealed interface RepeatRule {

    data object Daily : RepeatRule

    data class Weekly(val days: Set<DayOfWeek>) : RepeatRule

    fun serialize(): String = when (this) {
        Daily -> DAILY
        is Weekly -> WEEKLY_PREFIX + days.sorted().joinToString(",") { it.name }
    }

    companion object {
        private const val DAILY = "DAILY"
        private const val WEEKLY_PREFIX = "WEEKLY:"

        /** @return null для одноразового напоминания или нераспознанной строки. */
        fun parse(raw: String?): RepeatRule? = when {
            raw.isNullOrBlank() -> null
            raw == DAILY -> Daily
            raw.startsWith(WEEKLY_PREFIX) -> {
                val days = raw.removePrefix(WEEKLY_PREFIX)
                    .split(",")
                    .mapNotNull { name -> DayOfWeek.entries.firstOrNull { it.name == name } }
                    .toSet()
                if (days.isEmpty()) null else Weekly(days)
            }

            else -> null
        }
    }
}

/**
 * Следующее срабатывание строго после [after], с сохранением времени суток.
 *
 * Считается от переданного момента, а не «прибавить период к прошлому
 * срабатыванию»: после долгого простоя телефона или пропуска второй вариант
 * выдал бы прошедшую дату и посыпал бы уведомлениями за все пропущенные дни.
 */
fun RepeatRule.nextAfter(after: ZonedDateTime, timeOfDay: ZonedDateTime): ZonedDateTime {
    val candidate = after
        .withHour(timeOfDay.hour)
        .withMinute(timeOfDay.minute)
        .withSecond(0)
        .withNano(0)

    return when (this) {
        RepeatRule.Daily ->
            if (candidate.isAfter(after)) candidate else candidate.plusDays(1)

        is RepeatRule.Weekly -> {
            // Максимум восемь шагов: сегодня + полная неделя вперёд.
            generateSequence(candidate) { it.plusDays(1) }
                .take(8)
                .first { it.isAfter(after) && it.dayOfWeek in days }
        }
    }
}
