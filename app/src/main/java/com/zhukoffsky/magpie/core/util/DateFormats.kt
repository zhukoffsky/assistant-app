package com.zhukoffsky.magpie.core.util

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Даты во всём приложении — в двух видах, и только в них.
 *
 * Раньше их было пять: «4 нояб. 2026 г.» в истории, «4 нояб. 2026» в форме,
 * «15 янв. 2027» в диалоге, «15 янв., 10:17» в списке и «Nov 4, 2026» по-
 * английски рядом с «4 Nov 2026». Хуже разнобоя было то, что в списке
 * напоминаний год не писался вовсе: январь этого года и следующего
 * выглядели одинаково.
 *
 * - [dateTime] — строка списка: «15 янв., 10:17», а если год не текущий —
 *   «15 янв. 2027, 10:17». Год появляется ровно тогда, когда без него
 *   ошибёшься.
 * - [date] — поле и история: «4 нояб. 2026», всегда с годом.
 *
 * Порядок «день месяц» и для английского: приложение не американское, а
 * «Nov 4» и «4 Nov» рядом путают сильнее, чем непривычный порядок.
 */
object DateFormats {

    fun dateTime(moment: LocalDateTime, locale: Locale, today: LocalDate = LocalDate.now()): String {
        val day = if (moment.year == today.year) "d MMM" else "d MMM yyyy"
        return moment.format(DateTimeFormatter.ofPattern("$day, HH:mm", locale))
    }

    fun date(date: LocalDate, locale: Locale): String =
        date.format(DateTimeFormatter.ofPattern("d MMM yyyy", locale))

    fun time(moment: LocalDateTime, locale: Locale): String =
        moment.format(DateTimeFormatter.ofPattern("HH:mm", locale))
}
