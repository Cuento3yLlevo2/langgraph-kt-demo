package org.langgraphkt.demo

import kotlinx.coroutines.test.runTest
import org.langgraphkt.GraphConfig
import org.langgraphkt.GraphResult
import org.langgraphkt.demo.game.Desk
import org.langgraphkt.demo.game.HelpDesks
import org.langgraphkt.demo.game.Ticket
import org.langgraphkt.demo.game.scriptedModel
import org.langgraphkt.demo.storage.LocalStorageStore
import org.langgraphkt.demo.storage.StorageCheckpointer
import org.langgraphkt.serialization.CheckpointCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** Runs in a real browser: the paused run lives in `localStorage` between two separate sessions. */
class LocalStorageCheckpointTest {
    private fun config() = GraphConfig(
        threadId = "browser-test",
        checkpointer = StorageCheckpointer(LocalStorageStore(), CheckpointCodec<Ticket>()),
        interruptBefore = setOf(HelpDesks.PAY),
    )

    private fun graph() = HelpDesks.savePoints(Desk(scriptedModel()))

    @Test
    fun aRunPausedInLocalStorageIsResumedByANewSession() = runTest {
        val paused = graph().invoke(Ticket("Ben", "I want a refund"), config())
        assertIs<GraphResult.Interrupted<Ticket>>(paused)

        val config = config()
        assertEquals(listOf(HelpDesks.PAY), config.checkpointer!!.load("browser-test")!!.nextNodes)
        val done = graph().resume(config) { it.copy(approved = true) }

        assertEquals("Sorry Ben! We sent you 12 euros.", done.state.reply)
        config.checkpointer!!.delete("browser-test")
        assertNull(config.checkpointer!!.load("browser-test"))
    }
}
