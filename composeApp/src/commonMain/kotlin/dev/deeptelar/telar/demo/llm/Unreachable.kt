package dev.deeptelar.telar.demo.llm

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import dev.deeptelar.telar.agent.ChatEvent
import dev.deeptelar.telar.agent.ChatModel
import dev.deeptelar.telar.agent.ChatModelException
import dev.deeptelar.telar.agent.ChatRequest
import dev.deeptelar.telar.agent.ChatResponse

/**
 * This model, with a request that got no response reported as a [ChatModelException].
 *
 * In a browser such a request fails with an `Error` that says only "Fail to fetch": the server at
 * [host] is not running, the device is offline, or the server does not take requests from this
 * page. An `Error` is not an `Exception`, so the library's model classes and its engine let it
 * pass, and the run would end without a game over screen.
 */
fun ChatModel.orUnreachable(host: String): ChatModel {
    val model = this
    return object : ChatModel {
        override suspend fun chat(request: ChatRequest): ChatResponse = try {
            model.chat(request)
        } catch (e: Error) {
            throw e.asUnreachable(host)
        }

        override fun stream(request: ChatRequest): Flow<ChatEvent> = model.stream(request).catch { throw it.asUnreachable(host) }
    }
}

/** Only the failed request of a browser is turned into an exception of the model. Everything else stays what it is. */
private fun Throwable.asUnreachable(host: String): Throwable =
    if (this is Error && message.orEmpty().contains("fetch", ignoreCase = true)) {
        ChatModelException("Could not reach $host. Is it running, is this device online, and does it take requests from this page?", this)
    } else {
        this
    }
