package com.zhukoffsky.magpie.feature.shopping.domain

import com.zhukoffsky.magpie.core.util.MagpieLog

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

/** Правила: режет по запятым, точкам с запятой и союзам. Отделов не знают. */
class RuleBasedShoppingParser : ShoppingItemsParser {
    override suspend fun parse(phrase: String): List<ParsedShoppingItem> =
        ShoppingPhraseParser.parse(phrase).map { ParsedShoppingItem(title = it) }
}

/**
 * Сначала правила, при неудаче — LLM.
 *
 * **Что считается неудачей.** Отсутствие во фразе знаков препинания.
 *
 * Первая версия спрашивала модель, только когда правила вернули ровно одну
 * позицию. Это не сработало на живой диктовке: «надо молоко мясо колбаса сыр
 * и салфетки» правила разрезали по союзу «и» — получилось две позиции, к
 * модели не пошли вовсе, и первая осталась комом из четырёх продуктов.
 *
 * Союз не является надёжной границей: он встречается и внутри перечисления,
 * и между двумя из пяти позиций. Надёжны только знаки препинания, а их
 * распознавание речи не ставит. Поэтому правило простое: нет `,` `;` или
 * перевода строки — значит границы неизвестны, спрашиваем модель.
 *
 * Одного слова достаточно, чтобы не ходить в сеть: «молоко» — это молоко.
 *
 * **Почему решает модель, а не счётчик слов.** Резать по пробелам нельзя:
 * «хлеб бородинский» и «зубная паста» — по одной позиции, а «молоко хлеб» —
 * две, и отличить это может только тот, кто знает язык. Правило «два слова
 * значит две покупки» ломает половину реальных списков.
 *
 * Ответ LLM отбрасывается, если она вернула пустоту: результат правил хуже,
 * но он всегда есть, и фраза не теряется никогда.
 */
class HybridShoppingParser(
    private val rules: ShoppingItemsParser,
    private val llm: ShoppingItemsParser,
) : ShoppingItemsParser {

    override suspend fun parse(phrase: String): List<ParsedShoppingItem> {
        val byRules = rules.parse(phrase)
        if (!needsLlm(phrase, byRules)) {
            MagpieLog.i("shopping parse: rules, ${byRules.size} item(s)")
            return byRules
        }

        val byLlm = llm.parse(phrase).filter { it.title.isNotBlank() }
        if (byLlm.isEmpty()) {
            MagpieLog.i("shopping parse: fallback to rules")
            return byRules
        }

        MagpieLog.i("shopping parse: llm, ${byLlm.size} item(s)")
        return byLlm
    }

    private fun needsLlm(phrase: String, byRules: List<ParsedShoppingItem>): Boolean {
        if (STRONG_SEPARATOR.containsMatchIn(phrase)) return false
        return phrase.trim().split(WHITESPACE).size > 1 || byRules.size > 1
    }

    private companion object {
        /** То, что распознавание речи не ставит, а человек с клавиатуры — ставит. */
        val STRONG_SEPARATOR = Regex("""[,;\n]""")
        val WHITESPACE = Regex("""\s+""")
    }
}
