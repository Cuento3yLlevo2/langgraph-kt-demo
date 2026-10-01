package org.langgraphkt.demo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * The graph's stages from left to right, with the nodes running right now highlighted.
 * Nodes that run in parallel share a stage and are stacked.
 */
@Composable
fun GraphStrip(stages: List<List<String>>, controller: WorkflowController, modifier: Modifier = Modifier) {
    val active by controller.tracker.active.collectAsState()
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        stages.forEachIndexed { index, stage ->
            if (index > 0) Text(">", color = MaterialTheme.colorScheme.outline)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                stage.forEach { node -> NodeChip(node, running = node in active) }
            }
        }
    }
}

@Composable
private fun NodeChip(node: String, running: Boolean) {
    val shape = RoundedCornerShape(8.dp)
    Text(
        // START and END are "__START__" and "__END__" in the library.
        node.trim('_'),
        Modifier
            .background(if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface, shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        color = if (running) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.labelLarge,
        fontFamily = FontFamily.Monospace,
    )
}

/** Run status: a spinner with a stop button while running, the error if it failed, and the steps so far. */
@Composable
fun RunStatus(controller: WorkflowController, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (controller.running) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text("Running", style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = controller::stop) { Text("Stop") }
            }
        }
        controller.error?.let { ErrorBanner(it) }
        if (controller.steps.isNotEmpty()) {
            Text(
                controller.steps.joinToString("\n"),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun ErrorBanner(message: String, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(8.dp)) {
        Text(message, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
    }
}

@Composable
fun InfoBanner(message: String, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(8.dp)) {
        Text(message, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

/** A titled block of text, used for drafts, findings and summaries. */
@Composable
fun TextCard(title: String, body: String, modifier: Modifier = Modifier, busy: Boolean = false) {
    Surface(
        modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (busy) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
            }
            if (body.isNotEmpty()) Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun ScreenIntro(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
