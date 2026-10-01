package org.langgraphkt.demo

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.langgraphkt.GraphEvent
import org.langgraphkt.GraphResult
import org.langgraphkt.demo.llm.ChatMessage
import org.langgraphkt.demo.llm.ChatModel
import org.langgraphkt.demo.llm.ToolCall
import org.langgraphkt.demo.workflows.AgentState
import org.langgraphkt.demo.workflows.ToolAgent
import org.langgraphkt.demo.workflows.scriptedDemoModel
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ToolAgentTest {
    private val graph = ToolAgent.graph(scriptedDemoModel())

    @Test
    fun loopsThroughTheToolAndAnswers() = runTest {
        val events = graph.stream(AgentState(listOf(ChatMessage.user("What is 12 * (3 + 4)?")))).toList()

        val steps = events.filterIsInstance<GraphEvent.StepCompleted<AgentState>>().map { it.nodes }
        assertEquals(listOf(listOf("assistant"), listOf("tools"), listOf("assistant")), steps)
        val messages = assertIs<GraphEvent.Completed<AgentState>>(events.last()).state.messages
        assertEquals("84", messages[2].toolResults.single().content)
        assertEquals("The result is 84.", messages.last().text)
    }

    @Test
    fun runsSeveralToolCallsOfOneTurnAndReturnsThemTogether() = runTest {
        val result = graph.invoke(AgentState(listOf(ChatMessage.user("What is 2+2, and what is the weather in New York?"))))

        val messages = assertIs<GraphResult.Completed<AgentState>>(result).state.messages
        assertEquals(listOf("calculate", "get_weather"), messages[1].toolCalls.map { it.name })
        val results = messages[2].toolResults
        assertEquals(2, results.size)
        assertEquals("4", results[0].content)
        assertTrue(results[1].content.endsWith("in New York"), results[1].content)
    }

    @Test
    fun answersDirectlyWhenNoToolIsNeeded() = runTest {
        val result = graph.invoke(AgentState(listOf(ChatMessage.user("Hello"))))

        assertEquals(2, result.state.messages.size)
    }

    @Test
    fun reportsAFailingToolToTheModelInsteadOfFailingTheRun() = runTest {
        var turn = 0
        val model = ChatModel { request ->
            if (turn++ == 0) {
                ChatMessage.assistantToolCalls(
                    listOf(
                        ToolCall("a", "calculate", buildJsonObject { put("expression", "1 / 0") }),
                        ToolCall("b", "no_such_tool", buildJsonObject { }),
                    ),
                )
            } else {
                ChatMessage.assistant(request.messages.last().toolResults.joinToString("; ") { "${it.isError}: ${it.content}" })
            }
        }

        val result = ToolAgent.graph(model).invoke(AgentState(listOf(ChatMessage.user("go"))))

        assertEquals("true: Division by zero; true: Unknown tool 'no_such_tool'", result.state.messages.last().text)
    }
}
