package com.zhukoffsky.magpie.core.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface ShoppingDao {

    @Query("SELECT * FROM shopping_items ORDER BY isChecked ASC, position ASC, id ASC")
    fun observeAll(): Flow<List<ShoppingItemEntity>>

    @Query("SELECT COALESCE(MAX(position), 0) FROM shopping_items")
    suspend fun maxPosition(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: ShoppingItemEntity): Long

    @Query("UPDATE shopping_items SET isChecked = :isChecked, checkedAt = :checkedAt WHERE id = :id")
    suspend fun setChecked(id: Long, isChecked: Boolean, checkedAt: Instant?)

    @Query("DELETE FROM shopping_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM shopping_items WHERE isChecked = 1")
    suspend fun deleteChecked()
}

@Dao
interface ReminderDao {

    @Query("SELECT * FROM reminders ORDER BY isDone ASC, dueAt IS NULL, dueAt ASC, id ASC")
    fun observeAll(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun byId(id: Long): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE isDone = 0 AND dueAt IS NOT NULL")
    suspend fun pendingScheduled(): List<ReminderEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(reminder: ReminderEntity): Long

    @Query("UPDATE reminders SET isDone = :isDone, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setDone(id: Long, isDone: Boolean, updatedAt: Instant)

    @Query("UPDATE reminders SET dueAt = :dueAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setDueAt(id: Long, dueAt: Instant?, updatedAt: Instant)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM reminders WHERE syncState != 'SYNCED' LIMIT :limit")
    suspend fun pendingSync(limit: Int): List<ReminderEntity>

    @Query("UPDATE reminders SET syncState = :syncState WHERE id = :id")
    suspend fun setSyncState(id: Long, syncState: SyncState)

    @Query("UPDATE reminders SET remoteTaskId = :remoteTaskId, syncState = :syncState WHERE id = :id")
    suspend fun setRemoteId(id: Long, remoteTaskId: String?, syncState: SyncState)

    /** При первом включении синхронизации выгрузить нужно всё накопленное. */
    @Query("UPDATE reminders SET syncState = 'PENDING_UPLOAD' WHERE syncState = 'LOCAL_ONLY'")
    suspend fun markEverythingForUpload()
}

@Dao
interface MedDao {

    @Query("SELECT * FROM med_courses ORDER BY isActive DESC, id ASC")
    fun observeCourses(): Flow<List<MedCourseEntity>>

    @Query("SELECT * FROM med_courses WHERE isActive = 1 LIMIT 1")
    suspend fun activeCourse(): MedCourseEntity?

    @Query("SELECT * FROM med_courses WHERE isActive = 1 LIMIT 1")
    fun observeActiveCourse(): Flow<MedCourseEntity?>

    @Upsert
    suspend fun upsertCourse(course: MedCourseEntity): Long

    @Delete
    suspend fun deleteCourse(course: MedCourseEntity)

    @Query(
        """
        SELECT * FROM med_intakes
        WHERE courseId = :courseId AND scheduledAt >= :from
        ORDER BY scheduledAt DESC
        """,
    )
    fun observeIntakes(courseId: Long, from: Instant): Flow<List<MedIntakeEntity>>

    @Query("SELECT * FROM med_intakes WHERE courseId = :courseId AND scheduledAt = :scheduledAt")
    suspend fun intakeAt(courseId: Long, scheduledAt: Instant): MedIntakeEntity?

    @Upsert
    suspend fun upsertIntake(intake: MedIntakeEntity): Long
}
