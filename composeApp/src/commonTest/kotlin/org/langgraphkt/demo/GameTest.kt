package org.langgraphkt.demo

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.langgraphkt.END
import org.langgraphkt.START
import org.langgraphkt.demo.game.Agent
import org.langgraphkt.demo.game.HelpDesks
import org.langgraphkt.demo.game.Mail
import org.langgraphkt.demo.game.scriptedModel
import org.langgraphkt.demo.storage.MemoryStore
import org.langgraphkt.demo.ui.Game
import org.langgraphkt.demo.ui.Phase
import org.langgraphkt.demo.ui.Tone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The game behind the screens, driven the way the buttons drive it. */
@OptIn(ExperimentalCoroutinesApi::class)
class GameTest {
    private val store = MemoryStore()
    private val delivery = Mail("Ana", "Where is my pizza?")
    private val refund = Mail("Ben", "My pizza arrived cold. I want a refund.")

    /** A game whose nodes each take a second, so a test can look at a run halfway. */
    private fun TestScope.game(workMillis: Long = 1_000) = Game(scriptedModel(delayMillis = workMillis), store, this, workMillis)

    @Test
    fun aRunLightsItsPathAndClearsTheStage() = runTest {
        val game = game()
        val stage = game.controller(2)

        stage.play(delivery)
        advanceTimeBy(500)
        assertTrue(stage.running)
        assertEquals(setOf("read"), stage.active)

        advanceUntilIdle()

        assertEquals(Phase.Clear, stage.phase)
        assertEquals(setOf(START, "read", "track", END), stage.visited)
        assertEquals(setOf(START to "read", "read" to "track", "track" to END), stage.trail)
        assertEquals(listOf("01" to "read", "02" to "track", "END" to "reply sent"), stage.log.map { it.tag to it.text })
        assertEquals(setOf(2), game.cleared)
    }

    @Test
    fun parallelNodesAreActiveTogetherAndShareAStep() = runTest {
        val stage = game().controller(4)

        stage.play(delivery)
        advanceTimeBy(500)
        assertEquals(setOf(HelpDesks.KITCHEN, "driver"), stage.active)

        advanceUntilIdle()

        assertEquals(2, stage.step)
        assertTrue(stage.trail.containsAll(setOf(HelpDesks.KITCHEN to "answer", "driver" to "answer")))
    }

    @Test
    fun aLoopLightsTheWayBack() = runTest {
        val stage = game().controller(3)

        stage.play(Mail("Ana", "My pizza is late!"))
        advanceUntilIdle()

        assertTrue("check" to "write" in stage.trail)
        assertEquals(3, stage.ticket?.attempts)
    }

    @Test
    fun aSavePointWaitsForThePlayerAndPaysWhatWasDecided() = runTest {
        val game = game()
        val stage = game.controller(5)

        stage.play(refund)
        advanceUntilIdle()

        assertEquals(Phase.SavePoint, stage.phase)
        assertEquals(listOf(HelpDesks.PAY), stage.waitingAt)
        assertEquals(emptySet(), game.cleared)

        stage.decide(approved = true)
        advanceUntilIdle()

        assertEquals(Phase.Clear, stage.phase)
        assertEquals("Sorry Ben! We sent you 12 euros.", stage.ticket?.reply)
        assertEquals(setOf(5), game.cleared)
    }

    @Test
    fun aNewSessionRestoresARunThatWaitsAtASavePoint() = runTest {
        game().controller(5).play(refund)
        advanceUntilIdle()

        val reloaded = game().controller(5)
        advanceUntilIdle()

        assertEquals(Phase.SavePoint, reloaded.phase)
        assertTrue(reloaded.restored)
        assertEquals(12, reloaded.ticket?.refund)

        reloaded.decide(approved = false)
        advanceUntilIdle()

        assertEquals("Sorry Ben, we cannot refund this order.", reloaded.ticket?.reply)
    }

    @Test
    fun aFailedNodeIsGameOverAndRetryPicksUpThere() = runTest {
        val game = game()
        val stage = game.controller(7)

        stage.play(delivery)
        advanceUntilIdle()

        assertEquals(Phase.GameOver, stage.phase)
        assertEquals(HelpDesks.KITCHEN, stage.failedNode)
        assertEquals("the kitchen phone is busy", stage.error)
        assertEquals(listOf(HelpDesks.KITCHEN), stage.waitingAt)
        assertEquals(Tone.Failed, stage.log.last().tone)
        assertEquals(emptySet(), game.cleared)

        stage.retry()
        advanceUntilIdle()

        assertEquals(Phase.Clear, stage.phase)
        assertNull(stage.failedNode)
        assertEquals("Hi Ana! Your pizza is in the oven.", stage.ticket?.reply)
        // The retry did not run `greet` again.
        assertEquals(listOf("01", "ERR", "02", "END"), stage.log.map { it.tag })
    }

    @Test
    fun stopLeavesTheRunUnfinishedAndContinueFinishesIt() = runTest {
        val stage = game().controller(1)

        stage.play(delivery)
        advanceTimeBy(1_500)
        assertEquals(setOf("answer"), stage.active)

        stage.stop()
        advanceUntilIdle()

        assertFalse(stage.running)
        assertEquals(emptySet(), stage.active)
        assertEquals(Phase.Unfinished, stage.phase)
        assertEquals(listOf("answer"), stage.waitingAt)

        stage.retry()
        advanceUntilIdle()

        assertEquals(Phase.Clear, stage.phase)
    }

    @Test
    fun theAgentsToolCallsAndResultsAreLogged() = runTest {
        val stage = game().controller(6)

        stage.play(Mail("Cleo", "Do you sell sushi?"))
        advanceUntilIdle()

        assertEquals(listOf("01", " >>", "02", " <<", "03", "END"), stage.log.map { it.tag })
        assertEquals(Tone.Failed, stage.log[3].tone)
        assertTrue(Agent.TOOLS to Agent.ASSISTANT in stage.trail)
    }

    @Test
    fun resetThrowsTheSavedRunAway() = runTest {
        val stage = game().controller(5)
        stage.play(refund)
        advanceUntilIdle()

        stage.reset()
        advanceUntilIdle()

        assertEquals(Phase.Ready, stage.phase)
        assertNull(stage.ticket)

        val reloaded = game().controller(5)
        advanceUntilIdle()
        assertEquals(Phase.Ready, reloaded.phase)
    }

    @Test
    fun clearedStagesSurviveANewSessionUntilTheyAreErased() = runTest {
        val game = game()
        game.controller(1).play(delivery)
        advanceUntilIdle()

        val reloaded = game()
        advanceUntilIdle()
        assertEquals(setOf(1), reloaded.cleared)

        reloaded.erase()
        advanceUntilIdle()

        assertEquals(emptySet(), game().also { advanceUntilIdle() }.cleared)
    }
}
