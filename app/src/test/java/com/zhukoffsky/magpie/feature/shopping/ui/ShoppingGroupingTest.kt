package com.zhukoffsky.magpie.feature.shopping.ui

import com.zhukoffsky.magpie.feature.shopping.domain.ShoppingCategory
import com.zhukoffsky.magpie.feature.shopping.domain.ShoppingItem
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Раскладка списка по отделам — чистая функция, и ошибка в ней видна только
 * глазами в магазине. Поэтому она под тестом, а не под наблюдением.
 */
class ShoppingGroupingTest {

    private var nextId = 0L

    private fun item(
        title: String,
        category: ShoppingCategory,
        isChecked: Boolean = false,
    ) = ShoppingItem(id = ++nextId, title = title, isChecked = isChecked, category = category)

    @Test
    fun `groups follow the order the shop is walked, not the order of adding`() {
        val state = ShoppingUiState(
            items = listOf(
                item("средство для мытья посуды", ShoppingCategory.HOUSEHOLD),
                item("молоко", ShoppingCategory.DAIRY),
                item("яблоки", ShoppingCategory.PRODUCE),
            ),
        )

        assertEquals(
            listOf(
                ShoppingCategory.PRODUCE,
                ShoppingCategory.DAIRY,
                ShoppingCategory.HOUSEHOLD,
            ),
            state.groups.map { it.first },
        )
    }

    @Test
    fun `other comes last`() {
        val state = ShoppingUiState(
            items = listOf(
                item("батарейки", ShoppingCategory.OTHER),
                item("кефир", ShoppingCategory.DAIRY),
            ),
        )

        assertEquals(ShoppingCategory.OTHER, state.groups.last().first)
    }

    @Test
    fun `empty aisles are not shown`() {
        val state = ShoppingUiState(items = listOf(item("кефир", ShoppingCategory.DAIRY)))

        assertEquals(1, state.groups.size)
    }

    @Test
    fun `every item lands in exactly one group`() {
        val items = listOf(
            item("хлеб", ShoppingCategory.BAKERY),
            item("молоко", ShoppingCategory.DAIRY),
            item("сыр", ShoppingCategory.DAIRY, isChecked = true),
            item("батарейки", ShoppingCategory.OTHER),
        )

        val grouped = ShoppingUiState(items = items).groups.flatMap { it.second }

        assertEquals(items.size, grouped.size)
        assertEquals(items.map { it.id }.toSet(), grouped.map { it.id }.toSet())
    }

    @Test
    fun `order inside an aisle is the order the list already had`() {
        val state = ShoppingUiState(
            items = listOf(
                item("молоко", ShoppingCategory.DAIRY),
                item("сыр", ShoppingCategory.DAIRY, isChecked = true),
                item("кефир", ShoppingCategory.DAIRY),
            ),
        )

        assertEquals(
            listOf("молоко", "сыр", "кефир"),
            state.groups.single().second.map { it.title },
        )
    }
}
