package com.zhukoffsky.magpie.feature.shopping.data

import com.zhukoffsky.magpie.core.data.db.ShoppingDao
import com.zhukoffsky.magpie.core.data.db.ShoppingItemEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class ShoppingRepositoryTest {

    private val now = Instant.parse("2026-08-08T10:00:00Z")
    private val dao = FakeShoppingDao()
    private val repository = ShoppingRepository(dao, Clock.fixed(now, ZoneOffset.UTC))

    @Test
    fun `add trims surrounding whitespace`() = runTest {
        assertTrue(repository.add("  молоко  "))
        assertEquals("молоко", dao.items.value.single().title)
    }

    @Test
    fun `add rejects blank input without touching the table`() = runTest {
        assertFalse(repository.add("   "))
        assertTrue(dao.items.value.isEmpty())
    }

    @Test
    fun `add appends to the end of the list`() = runTest {
        repository.add("молоко")
        repository.add("хлеб")
        repository.add("яйца")

        assertEquals(listOf(1, 2, 3), dao.items.value.map { it.position })
    }

    @Test
    fun `checking an item records the time, unchecking clears it`() = runTest {
        repository.add("молоко")
        val id = dao.items.value.single().id

        repository.setChecked(id, true)
        assertEquals(now, dao.items.value.single().checkedAt)

        repository.setChecked(id, false)
        assertNull(dao.items.value.single().checkedAt)
    }

    @Test
    fun `deleteChecked keeps unchecked items`() = runTest {
        repository.add("молоко")
        repository.add("хлеб")
        repository.setChecked(dao.items.value.first().id, true)

        repository.deleteChecked()

        assertEquals(listOf("хлеб"), dao.items.value.map { it.title })
    }

    @Test
    fun `observeItems exposes only the fields the UI needs`() = runTest {
        repository.add("молоко")

        val item = repository.observeItems().first().single()

        assertEquals("молоко", item.title)
        assertFalse(item.isChecked)
    }
}

/** Минимальная замена Room DAO: список в памяти плюс автоинкремент id. */
private class FakeShoppingDao : ShoppingDao {

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
