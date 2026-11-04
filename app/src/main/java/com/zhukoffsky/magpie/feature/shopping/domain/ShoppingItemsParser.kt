package com.zhukoffsky.magpie.feature.shopping.domain

import com.zhukoffsky.magpie.core.util.MagpieLog
import com.zhukoffsky.magpie.core.llm.LLM_DEADLINE_MILLIS
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Разбор надиктованной фразы в список покупок.
 *
 * Интерфейс появился ровно по той же причине, что и `PhraseParser` для
 * напоминаний: правила дёшевы и мгновенны, но покрывают не всё, а LLM
 * покрывает, но ходит в сеть. Реализации взаимозаменяемы.
 */
fun interface ShoppingItemsParser {
    suspend fun parse(phrase: String): List<ParsedShoppingItem>
}

/**
 * Правила: режут по знакам препинания и союзам, а сплошной остаток — по
 * смене отдела ([ShoppingAisleSplitter]). Отдел проставляет вызывающий.
 */
class RuleBasedShoppingParser : ShoppingItemsParser {
    override suspend fun parse(phrase: String): List<ParsedShoppingItem> =
        ShoppingPhraseParser.parse(phrase)
            .flatMap(ShoppingAisleSplitter::split)
            .map { ParsedShoppingItem(title = it) }
}

/**
 * Правила — когда уверены, модель — когда нет, правила — запасной путь.
 *
 * **Почему не «модель всегда», как было до 4 ноября.** Та схема родилась,
 * когда правила не знали отделов: Whisper поставил запятые, правила
 * разрезали фразу сами, и девять позиций легли без отдела. С тех пор отдел
 * даёт словарь ([ShoppingCategoryDictionary]), а правила режут сплошную
 * строку по смене отдела и согласованию ([ShoppingAisleSplitter]). Замер на
 * живой диктовке: правила разобрали двенадцать позиций верно за 0,2 с, а
 * модель — так же, но за 5,7 с (Z.ai `429`, затем Mistral). Владелец выбрал
 * скорость.
 *
 * **Когда правила уверены** ([ShoppingAisleSplitter.isSure]): у каждой
 * позиции есть отдел, каждое существительное до предлога знакомо словарю,
 * и слов там не больше трёх. Незнакомое слово значит, что позиция могла
 * быть расслышана неверно или склеена с чем-то; четыре слова до предлога —
 * почти наверняка склейка («средство для мытья посуды» — одно слово до
 * «для»). Тогда спрашиваем модель, а её отдел главнее словарного.
 *
 * Модель не ответила — остаётся разбор правил: хуже, но фраза не теряется
 * никогда. Ввод с клавиатуры сюда не попадает вовсе.
 */
class HybridShoppingParser(
    private val rules: ShoppingItemsParser,
    private val llm: ShoppingItemsParser,
) : ShoppingItemsParser {

    override suspend fun parse(phrase: String): List<ParsedShoppingItem> {
        val byRules = rules.parse(phrase).map(::withDictionaryAisle)
        if (byRules.isNotEmpty() && byRules.all(::isConfident)) {
            MagpieLog.i("shopping parse: rules, ${byRules.size} item(s)")
            return byRules
        }

        // Общий предел на модель (LLM_DEADLINE_MILLIS): не уложились —
        // остаётся разбор правил, а не ожидание второй модели за первой.
        val byLlm = withTimeoutOrNull(LLM_DEADLINE_MILLIS) { llm.parse(phrase) }
            .orEmpty()
            .filter { it.title.isNotBlank() }
        if (byLlm.isNotEmpty()) {
            MagpieLog.i("shopping parse: llm, ${byLlm.size} item(s)")
            return byLlm.map(::withDictionaryAisle)
        }

        MagpieLog.i("shopping parse: fallback to rules, ${byRules.size} item(s)")
        return byRules
    }

    private fun isConfident(item: ParsedShoppingItem): Boolean =
        item.category != null && ShoppingAisleSplitter.isSure(item.title)

    /** Отдел модели главнее; словарь заполняет только пустоту и «Прочее». */
    private fun withDictionaryAisle(item: ParsedShoppingItem): ParsedShoppingItem {
        if (item.category != null && item.category != ShoppingCategory.OTHER) return item
        return ShoppingCategoryDictionary.categorize(item.title)
            ?.let { item.copy(category = it) }
            ?: item
    }
}
