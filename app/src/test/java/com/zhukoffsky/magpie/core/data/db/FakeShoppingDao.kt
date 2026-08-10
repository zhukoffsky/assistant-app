package com.zhukoffsky.magpie.core.data.db

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Instant

/** Минимальная замена Room DAO: список в памяти плюс автоинкремент id. */
class FakeShoppingDao : ShoppingDao {

    val items = MutableStateFlow<List<ShoppingItemEntity>>(emptyList())
    private var nextId = 1L

    override fun observeAll(): Flow<List<ShoppingItemEntity>> = items

    override suspend fun maxPosition(): Int = items.value.maxOfOrNull { it.position } ?: 0

    override suspend fun insert(item: ShoppingItemEntity): Long {
        val id = nextId++
        items.value = items.value + item.copy(id = id)
        return id
    }

    override suspend fun setChecked(id: Long, isChecked: Boolean, checkedAt: Instant?) {
        items.value = items.value.map {
            if (it.id == id) it.copy(isChecked = isChecked, checkedAt = checkedAt) else it
        }
    }

    override suspend fun deleteById(id: Long) {
        items.value = items.value.filterNot { it.id == id }
    }

    override suspend fun deleteChecked() {
        items.value = items.value.filterNot { it.isChecked }
    }
}
