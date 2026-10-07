package dev.deeptelar.telar.demo.storage

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Small persistent string storage for the settings and the cleared stages: `localStorage` in the browser, files elsewhere. */
interface KeyValueStore {
    suspend fun get(key: String): String?

    suspend fun set(key: String, value: String)

    suspend fun remove(key: String)
}

/** The store this platform persists to. */
expect fun platformStore(): KeyValueStore

class MemoryStore : KeyValueStore {
    private val mutex = Mutex()
    private val values = mutableMapOf<String, String>()

    override suspend fun get(key: String): String? = mutex.withLock { values[key] }

    override suspend fun set(key: String, value: String) {
        mutex.withLock { values[key] = value }
    }

    override suspend fun remove(key: String) {
        mutex.withLock { values.remove(key) }
    }
}
