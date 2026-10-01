package org.langgraphkt.demo

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.langgraphkt.GraphEvent
import org.langgraphkt.demo.workflows.Research
import org.langgraphkt.demo.workflows.ResearchState
import org.langgraphkt.demo.workflows.EmailApproval
import org.langgraphkt.demo.workflows.ToolAgent
import org.langgraphkt.demo.ui.stages
import org.langgraphkt.END
import org.langgraphkt.START
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
    fun anglesRunInParallelAndReportTheirOwnFinding() = runTest {
        val events = Research.graph(scriptedDemoModel(delayMillis = 1000)).stream(ResearchState("q")).toList()

        // Three angles in parallel take one model delay, not three; the summary takes a second one.
        assertEquals(2000, currentTime)
        assertEquals(Research.angles, events.take(3).map { assertIs<GraphEvent.NodeStarted<ResearchState>>(it).node })
        val finished = events.filterIsInstance<GraphEvent.NodeCompleted<ResearchState>>().filter { it.step == 1 }
        assertEquals(Research.angles.map { setOf(it) }, finished.map { it.state.findings.keys })
    }

    @Test
    fun theTopologyGivesTheStagesTheScreenDraws() {
        assertEquals(
            listOf(listOf(START), Research.angles, listOf(Research.SUMMARIZE), listOf(END)),
            Research.graph(scriptedDemoModel()).topology.stages(),
        )
        assertEquals(
            listOf(listOf(START), listOf(ToolAgent.ASSISTANT), listOf(ToolAgent.TOOLS), listOf(END)),
            ToolAgent.graph(scriptedDemoModel()).topology.stages(),
        )
        assertEquals(
            listOf(listOf(START), listOf(EmailApproval.DRAFT), listOf(EmailApproval.REVIEW), listOf(EmailApproval.SEND), listOf(END)),
            EmailApproval.graph(scriptedDemoModel()).topology.stages(),
        )
    }
}
