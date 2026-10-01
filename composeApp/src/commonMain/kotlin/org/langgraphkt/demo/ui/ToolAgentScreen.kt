package org.langgraphkt.demo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import org.langgraphkt.demo.llm.ChatMessage

private val suggestions = listOf("What is 12 * (3 + 4)?", "What is the weather in Madrid?", "What is 2 + 2, and the weather in Lima?")

@Composable
fun ToolAgentScreen(controller: ToolAgentController) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val send = {
        controller.send(input)
        input = ""
    }
    LaunchedEffect(controller.messages.size) {
        if (controller.messages.isNotEmpty()) listState.animateScrollToItem(controller.messages.lastIndex)
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenIntro("A chat agent that loops between the model and its tools until the model answers in plain text.")
        GraphStrip(controller)

        LazyColumn(Modifier.weight(1f).fillMaxWidth(), listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (controller.messages.isEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Try one of these:", style = MaterialTheme.typography.bodyMedium)
                        suggestions.forEach { suggestion ->
                            OutlinedButton(onClick = { controller.send(suggestion) }) { Text(suggestion) }
                        }
                    }
                }
            }
            items(controller.messages) { message -> MessageRow(message) }
        }

        RunStatus(controller)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask something") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
            )
            Button(onClick = send, enabled = !controller.running && input.isNotBlank()) { Text("Send") }
            TextButton(onClick = controller::clear, enabled = controller.messages.isNotEmpty()) { Text("Clear") }
        }
    }
}

@Composable
private fun MessageRow(message: ChatMessage) {
    val fromUser = message.role == ChatMessage.USER
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (message.text.isNotBlank()) {
            Box(Modifier.fillMaxWidth(), contentAlignment = if (fromUser) Alignment.CenterEnd else Alignment.CenterStart) {
                Surface(
                    Modifier.widthIn(max = 560.dp),
                    color = if (fromUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(message.text, Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                }
            }
        }
        message.toolCalls.forEach { ToolLine("call  ${it.name}(${it.input})") }
        message.toolResults.forEach { ToolLine((if (it.isError) "error " else "result ") + it.content) }
    }
}

@Composable
private fun ToolLine(text: String) {
    Text(
        text,
        Modifier.padding(start = 12.dp),
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
