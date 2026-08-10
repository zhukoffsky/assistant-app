package com.zhukoffsky.magpie.feature.reminders.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.zhukoffsky.magpie.R
import com.zhukoffsky.magpie.feature.reminders.domain.RepeatRule
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import com.zhukoffsky.magpie.core.ui.appLocale

/**
 * Подпись под заголовком напоминания: «12 авг, 09:00 · по вторникам».
 *
 * Локаль берётся из конфигурации композиции, а не системная: иначе при
 * выбранном в приложении английском месяцы остались бы русскими.
 */
@Composable
fun dueLabel(dueAt: ZonedDateTime?, repeat: RepeatRule?): String {
    val locale = appLocale()
    val time = dueAt?.format(DateTimeFormatter.ofPattern("d MMM, HH:mm", locale))

    val repeatLabel = when (repeat) {
        null -> null
        RepeatRule.Daily -> stringResource(R.string.repeat_daily)
        is RepeatRule.Weekly -> stringResource(
            R.string.repeat_weekly,
            repeat.days.sorted().joinToString(", ") { it.getDisplayName(TextStyle.SHORT, locale) },
        )
    }

    val parts = listOfNotNull(time, repeatLabel)
    return if (parts.isEmpty()) stringResource(R.string.reminder_no_time) else parts.joinToString(" · ")
}
