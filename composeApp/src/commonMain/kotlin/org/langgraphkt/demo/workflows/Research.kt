package org.langgraphkt.demo.workflows

import kotlinx.serialization.Serializable
import org.langgraphkt.CompiledGraph
import org.langgraphkt.END
import org.langgraphkt.Reducer
import org.langgraphkt.START
import org.langgraphkt.StateGraph
import org.langgraphkt.demo.llm.ChatMessage
import org.langgraphkt.demo.llm.ChatModel
import org.langgraphkt.demo.llm.ChatRequest
import org.langgraphkt.demo.llm.Responder

/** State of the research workflow. [findings] maps an angle to what was found from it. */
@Serializable
data class ResearchState(
    val question: String,
    val findings: Map<String, String> = emptyMap(),
    val summary: String = "",
)

object Research {
    const val SUMMARIZE: String = "summarize"

    /** Each angle is a node; all of them run in parallel on the same question. */
    val angles: List<String> = listOf("benefits", "risks", "alternatives")

    const val SUMMARY_SYSTEM: String =
        "You combine research notes into one balanced recommendation of at most four sentences."

    fun angleSystem(angle: String): String =
        "You research one angle of a question: its $angle. Answer in at most three short bullet points."

    /** Parallel branches return separate copies of the state; this merges their findings. */
    val mergeFindings: Reducer<ResearchState> = Reducer { current, updates ->
        current.copy(findings = updates.fold(current.findings) { all, update -> all + update.findings })
    }

    /** Fans out to one node per angle, then joins in `summarize`. */
    fun graph(model: ChatModel): CompiledGraph<ResearchState> = StateGraph<ResearchState> {
        val summarize = node(SUMMARIZE) { state ->
            val notes = state.findings.entries.joinToString("\n\n") { (angle, text) -> "$angle:\n$text" }
            val reply = model.chat(
                ChatRequest(listOf(ChatMessage.user("$QUESTION${state.question}\n\n$notes")), SUMMARY_SYSTEM),
            )
            state.copy(summary = reply.text.trim())
        }
        angles.forEach { angle ->
            val research = node(angle) { state ->
                val reply = model.chat(ChatRequest(listOf(ChatMessage.user(QUESTION + state.question)), angleSystem(angle)))
                state.copy(findings = state.findings + (angle to reply.text.trim()))
            }
            START then research then summarize
        }
        summarize then END
    }.compile(reducer = mergeFindings)

    private const val QUESTION = "Question: "

    val script: Responder = Responder { request ->
        val text = request.messages.last().text
        val question = text.lines().first().removePrefix(QUESTION)
        val angle = angles.firstOrNull { request.system == angleSystem(it) }
        when {
            angle != null -> ChatMessage.assistant("- A scripted note on the $angle of \"$question\".\n- A second point about $angle.")
            request.system == SUMMARY_SYSTEM ->
                ChatMessage.assistant("Scripted summary for \"$question\", weighing ${angles.joinToString(", ")}.")
            else -> null
        }
    }
}
