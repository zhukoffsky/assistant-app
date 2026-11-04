package com.zhukoffsky.magpie.core.data.db

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant

class FakeMedDao : MedDao {

    val courses = MutableStateFlow<List<MedCourseEntity>>(emptyList())
    val intakes = MutableStateFlow<List<MedIntakeEntity>>(emptyList())

    private var nextCourseId = 1L
    private var nextIntakeId = 1L

    private fun active(list: List<MedCourseEntity>) =
        list.filter { it.isActive }.sortedWith(compareBy({ it.timeOfDay }, { it.id }))

    override fun observeActiveCourses(): Flow<List<MedCourseEntity>> = courses.map(::active)

    override suspend fun activeCourses(): List<MedCourseEntity> = active(courses.value)

    override suspend fun activeCourse(id: Long): MedCourseEntity? =
        courses.value.firstOrNull { it.id == id && it.isActive }

    override suspend fun upsertCourse(course: MedCourseEntity): Long {
        val id = if (course.id == 0L) nextCourseId++ else course.id
        courses.value = courses.value.filterNot { it.id == id } + course.copy(id = id)
        // Как Room: при обновлении существующей строки `@Upsert` отдаёт -1.
        return if (course.id == 0L) id else -1
    }

    override suspend fun deleteCourse(course: MedCourseEntity) {
        courses.value = courses.value.filterNot { it.id == course.id }
        intakes.value = intakes.value.filterNot { it.courseId == course.id }
    }

    override fun observeIntakes(courseId: Long, from: Instant): Flow<List<MedIntakeEntity>> =
        intakes.map { list ->
            list.filter { it.courseId == courseId && !it.scheduledAt.isBefore(from) }
                .sortedByDescending { it.scheduledAt }
        }

    override suspend fun snoozedIntakes(): List<MedIntakeEntity> =
        intakes.value
            .filter { it.status == IntakeStatus.SNOOZED && it.snoozedUntil != null }
            .sortedByDescending { it.snoozedUntil!! }

    override suspend fun intakeAt(courseId: Long, scheduledAt: Instant): MedIntakeEntity? =
        intakes.value.firstOrNull { it.courseId == courseId && it.scheduledAt == scheduledAt }

    override suspend fun upsertIntake(intake: MedIntakeEntity): Long {
        val id = if (intake.id == 0L) nextIntakeId++ else intake.id
        intakes.value = intakes.value.filterNot { it.id == id } + intake.copy(id = id)
        return id
    }
}
