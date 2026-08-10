package com.zhukoffsky.magpie.feature.meds.domain

import com.zhukoffsky.magpie.core.data.db.IntakeStatus
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Курс приёма лекарства: один приём в сутки в фиксированное время,
 * доза берётся из цикла [dosesMg].
 */
data class MedCourse(
    val id: Long,
    val name: String,
    val dosesMg: List<Int>,
    val timeOfDay: LocalTime,
    val startDate: LocalDate,
    val isActive: Boolean,
)

data class MedIntake(
    val id: Long,
    val courseId: Long,
    val scheduledAt: Instant,
    val takenAt: Instant?,
    val status: IntakeStatus,
    val doseMg: Int,
    val snoozeCount: Int,
)

/** Строка истории: запланированный приём и его судьба, если она известна. */
data class DoseDay(
    val date: LocalDate,
    val doseMg: Int,
    val status: IntakeStatus,
    val takenAt: Instant?,
)
