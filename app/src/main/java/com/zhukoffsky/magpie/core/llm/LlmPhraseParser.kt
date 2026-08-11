package com.zhukoffsky.magpie.core.llm

import com.zhukoffsky.magpie.core.util.MagpieLog
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
 * Разбор фразы через GLM-4.7-Flash (Z.ai), OpenAI-совместимым вызовом.
 *
 * Вызывается только когда правила не справились, поэтому задержка платится
 * редко. Любая неудача — нет ключа, нет сети, кривой ответ — возвращает null:
 * гибридный парсер откатится на результат правил.
 */
class LlmPhraseParser(
    private val api: OpenAiCompatApi,
    /** Ключ вводится пользователем в настройках; в коде и репозитории его нет. */
    private val apiKey: suspend () -> String?,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : PhraseParser {

    override suspend fun parse(phrase: String, now: ZonedDateTime): ParsedReminder? {
        val key = apiKey()?.trim()?.takeIf { it.isNotEmpty() } ?: return null

        val response = runCatching {
            api.chatCompletions(
                authorization = "Bearer $key",
                body = ChatRequest(
                    model = OpenAiCompatApi.MODEL,
                    maxTokens = MAX_TOKENS,
                    // Задача детерминированная: разброс тут только вредит.
                    temperature = 0.0,
                    responseFormat = ResponseFormat("json_object"),
                    thinking = Thinking("disabled"),
                    messages = listOf(
                        ChatMessage(role = "system", content = systemPrompt(now)),
                        ChatMessage(role = "user", content = phrase),
                    ),
                ),
            )
        }
            // Без этого любая беда — не тот адрес, отозванный ключ, нет сети —
            // выглядит одинаково: молчаливый откат на правила. На поиск
            // опечатки в базовом URL так уходит вечер.
            .onFailure { MagpieLog.w("llm: call failed", it) }
            .getOrNull() ?: return null

        val text = response.choices.firstOrNull()?.message?.content
        if (text.isNullOrBlank()) {
            // Пустой content при непустом ответе — это упёршиеся в лимит
            // рассуждения: модель истратила бюджет на них и до JSON не дошла.
            MagpieLog.w("llm: empty content")
            return null
        }
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
        - title — что нужно сделать, на языке исходной фразы, начиная с глагола.
          Вычеркни из title все слова о времени: они уже учтены в dueAt, и
          повторять их нельзя.
        - dueAt — момент срабатывания, строго позже текущего времени. Считай его
          от текущего времени, указанного ниже.
        - repeat — только для явно повторяющихся напоминаний, дни недели из
          java.time.DayOfWeek заглавными буквами;
        - если время не названо, выбери ближайшее разумное.

        Примеры того, что вычёркивается из title:
        «посылку бы забрать на неделе» -> title «забрать посылку»
        "pick up the parcel sometime next week" -> title "pick up the parcel"
        «каждый вторник вынести мусор» -> title «вынести мусор», repeat "WEEKLY:TUESDAY"
        «позвонить маме завтра в 9» -> title «позвонить маме»

        Текущее время пользователя: ${now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)}
        Часовой пояс: ${now.zone}
    """.trimIndent()

    private companion object {
        /**
         * Самого JSON тут токенов на шестьдесят, остальное — запас на
         * рассуждения GLM. Просьбу их отключить (`thinking`) Z.ai, по
         * сообщениям, местами игнорирует, а упёршись в лимит модель обрежет
         * ответ на полуслове: `decode` вернёт null, и разбор молча откатится
         * на правила. Токены бесплатные, скупиться незачем.
         */
        const val MAX_TOKENS = 1024
    }
}
