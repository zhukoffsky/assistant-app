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
    suspend fun parse(phrase: String): List<String>
}

/** Правила: режет по запятым, точкам с запятой и союзам. */
class RuleBasedShoppingParser : ShoppingItemsParser {
    override suspend fun parse(phrase: String): List<String> = ShoppingPhraseParser.parse(phrase)
}

/**
 * Сначала правила, при неудаче — LLM.
 *
 * **Что считается неудачей.** Правила режут по разделителям, а распознавание
 * речи их не ставит: «молоко хлеб яйца» приезжает сплошной строкой и
 * становится одной позицией. Поэтому к LLM уходит фраза, из которой правила
 * достали ровно одну позицию длиной в несколько слов — то есть разделителей
 * не нашлось вовсе, а слов больше одного.
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

    override suspend fun parse(phrase: String): List<String> {
        val byRules = rules.parse(phrase)
        if (!needsLlm(phrase, byRules)) {
            MagpieLog.i("shopping parse: rules, ${byRules.size} item(s)")
            return byRules
        }

        val byLlm = llm.parse(phrase).filter { it.isNotBlank() }
        if (byLlm.isEmpty()) {
            MagpieLog.i("shopping parse: fallback to rules")
            return byRules
        }

        MagpieLog.i("shopping parse: llm, ${byLlm.size} item(s)")
        return byLlm
    }

    private fun needsLlm(phrase: String, byRules: List<String>): Boolean =
        byRules.size == 1 && phrase.trim().split(WHITESPACE).size > 1

    private companion object {
        val WHITESPACE = Regex("""\s+""")
    }
}
