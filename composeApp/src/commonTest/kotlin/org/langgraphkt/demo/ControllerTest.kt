package org.langgraphkt.demo

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.langgraphkt.demo.storage.MemoryStore
import org.langgraphkt.demo.ui.EmailController
import org.langgraphkt.demo.ui.EmailPhase
import org.langgraphkt.demo.ui.ToolAgentController
import org.langgraphkt.demo.workflows.EmailApproval
import org.langgraphkt.demo.workflows.ToolAgent
import org.langgraphkt.demo.workflows.scriptedDemoModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The controllers behind the screens, driven the way the buttons drive them. */
@OptIn(ExperimentalCoroutinesApi::class)
class ControllerTest {
    private val slowModel = scriptedDemoModel(delayMillis = 1_000)

    @Test
    fun stopCancelsARunningToolAgent() = runTest {
        val controller = ToolAgentController(slowModel, this)

        controller.send("What is 12 * 7?")
        advanceTimeBy(500)

        assertTrue(controller.running)
        assertEquals(setOf(ToolAgent.ASSISTANT), controller.activeNodes)

        controller.stop()
        advanceUntilIdle()

        assertFalse(controller.running)
        assertEquals(emptySet(), controller.activeNodes)
        assertNull(controller.error)
        assertEquals(1, controller.messages.size)
    }

    @Test
    fun aStoppedToolAgentAcceptsTheNextMessage() = runTest {
        val controller = ToolAgentController(slowModel, this)
        controller.send("What is 12 * 7?")
        advanceTimeBy(500)
        controller.stop()
        advanceUntilIdle()

        controller.send("And 2 + 3?")
        advanceUntilIdle()

        assertFalse(controller.running)
        assertTrue(controller.messages.last().text.contains("5"), controller.messages.last().text)
    }

    @Test
    fun stoppingARedraftLeavesTheEmailUnfinishedAndContinueFinishesIt() = runTest {
        val store = MemoryStore()
        val controller = EmailController(slowModel, store, this)
        controller.start("Ana", "confirm the meeting on Friday")
        advanceUntilIdle()
        assertEquals(EmailPhase.AwaitingReview, controller.phase)

        controller.requestChanges("make it shorter")
        advanceTimeBy(500)
        assertEquals(setOf(EmailApproval.DRAFT), controller.activeNodes)

        controller.stop()
        advanceUntilIdle()

        // The saved run now stands before `draft`, so offering Approve would be wrong.
        assertFalse(controller.running)
        assertEquals(EmailPhase.Unfinished, controller.phase)

        controller.continueRun()
        advanceUntilIdle()

        assertEquals(EmailPhase.AwaitingReview, controller.phase)
        assertEquals(2, controller.state?.revisions)
        assertTrue(controller.state?.draft.orEmpty().contains("make it shorter"))
    }

    @Test
    fun aNewSessionRestoresAnEmailThatAwaitsReview() = runTest {
        val store = MemoryStore()
        EmailController(scriptedDemoModel(), store, this).start("Ana", "confirm the meeting on Friday")
        advanceUntilIdle()

        val reloaded = EmailController(scriptedDemoModel(), store, this)
        advanceUntilIdle()

        assertEquals(EmailPhase.AwaitingReview, reloaded.phase)
        assertTrue(reloaded.restored)
        assertTrue(reloaded.state?.draft.orEmpty().contains("Hi Ana,"))
    }

    @Test
    fun discardingWhileDraftingLeavesNothingBehind() = runTest {
        val store = MemoryStore()
        val controller = EmailController(slowModel, store, this)
        controller.start("Ana", "confirm the meeting on Friday")
        advanceTimeBy(500)

        controller.discard()
        advanceUntilIdle()

        assertFalse(controller.running)
        assertEquals(EmailPhase.Idle, controller.phase)
        assertNull(controller.state)

        val reloaded = EmailController(scriptedDemoModel(), store, this)
        advanceUntilIdle()
        assertEquals(EmailPhase.Idle, reloaded.phase)
    }
}
