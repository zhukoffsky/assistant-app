package com.zhukoffsky.magpie.core.data.db

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Конвертеры типов для Room.
 *
 * Время хранится в примитивах, а не в строках: `Instant` — миллисекунды
 * эпохи, `LocalDate` — номер дня эпохи, `LocalTime` — секунда от полуночи.
 * Так сравнения и сортировки работают прямо в SQL.
 */
class MagpieConverters {

    @TypeConverter
    fun instantToMillis(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun millisToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun localDateToEpochDay(value: LocalDate?): Long? = value?.toEpochDay()

    @TypeConverter
    fun epochDayToLocalDate(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)

    @TypeConverter
    fun localTimeToSecondOfDay(value: LocalTime?): Int? = value?.toSecondOfDay()

    @TypeConverter
    fun secondOfDayToLocalTime(value: Int?): LocalTime? = value?.let(LocalTime::ofSecondOfDay)

    @TypeConverter
    fun intListToString(value: List<Int>?): String? = value?.joinToString(separator = ",")

    @TypeConverter
    fun stringToIntList(value: String?): List<Int>? =
        value?.split(",")?.filter { it.isNotBlank() }?.map { it.trim().toInt() }

    @TypeConverter
    fun syncStateToString(value: SyncState?): String? = value?.name

    @TypeConverter
    fun stringToSyncState(value: String?): SyncState? = value?.let(SyncState::valueOf)

    @TypeConverter
    fun intakeStatusToString(value: IntakeStatus?): String? = value?.name

    @TypeConverter
    fun stringToIntakeStatus(value: String?): IntakeStatus? = value?.let(IntakeStatus::valueOf)
}
