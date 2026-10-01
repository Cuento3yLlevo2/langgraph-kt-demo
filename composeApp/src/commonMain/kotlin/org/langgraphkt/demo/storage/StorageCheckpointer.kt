package org.langgraphkt.demo.storage

import org.langgraphkt.Checkpoint
import org.langgraphkt.Checkpointer
import org.langgraphkt.serialization.CheckpointCodec

/**
 * Keeps one checkpoint per thread in a [KeyValueStore].
 *
 * The library's `FileCheckpointer` needs a file system, which a browser does not have, so the
 * demo brings its own checkpointer for `localStorage`. [codec] does the conversion to a string.
 */
class StorageCheckpointer<State>(
    private val store: KeyValueStore,
    private val codec: CheckpointCodec<State>,
    private val keyPrefix: String = "langgraph.checkpoint.",
) : Checkpointer<State> {
    override suspend fun save(threadId: String, checkpoint: Checkpoint<State>) =
        store.set(keyPrefix + threadId, codec.encode(checkpoint))

    override suspend fun load(threadId: String): Checkpoint<State>? =
        store.get(keyPrefix + threadId)?.let { codec.decode(threadId, it) }

    override suspend fun delete(threadId: String) = store.remove(keyPrefix + threadId)
}
