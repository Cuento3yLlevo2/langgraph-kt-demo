package org.langgraphkt.demo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.langgraphkt.demo.workflows.Research

@Composable
fun ResearchScreen(controller: ResearchController) {
    var question by remember { mutableStateOf("Should a small team adopt Kotlin Multiplatform?") }
    val active by controller.tracker.active.collectAsState()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenIntro(
            "Three nodes research the question from different angles at the same time. A reducer merges what they " +
                "found, and a last node writes the summary.",
        )
        GraphStrip(Research.stages, controller)
        OutlinedTextField(question, { question = it }, Modifier.fillMaxWidth(), label = { Text("Question") })
        Button(onClick = { controller.research(question) }, enabled = !controller.running && question.isNotBlank()) {
            Text("Research")
        }
        RunStatus(controller)

        val state = controller.state
        if (state != null) {
            Research.angles.forEach { angle ->
                val finding = state.findings[angle]
                if (finding != null || angle in active) {
                    TextCard(angle.replaceFirstChar { it.uppercase() }, finding.orEmpty(), busy = angle in active)
                }
            }
            if (state.summary.isNotEmpty() || Research.SUMMARIZE in active) {
                TextCard("Summary", state.summary, busy = Research.SUMMARIZE in active)
            }
        }
    }
}
