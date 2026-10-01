package org.langgraphkt.demo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(settings: Settings, onSave: (Settings) -> Unit) {
    var draft by remember(settings) { mutableStateOf(settings) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenIntro("Choose the model behind the workflows. Saving starts the workflows afresh.")

        ModelMode.entries.forEach { mode ->
            Row(
                Modifier.fillMaxWidth().selectable(draft.mode == mode, role = Role.RadioButton) { draft = draft.copy(mode = mode) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = draft.mode == mode, onClick = null)
                Text(
                    when (mode) {
                        ModelMode.Scripted -> "  Scripted model: fixed answers, works offline, no key needed"
                        ModelMode.Claude -> "  Claude: real answers, needs your Anthropic API key"
                    },
                )
            }
        }

        if (draft.mode == ModelMode.Claude) {
            OutlinedTextField(
                draft.apiKey,
                { draft = draft.copy(apiKey = it) },
                Modifier.fillMaxWidth(),
                label = { Text("Anthropic API key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )
            OutlinedTextField(
                draft.modelId,
                { draft = draft.copy(modelId = it) },
                Modifier.fillMaxWidth(),
                label = { Text("Model") },
                singleLine = true,
            )
            Row(
                Modifier.toggleable(draft.rememberKey, role = Role.Checkbox) { draft = draft.copy(rememberKey = it) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = draft.rememberKey, onCheckedChange = null)
                Text("  Remember the key on this device")
            }
            InfoBanner(
                "The key is sent only to api.anthropic.com, straight from this app. Without \"Remember\" it is " +
                    "kept in memory and forgotten when you close the page. Requests are billed to your account.",
            )
            if (draft.apiKey.isBlank()) InfoBanner("Without a key the app keeps using the scripted model.")
        }

        Button(onClick = { onSave(draft) }, enabled = draft != settings) { Text("Save") }
    }
}
