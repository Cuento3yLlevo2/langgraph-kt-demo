package org.langgraphkt.demo.llm

import kotlinx.coroutines.delay

/** Answers a request it recognises, or returns null to let the next responder try. */
fun interface Responder {
    fun respond(request: ChatRequest): ChatMessage?
}

/**
 * A deterministic stand-in for a language model, so every workflow runs without an API key.
 * Each workflow contributes a [Responder] for its own prompts.
 */
class ScriptedChatModel(
    private val responders: List<Responder>,
    private val delayMillis: Long = 0,
) : ChatModel {
    override suspend fun chat(request: ChatRequest): ChatMessage {
        delay(delayMillis)
        return responders.firstNotNullOfOrNull { it.respond(request) }
            ?: ChatMessage.assistant("The scripted model has no answer for this. Switch to Claude in Settings for real replies.")
    }
}
