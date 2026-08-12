package com.zhukoffsky.magpie.feature.shopping.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zhukoffsky.magpie.MagpieApp
import com.zhukoffsky.magpie.feature.shopping.data.ShoppingRepository
import com.zhukoffsky.magpie.core.settings.ShoppingPreferences
import com.zhukoffsky.magpie.feature.shopping.domain.ShoppingCategory
import com.zhukoffsky.magpie.feature.shopping.domain.ShoppingItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ShoppingUiState(
    val input: String = "",
    val items: List<ShoppingItem> = emptyList(),
    val isLoading: Boolean = true,
    /** Показывать список отделами. Выключено — плоский список, как было. */
    val groupByCategory: Boolean = false,
) {
    val checkedCount: Int get() = items.count { it.isChecked }

    /**
     * Список, разложенный по отделам в порядке обхода магазина.
     *
     * Порядок внутри отдела — тот же, что и в плоском списке: сначала
     * некупленное, потом отмеченное. Пустые отделы не показываются, «Прочее»
     * оказывается последним само — по порядку объявления в перечислении.
     */
    val groups: List<Pair<ShoppingCategory, List<ShoppingItem>>>
        get() = ShoppingCategory.entries
            .mapNotNull { category ->
                items.filter { it.category == category }
                    .takeIf { it.isNotEmpty() }
                    ?.let { category to it }
            }
}

class ShoppingViewModel(
    private val repository: ShoppingRepository,
    preferences: ShoppingPreferences,
) : ViewModel() {

    private val input = MutableStateFlow("")

    val uiState: StateFlow<ShoppingUiState> =
        combine(
            input,
            repository.observeItems(),
            preferences.groupByCategory,
        ) { text, items, grouped ->
            ShoppingUiState(
                input = text,
                items = items,
                isLoading = false,
                groupByCategory = grouped,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = ShoppingUiState(),
        )

    fun onInputChange(value: String) = input.update { value }

    fun onAddClick() {
        val text = input.value
        // Поле очищается сразу, не дожидаясь записи в БД: иначе при быстрой
        // диктовке нескольких позиций подряд ввод «залипает».
        input.value = ""
        viewModelScope.launch {
            if (!repository.add(text)) input.value = text
        }
    }

    fun onCheckedChange(item: ShoppingItem, isChecked: Boolean) {
        viewModelScope.launch { repository.setChecked(item.id, isChecked) }
    }

    /**
     * Удаление окончательное, без предложения отменить.
     *
     * Отмена здесь была и убрана по просьбе владельца: плашка выскакивала на
     * каждое смахивание, перекрывала список и требовала реакции там, где
     * человек уже принял решение. Смахнуть нужно намеренно, а вернуть
     * случайно удалённое проще, продиктовав заново, чем читать баннер после
     * каждой покупки.
     */
    fun onDelete(item: ShoppingItem) {
        viewModelScope.launch { repository.delete(item.id) }
    }

    fun onClearChecked() {
        viewModelScope.launch { repository.deleteChecked() }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MagpieApp
                ShoppingViewModel(
                    repository = app.container.shoppingRepository,
                    preferences = ShoppingPreferences(app),
                )
            }
        }
    }
}
