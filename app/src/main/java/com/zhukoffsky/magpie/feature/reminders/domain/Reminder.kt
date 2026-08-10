package com.zhukoffsky.magpie.feature.reminders.domain

import java.time.Instant

data class Reminder(
    val id: Long,
    val title: String,
    /** Момент следующего срабатывания; null — задача без времени. */
    val dueAt: Instant?,
    val repeat: RepeatRule?,
    val isDone: Boolean,
)
