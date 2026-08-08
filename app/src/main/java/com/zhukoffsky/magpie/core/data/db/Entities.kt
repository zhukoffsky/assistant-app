package com.zhukoffsky.magpie.core.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Состояние синхронизации записи с Google Tasks.
 *
 * Поля синхронизации заведены сразу, хотя сама синхронизация появится на
 * этапе 8 — чтобы не мигрировать схему БД задним числом.
 */
enum class SyncState {
    /** Существует только локально, выгрузка не требуется. */
    LOCAL_ONLY,

    /** Изменено локально, ждёт выгрузки. */
    PENDING_UPLOAD,

    /** Выгружено, локальная и удалённая версии совпадают. */
    SYNCED,

    /** Последняя попытка выгрузки не удалась. */
    ERROR,
}

/** Итог запланированного приёма лекарства. */
enum class IntakeStatus {
    /** Время ещё не наступило или пользователь не отреагировал. */
    PENDING,

    /** Принято. */
    TAKEN,

    /** Пропущено (явно или по истечении суток). */
    SKIPPED,

    /** Отложено, будильник перевзведён. */
    SNOOZED,
}

@Entity(tableName = "shopping_items")
data class ShoppingItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val isChecked: Boolean = false,
    /** Порядок в списке; меньше — выше. */
    val position: Int = 0,
    val createdAt: Instant,
    val checkedAt: Instant? = null,
    val remoteTaskId: String? = null,
    val syncState: SyncState = SyncState.LOCAL_ONLY,
)

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    /** Момент срабатывания. null — задача без конкретного времени. */
    val dueAt: Instant? = null,
    /** Правило повтора; null — одноразовое напоминание. */
    val repeatRule: String? = null,
    val isDone: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant,
    val remoteTaskId: String? = null,
    val syncState: SyncState = SyncState.LOCAL_ONLY,
)

/**
 * Курс приёма лекарства.
 *
 * [dosesMg] — цикл доз, повторяемый по кругу: `[100, 75]` означает
 * «через день 100 мг и 75 мг». Индекс дозы вычисляется из [startDate],
 * а не хранится счётчиком — см. `DoseCycle`.
 */
@Entity(tableName = "med_courses")
data class MedCourseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val dosesMg: List<Int>,
    /** Время ежедневного приёма. */
    val timeOfDay: LocalTime,
    /** Первый день курса; задаёт фазу цикла доз. */
    val startDate: LocalDate,
    val isActive: Boolean = true,
    val createdAt: Instant,
)

@Entity(
    tableName = "med_intakes",
    foreignKeys = [
        ForeignKey(
            entity = MedCourseEntity::class,
            parentColumns = ["id"],
            childColumns = ["courseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("courseId"), Index(value = ["courseId", "scheduledAt"], unique = true)],
)
data class MedIntakeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val courseId: Long,
    val scheduledAt: Instant,
    val takenAt: Instant? = null,
    val status: IntakeStatus = IntakeStatus.PENDING,
    /** Доза, зафиксированная на момент планирования приёма. */
    val doseMg: Int,
    val snoozeCount: Int = 0,
)
