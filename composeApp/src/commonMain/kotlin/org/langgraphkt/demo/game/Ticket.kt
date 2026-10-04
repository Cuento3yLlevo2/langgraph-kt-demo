package org.langgraphkt.demo.game

import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import org.langgraphkt.agent.ChatMessage
import org.langgraphkt.agent.ChatModel

/**
 * The state of every stage: one customer message on its way to a reply.
 *
 * [customer] and [message] are the input. Everything else starts empty and is filled in by the
 * nodes; a stage only uses the fields its nodes need.
 */
@Serializable
data class Ticket(
    val customer: String,
    val message: String,
    val topic: String = "",
    val facts: List<String> = emptyList(),
    val refund: Int = 0,
    val approved: Boolean = false,
    val reply: String = "",
    val attempts: Int = 0,
    val problem: String = "",
    /** The conversation with the model, kept by the agent of stage 6. */
    val chat: List<ChatMessage> = emptyList(),
)

/** A message waiting in a stage's inbox. */
data class Mail(val from: String, val text: String)

/**
 * What the nodes of a stage work with.
 *
 * @property model writes the replies of the stages that use a model.
 * @property workMillis how long a node without a model pretends to work, so a player can watch
 * the run move across the board. Tests pass 0.
 */
class Desk(val model: ChatModel, private val workMillis: Long = 0) {
    suspend fun work() = delay(workMillis)
}
