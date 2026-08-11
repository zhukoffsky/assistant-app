package com.zhukoffsky.magpie.feature.reminders.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zhukoffsky.magpie.R
import com.zhukoffsky.magpie.core.ui.DatePickerDialog
import com.zhukoffsky.magpie.core.ui.GlassSurface
import com.zhukoffsky.magpie.core.ui.MagpieInputBar
import com.zhukoffsky.magpie.core.ui.TimePickerDialog
import com.zhukoffsky.magpie.core.ui.UndoDeleteEffect
import com.zhukoffsky.magpie.core.ui.appLocale
import com.zhukoffsky.magpie.core.ui.staggeredEntrance
import com.zhukoffsky.magpie.core.ui.theme.MagpieRadius
import com.zhukoffsky.magpie.core.ui.theme.MagpieTheme
import com.zhukoffsky.magpie.feature.reminders.domain.Reminder
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun RemindersScreen(
    onVoiceInput: () -> Unit,
    viewModel: RemindersViewModel = viewModel(factory = RemindersViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Reminder?>(null) }

    UndoDeleteEffect(
        deleted = viewModel.undoDelete.collectAsStateWithLifecycle().value,
        onUndo = viewModel::onUndoDelete,
        onDismiss = viewModel::onUndoDismissed,
    )

    editing?.let { reminder ->
        EditReminderDialog(
            reminder = reminder,
            onDismiss = { editing = null },
            onConfirm = { title, dueAt ->
                viewModel.onEdit(reminder, title, dueAt)
                editing = null
            },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        MagpieInputBar(
            value = state.input,
            onValueChange = viewModel::onInputChange,
            onSubmit = viewModel::onAddClick,
            onVoiceInput = onVoiceInput,
            placeholder = stringResource(R.string.reminders_input_hint),
            addContentDescription = stringResource(R.string.shopping_add),
        )

        if (state.reminders.isEmpty() && !state.isLoading) {
            EmptyState()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(
                    items = state.reminders,
                    key = { _, reminder -> reminder.id },
                ) { index, reminder ->
                    SwipeableRow(
                        reminder = reminder,
                        index = index,
                        onDoneChange = { done -> viewModel.onDoneChange(reminder, done) },
                        onDelete = { viewModel.onDelete(reminder) },
                        onEdit = { editing = reminder },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableRow(
    reminder: Reminder,
    index: Int,
    onDoneChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            val dismissed = value != SwipeToDismissBoxValue.Settled
            if (dismissed) onDelete()
            dismissed
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier.staggeredEntrance(index),
        backgroundContent = {
            // Корзина видна только пока строку тянут: сквозь стекло
            // постоянная подложка просвечивала бы на каждой строке.
            if (dismissState.dismissDirection != SwipeToDismissBoxValue.Settled) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.shopping_delete),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
    ) {
        ReminderRow(reminder = reminder, onDoneChange = onDoneChange, onEdit = onEdit)
    }
}

@Composable
private fun ReminderRow(
    reminder: Reminder,
    onDoneChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
) {
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(MagpieRadius.md),
    ) {
      Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = reminder.isDone,
            onCheckedChange = onDoneChange,
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.primary,
                checkmarkColor = MaterialTheme.colorScheme.onPrimary,
                uncheckedColor = MagpieTheme.colors.ink3,
            ),
        )
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            Text(
                text = reminder.title,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (reminder.isDone) TextDecoration.LineThrough else null,
                color = if (reminder.isDone) MagpieTheme.colors.ink3 else MagpieTheme.colors.ink,
            )
            Text(
                text = dueLabel(
                    dueAt = reminder.dueAt?.atZone(ZoneId.systemDefault()),
                    repeat = reminder.repeat,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MagpieTheme.colors.ink2,
            )
        }
      }
    }
}

/**
 * Правка напоминания.
 *
 * Разбор фразы ошибается, а без этого диалога единственным лекарством было
 * удалить запись и надиктовать заново.
 */
@Composable
private fun EditReminderDialog(
    reminder: Reminder,
    onDismiss: () -> Unit,
    onConfirm: (title: String, dueAt: ZonedDateTime) -> Unit,
) {
    val zone = ZoneId.systemDefault()
    var title by remember { mutableStateOf(reminder.title) }
    var moment by remember {
        mutableStateOf(reminder.dueAt?.atZone(zone) ?: ZonedDateTime.now(zone))
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        DatePickerDialog(
            initial = moment.toLocalDate(),
            onDismiss = { showDatePicker = false },
            onConfirm = { date ->
                moment = moment.withYear(date.year).withDayOfYear(date.dayOfYear)
                showDatePicker = false
            },
        )
    }

    if (showTimePicker) {
        TimePickerDialog(
            initial = moment.toLocalTime(),
            onDismiss = { showTimePicker = false },
            onConfirm = { time ->
                moment = moment.withHour(time.hour).withMinute(time.minute)
                showTimePicker = false
            },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reminder_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.reminder_edit_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { showDatePicker = true }) {
                        Text(moment.format(DateTimeFormatter.ofPattern("d MMM yyyy", appLocale())))
                    }
                    TextButton(onClick = { showTimePicker = true }) {
                        Text(moment.format(DateTimeFormatter.ofPattern("HH:mm", appLocale())))
                    }
                }
                if (reminder.repeat != null) {
                    Text(
                        text = stringResource(R.string.reminder_edit_repeat_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(title, moment) }) {
                Text(stringResource(R.string.picker_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.picker_cancel)) }
        },
    )
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.reminders_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
