package org.langgraphkt.demo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.deeptelar.telar.MemoryCheckpointer
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
            game = remember { Game(scriptedModel(), MemoryStore(), MemoryCheckpointer(), scope, workMillis = 0) }
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

    @Test
    fun aPlayerPressesATileToSeeTheCodeOfItsNode() = runComposeUiTest {
        setContent {
            val scope = rememberCoroutineScope()
            val game = remember { Game(scriptedModel(), MemoryStore(), MemoryCheckpointer(), scope, workMillis = 0) }
            PizzaTheme(dark = true) { GameScreens(game, Settings(), Screen.Play(1), onNavigate = {}, onSave = {}) }
        }

        onNodeWithText("PRESS A TILE TO SEE ITS CODE").assertIsDisplayed()
        onNodeWithText("READ").performClick()

        onNodeWithText("CODE OF READ").assertIsDisplayed()
        onNodeWithText("START then read then answer then END").assertIsDisplayed()
        onNodeWithText("fun topicOf(message: String)", substring = true).assertIsDisplayed()

        // Another tile replaces the code, and the same tile again puts it away.
        onNodeWithText("ANSWER").performClick()
        onNodeWithText("CODE OF ANSWER").assertIsDisplayed()
        onNodeWithText("ANSWER").performClick()
        assertEquals(0, onAllNodesWithText("CODE OF ANSWER").fetchSemanticsNodes().size)
        onNodeWithText("PRESS A TILE TO SEE ITS CODE").assertIsDisplayed()
    }
}
