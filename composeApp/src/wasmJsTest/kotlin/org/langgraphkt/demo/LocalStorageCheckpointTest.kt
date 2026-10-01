package org.langgraphkt.demo

import kotlinx.coroutines.test.runTest
import org.langgraphkt.GraphConfig
import org.langgraphkt.GraphResult
import org.langgraphkt.demo.storage.LocalStorageStore
import org.langgraphkt.demo.storage.StorageCheckpointer
import org.langgraphkt.demo.workflows.EmailApproval
import org.langgraphkt.demo.workflows.EmailState
import org.langgraphkt.demo.workflows.scriptedDemoModel
import org.langgraphkt.serialization.KotlinxStateSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Runs in a real browser: the paused run lives in `localStorage` between two separate sessions. */
class LocalStorageCheckpointTest {
    private fun config() = GraphConfig(
        threadId = "browser-test",
        checkpointer = StorageCheckpointer(LocalStorageStore(), KotlinxStateSerializer<EmailState>()),
        interruptBefore = setOf(EmailApproval.REVIEW),
    )

    @Test
    fun aRunPausedInLocalStorageIsResumedByANewSession() = runTest {
        val paused = EmailApproval.graph(scriptedDemoModel()).invoke(EmailState("Ana", "say hi"), config())
        assertIs<GraphResult.Interrupted<EmailState>>(paused)

        val config = config()
        assertEquals(listOf(EmailApproval.REVIEW), config.checkpointer!!.load("browser-test")!!.nextNodes)
        val done = EmailApproval.graph(scriptedDemoModel()).resume(config) { it.copy(approved = true) }

        assertTrue(done.state.sent)
        config.checkpointer!!.delete("browser-test")
        assertNull(config.checkpointer!!.load("browser-test"))
    }
}
