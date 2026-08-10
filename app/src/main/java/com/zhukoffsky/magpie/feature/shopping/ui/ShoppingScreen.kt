package com.zhukoffsky.magpie.feature.shopping.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zhukoffsky.magpie.R
import com.zhukoffsky.magpie.core.ui.GlassSurface
import com.zhukoffsky.magpie.core.ui.UndoDeleteEffect
import com.zhukoffsky.magpie.core.ui.staggeredEntrance
import com.zhukoffsky.magpie.core.ui.theme.MagpieRadius
import com.zhukoffsky.magpie.core.ui.theme.MagpieTheme
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
                    .padding(horizontal = 22.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.shopping_checked_count, state.checkedCount),
                    style = MaterialTheme.typography.labelMedium,
                    color = MagpieTheme.colors.ink2,
                )
                TextButton(onClick = onClearChecked) {
                    Text(
                        text = stringResource(R.string.shopping_clear_checked),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }

        // Разделителя нет намеренно: каждая позиция — отдельное стёклышко,
        // и линия между ними спорила бы с их собственными рамками.

        if (state.items.isEmpty() && !state.isLoading) {
            EmptyState()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(items = state.items, key = { _, item -> item.id }) { index, item ->
                    SwipeableRow(
                        item = item,
                        index = index,
                        onCheckedChange = { checked -> onCheckedChange(item, checked) },
                        onDelete = { onDelete(item) },
                        modifier = Modifier.animateItem(),
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
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        GlassSurface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(MagpieRadius.md),
        ) {
            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = {
                    Text(
                        text = stringResource(R.string.shopping_input_hint),
                        color = MagpieTheme.colors.ink3,
                    )
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                // Клавиатура намеренно не скрывается: подряд идущие покупки
                // удобнее добавлять не закрывая её.
                keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                // Поле рисует только текст: фон и рамку даёт стекло вокруг,
                // иначе получилось бы две рамки одна в другой.
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }
        RoundActionButton(
            onClick = onSubmit,
            contentDescription = stringResource(R.string.shopping_add),
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
        }
        RoundActionButton(
            onClick = onVoiceInput,
            contentDescription = stringResource(R.string.voice_input),
        ) {
            Icon(painter = painterResource(R.drawable.ic_mic), contentDescription = null)
        }
    }
}

/** Акцентная кнопка-квадратик со скруглением — «плюс» и микрофон. */
@Composable
private fun RoundActionButton(
    onClick: () -> Unit,
    contentDescription: String,
    content: @Composable () -> Unit,
) {
    FilledIconButton(
        onClick = onClick,
        modifier = Modifier
            .size(52.dp)
            .semantics { this.contentDescription = contentDescription },
        shape = RoundedCornerShape(MagpieRadius.sm),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableRow(
    item: ShoppingItem,
    index: Int,
    onCheckedChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
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
        modifier = modifier.then(staggeredEntrance(index)),
        backgroundContent = {
            /*
             * Корзина рисуется только пока строку тянут. Раньше её можно было
             * держать всегда: непрозрачная строка её закрывала. Стекло —
             * полупрозрачное, и подложка стала просвечивать сквозь каждую
             * строку, из-за чего список выглядел «уже удалённым».
             */
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
        ItemRow(item = item, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ItemRow(item: ShoppingItem, onCheckedChange: (Boolean) -> Unit) {
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(MagpieRadius.md),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = item.isChecked,
                onCheckedChange = onCheckedChange,
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    checkmarkColor = MaterialTheme.colorScheme.onPrimary,
                    uncheckedColor = MagpieTheme.colors.ink3,
                ),
            )
            Text(
                text = item.title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (item.isChecked) TextDecoration.LineThrough else null,
                color = if (item.isChecked) MagpieTheme.colors.ink3 else MagpieTheme.colors.ink,
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
            text = stringResource(R.string.shopping_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MagpieTheme.colors.ink2,
            textAlign = TextAlign.Center,
        )
    }
}
