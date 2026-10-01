package org.langgraphkt.demo.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.langgraphkt.GraphConfig
import org.langgraphkt.GraphEvent
import org.langgraphkt.GraphTopology
import org.langgraphkt.NodeExecutionException
import org.langgraphkt.demo.llm.ChatMessage
import org.langgraphkt.demo.llm.ChatModel
import org.langgraphkt.demo.storage.KeyValueStore
import org.langgraphkt.demo.storage.StorageCheckpointer
import org.langgraphkt.demo.workflows.AgentState
import org.langgraphkt.demo.workflows.EmailApproval
import org.langgraphkt.demo.workflows.EmailState
import org.langgraphkt.demo.workflows.Research
import org.langgraphkt.demo.workflows.ResearchState
import org.langgraphkt.demo.workflows.ToolAgent
import org.langgraphkt.serialization.CheckpointCodec

/** Runs a graph for a screen and exposes its progress as Compose state. */
abstract class WorkflowController(private val scope: CoroutineScope) {
    /** The graph's structure, for drawing it. */
    abstract val topology: GraphTopology

    /** The nodes that are running right now. */
    var activeNodes: Set<String> by mutableStateOf(emptySet())
        private set
    var running: Boolean by mutableStateOf(false)
        private set
    var error: String? by mutableStateOf(null)
        protected set
    var steps: List<String> by mutableStateOf(emptyList())
        protected set

    private var job: Job? = null

    /** Collects [events], recording each completed step. [onFinished] runs after success, failure or cancellation. */
    protected fun <State> run(
        events: Flow<GraphEvent<State>>,
        onFinished: suspend () -> Unit = {},
        onEvent: (GraphEvent<State>) -> Unit,
    ) {
        if (running) return
        running = true
        error = null
        job = scope.launch {
            try {
                events.collect { event ->
                    when (event) {
                        is GraphEvent.NodeStarted -> activeNodes = activeNodes + event.node
                        is GraphEvent.NodeCompleted -> activeNodes = activeNodes - event.node
                        is GraphEvent.StepCompleted -> steps = steps + "Step ${event.step}: ${event.nodes.joinToString(" + ")}"
                        is GraphEvent.Completed, is GraphEvent.Interrupted -> Unit
                    }
                    onEvent(event)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = describe(e)
            } finally {
                activeNodes = emptySet()
                running = false
            }
            onFinished()
        }
    }

    fun stop() {
        job?.cancel()
    }

    protected fun describe(e: Exception): String =
        if (e is NodeExecutionException) "Node '${e.nodeName}' failed: ${e.cause?.message}" else e.message ?: e.toString()
}

class ToolAgentController(model: ChatModel, scope: CoroutineScope) : WorkflowController(scope) {
    private val graph = ToolAgent.graph(model)
    override val topology: GraphTopology = graph.topology

    var messages: List<ChatMessage> by mutableStateOf(emptyList())
        private set

    fun send(text: String) {
        if (running || text.isBlank()) return
        val input = AgentState(messages + ChatMessage.user(text.trim()))
        messages = input.messages
        steps = emptyList()
        // A node's own result is not the conversation yet; the state after each step is.
        run(graph.stream(input)) { event -> if (event is GraphEvent.StepCompleted) messages = event.state.messages }
    }

    fun clear() {
        stop()
        messages = emptyList()
        steps = emptyList()
        error = null
    }
}

class ResearchController(model: ChatModel, scope: CoroutineScope) : WorkflowController(scope) {
    private val graph = Research.graph(model)
    override val topology: GraphTopology = graph.topology

    var state: ResearchState? by mutableStateOf(null)
        private set

    fun research(question: String) {
        if (running || question.isBlank()) return
        val input = ResearchState(question.trim())
        state = input
        steps = emptyList()
        run(graph.stream(input)) { event ->
            state = when (event) {
                is GraphEvent.NodeStarted -> return@run
                // Show each angle's finding as soon as its node finishes, before the step is merged.
                is GraphEvent.NodeCompleted -> state?.let { it.copy(findings = it.findings + event.state.findings) }
                else -> event.state
            }
        }
    }
}

enum class EmailPhase {
    /** Nothing in progress. */
    Idle,

    /** The run is paused before `review` and waits for the human. */
    AwaitingReview,

    /** A saved run stopped somewhere else, for example because the page was closed mid-step. */
    Unfinished,

    Sent,
}

class EmailController(model: ChatModel, store: KeyValueStore, private val scope: CoroutineScope) : WorkflowController(scope) {
    private val graph = EmailApproval.graph(model)
    override val topology: GraphTopology = graph.topology
    private val checkpointer = StorageCheckpointer(store, CheckpointCodec<EmailState>())
    private val config = GraphConfig(
        threadId = THREAD,
        checkpointer = checkpointer,
        interruptBefore = setOf(EmailApproval.REVIEW),
    )

    var state: EmailState? by mutableStateOf(null)
        private set
    var phase: EmailPhase by mutableStateOf(EmailPhase.Idle)
        private set

    /** True when the current state came from storage rather than from a run in this session. */
    var restored: Boolean by mutableStateOf(false)
        private set

    init {
        scope.launch {
            syncWithCheckpoint()
            restored = phase != EmailPhase.Idle
        }
    }

    fun start(recipient: String, purpose: String) {
        if (recipient.isBlank() || purpose.isBlank()) return
        val input = EmailState(recipient.trim(), purpose.trim())
        steps = emptyList()
        follow(graph.stream(input, config))
    }

    fun approve(editedDraft: String) = follow(graph.streamResume(config) { it.copy(draft = editedDraft, approved = true) })

    fun requestChanges(feedback: String) = follow(graph.streamResume(config) { it.copy(approved = false, feedback = feedback) })

    /** Continues a run that stopped in the middle of a step. */
    fun continueRun() = follow(graph.streamResume(config))

    fun discard() {
        stop()
        scope.launch {
            checkpointer.delete(THREAD)
            state = null
            phase = EmailPhase.Idle
            restored = false
            steps = emptyList()
            error = null
        }
    }

    private fun follow(events: Flow<GraphEvent<EmailState>>) {
        restored = false
        run(events, onFinished = { if (phase != EmailPhase.Sent) syncWithCheckpoint() }) { event ->
            when (event) {
                is GraphEvent.NodeStarted, is GraphEvent.NodeCompleted -> return@run
                is GraphEvent.StepCompleted -> Unit
                is GraphEvent.Completed -> phase = EmailPhase.Sent
                is GraphEvent.Interrupted -> phase = EmailPhase.AwaitingReview
            }
            state = event.state
        }
    }

    /** The checkpoint is the source of truth for where the run stands, also after a failure. */
    private suspend fun syncWithCheckpoint() {
        val checkpoint = try {
            checkpointer.load(THREAD)
        } catch (e: Exception) {
            error = describe(e)
            null
        }
        if (checkpoint == null || checkpoint.isComplete) {
            phase = EmailPhase.Idle
            return
        }
        state = checkpoint.state
        phase = if (checkpoint.interruptedBefore) EmailPhase.AwaitingReview else EmailPhase.Unfinished
    }

    private companion object {
        const val THREAD = "email-approval"
    }
}
