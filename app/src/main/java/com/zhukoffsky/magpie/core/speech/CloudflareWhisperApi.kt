package com.zhukoffsky.magpie.core.speech

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

@Serializable
data class WhisperRequest(
    /** Файл целиком, base64. Двоичное тело этот вариант модели не принимает. */
    val audio: String,
    val task: String = "transcribe",
    val language: String? = null,
)

@Serializable
data class WhisperResult(val text: String = "")

@Serializable
data class WhisperError(val code: Int = 0, val message: String = "")

@Serializable
data class WhisperResponse(
    val success: Boolean = false,
    val result: WhisperResult? = null,
    val errors: List<WhisperError> = emptyList(),
)

/**
 * Workers AI у Cloudflare: расшифровка записи одним запросом.
 *
 * Выбран по единственной причине, по которой здесь вообще выбирают
 * провайдеров, — он отвечает на российский интернет без VPN (проверено
 * 12 августа 2026 curl'ом с машины владельца: `403`, то есть жив и просит
 * ключ). Из тех же соображений отпали Anthropic, Google, Groq; SaluteSpeech
 * не ответил вовсе.
 *
 * Учётная запись задаётся **парой**: идентификатор идёт в адрес, токен — в
 * заголовок. Забыть половину легко, поэтому проверяются обе.
 */
interface CloudflareWhisperApi {

    /**
     * @param model путь модели вида `@cf/openai/whisper-large-v3-turbo`.
     *        `encoded = true` обязателен: в имени есть косые черты, и
     *        экранированные они превратились бы в один сегмент адреса,
     *        которого на той стороне нет.
     */
    @POST("client/v4/accounts/{account}/ai/run/{model}")
    suspend fun run(
        @Path("account") account: String,
        @Path(value = "model", encoded = true) model: String,
        /** Целиком заголовок, вместе со словом `Bearer`. */
        @Header("Authorization") authorization: String,
        @Body body: WhisperRequest,
    ): WhisperResponse

    companion object {
        const val BASE_URL = "https://api.cloudflare.com/"

        /**
         * Turbo, а не обычный `@cf/openai/whisper`: он заметно быстрее на
         * записи в полминуты и принимает подсказку языка, а русский без
         * подсказки Whisper иногда принимает за украинский или болгарский.
         *
         * Смена модели — эта строка. Смена провайдера — этот файл целиком,
         * и ради того он и отделён от остального.
         */
        const val MODEL = "@cf/openai/whisper-large-v3-turbo"
    }
}
