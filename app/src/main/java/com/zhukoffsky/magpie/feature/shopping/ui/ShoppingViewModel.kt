package com.zhukoffsky.magpie.feature.shopping.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zhukoffsky.magpie.MagpieApp
import com.zhukoffsky.magpie.feature.shopping.data.ShoppingRepository
import com.zhukoffsky.magpie.feature.shopping.domain.ShoppingItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ShoppingUiState(
    val input: String = "",
    val items: List<ShoppingItem> = emptyList(),
    val isLoading: Boolean = true,
) {
    val checkedCount: Int get() = items.count { it.isChecked }
}

class ShoppingViewModel(private val repository: ShoppingRepository) : ViewModel() {

    private val input = MutableStateFlow("")

    val uiState: StateFlow<ShoppingUiState> =
        combine(input, repository.observeItems()) { text, items ->
            ShoppingUiState(input = text, items = items, isLoading = false)
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
     * Смахнуть позицию легко случайно, поэтому удаление всегда предлагает
     * отмену. Запись при этом удаляется сразу — «отложенное» удаление
     * рассыпается, если приложение закроют до истечения таймера.
     */
    private val _undoDelete = MutableStateFlow<ShoppingItem?>(null)
    val undoDelete: StateFlow<ShoppingItem?> = _undoDelete.asStateFlow()

    fun onDelete(item: ShoppingItem) {
        viewModelScope.launch {
            repository.delete(item.id)
            _undoDelete.value = item
        }
    }

    fun onUndoDelete() {
        val item = _undoDelete.value ?: return
        _undoDelete.value = null
        viewModelScope.launch { repository.add(item.title) }
    }

    fun onUndoDismissed() {
        _undoDelete.value = null
    }

    fun onClearChecked() {
        viewModelScope.launch { repository.deleteChecked() }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MagpieApp
                ShoppingViewModel(app.container.shoppingRepository)
            }
        }
    }
}
