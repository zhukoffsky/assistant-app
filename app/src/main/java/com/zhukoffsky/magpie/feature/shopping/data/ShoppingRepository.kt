package com.zhukoffsky.magpie.feature.shopping.data

import com.zhukoffsky.magpie.core.data.db.ShoppingDao
import com.zhukoffsky.magpie.core.data.db.ShoppingItemEntity
import com.zhukoffsky.magpie.feature.shopping.domain.ShoppingItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock

/**
 * Единственная точка доступа к списку покупок.
 *
 * [clock] параметризован ради тестов: время создания записи иначе не
 * зафиксировать.
 */
class ShoppingRepository(
    private val dao: ShoppingDao,
    private val clock: Clock = Clock.systemUTC(),
) {

    fun observeItems(): Flow<List<ShoppingItem>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    /**
     * Добавляет позицию в конец списка.
     *
     * @return false, если после обрезки пробелов строка оказалась пустой —
     * такие записи молча игнорируются, чтобы случайный тап по «плюсу» не
     * плодил мусор.
     */
    suspend fun add(rawTitle: String): Boolean {
        val title = rawTitle.trim()
        if (title.isEmpty()) return false

        dao.insert(
            ShoppingItemEntity(
                title = title,
                position = dao.maxPosition() + 1,
                createdAt = clock.instant(),
            ),
        )
        return true
    }

    suspend fun setChecked(id: Long, isChecked: Boolean) {
        dao.setChecked(
            id = id,
            isChecked = isChecked,
            checkedAt = if (isChecked) clock.instant() else null,
        )
    }

    suspend fun delete(id: Long) = dao.deleteById(id)

    suspend fun deleteChecked() = dao.deleteChecked()
}

private fun ShoppingItemEntity.toDomain() = ShoppingItem(
    id = id,
    title = title,
    isChecked = isChecked,
)
