package com.zhukoffsky.magpie.core.llm

import com.zhukoffsky.magpie.core.util.MagpieLog
import kotlin.coroutines.cancellation.CancellationException

/**
 * Сколько ждать модель над карточкой диктовки, прежде чем резать правилами.
 *
 * Общий предел на всю цепочку провайдеров, а не на каждого: зависший
 * провайдер не должен растягивать ожидание вдвое.
 */
const val LLM_DEADLINE_MILLIS = 6_000L

/** Один OpenAI-совместимый провайдер: адрес, ключ и модель. */
class LlmProvider(
    val name: String,
    val api: OpenAiCompatApi,
    val apiKey: String,
    val model: String,
)

/**
 * Провайдеры по очереди: не ответил первый — тот же запрос уходит второму.
 *
 * **Зачем.** 4 ноября бесплатная `glm-4.7-flash` у Z.ai отвечала `429`
 * («The service may be temporarily overloaded») на каждый запрос подряд,
 * и диктовка покупок молча откатывалась на правила: позиции слипались, отделов
 * не было вовсе. Бесплатная модель не обещает доступности, поэтому ей нужен
 * запасной, а не повтор: повтор в ту же перегрузку ничего не даёт.
 *
 * Запасной — Mistral Small 3.1 на Cloudflare Workers AI. Учётная запись та же,
 * что у расшифровки речи, и с российского интернета она отвечает. Проверено
 * запросами с машины владельца на живых диктовках: восемь и семь позиций,
 * все с верными отделами, около трёх секунд.
 *
 * Цепочка сама подставляет ключ и модель каждого провайдера — то, что пришло
 * в вызов, игнорируется. Падает только если не ответил никто; парсеры ловят
 * это и откатываются на правила, как и раньше.
 */
class LlmProviderChain(private val providers: List<LlmProvider>) : OpenAiCompatApi {

    val isEmpty: Boolean get() = providers.isEmpty()

    override suspend fun chatCompletions(authorization: String, body: ChatRequest): ChatResponse {
        var lastError: Throwable = IllegalStateException("no LLM providers configured")
        for (provider in providers) {
            try {
                val response = provider.api.chatCompletions(
                    authorization = "Bearer ${provider.apiKey}",
                    body = body.copy(model = provider.model),
                )
                MagpieLog.i("llm: answered by ${provider.name}")
                return response
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                MagpieLog.w("llm: ${provider.name} failed, trying next", e)
                lastError = e
            }
        }
        throw lastError
    }
}
