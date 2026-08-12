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
 *
 * [onChanged] вызывается после каждой записи и служит одному: растолкать
 * виджет. Подписка на поток Room обновляет его только пока жива сессия
 * Glance, а она заканчивается вместе с процессом приложения — после чего
 * лаунчер показывает последний отрисованный кадр сколь угодно долго. Хук
 * лежит здесь, а не в двух ViewModel, потому что писать в список умеет ещё и
 * голосовой ввод, и третью точку записи легко забыть. Android-зависимостей у
 * репозитория при этом не появляется: чем именно «растолкать» — решает
 * `AppContainer`.
 */
class ShoppingRepository(
    private val dao: ShoppingDao,
    private val clock: Clock = Clock.systemUTC(),
    private val onChanged: suspend () -> Unit = {},
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
        onChanged()
        return true
    }

    /**
     * Добавляет сразу несколько позиций — результат одной диктовки.
     *
     * Не цикл по [add] снаружи: [onChanged] пересобирает виджет, и на пяти
     * позициях это пять IPC-вызовов подряд. Запись становится настолько
     * медленной, что успевает не вся, если вызывающий к тому моменту
     * закрылся. Здесь виджет трогается один раз в конце.
     *
     * @return идентификаторы записанного — по ним отменяют диктовку.
     */
    suspend fun addAll(rawTitles: List<String>): List<Long> {
        val ids = rawTitles.mapNotNull { raw ->
            val title = raw.trim()
            if (title.isEmpty()) {
                null
            } else {
                dao.insert(
                    ShoppingItemEntity(
                        title = title,
                        position = dao.maxPosition() + 1,
                        createdAt = clock.instant(),
                    ),
                )
            }
        }
        if (ids.isNotEmpty()) onChanged()
        return ids
    }

    suspend fun setChecked(id: Long, isChecked: Boolean) {
        dao.setChecked(
            id = id,
            isChecked = isChecked,
            checkedAt = if (isChecked) clock.instant() else null,
        )
        onChanged()
    }

    suspend fun delete(id: Long) {
        dao.deleteById(id)
        onChanged()
    }

    /** Откат одной диктовки: виджет трогается один раз, как и при записи. */
    suspend fun deleteAll(ids: List<Long>) {
        if (ids.isEmpty()) return

        ids.forEach { dao.deleteById(it) }
        onChanged()
    }

    suspend fun deleteChecked() {
        dao.deleteChecked()
        onChanged()
    }
}

private fun ShoppingItemEntity.toDomain() = ShoppingItem(
    id = id,
    title = title,
    isChecked = isChecked,
)
