package org.langgraphkt.demo.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLEncoder

/** Stores each value as a file in [directory]. */
class FileStore(private val directory: File) : KeyValueStore {
    override suspend fun get(key: String): String? = withContext(Dispatchers.IO) {
        file(key).takeIf { it.exists() }?.readText()
    }

    override suspend fun set(key: String, value: String) = withContext(Dispatchers.IO) {
        directory.mkdirs()
        file(key).writeText(value)
    }

    override suspend fun remove(key: String) {
        withContext(Dispatchers.IO) { file(key).delete() }
    }

    private fun file(key: String): File = File(directory, URLEncoder.encode(key, Charsets.UTF_8))
}
