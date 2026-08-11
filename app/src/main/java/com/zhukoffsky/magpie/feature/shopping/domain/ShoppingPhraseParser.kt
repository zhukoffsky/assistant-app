package com.zhukoffsky.magpie.feature.shopping.domain

/**
 * Разбор надиктованной фразы в список покупок.
 *
 * Первая — и пока единственная — реализация разбора на правилах. Задача
 * узкая: одна фраза, произнесённая на одном дыхании, содержит несколько
 * позиций («молоко, хлеб и яйца»), и распознавание отдаёт её сплошной
 * строкой.
 *
 * Тип записи здесь не определяется: сюда попадает только то, что пришло из
 * точки входа «покупки».
 */
object ShoppingPhraseParser {

    /** Запятые, точки с запятой, переводы строк и союзы между позициями. */
    private val separators = Regex("""[,;\n]+|\s+(?:и|да|and)\s+""", RegexOption.IGNORE_CASE)

    /** Ведущий глагол, который распознавание почти всегда приписывает к первой позиции. */
    private val leadingFiller = Regex(
        """^\s*(?:надо|нужно|нужны|нужен|нужна)?\s*(?:купить|купи|добавить|добавь|buy|add)?\s+""",
        RegexOption.IGNORE_CASE,
    )

    fun parse(phrase: String): List<String> =
        phrase.replaceFirst(leadingFiller, "")
            .split(separators)
            .map { it.trim().trimEnd('.', '!', '?').trim() }
            .filter { it.isNotEmpty() }
}
