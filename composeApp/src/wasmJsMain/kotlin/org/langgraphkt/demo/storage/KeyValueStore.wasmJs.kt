package org.langgraphkt.demo.storage

actual fun platformStore(): KeyValueStore = LocalStorageStore()

/** Stores values in the browser's `localStorage`, so they survive a page reload. */
class LocalStorageStore : KeyValueStore {
    override suspend fun get(key: String): String? = localStorageGet(key)

    override suspend fun set(key: String, value: String) = localStorageSet(key, value)

    override suspend fun remove(key: String) = localStorageRemove(key)
}

private fun localStorageGet(key: String): String? = js("window.localStorage.getItem(key)")

private fun localStorageSet(key: String, value: String): Unit = js("window.localStorage.setItem(key, value)")

private fun localStorageRemove(key: String): Unit = js("window.localStorage.removeItem(key)")
