package org.langgraphkt.demo

import kotlinx.coroutines.test.runTest
import dev.deeptelar.telar.END
import dev.deeptelar.telar.GraphConfig
import dev.deeptelar.telar.GraphResult
import dev.deeptelar.telar.MemoryCheckpointer
import dev.deeptelar.telar.NodeExecutionException
import dev.deeptelar.telar.START
import dev.deeptelar.telar.agent.ChatMessage
import org.langgraphkt.demo.game.Agent
import org.langgraphkt.demo.game.Desk
import org.langgraphkt.demo.game.HelpDesks
import org.langgraphkt.demo.game.Ticket
import org.langgraphkt.demo.game.scriptedModel
import org.langgraphkt.demo.game.stages
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The graph of every stage, run the way the tutorial runs its levels. */
class StagesTest {
    private val desk = Desk(scriptedModel())
    private val ana = Ticket("Ana", "Where is my pizza?")

    private fun pausingBeforePay() =
        GraphConfig(threadId = "t", checkpointer = MemoryCheckpointer<Ticket>(), interruptBefore = setOf(HelpDesks.PAY))

    @Test
    fun everyBoardPlacesEachNodeOfItsGraphOnACellOfItsOwn() {
        stages.forEach { stage ->
            val nodes = stage.graph(desk).topology.nodes
            assertEquals((nodes + START + END).toSet(), stage.board.keys, "stage ${stage.number}")
            assertEquals(stage.board.size, stage.board.values.toSet().size, "stage ${stage.number} puts two nodes on one cell")
            assertTrue(nodes.containsAll(stage.pauseBefore), "stage ${stage.number}")
        }
    }

    @Test
    fun aLineReadsTheTopicAndAnswers() = runTest {
        val ticket = HelpDesks.line(desk).invoke(ana).state

        assertEquals("delivery", ticket.topic)
        assertEquals("Hi Ana, thanks for writing to Pixel Pizza. We filed this under: delivery.", ticket.reply)
    }

    @Test
    fun choicesSendEachTopicDownItsOwnPath() = runTest {
        val graph = HelpDesks.choices(desk)

        assertEquals("Hi Ana, your pizza left the oven and is on its way.", graph.invoke(ana).state.reply)
        assertEquals("We are sorry, Ben. Your money is on its way back.", graph.invoke(Ticket("Ben", "I want a refund")).state.reply)
        assertEquals("Thanks for your message, Cleo. A colleague will reply soon.", graph.invoke(Ticket("Cleo", "Do you sell salad?")).state.reply)
    }

    @Test
    fun theLoopRewritesTheReplyUntilItPassesTheCheck() = runTest {
        val ticket = HelpDesks.loops(desk).invoke(Ticket("Ana", "My pizza is late!")).state

        assertEquals(3, ticket.attempts)
        assertEquals("", ticket.problem)
        assertEquals("Sorry Ana, your pizza is late. It arrives in 10 minutes.", ticket.reply)
    }

    @Test
    fun bothLookupsWriteTheirFactIntoTheTicket() = runTest {
        val ticket = HelpDesks.parallel(desk).invoke(ana).state

        assertEquals(listOf("your pizza left the oven", "the driver is 5 minutes away"), ticket.facts)
        assertEquals("Hi Ana, your pizza left the oven and the driver is 5 minutes away.", ticket.reply)
    }

    @Test
    fun theRunStopsBeforePayAndPaysWhatThePlayerDecides() = runTest {
        val graph = HelpDesks.savePoints(desk)
        val refund = Ticket("Ben", "My pizza arrived cold. I want a refund.")

        val approving = pausingBeforePay()
        val paused = graph.invoke(refund, approving)
        assertIs<GraphResult.Interrupted<Ticket>>(paused)
        assertEquals(12, paused.state.refund)
        assertEquals("", paused.state.reply)
        assertEquals("Sorry Ben! We sent you 12 euros.", graph.resume(approving) { it.copy(approved = true) }.state.reply)

        val denying = pausingBeforePay()
        graph.invoke(refund, denying)
        assertEquals("Sorry Ben, we cannot refund this order.", graph.resume(denying) { it.copy(approved = false) }.state.reply)
    }

    @Test
    fun theAgentAsksAToolAndAnswersWithItsResult() = runTest {
        val ticket = Agent.graph(desk).invoke(ana).state

        assertEquals("Hi Ana! The pizza for Ana left the oven and the driver is 5 minutes away.", ticket.reply)
        assertEquals(4, ticket.chat.size)
        assertEquals("order_status", (ticket.chat[1] as ChatMessage.Assistant).toolCalls.single().name)
    }

    @Test
    fun theAgentRunsSeveralToolsInOneTurn() = runTest {
        val ticket = Agent.graph(desk).invoke(Ticket("Ben", "How much is a margherita and a cola?")).state

        assertEquals(listOf("One margherita costs 9 euros.", "One cola costs 2 euros."), ticket.facts)
        assertEquals("Hi Ben! One margherita costs 9 euros. One cola costs 2 euros.", ticket.reply)
    }

    @Test
    fun aFailingToolIsReportedToTheModelInsteadOfFailingTheRun() = runTest {
        val ticket = Agent.graph(desk).invoke(Ticket("Cleo", "Do you sell sushi?")).state

        assertTrue((ticket.chat[2] as ChatMessage.ToolResult).isError)
        assertEquals("Hi Cleo! Pixel Pizza does not sell sushi.", ticket.reply)
    }

    @Test
    fun theAgentAnswersDirectlyWhenNoToolFits() = runTest {
        val ticket = Agent.graph(desk).invoke(Ticket("Ana", "You are great")).state

        assertEquals(2, ticket.chat.size)
        assertEquals("Hi Ana, thanks for writing to Pixel Pizza. A colleague will reply soon.", ticket.reply)
    }

    @Test
    fun aBusyPhoneFailsTheRunAndResumeRetriesTheFailedNode() = runTest {
        val graph = HelpDesks.gameOver(desk)
        val config = GraphConfig(threadId = "t", checkpointer = MemoryCheckpointer<Ticket>())

        val failure = assertFailsWith<NodeExecutionException> { graph.invoke(ana, config) }
        assertEquals(HelpDesks.KITCHEN, failure.nodeName)

        assertEquals("Hi Ana! Your pizza is in the oven.", graph.resume(config).state.reply)
    }

    @Test
    fun theFullDeskLooksUpADeliveryAndRewritesTheReplyOnce() = runTest {
        val ticket = HelpDesks.fullDesk(desk).invoke(ana, pausingBeforePay()).state

        assertEquals(2, ticket.attempts)
        assertEquals("Hi Ana, your pizza left the oven and the driver is 5 minutes away.", ticket.reply)
    }

    @Test
    fun theFullDeskStopsARefundBeforePay() = runTest {
        val graph = HelpDesks.fullDesk(desk)
        val config = pausingBeforePay()

        val paused = graph.invoke(Ticket("Ben", "My pizza arrived cold. I want a refund."), config)
        assertIs<GraphResult.Interrupted<Ticket>>(paused)
        assertEquals(listOf(HelpDesks.PAY), paused.nextNodes)

        assertEquals("Sorry Ben! We sent you 12 euros.", graph.resume(config) { it.copy(approved = true) }.state.reply)
    }

    @Test
    fun theFullDeskAnswersAnythingElseWithoutALookup() = runTest {
        val ticket = HelpDesks.fullDesk(desk).invoke(Ticket("Cleo", "Do you sell salad?"), pausingBeforePay()).state

        assertEquals(emptyList(), ticket.facts)
        assertEquals("Hi Cleo, a colleague will reply soon.", ticket.reply)
    }
}
