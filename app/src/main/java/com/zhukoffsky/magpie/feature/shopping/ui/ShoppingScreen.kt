package com.zhukoffsky.magpie.feature.shopping.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zhukoffsky.magpie.R
import com.zhukoffsky.magpie.core.ui.UndoDeleteEffect
import com.zhukoffsky.magpie.feature.shopping.domain.ShoppingItem

@Composable
fun ShoppingScreen(
    onVoiceInput: () -> Unit,
    viewModel: ShoppingViewModel = viewModel(factory = ShoppingViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    UndoDeleteEffect(
        deleted = viewModel.undoDelete.collectAsStateWithLifecycle().value,
        onUndo = viewModel::onUndoDelete,
        onDismiss = viewModel::onUndoDismissed,
    )

    ShoppingScreenContent(
        state = state,
        onInputChange = viewModel::onInputChange,
        onAddClick = viewModel::onAddClick,
        onVoiceInput = onVoiceInput,
        onCheckedChange = viewModel::onCheckedChange,
        onDelete = viewModel::onDelete,
        onClearChecked = viewModel::onClearChecked,
    )
}

@Composable
private fun ShoppingScreenContent(
    state: ShoppingUiState,
    onInputChange: (String) -> Unit,
    onAddClick: () -> Unit,
    onVoiceInput: () -> Unit,
    onCheckedChange: (ShoppingItem, Boolean) -> Unit,
    onDelete: (ShoppingItem) -> Unit,
    onClearChecked: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        InputRow(
            value = state.input,
            onValueChange = onInputChange,
            onSubmit = onAddClick,
            onVoiceInput = onVoiceInput,
        )

        if (state.checkedCount > 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.shopping_checked_count, state.checkedCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onClearChecked) {
                    Text(stringResource(R.string.shopping_clear_checked))
                }
            }
        }

        HorizontalDivider()

        if (state.items.isEmpty() && !state.isLoading) {
            EmptyState()
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(items = state.items, key = { it.id }) { item ->
                    SwipeableRow(
                        item = item,
                        onCheckedChange = { checked -> onCheckedChange(item, checked) },
                        onDelete = { onDelete(item) },
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
            placeholder = { Text(stringResource(R.string.shopping_input_hint)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            // Клавиатура намеренно не скрывается: подряд идущие покупки
            // удобнее добавлять не закрывая её.
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
    item: ShoppingItem,
    onCheckedChange: (Boolean) -> Unit,
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
        ItemRow(item = item, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ItemRow(item: ShoppingItem, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = item.isChecked, onCheckedChange = onCheckedChange)
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyLarge,
            textDecoration = if (item.isChecked) TextDecoration.LineThrough else null,
            color = if (item.isChecked) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
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
            text = stringResource(R.string.shopping_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
