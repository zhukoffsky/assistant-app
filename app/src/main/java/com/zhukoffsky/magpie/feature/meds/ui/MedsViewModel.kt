package com.zhukoffsky.magpie.feature.meds.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zhukoffsky.magpie.MagpieApp
import com.zhukoffsky.magpie.core.data.db.IntakeStatus
import com.zhukoffsky.magpie.feature.meds.data.MedRepository
import com.zhukoffsky.magpie.feature.meds.domain.DoseCycle
import com.zhukoffsky.magpie.feature.meds.domain.DoseDay
import com.zhukoffsky.magpie.feature.meds.domain.MedCourse
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

data class MedsUiState(
    val course: MedCourse? = null,
    val todayDoseMg: Int? = null,
    val todayStatus: IntakeStatus = IntakeStatus.PENDING,
    val history: List<DoseDay> = emptyList(),
    val isLoading: Boolean = true,
)

class MedsViewModel(
    private val repository: MedRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<MedsUiState> = repository.observeCourse()
        .flatMapLatest { course ->
            if (course == null) {
                flowOf(MedsUiState(isLoading = false))
            } else {
                val since = clock.instant().minus(Duration.ofDays(HISTORY_DAYS))
                combine(
                    flowOf(course),
                    repository.observeIntakes(course.id, since),
                ) { activeCourse, intakes ->
                    val history = repository.historyFor(activeCourse, intakes, HISTORY_DAYS)
                    val today = clock.instant().atZone(clock.zone).toLocalDate()

                    MedsUiState(
                        course = activeCourse,
                        todayDoseMg = DoseCycle.doseFor(activeCourse, today),
                        todayStatus = history.firstOrNull { it.date == today }?.status
                            ?: IntakeStatus.PENDING,
                        history = history,
                        isLoading = false,
                    )
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = MedsUiState(),
        )

    fun onSaveCourse(name: String, doses: String, time: String, startDate: String): Boolean {
        val parsedDoses = parseDoses(doses) ?: return false
        val parsedTime = parseTime(time) ?: return false
        val parsedDate = parseDate(startDate) ?: return false

        viewModelScope.launch {
            repository.saveCourse(
                id = uiState.value.course?.id ?: 0,
                name = name,
                dosesMg = parsedDoses,
                timeOfDay = parsedTime,
                startDate = parsedDate,
            )
        }
        return true
    }

    fun onTakenToday() {
        val course = uiState.value.course ?: return
        viewModelScope.launch {
            repository.markTaken(
                DoseCycle.scheduledAt(course, clock.instant().atZone(clock.zone).toLocalDate(), clock.zone),
            )
        }
    }

    fun onTakenOn(date: LocalDate) {
        viewModelScope.launch { repository.markTakenOn(date) }
    }

    fun onSnooze(minutes: Long) {
        val course = uiState.value.course ?: return
        viewModelScope.launch {
            repository.snooze(
                scheduledAt = DoseCycle.scheduledAt(
                    course,
                    clock.instant().atZone(clock.zone).toLocalDate(),
                    clock.zone,
                ),
                delay = Duration.ofMinutes(minutes),
            )
        }
    }

    fun onDeleteCourse() {
        val course = uiState.value.course ?: return
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

        fun parseTime(raw: String): LocalTime? = runCatching {
            val (hour, minute) = raw.trim().split(":", ".").map { it.trim().toInt() }
            LocalTime.of(hour, minute)
        }.getOrNull()

        fun parseDate(raw: String): LocalDate? = runCatching { LocalDate.parse(raw.trim()) }.getOrNull()

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MagpieApp
                MedsViewModel(app.container.medRepository)
            }
        }
    }
}
