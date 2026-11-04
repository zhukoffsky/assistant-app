package com.zhukoffsky.magpie.feature.shopping.domain

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Проверяется не качество разбора, а **когда** спрашивают модель. Лишний
 * поход стоит пяти секунд над карточкой, пропущенный — склеенной позиции.
 */
class HybridShoppingParserTest {

    private class FakeLlm(vararg titles: String) : ShoppingItemsParser {
        private val result = titles.map { ParsedShoppingItem(it) }
        var called = false
            private set

        override suspend fun parse(phrase: String): List<ParsedShoppingItem> {
            called = true
            return result
        }
    }

    /** Сравниваем названия: категории у правил нет по определению. */
    private fun List<ParsedShoppingItem>.titles() = map { it.title }

    private fun parser(llm: FakeLlm) = HybridShoppingParser(RuleBasedShoppingParser(), llm)

    /**
     * Живая диктовка 4 ноября: правила разобрали её верно, и модель, которая
     * отвечала бы ещё 5,7 с, не нужна.
     */
    @Test
    fun `confident rules answer without the model`() = runTest {
        val llm = FakeLlm("не", "должно", "понадобиться")

        val items = parser(llm).parse(
            "Хлеб Бородинский булочка с маком молоко яйца куриные. Подсолнечное масло пачка сливочного масла " +
                "сыр моцарелла картошка фри яблоки зелёные бананы вино красное сухое средство для мытья посуды.",
        )

        assertFalse(llm.called)
        assertEquals(12, items.size)
        assertTrue("у каждой позиции отдел из словаря", items.all { it.category != null })
    }

    @Test
    fun `punctuated phrase of known items needs no model`() = runTest {
        val llm = FakeLlm()

        val items = parser(llm).parse("молоко, хлеб и яйца")

        assertEquals(listOf("молоко", "хлеб", "яйца"), items.titles())
        assertFalse(llm.called)
    }

    /** Незнакомое слово — возможно, расслышано неверно или склеено: решает модель. */
    @Test
    fun `unknown word sends the phrase to the model`() = runTest {
        val llm = FakeLlm("молоко", "подарок маме")

        val items = parser(llm).parse("молоко подарок маме")

        assertTrue(llm.called)
        assertEquals(listOf("молоко", "подарок маме"), items.titles())
    }

    /** Четыре слова до предлога правила не отличат от склейки. */
    @Test
    fun `long item sends the phrase to the model`() = runTest {
        val llm = FakeLlm("колбаса докторская", "нарезка")

        parser(llm).parse("колбаса докторская варёная нарезка")

        assertTrue(llm.called)
    }

    @Test
    fun `aisle comes from the model when it was asked`() = runTest {
        val llm = object : ShoppingItemsParser {
            override suspend fun parse(phrase: String) = listOf(
                ParsedShoppingItem("молоко", ShoppingCategory.DAIRY),
                ParsedShoppingItem("подарок маме", ShoppingCategory.OTHER),
            )
        }

        val items = HybridShoppingParser(RuleBasedShoppingParser(), llm).parse("молоко подарок маме")

        assertEquals(listOf(ShoppingCategory.DAIRY, ShoppingCategory.OTHER), items.map { it.category })
    }

    @Test
    fun `phrase survives when the model is unavailable`() = runTest {
        val llm = FakeLlm()

        val items = parser(llm).parse("молоко подарок маме")

        assertTrue(llm.called)
        // Незнакомое правила оставляют с соседом — хуже, но не потеряно.
        assertEquals("фраза не теряется никогда", listOf("молоко подарок маме"), items.titles())
    }

    @Test
    fun `blank answers from the model are ignored`() = runTest {
        val llm = FakeLlm("  ", "")

        assertEquals(listOf("молоко подарок маме"), parser(llm).parse("молоко подарок маме").titles())
    }
}
