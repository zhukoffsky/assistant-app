package com.zhukoffsky.magpie.core.llm

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

@Serializable
data class AnthropicMessage(val role: String, val content: String)

@Serializable
data class MessagesRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val system: String,
    val messages: List<AnthropicMessage>,
)

@Serializable
data class ContentBlock(val type: String = "text", val text: String = "")

@Serializable
data class MessagesResponse(val content: List<ContentBlock> = emptyList())

interface AnthropicApi {

    @POST("v1/messages")
    suspend fun messages(
        @Header("x-api-key") apiKey: String,
        @Header("anthropic-version") version: String,
        @Body body: MessagesRequest,
    ): MessagesResponse

    companion object {
        const val BASE_URL = "https://api.anthropic.com/"
        const val VERSION = "2023-06-01"

        /** Самая дешёвая и быстрая модель: задача — разбор одной короткой фразы. */
        const val MODEL = "claude-haiku-4-5-20251001"
    }
}
