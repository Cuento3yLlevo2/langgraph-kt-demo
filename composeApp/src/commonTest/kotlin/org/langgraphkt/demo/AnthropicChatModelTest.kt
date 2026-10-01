package org.langgraphkt.demo

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.langgraphkt.demo.llm.AnthropicChatModel
import org.langgraphkt.demo.llm.ChatMessage
import org.langgraphkt.demo.llm.ChatModelException
import org.langgraphkt.demo.llm.ChatRequest
import org.langgraphkt.demo.workflows.ToolAgent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AnthropicChatModelTest {
    private var sent: HttpRequestData? = null

    private fun model(status: HttpStatusCode, body: String) = AnthropicChatModel(
        HttpClient(
            MockEngine { request ->
                sent = request
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
            },
        ),
        apiKey = "test-key",
    )

    private fun sentBody(): JsonObject = Json.parseToJsonElement((sent!!.body as TextContent).text).jsonObject

    @Test
    fun sendsTheRequestTheMessagesApiExpects() = runTest {
        val model = model(HttpStatusCode.OK, """{"stop_reason":"end_turn","content":[{"type":"text","text":"Hi"}]}""")

        val reply = model.chat(ChatRequest(listOf(ChatMessage.user("Hello")), "Be brief", listOf(ToolAgent.calculate.spec)))

        assertEquals("Hi", reply.text)
        val request = sent!!
        assertEquals("https://api.anthropic.com/v1/messages", request.url.toString())
        assertEquals("test-key", request.headers["x-api-key"])
        assertEquals("2023-06-01", request.headers["anthropic-version"])
        assertEquals("true", request.headers["anthropic-dangerous-direct-browser-access"])
        val body = sentBody()
        assertEquals("claude-opus-5-5", body["model"]!!.jsonPrimitive.content)
        assertEquals("Be brief", body["system"]!!.jsonPrimitive.content)
        assertEquals("calculate", body["tools"]!!.jsonArray.single().jsonObject["name"]!!.jsonPrimitive.content)
        assertEquals(
            """[{"role":"user","content":[{"type":"text","text":"Hello"}]}]""",
            body["messages"].toString(),
        )
        assertNull(body["thinking"])
    }

    @Test
    fun keepsEveryContentBlockSoTheTurnCanBeSentBackUnchanged() = runTest {
        val content =
            """[{"type":"thinking","thinking":"","signature":"sig"},""" +
                """{"type":"tool_use","id":"toolu_1","name":"calculate","input":{"expression":"1+1"}}]"""
        val model = model(HttpStatusCode.OK, """{"stop_reason":"tool_use","content":$content}""")

        val reply = model.chat(ChatRequest(listOf(ChatMessage.user("1+1?"))))

        assertEquals("calculate", reply.toolCalls.single().name)
        assertEquals(content, Json.encodeToString(reply.content))
    }

    @Test
    fun reportsApiErrorsWithTheirMessage() = runTest {
        val model = model(
            HttpStatusCode.Unauthorized,
            """{"type":"error","error":{"type":"authentication_error","message":"invalid x-api-key"}}""",
        )

        val error = assertFailsWith<ChatModelException> { model.chat(ChatRequest(listOf(ChatMessage.user("Hello")))) }

        assertEquals("Claude API authentication_error: invalid x-api-key", error.message)
    }

    @Test
    fun treatsARefusalAsAFailure() = runTest {
        val model = model(HttpStatusCode.OK, """{"stop_reason":"refusal","content":[]}""")

        assertFailsWith<ChatModelException> { model.chat(ChatRequest(listOf(ChatMessage.user("Hello")))) }
    }
}
