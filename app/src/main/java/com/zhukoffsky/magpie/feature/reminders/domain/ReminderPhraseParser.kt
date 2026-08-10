package com.zhukoffsky.magpie.feature.reminders.domain

import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZonedDateTime

data class ParsedReminder(
    val title: String,
    val dueAt: ZonedDateTime,
    val repeat: RepeatRule?,
)

/**
 * Разбор надиктованного напоминания на правилах.
 *
 * Покрывает формулировки, которыми владелец пользуется на практике:
 * «завтра в 9 позвонить в поликлинику», «каждый вторник вынести мусор»,
 * «через 20 минут снять с плиты». Всё, что правила не осилили, получает
 * время по умолчанию и уходит целиком в заголовок — на этапе 9 такие фразы
 * подхватит LLM.
 *
 * Реализация за интерфейсом не спрятана намеренно: пока реализация одна,
 * абстракция была бы пустой. Интерфейс `PhraseParser` появится вместе со
 * второй реализацией.
 */
object ReminderPhraseParser {

    private val DEFAULT_TIME: LocalTime = LocalTime.of(9, 0)

    private val weekdayPatterns = listOf(
        """понедельник(?:ам|а)?|monday""" to DayOfWeek.MONDAY,
        """вторник(?:ам|а)?|tuesday""" to DayOfWeek.TUESDAY,
        """сред(?:у|ам|ы|а)|wednesday""" to DayOfWeek.WEDNESDAY,
        """четверг(?:ам|а)?|thursday""" to DayOfWeek.THURSDAY,
        """пятниц(?:у|ам|ы|а)|friday""" to DayOfWeek.FRIDAY,
        """суббот(?:у|ам|ы|а)|saturday""" to DayOfWeek.SATURDAY,
        """воскресень(?:е|ям|я)|sunday""" to DayOfWeek.SUNDAY,
    )

    private val anyWeekday = weekdayPatterns.joinToString("|") { "(?:${it.first})" }

    private val leadingVerb = Regex(
        """^\s*(?:напомни(?:ть)?(?:\s+мне)?|remind\s+me(?:\s+to)?)\s+""",
        RegexOption.IGNORE_CASE,
    )
    private val dailyRepeat = Regex(
        """кажд(?:ый|ую)\s+день|ежедневно|every\s+day|daily""",
        RegexOption.IGNORE_CASE,
    )
    private val weeklyRepeat = Regex(
        """(?:кажд(?:ый|ую)|по|every)\s+(?:$anyWeekday)(?:\s*(?:,|и|and)\s*(?:$anyWeekday))*""",
        RegexOption.IGNORE_CASE,
    )
    private val relativeOffset = Regex(
        """через\s+(\d+)\s*(минут\w*|час\w*|дн\w*|день|недел\w*)|in\s+(\d+)\s*(minute\w*|hour\w*|day\w*|week\w*)""",
        RegexOption.IGNORE_CASE,
    )
    private val bareWeekday = Regex(
        """(?:в|во|on)\s+(?:$anyWeekday)|$anyWeekday""",
        RegexOption.IGNORE_CASE,
    )
    private val namedTime = Regex(
        """в\s+полдень|в\s+полночь|утром|днём|днем|вечером|ночью""",
        RegexOption.IGNORE_CASE,
    )
    private val numericTime = Regex(
        """(?:в|at)\s+(\d{1,2})(?:[:.](\d{2}))?\s*(утра|дня|вечера|ночи|am|pm)?""",
        RegexOption.IGNORE_CASE,
    )

    fun parse(phrase: String, now: ZonedDateTime): ParsedReminder {
        val text = Remainder(phrase.replaceFirst(leadingVerb, ""))

        val repeat = parseRepeat(text)
        val offset = text.cut(relativeOffset)
        val dayShift = parseDayShift(text)
        val weekday = if (repeat == null && dayShift == null) parseWeekday(text) else null
        val time = parseTime(text)

        val dueAt = when {
            offset != null -> applyOffset(now, offset)
            else -> resolveDate(
                now = now,
                repeat = repeat,
                dayShift = dayShift,
                weekday = weekday,
                time = time,
                hasExplicitDate = dayShift != null || weekday != null,
            )
        }

        val title = text.value
            .replace(Regex("""\s+"""), " ")
            .trim()
            .trim(',', '.', '!', '?', '—', '-')
            .trim()

        return ParsedReminder(
            title = title.ifEmpty { phrase.trim() },
            dueAt = dueAt,
            repeat = repeat,
        )
    }

    private fun parseRepeat(text: Remainder): RepeatRule? {
        if (text.cut(dailyRepeat) != null) return RepeatRule.Daily

        val match = text.cut(weeklyRepeat) ?: return null
        val days = weekdayPatterns
            .filter { (pattern, _) -> Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(match.value) }
            .map { it.second }
            .toSet()

        return if (days.isEmpty()) null else RepeatRule.Weekly(days)
    }

    private fun parseDayShift(text: Remainder): Long? = when {
        text.cut(Regex("""послезавтра""", RegexOption.IGNORE_CASE)) != null -> 2
        text.cut(Regex("""завтра|tomorrow""", RegexOption.IGNORE_CASE)) != null -> 1
        text.cut(Regex("""сегодня|today""", RegexOption.IGNORE_CASE)) != null -> 0
        else -> null
    }

    private fun parseWeekday(text: Remainder): DayOfWeek? {
        val match = text.cut(bareWeekday) ?: return null
        return weekdayPatterns
            .firstOrNull { (pattern, _) ->
                Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(match.value)
            }
            ?.second
    }

    private fun parseTime(text: Remainder): LocalTime? {
        text.cut(namedTime)?.let { match ->
            return when (match.value.lowercase().trim()) {
                "в полдень" -> LocalTime.NOON
                "в полночь" -> LocalTime.MIDNIGHT
                "утром" -> LocalTime.of(9, 0)
                "днём", "днем" -> LocalTime.of(13, 0)
                "вечером" -> LocalTime.of(19, 0)
                "ночью" -> LocalTime.of(22, 0)
                else -> null
            }
        }

        val match = text.cut(numericTime) ?: return null
        val rawHour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: 0
        if (rawHour > 23 || minute > 59) return null

        val hour = when (match.groupValues[3].lowercase()) {
            "вечера", "pm" -> if (rawHour in 1..11) rawHour + 12 else rawHour
            "дня" -> if (rawHour in 1..11) rawHour + 12 else rawHour
            "утра", "am" -> if (rawHour == 12) 0 else rawHour
            "ночи" -> if (rawHour == 12) 0 else rawHour
            else -> rawHour
        }

        return LocalTime.of(hour, minute)
    }

    private fun applyOffset(now: ZonedDateTime, match: MatchResult): ZonedDateTime {
        val amount = (match.groupValues[1].ifEmpty { match.groupValues[3] }).toLongOrNull() ?: return now
        val unit = (match.groupValues[2].ifEmpty { match.groupValues[4] }).lowercase()

        return when {
            unit.startsWith("минут") || unit.startsWith("minute") -> now.plusMinutes(amount)
            unit.startsWith("час") || unit.startsWith("hour") -> now.plusHours(amount)
            unit.startsWith("недел") || unit.startsWith("week") -> now.plusWeeks(amount)
            else -> now.plusDays(amount)
        }.withSecond(0).withNano(0)
    }

    private fun resolveDate(
        now: ZonedDateTime,
        repeat: RepeatRule?,
        dayShift: Long?,
        weekday: DayOfWeek?,
        time: LocalTime?,
        hasExplicitDate: Boolean,
    ): ZonedDateTime {
        val atTime = time ?: DEFAULT_TIME
        val base = when {
            dayShift != null -> now.plusDays(dayShift)
            weekday != null -> generateSequence(now) { it.plusDays(1) }.first { it.dayOfWeek == weekday }
            else -> now
        }

        val candidate = base
            .withHour(atTime.hour)
            .withMinute(atTime.minute)
            .withSecond(0)
            .withNano(0)

        return when {
            // «каждый вторник» без даты — ближайший вторник в будущем.
            repeat != null -> repeat.nextAfter(now, candidate)

            // «в пятницу», но эта пятница уже прошла — значит следующая.
            weekday != null && !candidate.isAfter(now) -> candidate.plusWeeks(1)

            // «в 9» произнесённое в 15:00 — это завтрашние 9, а не прошедшие.
            !hasExplicitDate && !candidate.isAfter(now) -> candidate.plusDays(1)

            else -> candidate
        }
    }

    /** Текст, из которого по мере разбора вырезаются распознанные куски. */
    private class Remainder(var value: String) {
        fun cut(regex: Regex): MatchResult? {
            val match = regex.find(value) ?: return null
            value = value.removeRange(match.range)
            return match
        }
    }
}
