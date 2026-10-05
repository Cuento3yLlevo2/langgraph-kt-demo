package org.langgraphkt.demo.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.langgraphkt.CheckpointCorruptedException
import org.langgraphkt.END
import org.langgraphkt.GraphConfig
import org.langgraphkt.GraphEvent
import org.langgraphkt.GraphResult
import org.langgraphkt.GraphTopology
import org.langgraphkt.NodeExecutionException
import org.langgraphkt.START
import org.langgraphkt.agent.ChatMessage
import org.langgraphkt.agent.ChatModel
import org.langgraphkt.agent.textDelta
import org.langgraphkt.demo.game.Desk
import org.langgraphkt.demo.game.Mail
import org.langgraphkt.demo.game.Stage
import org.langgraphkt.demo.game.Ticket
import org.langgraphkt.demo.game.stages
import org.langgraphkt.demo.storage.KeyValueStore
import org.langgraphkt.demo.storage.StorageCheckpointer
import org.langgraphkt.serialization.CheckpointCodec

enum class Phase {
    /** Nothing in progress. */
    Ready,

    /** The run stopped at a save point and waits for the player's decision. */
    SavePoint,

    /** A saved run stopped somewhere else, because the player stopped it or closed the app mid-run. */
    Unfinished,

    /** A node failed. */
    GameOver,

    /** The run reached END. */
    Clear,
}

/** What kind of line a [LogLine] is, which decides its colour. */
enum class Tone { Plain, Saved, Failed, Done }

/** One line of a stage's run log: a short [tag] and what happened. */
data class LogLine(val tag: String, val text: String, val tone: Tone = Tone.Plain)

/** Runs the graph of one stage and exposes the run as Compose state. */
class StageController(
    val stage: Stage,
    desk: Desk,
    store: KeyValueStore,
    private val scope: CoroutineScope,
    private val onClear: (Stage) -> Unit = {},
) {
    private val graph = stage.graph(desk)
    private val checkpointer = StorageCheckpointer(store, CheckpointCodec<Ticket>(), keyPrefix = "pixelpizza.save.")
    private val threadId = "stage-${stage.number}"
    private val config = GraphConfig(threadId = threadId, checkpointer = checkpointer, interruptBefore = stage.pauseBefore)

    /** The graph's structure, for drawing the board. */
    val topology: GraphTopology = graph.topology

    var phase: Phase by mutableStateOf(Phase.Ready)
        private set
    var running: Boolean by mutableStateOf(false)
        private set

    /** The ticket as of the last finished step. */
    var ticket: Ticket? by mutableStateOf(null)
        private set

    /** The nodes that are running right now. */
    var active: Set<String> by mutableStateOf(emptySet())
        private set

    /** The nodes that finished in this run, with START, and with END once the run got there. */
    var visited: Set<String> by mutableStateOf(emptySet())
        private set

    /** The arrows this run followed, as pairs of node names. */
    var trail: Set<Pair<String, String>> by mutableStateOf(emptySet())
        private set

    /** The nodes a saved run starts with when it continues. Empty when nothing is saved. */
    var waitingAt: List<String> by mutableStateOf(emptyList())
        private set

    /** The node that failed, when the phase is [Phase.GameOver]. */
    var failedNode: String? by mutableStateOf(null)
        private set
    var error: String? by mutableStateOf(null)
        private set
    var log: List<LogLine> by mutableStateOf(emptyList())
        private set

    /** What a model has written so far of the answer it is working on. Empty when no model is writing. */
    var writing: String by mutableStateOf("")
        private set
    var step: Int by mutableStateOf(0)
        private set

    /** True when what is shown came from storage rather than from a run in this session. */
    var restored: Boolean by mutableStateOf(false)
        private set

    private var job: Job? = null
    private var lastNodes: List<String> = emptyList()
    private var lastMail: Mail? = null

    init {
        scope.launch {
            phase = savedPhase()
            restored = phase != Phase.Ready
        }
    }

    /** Starts a new run for [mail]. Whatever was saved for this stage is replaced. */
    fun play(mail: Mail) {
        if (running || mail.text.isBlank()) return
        lastMail = mail
        ticket = Ticket(mail.from, mail.text.trim())
        visited = setOf(START)
        trail = emptySet()
        log = emptyList()
        writing = ""
        step = 0
        lastNodes = listOf(START)
        follow(graph.stream(ticket!!, config))
    }

    /** Answers a save point: the decision is written into the ticket and the run continues. */
    fun decide(approved: Boolean) {
        if (running) return
        log = log + LogLine("YOU", if (approved) "approved" else "denied", Tone.Saved)
        follow(graph.streamResume(config) { it.copy(approved = approved) })
    }

    /** Continues after a failure or a stop: from the last save if there is one, from the start otherwise. */
    fun retry() {
        if (running) return
        if (waitingAt.isNotEmpty()) follow(graph.streamResume(config)) else lastMail?.let(::play)
    }

    fun stop() {
        job?.cancel()
    }

    /** Throws the run away, saved or not, and clears the board. */
    fun reset() {
        scope.launch {
            job?.cancelAndJoin()
            checkpointer.delete(threadId)
            phase = Phase.Ready
            ticket = null
            visited = emptySet()
            trail = emptySet()
            waitingAt = emptyList()
            log = emptyList()
            writing = ""
            step = 0
            error = null
            failedNode = null
            restored = false
        }
    }

    private fun follow(events: Flow<GraphEvent<Ticket>>) {
        if (running) return
        running = true
        restored = false
        error = null
        failedNode = null
        waitingAt = emptyList()
        job = scope.launch {
            var outcome: Phase? = null
            try {
                events.collect { event ->
                    record(event)
                    if (event is GraphEvent.Completed) outcome = Phase.Clear
                    if (event is GraphEvent.Interrupted) outcome = Phase.SavePoint
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                outcome = Phase.GameOver
                failedNode = (e as? NodeExecutionException)?.nodeName
                error = (if (e is NodeExecutionException) e.cause?.message else null) ?: e.message ?: e.toString()
                log = log + LogLine("ERR", failedNode?.let { "$it: $error" } ?: error.orEmpty(), Tone.Failed)
            } finally {
                active = emptySet()
                // Text that arrived before a failure or a stop is not an answer.
                writing = ""
                withContext(NonCancellable) {
                    // The saved run says where a retry would pick up, and after Stop it is all there is to go by.
                    val saved = savedPhase()
                    phase = outcome ?: saved
                }
                running = false
                if (outcome == Phase.Clear) onClear(stage)
            }
        }
    }

    private fun record(event: GraphEvent<Ticket>) {
        when (event) {
            is GraphEvent.NodeStarted -> {
                active = active + event.node
                trail = trail + lastNodes.filter { leadsTo(it, event.node) }.map { it to event.node }
            }
            // A model node reports each piece of text while the model writes it.
            is GraphEvent.NodeProgress -> event.textDelta?.let { writing += it }
            is GraphEvent.NodeCompleted -> {
                active = active - event.node
                visited = visited + event.node
                // The answer is in the state now.
                writing = ""
            }
            is GraphEvent.StepCompleted -> {
                step = event.step
                log = log + LogLine(event.step.toString().padStart(2, '0'), event.nodes.joinToString(" + ")) +
                    event.state.chat.drop(ticket?.chat.orEmpty().size).flatMap(::toolLines)
                ticket = event.state
                lastNodes = event.nodes
            }
            is GraphEvent.Interrupted -> {
                ticket = event.state
                waitingAt = event.nextNodes
                log = log + LogLine("SAV", "stopped before ${event.nextNodes.joinToString(" + ")}", Tone.Saved)
            }
            is GraphEvent.Completed -> {
                ticket = event.state
                trail = trail + lastNodes.filter { leadsTo(it, END) }.map { it to END }
                visited = visited + END
                log = log + LogLine("END", "reply sent", Tone.Done)
            }
        }
    }

    private fun leadsTo(from: String, to: String): Boolean = topology.edges.any { it.from == from && it.to == to }

    private fun toolLines(message: ChatMessage): List<LogLine> = when (message) {
        is ChatMessage.Assistant -> message.toolCalls.map { LogLine(" >>", "${it.name} ${it.input}") }
        is ChatMessage.ToolResult -> listOf(LogLine(" <<", message.text, if (message.isError) Tone.Failed else Tone.Plain))
        is ChatMessage.User -> emptyList()
    }

    /** The checkpoint is the source of truth for where a run stands, also after a reload, a failure or Stop. */
    private suspend fun savedRun(): GraphResult.Interrupted<Ticket>? = try {
        graph.lastResult(config) as? GraphResult.Interrupted
    } catch (e: CancellationException) {
        throw e
    } catch (_: CheckpointCorruptedException) {
        // A save from an older version of the game, whose tickets looked different. It cannot be continued.
        checkpointer.delete(threadId)
        null
    } catch (e: Exception) {
        error = e.message
        null
    }

    private suspend fun savedPhase(): Phase {
        val saved = savedRun() ?: return Phase.Ready
        ticket = saved.state
        waitingAt = saved.nextNodes
        return if (saved.nextNodes.any { it in stage.pauseBefore }) Phase.SavePoint else Phase.Unfinished
    }
}

/** The whole game: one controller per stage, and which stages the player has cleared. */
class Game(model: ChatModel, private val store: KeyValueStore, private val scope: CoroutineScope, workMillis: Long = 650) {
    var cleared: Set<Int> by mutableStateOf(emptySet())
        private set

    val controllers: List<StageController> = stages.map { StageController(it, Desk(model, workMillis), store, scope, ::markCleared) }

    init {
        scope.launch {
            cleared = store.get(CLEARED).orEmpty().split(',').mapNotNull { it.toIntOrNull() }.toSet()
        }
    }

    fun controller(number: Int): StageController = controllers.first { it.stage.number == number }

    private fun markCleared(stage: Stage) {
        cleared = cleared + stage.number
        scope.launch { store.set(CLEARED, cleared.sorted().joinToString(",")) }
    }

    /** Forgets the cleared stages and every saved run. */
    fun erase() {
        controllers.forEach { it.reset() }
        cleared = emptySet()
        scope.launch { store.remove(CLEARED) }
    }

    private companion object {
        const val CLEARED = "pixelpizza.cleared"
    }
}
