package dev.deeptelar.telar.demo.ui

import io.ktor.client.HttpClient
import dev.deeptelar.telar.demo.game.scriptedModel
import dev.deeptelar.telar.agent.ChatModel
import dev.deeptelar.telar.demo.llm.ClaudeModels
import dev.deeptelar.telar.demo.storage.KeyValueStore

enum class ModelMode { Scripted, Claude }

data class Settings(
    val mode: ModelMode = ModelMode.Scripted,
    val apiKey: String = "",
    val modelId: String = ClaudeModels.default.id,
    val rememberKey: Boolean = false,
) {
    /** Claude is only used once a key has been entered. */
    val usesClaude: Boolean
        get() = mode == ModelMode.Claude && apiKey.isNotBlank()

    val modelLabel: String
        get() = if (usesClaude) modelId else "scripted"

    fun chatModel(client: HttpClient): ChatModel =
        if (usesClaude) ClaudeModels.byId(modelId).chatModel(client, apiKey.trim()) else scriptedModel(delayMillis = 650)
}

/** Persists the settings. The API key is stored only if the user asked for it. */
class SettingsRepository(private val store: KeyValueStore) {
    suspend fun load(): Settings {
        val key = store.get(API_KEY).orEmpty()
        return Settings(
            mode = ModelMode.entries.firstOrNull { it.name == store.get(MODE) } ?: ModelMode.Scripted,
            apiKey = key,
            // An id saved by an earlier version may be one the list no longer has.
            modelId = ClaudeModels.byId(store.get(MODEL).orEmpty()).id,
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
