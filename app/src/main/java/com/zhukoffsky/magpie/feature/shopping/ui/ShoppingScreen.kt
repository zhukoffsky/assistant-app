package com.zhukoffsky.magpie.feature.shopping.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zhukoffsky.magpie.R
import com.zhukoffsky.magpie.core.ui.GroupPosition
import com.zhukoffsky.magpie.core.ui.ListEntrance
import com.zhukoffsky.magpie.core.ui.MagpieAlertDialog
import com.zhukoffsky.magpie.core.ui.MagpieCheckbox
import com.zhukoffsky.magpie.core.ui.MagpieTopBar
import com.zhukoffsky.magpie.core.ui.fadingTopEdge
import com.zhukoffsky.magpie.core.ui.glassGroupSegment
import com.zhukoffsky.magpie.core.ui.MagpieInputBar
import com.zhukoffsky.magpie.core.ui.rememberListEntrance
import com.zhukoffsky.magpie.core.ui.staggeredEntrance
import com.zhukoffsky.magpie.core.ui.theme.MagpieTheme
import com.zhukoffsky.magpie.feature.shopping.domain.ShoppingCategory
import com.zhukoffsky.magpie.feature.shopping.domain.ShoppingGroup
import com.zhukoffsky.magpie.feature.shopping.domain.ShoppingItem

@Composable
fun ShoppingScreen(
    onVoiceInput: () -> Unit,
    viewModel: ShoppingViewModel = viewModel(factory = ShoppingViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ShoppingScreenContent(
        state = state,
        onInputChange = viewModel::onInputChange,
        onAddClick = viewModel::onAddClick,
        onVoiceInput = onVoiceInput,
        onCheckedChange = viewModel::onCheckedChange,
        onDelete = viewModel::onDelete,
        onClearChecked = viewModel::onClearChecked,
        onClearAll = viewModel::onClearAll,
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
    onClearAll: () -> Unit,
) {
    val listState = rememberLazyListState()
    var confirmClearAll by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        MagpieTopBar(
            title = stringResource(R.string.nav_shopping),
            actions = {
                if (state.items.isNotEmpty()) {
                    TextButton(
                        onClick = { confirmClearAll = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MagpieTheme.colors.ink2),
                    ) { Text(stringResource(R.string.shopping_clear_all)) }
                }
            },
        )

        if (confirmClearAll) {
            MagpieAlertDialog(
                onDismissRequest = { confirmClearAll = false },
                title = { Text(stringResource(R.string.shopping_clear_all_title)) },
                text = { Text(stringResource(R.string.shopping_clear_all_text)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirmClearAll = false
                            onClearAll()
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text(stringResource(R.string.shopping_clear_all)) }
                },
                dismissButton = {
                    TextButton(
                        onClick = { confirmClearAll = false },
                        colors = ButtonDefaults.textButtonColors(contentColor = MagpieTheme.colors.ink2),
                    ) { Text(stringResource(R.string.picker_cancel)) }
                },
            )
        }

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
                    .padding(start = 22.dp, end = 10.dp),
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

        if (state.items.isEmpty() && !state.isLoading) {
            EmptyState()
        } else {
            /*
             * Отдел — одна стеклянная карточка, позиции внутри через
             * разделители. Раньше каждая позиция была отдельным стёклышком
             * в 78dp с зазором: пять позиций занимали полэкрана, а список
             * покупок — ровно то место, где плотность важнее всего.
             */
            val groups = if (state.groupByCategory) {
                state.groups
            } else {
                listOf(ShoppingGroup(ShoppingCategory.OTHER, state.items))
            }
            // Заголовок нужен, только когда отделов больше одного: одинокое
            // «Прочее» над всем списком ничего не сообщает.
            val showHeaders = state.groupByCategory && groups.size > 1
            val entrance = rememberListEntrance()

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .fadingTopEdge(scrolled = listState.canScrollBackward),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 12.dp),
            ) {
                /*
                 * Сквозная нумерация через все отделы, а не своя в каждом:
                 * каскадное появление списка считает по ней задержку, и с
                 * нумерацией по группам строки въезжали бы пачками, как будто
                 * список моргает.
                 */
                var index = 0
                groups.forEachIndexed { groupIndex, (category, items) ->
                    val groupKey = category?.name ?: "bought"
                    if (showHeaders) {
                        item(key = "category-$groupKey") {
                            CategoryHeader(category, first = groupIndex == 0)
                        }
                    } else if (groupIndex > 0) {
                        item(key = "gap-$groupKey") { Spacer(Modifier.height(12.dp)) }
                    }
                    itemsIndexed(items = items, key = { _, item -> item.id }) { position, item ->
                        SwipeableRow(
                            item = item,
                            index = index++,
                            entrance = entrance,
                            position = GroupPosition.of(position, items.size),
                            onCheckedChange = { checked -> onCheckedChange(item, checked) },
                            onDelete = { onDelete(item) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

/** Заголовок отдела: только текст, без стекла — это не строка списка. Без отдела — купленное. */
@Composable
private fun CategoryHeader(category: ShoppingCategory?, first: Boolean) {
    Text(
        text = stringResource(category?.labelRes ?: R.string.shopping_bought_header),
        style = MaterialTheme.typography.labelLarge,
        color = MagpieTheme.colors.ink2,
        modifier = Modifier.padding(start = 10.dp, top = if (first) 4.dp else 18.dp, bottom = 8.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableRow(
    item: ShoppingItem,
    index: Int,
    entrance: ListEntrance,
    position: GroupPosition,
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
        // Кусок карточки висит на самой коробке, а не на строке: строку
        // тянут, а карточка остаётся на месте, и корзина видна на её фоне.
        modifier = modifier
            .staggeredEntrance(index, entrance)
            .glassGroupSegment(position, MagpieTheme.colors),
        backgroundContent = {
            // Корзина рисуется только пока строку тянут: в покое строка
            // прозрачная, и постоянная корзина просвечивала бы сквозь неё.
            if (dismissState.dismissDirection != SwipeToDismissBoxValue.Settled) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_delete),
                        contentDescription = stringResource(R.string.shopping_delete),
                        tint = MaterialTheme.colorScheme.error,
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 16.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MagpieCheckbox(checked = item.isChecked, onCheckedChange = onCheckedChange)
        Text(
            text = item.title,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 10.dp),
            style = MaterialTheme.typography.bodyLarge,
            textDecoration = if (item.isChecked) TextDecoration.LineThrough else null,
            color = if (item.isChecked) MagpieTheme.colors.ink3 else MagpieTheme.colors.ink,
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
            color = MagpieTheme.colors.ink2,
            textAlign = TextAlign.Center,
        )
    }
}
