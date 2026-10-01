package org.langgraphkt.demo

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.langgraphkt.GraphEvent
import org.langgraphkt.demo.workflows.Research
import org.langgraphkt.demo.workflows.ResearchState
import org.langgraphkt.demo.workflows.RunTracker
import org.langgraphkt.demo.workflows.scriptedDemoModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ResearchTest {
    @Test
    fun researchesEveryAngleThenSummarizes() = runTest {
        val events = Research.graph(scriptedDemoModel()).stream(ResearchState("Should we adopt Kotlin Multiplatform?")).toList()

        val steps = events.filterIsInstance<GraphEvent.StepCompleted<ResearchState>>()
        assertEquals(listOf(Research.angles.toSet(), setOf(Research.SUMMARIZE)), steps.map { it.nodes.toSet() })
        assertEquals(Research.angles.toSet(), steps.first().state.findings.keys)
        val final = assertIs<GraphEvent.Completed<ResearchState>>(events.last()).state
        assertTrue(final.summary.startsWith("Scripted summary"), final.summary)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun anglesRunInParallelAndTheTrackerSeesThem() = runTest {
        val tracker = RunTracker()
        val seen = mutableSetOf<Set<String>>()
        val graph = Research.graph(scriptedDemoModel(delayMillis = 1000), tracker)

        graph.stream(ResearchState("q")).collect { seen += tracker.active.value }

        // Three angles in parallel take one model delay, not three; the summary takes a second one.
        assertEquals(2000, currentTime)
        assertEquals(emptySet(), tracker.active.value)
    }
}
