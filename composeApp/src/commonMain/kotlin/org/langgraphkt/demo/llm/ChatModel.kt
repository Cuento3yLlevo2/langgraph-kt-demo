package org.langgraphkt.demo.llm

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * One turn of a conversation.
 *
 * [content] holds the content blocks exactly as the Messages API sends and expects them
 * (`text`, `tool_use`, `tool_result`, `thinking`, ...). Keeping the raw blocks means an assistant
 * turn can be sent back unchanged, which the API requires for thinking blocks.
 */
@Serializable
data class ChatMessage(val role: String, val content: List<JsonObject>) {
    /** The text blocks of this message joined together. */
    val text: String
        get() = content.filter { it.type == "text" }.joinToString("\n") { it.string("text").orEmpty() }

    /** The tool calls the model asked for in this message. */
    val toolCalls: List<ToolCall>
        get() = content.filter { it.type == "tool_use" }.map {
            ToolCall(
                id = it.string("id").orEmpty(),
                name = it.string("name").orEmpty(),
                input = it["input"]?.jsonObject ?: JsonObject(emptyMap()),
            )
        }

    /** The tool results carried by this message. */
    val toolResults: List<ToolResult>
        get() = content.filter { it.type == "tool_result" }.map {
            ToolResult(
                toolCallId = it.string("tool_use_id").orEmpty(),
                content = it.string("content").orEmpty(),
                isError = it["is_error"]?.jsonPrimitive?.contentOrNull == "true",
            )
        }

    companion object {
        const val USER: String = "user"
        const val ASSISTANT: String = "assistant"

        fun user(text: String): ChatMessage = ChatMessage(USER, listOf(textBlock(text)))

        fun assistant(text: String): ChatMessage = ChatMessage(ASSISTANT, listOf(textBlock(text)))

        fun assistantToolCalls(calls: List<ToolCall>): ChatMessage = ChatMessage(
            ASSISTANT,
            calls.map {
                buildJsonObject {
                    put("type", "tool_use")
                    put("id", it.id)
                    put("name", it.name)
                    put("input", it.input)
                }
            },
        )

        /** All results of one assistant turn go back in a single user message. */
        fun toolResults(results: List<ToolResult>): ChatMessage = ChatMessage(
            USER,
            results.map {
                buildJsonObject {
                    put("type", "tool_result")
                    put("tool_use_id", it.toolCallId)
                    put("content", it.content)
                    if (it.isError) put("is_error", true)
                }
            },
        )

        private fun textBlock(text: String): JsonObject = buildJsonObject {
            put("type", "text")
            put("text", text)
        }
    }
}

data class ToolCall(val id: String, val name: String, val input: JsonObject)

data class ToolResult(val toolCallId: String, val content: String, val isError: Boolean = false)

/** A tool the model may call. [inputSchema] is a JSON Schema object. */
data class ToolSpec(val name: String, val description: String, val inputSchema: JsonObject)

data class ChatRequest(
    val messages: List<ChatMessage>,
    val system: String? = null,
    val tools: List<ToolSpec> = emptyList(),
)

/** The language model behind the demo workflows. */
fun interface ChatModel {
    /** Returns the assistant's next message. Throws [ChatModelException] when the call fails. */
    suspend fun chat(request: ChatRequest): ChatMessage
}

class ChatModelException(message: String, cause: Throwable? = null) : Exception(message, cause)

private val JsonObject.type: String?
    get() = string("type")

private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
