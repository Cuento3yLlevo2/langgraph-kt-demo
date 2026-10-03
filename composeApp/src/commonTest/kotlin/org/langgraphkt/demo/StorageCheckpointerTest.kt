package org.langgraphkt.demo

import kotlinx.coroutines.test.runTest
import org.langgraphkt.Checkpoint
import org.langgraphkt.CheckpointCorruptedException
import org.langgraphkt.demo.game.Ticket
import org.langgraphkt.demo.storage.MemoryStore
import org.langgraphkt.demo.storage.StorageCheckpointer
import org.langgraphkt.serialization.CheckpointCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class StorageCheckpointerTest {
    private val store = MemoryStore()
    private val checkpointer = StorageCheckpointer(store, CheckpointCodec<Ticket>(), keyPrefix = "cp.")

    @Test
    fun roundTripsACheckpoint() = runTest {
        val checkpoint = Checkpoint(Ticket("Ana", "I want a refund", refund = 12), listOf("pay"), step = 1, interruptedBefore = true)

        checkpointer.save("t1", checkpoint)

        assertEquals(checkpoint, checkpointer.load("t1"))
        assertNull(checkpointer.load("other"))
    }

    @Test
    fun deleteRemovesTheCheckpoint() = runTest {
        checkpointer.save("t1", Checkpoint(Ticket("Ana", "Where is my pizza?"), emptyList(), step = 3))

        checkpointer.delete("t1")

        assertNull(checkpointer.load("t1"))
    }

    @Test
    fun unreadableDataIsReportedAsCorrupted() = runTest {
        store.set("cp.t1", "not json")

        assertFailsWith<CheckpointCorruptedException> { checkpointer.load("t1") }
    }
}
