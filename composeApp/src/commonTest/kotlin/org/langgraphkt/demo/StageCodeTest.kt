package org.langgraphkt.demo

import dev.deeptelar.telar.END
import dev.deeptelar.telar.START
import org.langgraphkt.demo.game.CodePart
import org.langgraphkt.demo.game.stages
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The code a tile shows is cut from the source of the stage, so these tests read the real files. */
class StageCodeTest {
    private fun code(stage: Int, node: String): Map<String, String> =
        stages[stage - 1].code.of(node).associate { it.title to it.code }

    @Test
    fun everyTileOfEveryBoardHasCode() {
        for (stage in stages) {
            for (node in stage.board.keys) {
                val parts = stage.code.of(node)
                assertTrue(parts.isNotEmpty() && parts.all { it.code.isNotBlank() }, "stage ${stage.number}, $node")
                // A tile that has to fall back on the whole graph means its node was not found.
                assertTrue(parts.none { it.title == "the graph" } || node == END, "stage ${stage.number}, $node shows the whole graph")
            }
        }
    }

    @Test
    fun aNodeShowsItsCodeItsArrowsAndWhatItCalls() {
        assertEquals(
            listOf(
                CodePart(
                    "the node",
                    """
                    val read = node("read") { ticket ->
                        desk.work()
                        ticket.copy(topic = topicOf(ticket.message))
                    }
                    """.trimIndent(),
                ),
                CodePart("its arrows", "START then read then answer then END"),
                CodePart(
                    "it uses",
                    """
                    /** A real help desk would ask a model. Looking for keywords is enough to learn the moves. */
                    fun topicOf(message: String): String = when {
                        "refund" in message.lowercase() -> "refund"
                        "where" in message.lowercase() -> "delivery"
                        else -> "other"
                    }
                    """.trimIndent(),
                ),
            ),
            stages[0].code.of("read"),
        )
    }

    @Test
    fun aRouterShowsItsConditionalEdge() {
        val arrows = code(2, "read").getValue("its arrows")

        assertTrue(arrows.startsWith("START then read\nconditionalEdge(read, targets = setOf(track, refund, answer)) { ticket ->"), arrows)
        assertTrue(arrows.endsWith("}"), arrows)
        // A node that the router can pick shows the edge that leads to it, and its own way out.
        assertTrue(code(2, "track").getValue("its arrows").endsWith("}\ntrack then END"))
    }

    @Test
    fun aNodeNamedByAConstantIsFound() {
        val kitchen = code(4, "kitchen")

        assertTrue(kitchen.getValue("the node").contains("val kitchen = node(KITCHEN, work = { askKitchen(desk) })"))
        assertTrue(kitchen.getValue("the node").startsWith("// `work` asks and returns what it found."), "a comment stays with its statement")
        assertEquals("START then kitchen then answer", kitchen.getValue("its arrows"))
        assertTrue(kitchen.getValue("it uses").contains("private suspend fun askKitchen(desk: Desk): String {"))
    }

    @Test
    fun aNodeShowsWhatAParameterOfTheGraphStandsFor() {
        assertTrue(code(7, "kitchen").getValue("it uses").contains("class BusyPhone {"))
        assertTrue("it uses" !in code(7, "greet"))
    }

    @Test
    fun startAndEndShowTheirArrows() {
        assertTrue(code(6, START).getValue("arrows from START").startsWith("START then toolLoop("))
        assertEquals(mapOf("arrows from START" to "START then kitchen then answer\nSTART then driver then answer"), code(4, START))
        assertEquals(mapOf("arrows to END" to "answer then END"), code(4, END))
        assertTrue(code(3, END).getValue("arrows to END").contains("NodeRef.END else write"))
    }

    @Test
    fun theNodesOfTheAgentShowTheLoopAndItsTools() {
        for (node in listOf("assistant", "tools")) {
            val parts = code(6, node)

            assertTrue(parts.getValue("the agent loop adds this node").startsWith("START then toolLoop("), node)
            assertTrue(parts.getValue("it uses").contains("val menuPrice: Tool = Tool<MenuLookup>("), node)
            assertTrue(parts.getValue("it uses").contains("private val menu = mapOf("), "what a tool uses is shown too")
        }
    }

    @Test
    fun theWriterOfTheLastStageShowsHowItBuildsItsPrompt() {
        val write = code(8, "write")

        assertTrue(write.getValue("the node").contains("desk.model.chatWithProgress(writerPrompt(ticket), WRITER_SYSTEM)"))
        assertTrue(write.getValue("it uses").contains("private fun writerPrompt(ticket: Ticket): String = buildString {"))
        assertTrue(write.getValue("it uses").startsWith("const val WRITER_SYSTEM: String =\n    \"You write replies"), "a prompt is shown")
        assertTrue(write.getValue("its arrows").contains("write then check"))
    }
}
