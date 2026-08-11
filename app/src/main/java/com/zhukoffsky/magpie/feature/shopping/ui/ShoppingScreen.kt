package com.zhukoffsky.magpie.feature.shopping.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zhukoffsky.magpie.R
import com.zhukoffsky.magpie.core.ui.GlassSurface
import com.zhukoffsky.magpie.core.ui.MagpieInputBar
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
        MagpieInputBar(
            value = state.input,
            onValueChange = onInputChange,
            onSubmit = onAddClick,
            onVoiceInput = onVoiceInput,
            placeholder = stringResource(R.string.shopping_input_hint),
            addContentDescription = stringResource(R.string.shopping_add),
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
