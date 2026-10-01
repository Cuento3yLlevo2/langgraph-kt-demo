package org.langgraphkt.demo.workflows

import kotlinx.serialization.Serializable
import org.langgraphkt.CompiledGraph
import org.langgraphkt.END
import org.langgraphkt.START
import org.langgraphkt.StateGraph
import org.langgraphkt.demo.llm.ChatMessage
import org.langgraphkt.demo.llm.ChatModel
import org.langgraphkt.demo.llm.ChatRequest
import org.langgraphkt.demo.llm.Responder

/** State of the email workflow. [approved] and [feedback] are set by the human reviewer. */
@Serializable
data class EmailState(
    val recipient: String,
    val purpose: String,
    val draft: String = "",
    val revisions: Int = 0,
    val approved: Boolean = false,
    val feedback: String = "",
    val sent: Boolean = false,
)

object EmailApproval {
    const val DRAFT: String = "draft"
    const val REVIEW: String = "review"
    const val SEND: String = "send"

    val nodes: List<String> = listOf(START, DRAFT, REVIEW, SEND, END)

    const val SYSTEM: String =
        "You write short, polite emails. Reply with the email only: a 'Subject:' line, a blank line, then the body."

    /**
     * Drafts an email, waits for a human, then either sends it or drafts again.
     *
     * Run it with `interruptBefore = setOf(REVIEW)`: the run pauses before `review`, and the
     * reviewer's decision is written into the state when the run is resumed.
     */
    fun graph(model: ChatModel, tracker: RunTracker? = null): CompiledGraph<EmailState> = StateGraph<EmailState> {
        val draft = node(DRAFT, tracker) { state ->
            val reply = model.chat(ChatRequest(listOf(ChatMessage.user(prompt(state))), SYSTEM))
            state.copy(draft = reply.text.trim(), revisions = state.revisions + 1, feedback = "")
        }
        val review = node(REVIEW, tracker) { it }
        val send = node(SEND, tracker) { it.copy(sent = true) }

        START then draft then review
        conditionalEdge(review, targets = setOf(send.name, draft.name)) { state ->
            if (state.approved) send.name else draft.name
        }
        send then END
    }.compile()

    private fun prompt(state: EmailState): String = buildString {
        appendLine("$RECIPIENT${state.recipient}")
        appendLine("$PURPOSE${state.purpose}")
        if (state.feedback.isNotBlank()) {
            appendLine("$FEEDBACK${state.feedback}")
            appendLine("Previous draft:")
            appendLine(state.draft)
        }
    }

    private const val RECIPIENT = "Recipient: "
    private const val PURPOSE = "Purpose: "
    private const val FEEDBACK = "Change requested by the reviewer: "

    val script: Responder = Responder { request ->
        if (request.system != SYSTEM) return@Responder null
        val lines = request.messages.last().text.lines()
        fun field(prefix: String) = lines.firstOrNull { it.startsWith(prefix) }?.removePrefix(prefix).orEmpty()
        val feedback = field(FEEDBACK)
        ChatMessage.assistant(
            buildString {
                appendLine("Subject: ${field(PURPOSE).replaceFirstChar { it.uppercase() }}")
                appendLine()
                appendLine("Hi ${field(RECIPIENT)},")
                appendLine()
                appendLine("I am writing to ${field(PURPOSE)}.")
                if (feedback.isNotBlank()) appendLine("(Revised as requested: $feedback)")
                appendLine()
                append("Best regards")
            },
        )
    }
}
