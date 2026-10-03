package org.langgraphkt.demo.game

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.langgraphkt.CompiledGraph
import org.langgraphkt.NodeRef
import org.langgraphkt.START
import org.langgraphkt.StateGraph
import org.langgraphkt.demo.llm.ChatMessage
import org.langgraphkt.demo.llm.ChatRequest
import org.langgraphkt.demo.llm.Responder
import org.langgraphkt.demo.llm.ToolCall
import org.langgraphkt.demo.llm.ToolResult
import org.langgraphkt.demo.llm.ToolSpec

/** A tool the agent can run. [run] returns the text handed back to the model. */
class Tool(val spec: ToolSpec, val run: suspend (JsonObject) -> String)

/** Stage 6: a model that decides by itself whether to answer or to ask a tool first. */
object Agent {
    const val ASSISTANT: String = "assistant"
    const val TOOLS: String = "tools"

    const val SYSTEM: String =
        "You work at the help desk of Pixel Pizza. Answer the customer in one or two friendly sentences and use their name. " +
            "Use the order_status tool for questions about a delivery and the menu_price tool for questions about what " +
            "we sell or what it costs. Never guess a price."

    private const val CUSTOMER = "Customer: "
    private const val MESSAGE = "Message: "

    /**
     * The classic agent loop as a graph: the model answers or asks for tools, the tools run, and
     * their results go back to the model until it answers in plain text.
     */
    fun graph(desk: Desk, tools: List<Tool> = deskTools): CompiledGraph<Ticket> = StateGraph<Ticket> {
        val assistant = node(ASSISTANT) { ticket ->
            val chat = ticket.chat.ifEmpty { listOf(ChatMessage.user("$CUSTOMER${ticket.customer}\n$MESSAGE${ticket.message}")) }
            val answer = desk.model.chat(ChatRequest(chat, SYSTEM, tools.map { it.spec }))
            ticket.copy(chat = chat + answer, reply = if (answer.toolCalls.isEmpty()) answer.text.trim() else ticket.reply)
        }
        val runTools = node(TOOLS) { ticket ->
            desk.work()
            val results = coroutineScope {
                ticket.chat.last().toolCalls.map { call -> async { runTool(tools, call) } }.awaitAll()
            }
            ticket.copy(chat = ticket.chat + ChatMessage.toolResults(results), facts = ticket.facts + results.map { it.content })
        }

        START then assistant
        conditionalEdge(assistant, targets = setOf(runTools, NodeRef.END)) { ticket ->
            if (ticket.chat.last().toolCalls.isEmpty()) NodeRef.END else runTools
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

    val orderStatus: Tool = Tool(
        ToolSpec(
            name = "order_status",
            description = "Returns where a customer's order is right now. The data is made up for the game.",
            inputSchema = stringInputSchema("customer", "The customer's name, for example \"Ana\""),
        ),
    ) { input -> "The pizza for ${input.requireString("customer")} left the oven and the driver is 5 minutes away." }

    private val menu = mapOf("margherita" to 9, "pepperoni" to 11, "salad" to 6, "cola" to 2)

    val menuPrice: Tool = Tool(
        ToolSpec(
            name = "menu_price",
            description = "Returns the price of one item on the Pixel Pizza menu, or an error if we do not sell it.",
            inputSchema = stringInputSchema("item", "The item, for example \"margherita\""),
        ),
    ) { input ->
        val item = input.requireString("item").trim().lowercase()
        val price = menu[item] ?: throw IllegalArgumentException("Pixel Pizza does not sell $item.")
        "One $item costs $price euros."
    }

    val deskTools: List<Tool> = listOf(orderStatus, menuPrice)

    /** Scripted behaviour: calls a tool when the message clearly needs one, then reports what came back. */
    val script: Responder = Responder { request ->
        if (request.system != SYSTEM) return@Responder null
        val ticket = request.messages.first().text.lines()
        val customer = ticket.first().removePrefix(CUSTOMER)
        val results = request.messages.last().toolResults
        if (results.isNotEmpty()) {
            return@Responder ChatMessage.assistant("Hi $customer! " + results.joinToString(" ") { it.content })
        }
        val message = ticket.last().removePrefix(MESSAGE).lowercase()
        val items = (menu.keys.filter { it in message } + listOfNotNull(sells.find(message)?.groupValues?.get(1))).distinct()
        val calls = buildList {
            if ("where" in message) {
                add(ToolCall("toolu_scripted_status", orderStatus.spec.name, buildJsonObject { put("customer", customer) }))
            }
            items.forEach { item ->
                add(ToolCall("toolu_scripted_price_$item", menuPrice.spec.name, buildJsonObject { put("item", item) }))
            }
        }
        if (calls.isNotEmpty()) {
            ChatMessage.assistantToolCalls(calls)
        } else {
            ChatMessage.assistant("Hi $customer, thanks for writing to Pixel Pizza. A colleague will reply soon.")
        }
    }

    private val sells = Regex("""sell (\p{L}+)""")

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
