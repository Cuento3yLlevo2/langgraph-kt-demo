package org.langgraphkt.demo.workflows

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.langgraphkt.CompiledGraph
import org.langgraphkt.END
import org.langgraphkt.START
import org.langgraphkt.StateGraph
import org.langgraphkt.demo.llm.ChatMessage
import org.langgraphkt.demo.llm.ChatModel
import org.langgraphkt.demo.llm.ChatRequest
import org.langgraphkt.demo.llm.Responder
import org.langgraphkt.demo.llm.ToolCall
import org.langgraphkt.demo.llm.ToolResult
import org.langgraphkt.demo.llm.ToolSpec

/** State of the tool-calling agent: the whole conversation so far. */
@Serializable
data class AgentState(val messages: List<ChatMessage> = emptyList())

/** A tool the agent can run. [run] returns the text handed back to the model. */
class Tool(val spec: ToolSpec, val run: suspend (JsonObject) -> String)

object ToolAgent {
    const val ASSISTANT: String = "assistant"
    const val TOOLS: String = "tools"

    /** The order in which the UI draws the nodes. */
    val nodes: List<String> = listOf(START, ASSISTANT, TOOLS, END)

    const val SYSTEM: String =
        "You are a helpful assistant in a demo of the langgraph-kt library. " +
            "Use the calculate tool for any arithmetic and the get_weather tool for weather questions. " +
            "Keep answers short."

    /**
     * The classic agent loop as a graph: the model answers or asks for tools, the tools run, and
     * their results go back to the model until it answers in plain text.
     */
    fun graph(model: ChatModel, tools: List<Tool> = demoTools, tracker: RunTracker? = null): CompiledGraph<AgentState> =
        StateGraph<AgentState> {
            val assistant = node(ASSISTANT, tracker) { state ->
                val reply = model.chat(ChatRequest(state.messages, SYSTEM, tools.map { it.spec }))
                state.copy(messages = state.messages + reply)
            }
            val runTools = node(TOOLS, tracker) { state ->
                val results = coroutineScope {
                    state.messages.last().toolCalls.map { call -> async { runTool(tools, call) } }.awaitAll()
                }
                state.copy(messages = state.messages + ChatMessage.toolResults(results))
            }

            START then assistant
            conditionalEdge(assistant, targets = setOf(runTools.name, END)) { state ->
                if (state.messages.last().toolCalls.isEmpty()) END else runTools.name
            }
            runTools then assistant
        }.compile()

    /** A failing tool is reported to the model as an error result instead of failing the run. */
    private suspend fun runTool(tools: List<Tool>, call: ToolCall): ToolResult {
        val tool = tools.firstOrNull { it.spec.name == call.name }
            ?: return ToolResult(call.id, "Unknown tool '${call.name}'", isError = true)
        return try {
            ToolResult(call.id, tool.run(call.input))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ToolResult(call.id, e.message ?: "The tool failed", isError = true)
        }
    }

    val calculate: Tool = Tool(
        ToolSpec(
            name = "calculate",
            description = "Evaluates an arithmetic expression with + - * / and parentheses, for example \"12 * (3 + 4)\".",
            inputSchema = stringInputSchema("expression", "The expression to evaluate"),
        ),
    ) { input -> formatNumber(evaluate(input.requireString("expression"))) }

    val getWeather: Tool = Tool(
        ToolSpec(
            name = "get_weather",
            description = "Returns the current weather for a city. The data is made up for the demo.",
            inputSchema = stringInputSchema("city", "City name, for example \"Madrid\""),
        ),
    ) { input ->
        val city = input.requireString("city")
        val conditions = listOf("sunny", "cloudy", "rainy", "windy")
        val seed = city.lowercase().sumOf { it.code }
        "${conditions[seed % conditions.size]}, ${10 + seed % 20} °C in $city"
    }

    val demoTools: List<Tool> = listOf(calculate, getWeather)

    /** Scripted behaviour: calls a tool when the question clearly needs one, then reports its result. */
    val script: Responder = Responder { request ->
        if (request.system != SYSTEM) return@Responder null
        val last = request.messages.last()
        val results = last.toolResults
        if (results.isNotEmpty()) {
            return@Responder ChatMessage.assistant(results.joinToString(" ") { "The result is ${it.content}." })
        }
        val question = last.text
        val calls = buildList {
            arithmetic.find(question)?.value?.trim()?.let {
                add(ToolCall("toolu_scripted_calc", calculate.spec.name, buildJsonObject { put("expression", it) }))
            }
            weatherCity.find(question)?.groupValues?.get(1)?.let {
                add(ToolCall("toolu_scripted_weather", getWeather.spec.name, buildJsonObject { put("city", it) }))
            }
        }
        if (calls.isNotEmpty()) {
            ChatMessage.assistantToolCalls(calls)
        } else {
            ChatMessage.assistant("I am a scripted model. Ask me to calculate something, or about the weather in a city.")
        }
    }

    private val arithmetic = Regex("""[-(]*\d[\d.\s()]*[-+*/][-+*/\d.\s()]*\d\)*""")
    private val weatherCity = Regex("""weather in ([\p{L}][\p{L} ]*[\p{L}])""", RegexOption.IGNORE_CASE)

    private fun stringInputSchema(property: String, description: String): JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject(property) {
                put("type", "string")
                put("description", description)
            }
        }
        putJsonArray("required") { add(property) }
    }

    private fun JsonObject.requireString(key: String): String =
        this[key]?.jsonPrimitive?.contentOrNull ?: throw IllegalArgumentException("Missing '$key'")
}
