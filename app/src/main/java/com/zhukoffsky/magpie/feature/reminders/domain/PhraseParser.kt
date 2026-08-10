package com.zhukoffsky.magpie.feature.reminders.domain

import java.time.ZonedDateTime

/**
 * Разбор надиктованной фразы в напоминание.
 *
 * @return null, если реализация не смогла разобрать фразу уверенно.
 * Решение, что делать дальше, принимает вызывающий.
 */
interface PhraseParser {
    suspend fun parse(phrase: String, now: ZonedDateTime): ParsedReminder?
}

/** Правила. Быстро, бесплатно, офлайн — но только на предсказуемых формулировках. */
class RuleBasedPhraseParser : PhraseParser {
    override suspend fun parse(phrase: String, now: ZonedDateTime): ParsedReminder? =
        ReminderPhraseParser.parse(phrase, now).takeIf { it.isConfident }
}

/**
 * Стратегия «сначала правила, при неудаче — LLM».
 *
 * Порядок именно такой: правила отвечают за миллисекунды и без сети, а
 * платный сетевой вызов достаётся только тем фразам, которые правила не
 * осилили. Если и LLM не смогла — отдаём результат правил как есть: пусть
 * лучше будет напоминание с временем по умолчанию, чем ничего.
 */
class HybridPhraseParser(
    private val rules: PhraseParser,
    private val llm: PhraseParser?,
) : PhraseParser {

    override suspend fun parse(phrase: String, now: ZonedDateTime): ParsedReminder {
        rules.parse(phrase, now)?.let { return it }

        llm?.parse(phrase, now)?.let { return it }

        return ReminderPhraseParser.parse(phrase, now)
    }
}
