package org.langgraphkt.demo.llm

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Calls the Claude Messages API over plain HTTP. There is no official Anthropic SDK for
 * Kotlin Multiplatform, so the request is built by hand.
 *
 * The key is sent straight from the user's browser, which the API only allows with the
 * `anthropic-dangerous-direct-browser-access` header. This is acceptable here because each user
 * supplies their own key; never ship an app with a key of yours embedded in it.
 */
class AnthropicChatModel(
    private val client: HttpClient,
    private val apiKey: String,
    private val model: ClaudeModel = ClaudeModels.default,
) : ChatModel {
    override suspend fun chat(request: ChatRequest): ChatMessage {
        val responseText: String
        val succeeded: Boolean
        try {
            val response = client.post(MESSAGES_URL) {
                header("x-api-key", apiKey)
                header("anthropic-version", API_VERSION)
                if (model.hasFallbacks) header("anthropic-beta", FALLBACK_BETA)
                header("anthropic-dangerous-direct-browser-access", "true")
                contentType(ContentType.Application.Json)
                setBody(requestBody(request).toString())
            }
            responseText = response.bodyAsText()
            succeeded = response.status.isSuccess()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw ChatModelException("Could not reach the Claude API: ${e.message ?: e::class.simpleName}", e)
        }

        val body = try {
            json.parseToJsonElement(responseText).jsonObject
        } catch (e: SerializationException) {
            throw ChatModelException("The Claude API returned a response that is not JSON.", e)
        }
        if (!succeeded) {
            val error = body["error"]?.jsonObject
            val type = error?.get("type")?.jsonPrimitive?.contentOrNull ?: "error"
            val message = error?.get("message")?.jsonPrimitive?.contentOrNull ?: responseText
            throw ChatModelException("Claude API $type: $message")
        }

        // A refusal is a successful response, so check the stop reason before reading the content.
        when (body["stop_reason"]?.jsonPrimitive?.contentOrNull) {
            "refusal" -> throw ChatModelException("Claude declined this request.")
            "max_tokens" -> throw ChatModelException("Claude's reply was cut off at the output limit.")
        }
        val content = body["content"]?.jsonArray?.map { it.jsonObject }.orEmpty()
        return ChatMessage(ChatMessage.ASSISTANT, content)
    }

    private fun requestBody(request: ChatRequest): JsonObject = buildJsonObject {
        put("model", model.id)
        put("max_tokens", MAX_TOKENS)
        // If a safety classifier declines the request, the API re-runs it on a fallback model.
        if (model.hasFallbacks) put("fallbacks", "default")
        if (model.takesEffort) putJsonObject("output_config") { put("effort", EFFORT) }
        request.system?.let { put("system", it) }
        if (request.tools.isNotEmpty()) {
            putJsonArray("tools") {
                request.tools.forEach { tool ->
                    add(
                        buildJsonObject {
                            put("name", tool.name)
                            put("description", tool.description)
                            put("input_schema", tool.inputSchema)
                        },
                    )
                }
            }
        }
        put("messages", json.encodeToJsonElement(request.messages))
    }

    private companion object {
        const val MESSAGES_URL = "https://api.anthropic.com/v1/messages"
        const val API_VERSION = "2023-06-01"
        const val FALLBACK_BETA = "server-side-fallback-2026-07-01"
        const val MAX_TOKENS = 16000
        const val EFFORT = "medium"

        val json = Json { ignoreUnknownKeys = true }
    }
}
