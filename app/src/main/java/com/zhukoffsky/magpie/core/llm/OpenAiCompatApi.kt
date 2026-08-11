package com.zhukoffsky.magpie.core.llm

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

@Serializable
data class ChatMessage(val role: String, val content: String = "")

@Serializable
data class ResponseFormat(val type: String)

/**
 * Расширение Z.ai. **Отключать обязательно**, а не для скорости: у GLM
 * рассуждения включены по умолчанию и на этой задаче съедают весь бюджет
 * `max_tokens` целиком — ответ приходит с `finish_reason: length`, пустым
 * `content` и тремя тысячами знаков в `reasoning_content`. С `disabled`
 * тот же запрос укладывается в полсотни токенов.
 */
@Serializable
data class Thinking(val type: String)

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    @SerialName("max_tokens") val maxTokens: Int,
    val temperature: Double = 0.0,
    @SerialName("response_format") val responseFormat: ResponseFormat? = null,
    val thinking: Thinking? = null,
)

@Serializable
data class ChatChoice(val message: ChatMessage? = null)

@Serializable
data class ChatResponse(val choices: List<ChatChoice> = emptyList())

/**
 * `chat/completions` в схеме OpenAI: тело с `messages`, ответ с `choices`.
 *
 * На этом формате говорят Z.ai, Cloudflare Workers AI, Cerebras, Mistral,
 * OpenRouter и YandexGPT, поэтому смена провайдера стоит двух констант ниже,
 * а не переписывания клиента.
 */
interface OpenAiCompatApi {

    @POST("chat/completions")
    suspend fun chatCompletions(
        /** Целиком заголовок, вместе со словом `Bearer`. */
        @Header("Authorization") authorization: String,
        @Body body: ChatRequest,
    ): ChatResponse

    companion object {
        /**
         * Родной эндпоинт Z.ai, а не разрекламированный `/api/openai/v1`:
         * последний на этом ключе отдаёт `{"code":500,"msg":"404 NOT_FOUND"}`
         * на любой путь, включая `/models`. Схема тел при этом всё равно
         * OpenAI-совместимая.
         */
        const val BASE_URL = "https://api.z.ai/api/paas/v4/"

        /**
         * Бесплатная модель Z.ai. Выбрана не за качество, а за доступность:
         * Anthropic, Google и Groq не отвечают на российские IP, а платить
         * за разбор одной короткой фразы не за что.
         *
         * В списке `GET /models` её нет — там только платные, — но запросы
         * она принимает и денег не просит, в отличие от `glm-4.5-air`,
         * который отвечает «Insufficient balance».
         */
        const val MODEL = "glm-4.7-flash"
    }
}
