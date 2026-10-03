package org.langgraphkt.demo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import org.langgraphkt.demo.game.scriptedModel
import org.langgraphkt.demo.storage.MemoryStore
import org.langgraphkt.demo.ui.Game
import org.langgraphkt.demo.ui.GameScreens
import org.langgraphkt.demo.ui.Phase
import org.langgraphkt.demo.ui.PizzaTheme
import org.langgraphkt.demo.ui.Screen
import org.langgraphkt.demo.ui.Settings
import kotlin.test.Test
import kotlin.test.assertEquals

/** Clicks through the screens the way a player does, so the buttons are known to be wired up. */
@OptIn(ExperimentalTestApi::class)
class PlayThroughTest {
    @Test
    fun aPlayerStartsTheGameAndPlaysASavePointToTheEnd() = runComposeUiTest {
        lateinit var game: Game
        setContent {
            val scope = rememberCoroutineScope()
            game = remember { Game(scriptedModel(), MemoryStore(), scope, workMillis = 0) }
            var screen by remember { mutableStateOf<Screen>(Screen.Title) }
            PizzaTheme(dark = true) { GameScreens(game, Settings(), screen, onNavigate = { screen = it }, onSave = {}) }
        }

        onNodeWithText("PRESS START").performClick()
        onNodeWithContentDescription("Save points").performClick()
        onNodeWithText("RUN").performScrollTo().performClick()
        waitUntil(timeoutMillis = 5_000) { game.controller(5).phase == Phase.SavePoint }

        onNodeWithText("Refund 12 euros to Ben?").assertIsDisplayed()
        onNodeWithText("APPROVE").performClick()
        waitUntil(timeoutMillis = 5_000) { game.controller(5).phase == Phase.Clear }

        onNodeWithContentDescription("Stage clear").assertIsDisplayed()
        assertEquals(setOf(5), game.cleared)

        onNodeWithText("NEXT STAGE").performClick()
        onNodeWithContentDescription("The agent").assertIsDisplayed()
    }
}
