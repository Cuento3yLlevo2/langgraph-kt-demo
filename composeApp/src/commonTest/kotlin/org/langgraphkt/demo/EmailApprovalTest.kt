package org.langgraphkt.demo

import kotlinx.coroutines.test.runTest
import org.langgraphkt.GraphConfig
import org.langgraphkt.GraphResult
import org.langgraphkt.demo.storage.MemoryStore
import org.langgraphkt.demo.storage.StorageCheckpointer
import org.langgraphkt.demo.workflows.EmailApproval
import org.langgraphkt.demo.workflows.EmailState
import org.langgraphkt.demo.workflows.scriptedDemoModel
import org.langgraphkt.serialization.CheckpointCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class EmailApprovalTest {
    private val store = MemoryStore()

    /** A fresh graph, checkpointer and config over the same store, as after a page reload. */
    private fun session() = EmailApproval.graph(scriptedDemoModel()) to GraphConfig(
        threadId = "email",
        checkpointer = StorageCheckpointer(store, CheckpointCodec<EmailState>()),
        interruptBefore = setOf(EmailApproval.REVIEW),
    )

    @Test
    fun pausesForReviewThenSendsOnceApproved() = runTest {
        val (graph, config) = session()

        val paused = graph.invoke(EmailState("Ana", "confirm the meeting on Friday"), config)

        assertIs<GraphResult.Interrupted<EmailState>>(paused)
        assertEquals(listOf(EmailApproval.REVIEW), paused.nextNodes)
        assertTrue(paused.state.draft.contains("Hi Ana,"), paused.state.draft)
        assertFalse(paused.state.sent)

        val done = graph.resume(config) { it.copy(approved = true) }

        assertIs<GraphResult.Completed<EmailState>>(done)
        assertTrue(done.state.sent)
        assertEquals(1, done.state.revisions)
    }

    @Test
    fun draftsAgainWhenChangesAreRequestedAndPausesForReviewAgain() = runTest {
        val (graph, config) = session()
        graph.invoke(EmailState("Ana", "confirm the meeting on Friday"), config)

        val second = graph.resume(config) { it.copy(feedback = "mention the room") }

        assertIs<GraphResult.Interrupted<EmailState>>(second)
        assertEquals(2, second.state.revisions)
        assertTrue(second.state.draft.contains("mention the room"), second.state.draft)
        assertFalse(second.state.sent)
    }

    @Test
    fun aPausedRunCanBeResumedAfterAReload() = runTest {
        val (graph, config) = session()
        graph.invoke(EmailState("Ana", "confirm the meeting on Friday"), config)

        val (reloadedGraph, reloadedConfig) = session()
        val checkpoint = reloadedConfig.checkpointer!!.load("email")!!
        assertEquals(listOf(EmailApproval.REVIEW), checkpoint.nextNodes)
        val done = reloadedGraph.resume(reloadedConfig) { it.copy(draft = "Edited by hand", approved = true) }

        assertIs<GraphResult.Completed<EmailState>>(done)
        assertEquals("Edited by hand", done.state.draft)
        assertTrue(done.state.sent)
    }
}
