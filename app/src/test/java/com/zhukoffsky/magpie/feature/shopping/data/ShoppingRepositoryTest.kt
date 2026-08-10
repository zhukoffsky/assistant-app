package com.zhukoffsky.magpie.feature.shopping.data

import com.zhukoffsky.magpie.core.data.db.FakeShoppingDao
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
