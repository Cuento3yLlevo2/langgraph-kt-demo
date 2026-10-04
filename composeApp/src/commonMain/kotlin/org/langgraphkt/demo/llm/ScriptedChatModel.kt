package org.langgraphkt.demo.llm

import kotlinx.coroutines.delay
import org.langgraphkt.agent.ChatMessage
import org.langgraphkt.agent.ChatModel
import org.langgraphkt.agent.ChatRequest
import org.langgraphkt.agent.ChatResponse

/** Answers a request it recognises, or returns null to let the next responder try. */
fun interface Responder {
    fun respond(request: ChatRequest): ChatMessage.Assistant?
}

/**
 * A deterministic stand-in for a language model, so every stage runs without an API key.
 * Each stage that asks a model contributes a [Responder] for its own prompts.
 */
class ScriptedChatModel(
    private val responders: List<Responder>,
    private val delayMillis: Long = 0,
) : ChatModel {
    override suspend fun chat(request: ChatRequest): ChatResponse {
        delay(delayMillis)
        return ChatResponse(
            responders.firstNotNullOfOrNull { it.respond(request) }
                ?: ChatMessage.Assistant("The scripted model has no answer for this. Switch to Claude under Options for real replies."),
        )
    }
}
