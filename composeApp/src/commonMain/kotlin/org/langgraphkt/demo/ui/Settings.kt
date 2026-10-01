package org.langgraphkt.demo.ui

import io.ktor.client.HttpClient
import org.langgraphkt.demo.llm.AnthropicChatModel
import org.langgraphkt.demo.llm.ChatModel
import org.langgraphkt.demo.storage.KeyValueStore
import org.langgraphkt.demo.workflows.scriptedDemoModel

enum class ModelMode { Scripted, Claude }

data class Settings(
    val mode: ModelMode = ModelMode.Scripted,
    val apiKey: String = "",
    val modelId: String = AnthropicChatModel.DEFAULT_MODEL,
    val rememberKey: Boolean = false,
) {
    /** Claude is only used once a key has been entered. */
    val usesClaude: Boolean
        get() = mode == ModelMode.Claude && apiKey.isNotBlank()

    val modelLabel: String
        get() = if (usesClaude) modelId else "Scripted model"

    fun chatModel(client: HttpClient): ChatModel =
        if (usesClaude) AnthropicChatModel(client, apiKey.trim(), modelId.trim()) else scriptedDemoModel(delayMillis = 700)
}

/** Persists the settings. The API key is stored only if the user asked for it. */
class SettingsRepository(private val store: KeyValueStore) {
    suspend fun load(): Settings {
        val key = store.get(API_KEY).orEmpty()
        return Settings(
            mode = ModelMode.entries.firstOrNull { it.name == store.get(MODE) } ?: ModelMode.Scripted,
            apiKey = key,
            modelId = store.get(MODEL) ?: AnthropicChatModel.DEFAULT_MODEL,
            rememberKey = key.isNotEmpty(),
        )
    }

    suspend fun save(settings: Settings) {
        store.set(MODE, settings.mode.name)
        store.set(MODEL, settings.modelId)
        if (settings.rememberKey) store.set(API_KEY, settings.apiKey) else store.remove(API_KEY)
    }

    private companion object {
        const val MODE = "langgraph.demo.mode"
        const val MODEL = "langgraph.demo.model"
        const val API_KEY = "langgraph.demo.apiKey"
    }
}
