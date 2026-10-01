package org.langgraphkt.demo.workflows

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.langgraphkt.NodeAction
import org.langgraphkt.NodeRef
import org.langgraphkt.StateGraph

/**
 * Reports which nodes are running right now.
 *
 * `CompiledGraph.stream` emits an event when a whole step has finished, so on its own it cannot
 * show that a node has started or which parallel branch is still working. Wrapping each node
 * action fills that gap for the UI.
 */
class RunTracker {
    private val running = MutableStateFlow<Set<String>>(emptySet())

    val active: StateFlow<Set<String>> = running

    fun <State> track(name: String, action: NodeAction<State>): NodeAction<State> = { state ->
        running.update { it + name }
        try {
            action(state)
        } finally {
            running.update { it - name }
        }
    }
}

/** Adds a node whose activity is reported to [tracker], if there is one. */
fun <State> StateGraph<State>.node(name: String, tracker: RunTracker?, action: NodeAction<State>): NodeRef =
    node(name, tracker?.track(name, action) ?: action)
