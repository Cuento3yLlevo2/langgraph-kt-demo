package org.langgraphkt.demo.storage

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.langgraphkt.Checkpoint
import org.langgraphkt.CheckpointCorruptedException
import org.langgraphkt.Checkpointer
import org.langgraphkt.StateSerializer

/**
 * Keeps one checkpoint per thread in a [KeyValueStore].
 *
 * The library's `FileCheckpointer` needs a file system, which a browser does not have, so the
 * demo brings its own checkpointer for `localStorage`.
 */
class StorageCheckpointer<State>(
    private val store: KeyValueStore,
    private val serializer: StateSerializer<State>,
    private val keyPrefix: String = "langgraph.checkpoint.",
) : Checkpointer<State> {
    override suspend fun save(threadId: String, checkpoint: Checkpoint<State>) {
        val stored = StoredCheckpoint(
            state = serializer.serialize(checkpoint.state),
            nextNodes = checkpoint.nextNodes,
            step = checkpoint.step,
            interruptedBefore = checkpoint.interruptedBefore,
        )
        store.set(keyPrefix + threadId, json.encodeToString(StoredCheckpoint.serializer(), stored))
    }

    override suspend fun load(threadId: String): Checkpoint<State>? {
        val text = store.get(keyPrefix + threadId) ?: return null
        return try {
            val stored = json.decodeFromString(StoredCheckpoint.serializer(), text)
            Checkpoint(serializer.deserialize(stored.state), stored.nextNodes, stored.step, stored.interruptedBefore)
        } catch (e: SerializationException) {
            throw CheckpointCorruptedException(threadId, "The stored checkpoint for '$threadId' cannot be read", e)
        } catch (e: IllegalArgumentException) {
            throw CheckpointCorruptedException(threadId, "The stored checkpoint for '$threadId' cannot be read", e)
        }
    }

    override suspend fun delete(threadId: String) {
        store.remove(keyPrefix + threadId)
    }

    @Serializable
    private data class StoredCheckpoint(
        val state: String,
        val nextNodes: List<String>,
        val step: Int,
        val interruptedBefore: Boolean = false,
    )

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
