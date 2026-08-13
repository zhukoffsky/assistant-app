package com.zhukoffsky.magpie.core.llm

import com.zhukoffsky.magpie.core.util.MagpieLog
import com.zhukoffsky.magpie.feature.shopping.domain.ParsedShoppingItem
import com.zhukoffsky.magpie.feature.shopping.domain.ShoppingCategory
import com.zhukoffsky.magpie.feature.shopping.domain.ShoppingItemsParser
import com.zhukoffsky.magpie.feature.shopping.domain.ShoppingPhraseParser
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class LlmShoppingItem(val title: String = "", val category: String? = null)

@Serializable
private data class LlmShoppingList(val items: List<LlmShoppingItem> = emptyList())

/**
 * Разбиение надиктованной фразы на позиции списка покупок через
 * GLM-4.7-Flash (Z.ai), OpenAI-совместимым вызовом. Заодно модель называет
 * отдел магазина — знать его больше некому.
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

    override suspend fun parse(phrase: String): List<ParsedShoppingItem> {
        val key = apiKey()?.trim()?.takeIf { it.isNotEmpty() } ?: return emptyList()

        val first = ask(key, phrase, retry = false)

        /*
         * Один повтор, если ответ выглядит слипшимся.
         *
         * 12 августа на живой диктовке «хлеб макароны мясо фарш куриный
         * индюшиные ноги ватрушку с изюмом средство для мытья посуды» модель
         * вернула две позиции: отделилось только средство для мытья посуды,
         * остальные семь товаров остались одной строкой. На коротких фразах
         * разрез верный — ломается именно длинное перечисление.
         *
         * Просить в промпте «режь внимательнее» бесполезно: он и так просит.
         * Зато видно, что ответ плох, — позиция из шести и более слов почти
         * наверняка склейка: самое длинное настоящее название в обиходе,
         * «средство для мытья посуды», это четыре слова.
         */
        if (first.none { it.isGlued() }) return first

        MagpieLog.i("llm shopping: looks glued, asking again")
        val second = ask(key, phrase, retry = true)

        // Второй ответ берём, только если он действительно лучше: модель
        // может повторить прежнее или, наоборот, раскрошить «хлеб
        // бородинский» на слова.
        return if (second.isNotEmpty() && second.count { it.isGlued() } < first.count { it.isGlued() }) {
            second
        } else {
            first
        }
    }

    private suspend fun ask(key: String, phrase: String, retry: Boolean): List<ParsedShoppingItem> {
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
                    messages = listOfNotNull(
                        ChatMessage(role = "system", content = SYSTEM_PROMPT),
                        ChatMessage(role = "user", content = phrase),
                        ChatMessage(role = "system", content = RETRY_HINT).takeIf { retry },
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
            ?.map {
                ParsedShoppingItem(
                    // Та же чистка, что и у правил. Промпт просит выбросить
                    // вводные слова, но модель делает это через раз: разрезав
                    // фразу верно, она вернула «купить хлеб» и «и наверное
                    // ещё фарш». Регулярка отрабатывает всегда, поэтому
                    // просьбе не доверяем.
                    title = ShoppingPhraseParser.clean(it.title),
                    category = ShoppingCategory.fromName(it.category),
                )
            }
            ?.filter { it.title.isNotEmpty() }
            .orEmpty()
    }

    private fun ParsedShoppingItem.isGlued(): Boolean =
        title.trim().split(WHITESPACE).size >= GLUED_WORDS

    private companion object {
        val WHITESPACE = Regex("""\s+""")

        /**
         * Со скольких слов позиция считается склейкой нескольких товаров.
         *
         * Шесть, потому что самое длинное живое название на памяти проекта —
         * «средство для мытья посуды», четыре слова. Порог с запасом: ложное
         * срабатывание стоит одного лишнего запроса, пропуск — списка,
         * непригодного в магазине.
         */
        const val GLUED_WORDS = 6

        val SYSTEM_PROMPT = """
            Ты разбираешь надиктованную фразу в список покупок.

            Верни ТОЛЬКО JSON, без пояснений и без markdown:
            {"items": [{"title": "строка", "category": "КАТЕГОРИЯ"}]}

            Правила:
            - каждая позиция — отдельный товар, на языке исходной фразы;
            - знаков препинания во фразе нет: их не поставило распознавание
              речи, границы товаров нужно определить по смыслу;
            - товаров в одной фразе бывает и восемь, и десять подряд — раздели
              их все, а не только первый и последний;
            - НЕ дроби название одного товара из нескольких слов;
            - выброси вводные глаголы («надо купить», «добавь», "buy") и
              слова-заминки в начале позиции («и», «ещё», «наверное», "also");
            - количество и единицы оставляй при товаре;
            - ничего не придумывай сверх сказанного.

            category — ровно одно из значений, отдел магазина:
            PRODUCE (овощи, фрукты, зелень), BAKERY (хлеб, выпечка),
            DAIRY (молочное, яйца), MEAT (мясо, птица, рыба),
            GROCERY (бакалея, крупы, консервы, сладости),
            FROZEN (заморозка), DRINKS (напитки),
            HOUSEHOLD (бытовая химия, хозтовары, гигиена),
            OTHER (всё остальное и сомнительное).

            Примеры:
            «молоко хлеб яйца» -> {"items": [
              {"title": "молоко", "category": "DAIRY"},
              {"title": "хлеб", "category": "BAKERY"},
              {"title": "яйца", "category": "DAIRY"}]}
            «купить хлеб масло колбасу и наверное ещё фарш» -> {"items": [
              {"title": "хлеб", "category": "BAKERY"},
              {"title": "масло", "category": "DAIRY"},
              {"title": "колбасу", "category": "MEAT"},
              {"title": "фарш", "category": "MEAT"}]}
            «хлеб макароны мясо фарш куриный индюшиные ноги ватрушку с изюмом
            средство для мытья посуды» -> {"items": [
              {"title": "хлеб", "category": "BAKERY"},
              {"title": "макароны", "category": "GROCERY"},
              {"title": "фарш куриный", "category": "MEAT"},
              {"title": "индюшиные ноги", "category": "MEAT"},
              {"title": "ватрушку с изюмом", "category": "BAKERY"},
              {"title": "средство для мытья посуды", "category": "HOUSEHOLD"}]}
            «хлеб бородинский» -> {"items": [
              {"title": "хлеб бородинский", "category": "BAKERY"}]}
            "milk bread eggs" -> {"items": [
              {"title": "milk", "category": "DAIRY"},
              {"title": "bread", "category": "BAKERY"},
              {"title": "eggs", "category": "DAIRY"}]}
        """.trimIndent()

        /** Добавляется только во втором запросе — см. проверку на склейку. */
        val RETRY_HINT = """
            В прошлом ответе несколько товаров остались одной позицией.
            Раздели перечисление до конца: в позиции не должно быть двух
            разных продуктов. При этом название одного товара из двух-трёх
            слов («хлеб бородинский», «средство для мытья посуды») не дроби.
        """.trimIndent()

        /** Список длиннее полусотни позиций надиктовать невозможно. */
        const val MAX_TOKENS = 1024
    }
}
