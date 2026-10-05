package org.langgraphkt.demo

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.langgraphkt.agent.ChatMessage
import org.langgraphkt.agent.ChatModel
import org.langgraphkt.agent.ChatModelException
import org.langgraphkt.agent.ChatRequest
import org.langgraphkt.agent.textDelta
import org.langgraphkt.demo.game.Agent
import org.langgraphkt.demo.game.Desk
import org.langgraphkt.demo.game.Ticket
import org.langgraphkt.demo.llm.ClaudeModel
import org.langgraphkt.demo.llm.ClaudeModels

/** What the game adds to the library's `AnthropicChatModel`: the models on offer and what each of them takes. */
class ClaudeModelsTest {
    private val sent = mutableListOf<HttpRequestData>()
    private val hello = ChatRequest(listOf(ChatMessage.User("Hello")))

    /** A model whose requests are answered with [replies], in order. */
    private fun model(vararg replies: Pair<HttpStatusCode, String>, model: ClaudeModel = ClaudeModels.default): ChatModel {
        val queue = ArrayDeque(replies.toList())
        val client = HttpClient(
            MockEngine { request ->
                sent += request
                val (status, body) = queue.removeFirst()
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
            },
        )
        return model.chatModel(client, apiKey = "test-key")
    }

    private fun sentBody(index: Int = 0): JsonObject = Json.parseToJsonElement((sent[index].body as TextContent).text).jsonObject

    private fun says(text: String) = HttpStatusCode.OK to """{"stop_reason":"end_turn","content":[{"type":"text","text":"$text"}]}"""

    @Test
    fun sendsTheKeyStraightFromTheBrowser() = runTest {
        val reply = model(says("Hi")).chat(hello.copy(system = "Be brief", tools = listOf(Agent.menuPrice.spec)))

        assertEquals("Hi", reply.message.text)
        val request = sent.single()
        assertEquals("https://api.anthropic.com/v1/messages", request.url.toString())
        assertEquals("test-key", request.headers["x-api-key"])
        assertEquals("true", request.headers["anthropic-dangerous-direct-browser-access"])
        assertEquals("claude-opus-5-5", sentBody()["model"]!!.jsonPrimitive.content)
        assertEquals("Be brief", sentBody()["system"]!!.jsonPrimitive.content)
        assertEquals("menu_price", sentBody()["tools"]!!.jsonArray.single().jsonObject["name"]!!.jsonPrimitive.content)
        assertNull(sentBody()["thinking"])
    }

    @Test
    fun leavesOutWhatTheChosenModelDoesNotTake() = runTest {
        model(says("Hi"), model = ClaudeModels.haiku).chat(hello)

        assertEquals("claude-haiku-4-5", sentBody()["model"]!!.jsonPrimitive.content)
        assertNull(sentBody()["output_config"])
        assertNull(sentBody()["fallbacks"])
        assertNull(sent[0].headers["anthropic-beta"])

        model(says("Hi"), model = ClaudeModels.sonnet).chat(hello)

        assertEquals("claude-sonnet-5-5", sentBody(1)["model"]!!.jsonPrimitive.content)
        assertEquals("medium", sentBody(1)["output_config"]!!.jsonObject["effort"]!!.jsonPrimitive.content)
        assertEquals("default", sentBody(1)["fallbacks"]!!.jsonPrimitive.content)
        assertEquals("server-side-fallback-2026-07-01", sent[1].headers["anthropic-beta"])
    }

    @Test
    fun theModelsAreListedFromCheapestToMostExpensive() {
        assertEquals(ClaudeModels.all.sortedBy { it.inputPrice }, ClaudeModels.all)
        assertEquals(ClaudeModels.all.sortedBy { it.outputPrice }, ClaudeModels.all)
        assertTrue(ClaudeModels.default in ClaudeModels.all)
    }

    @Test
    fun aStoredModelIdIsMatchedToAModelOnTheList() {
        assertEquals(ClaudeModels.haiku, ClaudeModels.byId("claude-haiku-4-5-20251001"))
        assertEquals(ClaudeModels.sonnet, ClaudeModels.byId(" claude-sonnet-5-5 "))
        assertEquals(ClaudeModels.default, ClaudeModels.byId("some-model-that-is-gone"))
        assertEquals(ClaudeModels.default, ClaudeModels.byId(""))
    }

    @Test
    fun theAgentSendsClaudesThinkingBackWithTheToolResult() = runTest {
        val content =
            """[{"type":"thinking","thinking":"","signature":"sig"},""" +
                """{"type":"tool_use","id":"toolu_1","name":"menu_price","input":{"item":"cola"}}]"""
        val model = model(HttpStatusCode.OK to """{"stop_reason":"tool_use","content":$content}""", says("Hi Ana! One cola costs 2 euros."))

        val ticket = Agent.graph(Desk(model)).invoke(Ticket("Ana", "How much is a cola?")).state

        assertEquals("Hi Ana! One cola costs 2 euros.", ticket.reply)
        assertEquals(listOf("One cola costs 2 euros."), ticket.facts)
        val turns = sentBody(1)["messages"]!!.jsonArray
        assertEquals(content, turns[1].jsonObject["content"].toString())
        assertEquals(
            """[{"type":"tool_result","tool_use_id":"toolu_1","content":"One cola costs 2 euros."}]""",
            turns[2].jsonObject["content"].toString(),
        )
    }

    @Test
    fun theAgentStreamsClaudesAnswerWhenTheRunIsWatched() = runTest {
        fun event(data: String) = "event: message\ndata: $data\n\n"
        val stream =
            event("""{"type":"message_start","message":{"content":[],"usage":{"input_tokens":9,"output_tokens":1}}}""") +
                event("""{"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}""") +
                event("""{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Hi Ana! "}}""") +
                event("""{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"We open at noon."}}""") +
                event("""{"type":"content_block_stop","index":0}""") +
                event("""{"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"output_tokens":8}}""") +
                event("""{"type":"message_stop"}""")
        val model = model(HttpStatusCode.OK to stream)

        val events = Agent.graph(Desk(model)).stream(Ticket("Ana", "When do you open?")).toList()

        assertEquals(listOf("Hi Ana! ", "We open at noon."), events.mapNotNull { it.textDelta })
        assertEquals("Hi Ana! We open at noon.", events.last().state.reply)
        // The fields the game adds for the chosen model travel with a streamed request too.
        assertEquals("true", sentBody()["stream"]!!.jsonPrimitive.content)
        assertEquals("default", sentBody()["fallbacks"]!!.jsonPrimitive.content)
        assertEquals("true", sent.single().headers["anthropic-dangerous-direct-browser-access"])
    }

    @Test
    fun reportsApiErrorsWithTheirMessage() = runTest {
        val model = model(
            HttpStatusCode.Unauthorized to """{"type":"error","error":{"type":"authentication_error","message":"invalid x-api-key"}}""",
        )

        val error = assertFailsWith<ChatModelException> { model.chat(hello) }

        assertEquals("Claude API error 401 (authentication_error): invalid x-api-key", error.message)
    }
}
