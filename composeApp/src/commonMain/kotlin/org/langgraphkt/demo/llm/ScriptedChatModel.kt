package org.langgraphkt.demo.llm

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.langgraphkt.agent.ChatEvent
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
 *
 * An answer takes [delayMillis], whether it is asked for whole or streamed.
 */
class ScriptedChatModel(
    private val responders: List<Responder>,
    private val delayMillis: Long = 0,
) : ChatModel {
    override suspend fun chat(request: ChatRequest): ChatResponse {
        delay(delayMillis)
        return ChatResponse(answer(request))
    }

    /** Thinks for half of the time, like a model before its first word, and writes word by word for the rest. */
    override fun stream(request: ChatRequest): Flow<ChatEvent> = flow {
        val answer = answer(request)
        val words = words(answer.text)
        val perWord = if (words.isEmpty()) 0 else delayMillis / 2 / words.size
        delay(delayMillis - perWord * words.size)
        words.forEach { word ->
            emit(ChatEvent.TextDelta(word))
            delay(perWord)
        }
        emit(ChatEvent.Completed(ChatResponse(answer)))
    }

    private fun answer(request: ChatRequest): ChatMessage.Assistant =
        responders.firstNotNullOfOrNull { it.respond(request) }
            ?: ChatMessage.Assistant("The scripted model has no answer for this. Switch to Claude under Options for real replies.")

    /** Returns the words of [text], each with the space that follows it, so that they join to [text] again. */
    private fun words(text: String): List<String> = buildList {
        var start = 0
        text.forEachIndexed { index, char ->
            if (char == ' ') {
                add(text.substring(start, index + 1))
                start = index + 1
            }
        }
        if (start < text.length) add(text.substring(start))
    }
}
