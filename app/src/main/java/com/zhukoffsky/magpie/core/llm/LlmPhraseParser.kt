package com.zhukoffsky.magpie.core.llm

import com.zhukoffsky.magpie.feature.reminders.domain.ParsedReminder
import com.zhukoffsky.magpie.feature.reminders.domain.PhraseParser
import com.zhukoffsky.magpie.feature.reminders.domain.RepeatRule
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@Serializable
private data class LlmReminder(
    val title: String = "",
    val dueAt: String? = null,
    val repeat: String? = null,
)

/**
 * Разбор фразы через Claude Haiku.
 *
 * Вызывается только когда правила не справились, поэтому цена и задержка
 * платятся редко. Любая неудача — нет ключа, нет сети, кривой ответ —
 * возвращает null: гибридный парсер откатится на результат правил.
 */
class LlmPhraseParser(
    private val api: AnthropicApi,
    /** Ключ вводится пользователем в настройках; в коде и репозитории его нет. */
    private val apiKey: suspend () -> String?,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : PhraseParser {

    override suspend fun parse(phrase: String, now: ZonedDateTime): ParsedReminder? {
        val key = apiKey()?.trim()?.takeIf { it.isNotEmpty() } ?: return null

        val response = runCatching {
            api.messages(
                apiKey = key,
                version = AnthropicApi.VERSION,
                body = MessagesRequest(
                    model = AnthropicApi.MODEL,
                    maxTokens = MAX_TOKENS,
                    system = systemPrompt(now),
                    messages = listOf(AnthropicMessage(role = "user", content = phrase)),
                ),
            )
        }.getOrNull() ?: return null

        val text = response.content.firstOrNull { it.type == "text" }?.text ?: return null
        return decode(text, now)
    }

    private fun decode(raw: String, now: ZonedDateTime): ParsedReminder? {
        // Модель иногда оборачивает JSON в markdown-заборчик, несмотря на
        // просьбу этого не делать.
        val cleaned = raw.trim().removeSurrounding("```json", "```").removeSurrounding("```").trim()

        val parsed = runCatching { json.decodeFromString<LlmReminder>(cleaned) }.getOrNull() ?: return null
        if (parsed.title.isBlank()) return null

        val dueAt = parsed.dueAt
            ?.let { runCatching { OffsetDateTime.parse(it).atZoneSameInstant(now.zone) }.getOrNull() }
            ?: return null

        return ParsedReminder(
            title = parsed.title.trim(),
            dueAt = dueAt,
            repeat = RepeatRule.parse(parsed.repeat),
        )
    }

    private fun systemPrompt(now: ZonedDateTime): String = """
        Ты разбираешь короткие фразы-напоминания на русском или английском языке.

        Верни ТОЛЬКО JSON, без пояснений и без markdown:
        {"title": "строка", "dueAt": "ISO-8601 со смещением", "repeat": null или "DAILY" или "WEEKLY:MONDAY,TUESDAY"}

        Правила:
        - title — суть напоминания без слов о времени;
        - dueAt — момент срабатывания, всегда в будущем относительно текущего времени;
        - repeat — только для явно повторяющихся напоминаний, дни недели из
          java.time.DayOfWeek заглавными буквами;
        - если время не названо, выбери ближайшее разумное.

        Текущее время пользователя: ${now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)}
        Часовой пояс: ${now.zone}
    """.trimIndent()

    private companion object {
        const val MAX_TOKENS = 300
    }
}
