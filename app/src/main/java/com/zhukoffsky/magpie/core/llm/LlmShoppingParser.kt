package com.zhukoffsky.magpie.core.llm

import com.zhukoffsky.magpie.core.util.MagpieLog
import com.zhukoffsky.magpie.feature.shopping.domain.ShoppingItemsParser
import com.zhukoffsky.magpie.feature.shopping.domain.ShoppingPhraseParser
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class LlmShoppingList(val items: List<String> = emptyList())

/**
 * Разбиение надиктованной фразы на позиции списка покупок через
 * GLM-4.7-Flash (Z.ai), OpenAI-совместимым вызовом.
 *
 * Зачем вообще модель на такой задаче: распознавание речи не ставит знаков
 * препинания. «Молоко хлеб яйца» приезжает одной строкой, и по правилам
 * разрезать её не на чем. Резать по пробелам нельзя — «хлеб бородинский» и
 * «зубная паста» распались бы на четыре позиции вместо двух. Отличить одно
 * от другого может только тот, кто понимает язык.
 *
 * Вызывается редко: только когда правила не нашли разделителей. Любая
 * неудача возвращает пустой список, и гибрид откатывается на правила.
 */
class LlmShoppingParser(
    private val api: OpenAiCompatApi,
    private val apiKey: suspend () -> String?,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : ShoppingItemsParser {

    override suspend fun parse(phrase: String): List<String> {
        val key = apiKey()?.trim()?.takeIf { it.isNotEmpty() } ?: return emptyList()

        val response = runCatching {
            api.chatCompletions(
                authorization = "Bearer $key",
                body = ChatRequest(
                    model = OpenAiCompatApi.MODEL,
                    maxTokens = MAX_TOKENS,
                    temperature = 0.0,
                    responseFormat = ResponseFormat("json_object"),
                    // У GLM рассуждения включены по умолчанию и съедают весь
                    // бюджет ответа — то же, что и в разборе напоминаний.
                    thinking = Thinking("disabled"),
                    messages = listOf(
                        ChatMessage(role = "system", content = SYSTEM_PROMPT),
                        ChatMessage(role = "user", content = phrase),
                    ),
                ),
            )
        }
            .onFailure { MagpieLog.w("llm shopping: call failed", it) }
            .getOrNull() ?: return emptyList()

        val text = response.choices.firstOrNull()?.message?.content
        if (text.isNullOrBlank()) {
            MagpieLog.w("llm shopping: empty content")
            return emptyList()
        }

        val cleaned = text.trim().removeSurrounding("```json", "```").removeSurrounding("```").trim()
        return runCatching { json.decodeFromString<LlmShoppingList>(cleaned) }
            .getOrNull()
            ?.items
            // Та же чистка, что и у правил. Промпт просит выбросить вводные
            // слова, но модель делает это через раз: разрезав фразу верно,
            // она вернула «купить хлеб» и «и наверное ещё фарш». Регулярка
            // отрабатывает всегда, поэтому просьбе не доверяем.
            ?.map(ShoppingPhraseParser::clean)
            ?.filter { it.isNotEmpty() }
            .orEmpty()
    }

    private companion object {
        /**
         * Примеры нужны в обе стороны — и где резать, и где НЕ резать.
         * Без вторых модель охотно дробит «хлеб бородинский» на два товара.
         */
        val SYSTEM_PROMPT = """
            Ты разбираешь надиктованную фразу в список покупок.

            Верни ТОЛЬКО JSON, без пояснений и без markdown:
            {"items": ["строка", "строка"]}

            Правила:
            - каждая позиция — отдельный товар, на языке исходной фразы;
            - знаков препинания во фразе нет: их не поставило распознавание
              речи, границы товаров нужно определить по смыслу;
            - НЕ дроби название одного товара из нескольких слов;
            - выброси вводные глаголы («надо купить», «добавь», "buy") и
              слова-заминки в начале позиции («и», «ещё», «наверное», "also");
            - количество и единицы оставляй при товаре;
            - ничего не придумывай сверх сказанного.

            Примеры:
            «молоко хлеб яйца» -> {"items": ["молоко", "хлеб", "яйца"]}
            «купить хлеб масло колбасу и наверное ещё фарш» ->
              {"items": ["хлеб", "масло", "колбасу", "фарш"]}
            «хлеб бородинский» -> {"items": ["хлеб бородинский"]}
            «зубная паста и туалетная бумага» -> {"items": ["зубная паста", "туалетная бумага"]}
            «купи два литра молока и десяток яиц» -> {"items": ["два литра молока", "десяток яиц"]}
            "milk bread eggs" -> {"items": ["milk", "bread", "eggs"]}
            "peanut butter" -> {"items": ["peanut butter"]}
        """.trimIndent()

        /** Список длиннее полусотни позиций надиктовать невозможно. */
        const val MAX_TOKENS = 1024
    }
}
