package com.zhukoffsky.magpie.feature.shopping.domain

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Проверяется не качество разбора — оно на стороне модели, — а **когда** к
 * ней вообще обращаются. Лишний поход в сеть на каждой покупке был бы
 * заметен, а пропущенный оставил бы «молоко хлеб яйца» одной строкой.
 */
class HybridShoppingParserTest {

    private class FakeLlm(private val result: List<String>) : ShoppingItemsParser {
        var called = false
            private set

        override suspend fun parse(phrase: String): List<String> {
            called = true
            return result
        }
    }

    private fun parser(llm: FakeLlm) = HybridShoppingParser(RuleBasedShoppingParser(), llm)

    @Test
    fun `phrase with separators is handled by rules alone`() = runTest {
        val llm = FakeLlm(listOf("не должно понадобиться"))

        val items = parser(llm).parse("молоко, хлеб и яйца")

        assertEquals(listOf("молоко", "хлеб", "яйца"), items)
        assertFalse("правила справились — в сеть ходить незачем", llm.called)
    }

    @Test
    fun `single word does not go to the network`() = runTest {
        val llm = FakeLlm(listOf("не должно понадобиться"))

        val items = parser(llm).parse("молоко")

        assertEquals(listOf("молоко"), items)
        assertFalse(llm.called)
    }

    @Test
    fun `phrase without separators is split by the model`() = runTest {
        val llm = FakeLlm(listOf("молоко", "хлеб", "яйца"))

        val items = parser(llm).parse("молоко хлеб яйца")

        assertEquals(listOf("молоко", "хлеб", "яйца"), items)
        assertTrue("разделителей нет — это случай для модели", llm.called)
    }

    /** Название из нескольких слов модель имеет право оставить одним. */
    @Test
    fun `model may keep a multi word name whole`() = runTest {
        val llm = FakeLlm(listOf("хлеб бородинский"))

        assertEquals(listOf("хлеб бородинский"), parser(llm).parse("хлеб бородинский"))
    }

    @Test
    fun `phrase survives when the model is unavailable`() = runTest {
        val llm = FakeLlm(emptyList())

        val items = parser(llm).parse("молоко хлеб яйца")

        assertEquals("фраза не теряется никогда", listOf("молоко хлеб яйца"), items)
        assertTrue(llm.called)
    }

    @Test
    fun `blank answers from the model are ignored`() = runTest {
        val llm = FakeLlm(listOf("  ", ""))

        assertEquals(listOf("молоко хлеб"), parser(llm).parse("молоко хлеб"))
    }
}
