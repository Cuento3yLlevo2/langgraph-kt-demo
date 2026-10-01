package org.langgraphkt.demo

import kotlinx.coroutines.test.runTest
import org.langgraphkt.Checkpoint
import org.langgraphkt.CheckpointCorruptedException
import org.langgraphkt.demo.storage.MemoryStore
import org.langgraphkt.demo.storage.StorageCheckpointer
import org.langgraphkt.demo.workflows.EmailState
import org.langgraphkt.serialization.KotlinxStateSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class StorageCheckpointerTest {
    private val store = MemoryStore()
    private val checkpointer = StorageCheckpointer(store, KotlinxStateSerializer<EmailState>(), keyPrefix = "cp.")

    @Test
    fun roundTripsACheckpoint() = runTest {
        val checkpoint = Checkpoint(EmailState("Ana", "say hi", draft = "Hi"), listOf("review"), step = 1, interruptedBefore = true)

        checkpointer.save("t1", checkpoint)

        assertEquals(checkpoint, checkpointer.load("t1"))
        assertNull(checkpointer.load("other"))
    }

    @Test
    fun deleteRemovesTheCheckpoint() = runTest {
        checkpointer.save("t1", Checkpoint(EmailState("Ana", "say hi"), emptyList(), step = 3))

        checkpointer.delete("t1")

        assertNull(checkpointer.load("t1"))
    }

    @Test
    fun unreadableDataIsReportedAsCorrupted() = runTest {
        store.set("cp.t1", "not json")

        assertFailsWith<CheckpointCorruptedException> { checkpointer.load("t1") }
    }
}
