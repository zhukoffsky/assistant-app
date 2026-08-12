package com.zhukoffsky.magpie.core.llm

import com.zhukoffsky.magpie.feature.shopping.domain.ShoppingCategory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Проверяется не качество разбора — оно на стороне модели, — а обращение с
 * её ответом: чистка, категории и повтор при склеенном перечислении.
 */
class LlmShoppingParserTest {

    private val api = FakeApi()

    private fun parser(key: String? = "sk-test") = LlmShoppingParser(api, apiKey = { key })

    @Test
    fun `without a key the network is never touched`() = runTest {
        assertEquals(emptyList<Any>(), parser(key = null).parse("молоко хлеб"))
        assertEquals(0, api.calls)
    }

    @Test
    fun `items come back with their aisle`() = runTest {
        api.replies += """{"items":[{"title":"молоко","category":"DAIRY"},
            {"title":"хлеб","category":"BAKERY"}]}"""

        val items = parser().parse("молоко хлеб")

        assertEquals(listOf("молоко", "хлеб"), items.map { it.title })
        assertEquals(
            listOf(ShoppingCategory.DAIRY, ShoppingCategory.BAKERY),
            items.map { it.category },
        )
    }

    @Test
    fun `an unknown aisle is not an error`() = runTest {
        api.replies += """{"items":[{"title":"батарейки","category":"ELECTRONICS"}]}"""

        assertEquals(ShoppingCategory.OTHER, parser().parse("батарейки").single().category)
    }

    @Test
    fun `a missing aisle is not an error either`() = runTest {
        api.replies += """{"items":[{"title":"батарейки"}]}"""

        assertEquals(ShoppingCategory.OTHER, parser().parse("батарейки").single().category)
    }

    @Test
    fun `leading filler is cut even when the model leaves it in`() = runTest {
        api.replies += """{"items":[{"title":"купить хлеб","category":"BAKERY"},
            {"title":"и наверное ещё фарш","category":"MEAT"}]}"""

        assertEquals(listOf("хлеб", "фарш"), parser().parse("что угодно").map { it.title })
    }

    /**
     * Регрессия 12 августа 2026: длинное перечисление вернулось двумя
     * позициями, семь товаров остались одной строкой. Одна позиция из шести и
     * более слов — почти наверняка склейка, и на неё модель переспрашивают.
     */
    @Test
    fun `a glued answer is asked again`() = runTest {
        api.replies += """{"items":[{"title":"хлеб макароны фарш куриный индюшиные ноги",
            "category":"OTHER"},{"title":"средство для мытья посуды","category":"HOUSEHOLD"}]}"""
        api.replies += """{"items":[{"title":"хлеб","category":"BAKERY"},
            {"title":"макароны","category":"GROCERY"},{"title":"фарш куриный","category":"MEAT"},
            {"title":"индюшиные ноги","category":"MEAT"},
            {"title":"средство для мытья посуды","category":"HOUSEHOLD"}]}"""

        val items = parser().parse("хлеб макароны фарш куриный индюшиные ноги средство для мытья посуды")

        assertEquals(5, items.size)
        assertEquals(2, api.calls)
    }

    @Test
    fun `a sane answer is not asked twice`() = runTest {
        api.replies += """{"items":[{"title":"молоко","category":"DAIRY"}]}"""

        parser().parse("молоко")

        assertEquals(1, api.calls)
    }

    /** Второй ответ берут, только если он действительно лучше первого. */
    @Test
    fun `a repeated glued answer does not make things worse`() = runTest {
        val glued = """{"items":[{"title":"хлеб макароны фарш куриный индюшиные ноги",
            "category":"OTHER"}]}"""
        api.replies += glued
        api.replies += """{"items":[{"title":"хлеб макароны фарш куриный индюшиные ноги ещё хуже",
            "category":"OTHER"}]}"""

        val items = parser().parse("хлеб макароны фарш куриный индюшиные ноги")

        assertEquals("хлеб макароны фарш куриный индюшиные ноги", items.single().title)
    }

    @Test
    fun `a broken call is an empty answer, not a crash`() = runTest {
        api.failWith = IOException("нет сети")

        assertEquals(emptyList<Any>(), parser().parse("молоко хлеб"))
    }

    @Test
    fun `a markdown fence around the json is tolerated`() = runTest {
        api.replies += """
            ```json
            {"items":[{"title":"молоко","category":"DAIRY"}]}
            ```
        """.trimIndent()

        assertEquals("молоко", parser().parse("молоко").single().title)
    }

    @Test
    fun `the key travels in the header`() = runTest {
        api.replies += """{"items":[{"title":"молоко","category":"DAIRY"}]}"""

        parser(key = "  sk-secret  ").parse("молоко")

        assertTrue(api.lastAuthorization == "Bearer sk-secret")
    }

    private class FakeApi : OpenAiCompatApi {
        val replies = mutableListOf<String>()
        var failWith: Exception? = null
        var calls = 0
        var lastAuthorization: String? = null

        override suspend fun chatCompletions(
            authorization: String,
            body: ChatRequest,
        ): ChatResponse {
            calls++
            lastAuthorization = authorization
            failWith?.let { throw it }

            val reply = replies.removeFirstOrNull().orEmpty()
            return ChatResponse(listOf(ChatChoice(ChatMessage(role = "assistant", content = reply))))
        }
    }
}
