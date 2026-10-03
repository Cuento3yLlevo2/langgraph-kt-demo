package org.langgraphkt.demo.llm

/**
 * A Claude model the game can be played with.
 *
 * @property inputPrice US dollars per million input tokens, at Anthropic's list price.
 * @property outputPrice US dollars per million output tokens.
 * @property takesEffort whether the model accepts `output_config.effort`. Haiku 4.5 rejects it.
 * @property hasFallbacks whether the API can re-run a request that a safety classifier declined
 * on another model. Only the models that run such classifiers take the `fallbacks` parameter.
 */
data class ClaudeModel(
    val id: String,
    val name: String,
    val inputPrice: Int,
    val outputPrice: Int,
    val takesEffort: Boolean = true,
    val hasFallbacks: Boolean = true,
)

/** The models offered under Options. Prices are from September 2026. */
object ClaudeModels {
    val haiku: ClaudeModel = ClaudeModel("claude-haiku-4-5", "Claude Haiku 4.5", 1, 5, takesEffort = false, hasFallbacks = false)
    val sonnet: ClaudeModel = ClaudeModel("claude-sonnet-5-5", "Claude Sonnet 5.5", 2, 10)
    val opus: ClaudeModel = ClaudeModel("claude-opus-5-5", "Claude Opus 5.5", 4, 20)
    val fable: ClaudeModel = ClaudeModel("claude-fable-5-1", "Claude Fable 5.1", 10, 50)

    /** Cheapest first. */
    val all: List<ClaudeModel> = listOf(haiku, sonnet, opus, fable)

    val default: ClaudeModel = opus

    /**
     * The model for a stored id. An id with a date behind it, such as `claude-haiku-4-5-20251001`,
     * is the same model; an id that is not on the list falls back to [default].
     */
    fun byId(id: String): ClaudeModel = all.firstOrNull { id.trim().startsWith(it.id) } ?: default
}
