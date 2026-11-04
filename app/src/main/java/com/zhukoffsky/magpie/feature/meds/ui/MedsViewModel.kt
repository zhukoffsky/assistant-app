package com.zhukoffsky.magpie.feature.meds.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zhukoffsky.magpie.MagpieApp
import com.zhukoffsky.magpie.core.data.db.DoseUnit
import com.zhukoffsky.magpie.core.data.db.IntakeStatus
import com.zhukoffsky.magpie.feature.meds.data.MedRepository
import com.zhukoffsky.magpie.feature.meds.domain.DoseCycle
import com.zhukoffsky.magpie.feature.meds.domain.DoseDay
import com.zhukoffsky.magpie.feature.meds.domain.MedCourse
import com.zhukoffsky.magpie.feature.meds.domain.MedIntake
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

/** Курс на экране: сам курс, сегодняшний приём и история. */
data class CourseUi(
    val course: MedCourse,
    val todayDoseMg: Int?,
    val todayStatus: IntakeStatus,
    val history: List<DoseDay>,
)

data class MedsUiState(
    val courses: List<CourseUi> = emptyList(),
    val isLoading: Boolean = true,
) {
    fun course(id: Long): CourseUi? = courses.firstOrNull { it.course.id == id }
}

class MedsViewModel(
    private val repository: MedRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<MedsUiState> = repository.observeCourses()
        .flatMapLatest { courses ->
            if (courses.isEmpty()) {
                flowOf(MedsUiState(isLoading = false))
            } else {
                val since = clock.instant().minus(Duration.ofDays(HISTORY_DAYS))
                combine(
                    courses.map { course ->
                        repository.observeIntakes(course.id, since).map { intakes -> courseUi(course, intakes) }
                    },
                ) { list -> MedsUiState(courses = list.toList(), isLoading = false) }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = MedsUiState(),
        )

    private fun courseUi(course: MedCourse, intakes: List<MedIntake>): CourseUi {
        val history = repository.historyFor(course, intakes, HISTORY_DAYS)
        val today = today()

        return CourseUi(
            course = course,
            todayDoseMg = DoseCycle.doseFor(course, today),
            todayStatus = history.firstOrNull { it.date == today }?.status ?: IntakeStatus.PENDING,
            history = history,
        )
    }

    private fun today(): LocalDate = clock.instant().atZone(clock.zone).toLocalDate()

    /**
     * @param id 0 — новый курс.
     * @return false, если ввод не разобрался; форма тогда остаётся открытой.
     */
    fun onSaveCourse(
        id: Long,
        name: String,
        doses: String,
        timeOfDay: LocalTime,
        startDate: LocalDate,
        unit: DoseUnit,
    ): Boolean {
        val parsedDoses = parseDoses(doses) ?: return false
        if (name.isBlank()) return false

        viewModelScope.launch {
            repository.saveCourse(
                id = id,
                name = name,
                dosesMg = parsedDoses,
                timeOfDay = timeOfDay,
                startDate = startDate,
                unit = unit,
            )
        }
        return true
    }

    fun onTakenToday(courseId: Long) {
        val course = uiState.value.course(courseId)?.course ?: return
        viewModelScope.launch {
            repository.markTaken(course.id, DoseCycle.scheduledAt(course, today(), clock.zone))
        }
    }

    fun onTakenOn(courseId: Long, date: LocalDate) {
        viewModelScope.launch { repository.markTakenOn(courseId, date) }
    }

    fun onSnooze(courseId: Long, minutes: Long) {
        val course = uiState.value.course(courseId)?.course ?: return
        viewModelScope.launch {
            repository.snooze(
                courseId = course.id,
                scheduledAt = DoseCycle.scheduledAt(course, today(), clock.zone),
                delay = Duration.ofMinutes(minutes),
            )
        }
    }

    fun onDeleteCourse(courseId: Long) {
        val course = uiState.value.course(courseId)?.course ?: return
        viewModelScope.launch { repository.deleteCourse(course) }
    }

    companion object {
        const val HISTORY_DAYS = 30L
        val SNOOZE_OPTIONS = listOf(5L, 10L, 15L, 30L, 60L)

        private const val STOP_TIMEOUT_MS = 5_000L

        /** «100, 75» → [100, 75]. Пустой или нечисловой ввод отвергается. */
        fun parseDoses(raw: String): List<Int>? {
            val values = raw.split(",", ";")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { it.toIntOrNull() ?: return null }

            return values.takeIf { it.isNotEmpty() && it.all { dose -> dose > 0 } }
        }

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MagpieApp
                MedsViewModel(app.container.medRepository)
            }
        }
    }
}
