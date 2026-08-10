package com.zhukoffsky.magpie.feature.reminders.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zhukoffsky.magpie.MagpieApp
import com.zhukoffsky.magpie.feature.reminders.data.ReminderRepository
import com.zhukoffsky.magpie.feature.reminders.domain.Reminder
import com.zhukoffsky.magpie.feature.reminders.domain.PhraseParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.ZonedDateTime

data class RemindersUiState(
    val input: String = "",
    val reminders: List<Reminder> = emptyList(),
    val isLoading: Boolean = true,
)

class RemindersViewModel(
    private val repository: ReminderRepository,
    private val parser: PhraseParser,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {

    private val input = MutableStateFlow("")

    val uiState: StateFlow<RemindersUiState> =
        combine(input, repository.observeAll()) { text, reminders ->
            RemindersUiState(input = text, reminders = reminders, isLoading = false)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = RemindersUiState(),
        )

    fun onInputChange(value: String) = input.update { value }

    /**
     * Набранный текст разбирается тем же парсером, что и надиктованный:
     * «завтра в 9 позвонить» с клавиатуры даёт тот же результат, что и
     * голосом.
     */
    fun onAddClick() {
        val phrase = input.value.trim()
        if (phrase.isEmpty()) return
        input.value = ""

        viewModelScope.launch {
            val parsed = parser.parse(phrase, ZonedDateTime.now(clock)) ?: return@launch
            repository.add(
                title = parsed.title,
                dueAt = parsed.dueAt.toInstant(),
                repeat = parsed.repeat,
            )
        }
    }

    fun onDoneChange(reminder: Reminder, isDone: Boolean) {
        viewModelScope.launch { repository.setDone(reminder.id, isDone) }
    }

    private val _undoDelete = MutableStateFlow<Reminder?>(null)
    val undoDelete: StateFlow<Reminder?> = _undoDelete.asStateFlow()

    fun onDelete(reminder: Reminder) {
        viewModelScope.launch {
            repository.delete(reminder.id)
            _undoDelete.value = reminder
        }
    }

    fun onUndoDelete() {
        val reminder = _undoDelete.value ?: return
        _undoDelete.value = null
        viewModelScope.launch {
            repository.add(reminder.title, reminder.dueAt, reminder.repeat)
        }
    }

    fun onUndoDismissed() {
        _undoDelete.value = null
    }

    fun onEdit(reminder: Reminder, title: String, dueAt: ZonedDateTime) {
        viewModelScope.launch { repository.update(reminder.id, title, dueAt.toInstant()) }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container =
                    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MagpieApp).container
                RemindersViewModel(container.reminderRepository, container.phraseParser)
            }
        }
    }
}
