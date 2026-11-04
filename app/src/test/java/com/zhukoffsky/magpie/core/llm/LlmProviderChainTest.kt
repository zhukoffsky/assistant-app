package com.zhukoffsky.magpie.core.llm

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class LlmProviderChainTest {

    private class Api(private val answer: String?) : OpenAiCompatApi {
        val calls = mutableListOf<Pair<String, ChatRequest>>()

        override suspend fun chatCompletions(authorization: String, body: ChatRequest): ChatResponse {
            calls += authorization to body
            answer ?: throw IOException("HTTP 429 overloaded")
            return ChatResponse(listOf(ChatChoice(ChatMessage(role = "assistant", content = answer))))
        }
    }

    private val request = ChatRequest(
        model = "ignored",
        messages = listOf(ChatMessage(role = "user", content = "молоко хлеб")),
        maxTokens = 10,
    )

    @Test
    fun `overloaded primary hands the same request to the fallback with its own key and model`() = runTest {
        val primary = Api(answer = null)
        val fallback = Api(answer = "ok")
        val chain = LlmProviderChain(
            listOf(
                LlmProvider("z.ai", primary, apiKey = "zai-key", model = "glm"),
                LlmProvider("workers-ai", fallback, apiKey = "cf-token", model = "mistral"),
            ),
        )

        val response = chain.chatCompletions("Bearer whatever", request)

        assertEquals("ok", response.choices.single().message?.content)
        assertEquals("Bearer zai-key" to "glm", primary.calls.single().let { it.first to it.second.model })
        assertEquals("Bearer cf-token" to "mistral", fallback.calls.single().let { it.first to it.second.model })
        assertEquals(request.messages, fallback.calls.single().second.messages)
    }

    @Test
    fun `healthy primary is the only one asked`() = runTest {
        val primary = Api(answer = "ok")
        val fallback = Api(answer = "never")
        val chain = LlmProviderChain(
            listOf(
                LlmProvider("z.ai", primary, apiKey = "k", model = "glm"),
                LlmProvider("workers-ai", fallback, apiKey = "t", model = "mistral"),
            ),
        )

        chain.chatCompletions("Bearer whatever", request)

        assertTrue(fallback.calls.isEmpty())
    }

    @Test(expected = IOException::class)
    fun `all providers down surfaces the error, so parsers fall back to rules`() = runTest {
        val chain = LlmProviderChain(
            listOf(
                LlmProvider("z.ai", Api(answer = null), apiKey = "k", model = "glm"),
                LlmProvider("workers-ai", Api(answer = null), apiKey = "t", model = "mistral"),
            ),
        )

        chain.chatCompletions("Bearer whatever", request)
    }
}
