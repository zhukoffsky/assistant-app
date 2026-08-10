package com.zhukoffsky.magpie.feature.reminders.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zhukoffsky.magpie.R
import com.zhukoffsky.magpie.feature.reminders.domain.Reminder
import java.time.ZoneId

@Composable
fun RemindersScreen(
    onVoiceInput: () -> Unit,
    viewModel: RemindersViewModel = viewModel(factory = RemindersViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        InputRow(
            value = state.input,
            onValueChange = viewModel::onInputChange,
            onSubmit = viewModel::onAddClick,
            onVoiceInput = onVoiceInput,
        )

        HorizontalDivider()

        if (state.reminders.isEmpty() && !state.isLoading) {
            EmptyState()
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(items = state.reminders, key = { it.id }) { reminder ->
                    SwipeableRow(
                        reminder = reminder,
                        onDoneChange = { done -> viewModel.onDoneChange(reminder, done) },
                        onDelete = { viewModel.onDelete(reminder) },
                    )
                }
            }
        }
    }
}

@Composable
private fun InputRow(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onVoiceInput: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            placeholder = { Text(stringResource(R.string.reminders_input_hint)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        )
        FilledIconButton(onClick = onSubmit) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.shopping_add))
        }
        FilledIconButton(onClick = onVoiceInput) {
            Icon(
                painter = painterResource(R.drawable.ic_mic),
                contentDescription = stringResource(R.string.voice_input),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableRow(
    reminder: Reminder,
    onDoneChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
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
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.shopping_delete),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        },
    ) {
        ReminderRow(reminder = reminder, onDoneChange = onDoneChange)
    }
}

@Composable
private fun ReminderRow(reminder: Reminder, onDoneChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = reminder.isDone, onCheckedChange = onDoneChange)
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            Text(
                text = reminder.title,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (reminder.isDone) TextDecoration.LineThrough else null,
                color = if (reminder.isDone) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                text = dueLabel(
                    dueAt = reminder.dueAt?.atZone(ZoneId.systemDefault()),
                    repeat = reminder.repeat,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
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
