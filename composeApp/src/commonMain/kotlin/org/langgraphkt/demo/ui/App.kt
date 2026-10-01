package org.langgraphkt.demo.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.ktor.client.HttpClient
import kotlinx.coroutines.launch
import org.langgraphkt.demo.storage.platformStore

private val tabs = listOf("Tool agent", "Approval", "Research", "Settings")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    val scope = rememberCoroutineScope()
    val store = remember { platformStore() }
    val repository = remember { SettingsRepository(store) }
    val httpClient = remember { HttpClient() }
    var loaded by remember { mutableStateOf<Settings?>(null) }
    var tab by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { loaded = repository.load() }

    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        val settings = loaded ?: return@MaterialTheme
        // New settings mean a new model, and with it fresh workflows.
        val model = remember(settings) { settings.chatModel(httpClient) }
        val agent = remember(model) { ToolAgentController(model, scope) }
        val email = remember(model) { EmailController(model, store, scope) }
        val research = remember(model) { ResearchController(model, scope) }

        Scaffold(
            topBar = {
                Column {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("langgraph-kt demo", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                        Text(settings.modelLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                    PrimaryTabRow(selectedTabIndex = tab) {
                        tabs.forEachIndexed { index, title ->
                            Tab(selected = tab == index, onClick = { tab = index }, text = { Text(title) })
                        }
                    }
                }
            },
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Box(Modifier.widthIn(max = 840.dp).fillMaxSize().padding(16.dp)) {
                    when (tab) {
                        0 -> ToolAgentScreen(agent)
                        1 -> EmailScreen(email)
                        2 -> ResearchScreen(research)
                        else -> SettingsScreen(settings) { updated ->
                            loaded = updated
                            scope.launch { repository.save(updated) }
                        }
                    }
                }
            }
        }
    }
}
