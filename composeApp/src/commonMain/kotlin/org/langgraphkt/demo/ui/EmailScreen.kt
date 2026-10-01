package org.langgraphkt.demo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.langgraphkt.demo.workflows.EmailState

@Composable
fun EmailScreen(controller: EmailController) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenIntro(
            "The model drafts an email and the run pauses for you before 'review'. Approve it, edit it, or ask for " +
                "changes. The paused run is saved, so you can reload the page and pick it up again.",
        )
        GraphStrip(controller)
        if (controller.restored) InfoBanner("This run was restored from a saved checkpoint.")

        val state = controller.state
        when (controller.phase) {
            EmailPhase.Idle -> RequestForm(controller)
            EmailPhase.AwaitingReview -> if (state != null) Review(controller, state)
            EmailPhase.Unfinished -> {
                InfoBanner("A saved run stopped before it reached the review step.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = controller::continueRun, enabled = !controller.running) { Text("Continue") }
                    TextButton(onClick = controller::discard) { Text("Discard") }
                }
            }
            EmailPhase.Sent -> if (state != null) {
                TextCard("Sent to ${state.recipient} after ${state.revisions} draft(s)", state.draft)
                Button(onClick = controller::discard) { Text("Write another") }
            }
        }
        RunStatus(controller)
    }
}

@Composable
private fun RequestForm(controller: EmailController) {
    var recipient by remember { mutableStateOf("Ana") }
    var purpose by remember { mutableStateOf("confirm our meeting on Friday at 10:00") }
    OutlinedTextField(recipient, { recipient = it }, Modifier.fillMaxWidth(), label = { Text("Recipient") }, singleLine = true)
    OutlinedTextField(purpose, { purpose = it }, Modifier.fillMaxWidth(), label = { Text("What the email should do") })
    Button(
        onClick = { controller.start(recipient, purpose) },
        enabled = !controller.running && recipient.isNotBlank() && purpose.isNotBlank(),
    ) { Text("Draft email") }
}

@Composable
private fun Review(controller: EmailController, state: EmailState) {
    // Keyed on the revision so a new draft replaces whatever was typed into the previous one.
    var draft by remember(state.revisions) { mutableStateOf(state.draft) }
    var feedback by remember(state.revisions) { mutableStateOf("") }
    OutlinedTextField(
        draft,
        { draft = it },
        Modifier.fillMaxWidth(),
        label = { Text("Draft ${state.revisions} for ${state.recipient} (you can edit it)") },
        minLines = 6,
    )
    OutlinedTextField(feedback, { feedback = it }, Modifier.fillMaxWidth(), label = { Text("Changes to ask for") })
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { controller.approve(draft) }, enabled = !controller.running && draft.isNotBlank()) {
            Text("Approve and send")
        }
        OutlinedButton(
            onClick = { controller.requestChanges(feedback) },
            enabled = !controller.running && feedback.isNotBlank(),
        ) { Text("Request changes") }
        TextButton(onClick = controller::discard) { Text("Discard") }
    }
}
