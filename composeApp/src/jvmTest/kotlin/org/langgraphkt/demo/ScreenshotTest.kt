package org.langgraphkt.demo

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import org.jetbrains.skia.EncodedImageFormat
import org.langgraphkt.demo.game.Mail
import org.langgraphkt.MemoryCheckpointer
import org.langgraphkt.demo.game.scriptedModel
import org.langgraphkt.demo.storage.MemoryStore
import org.langgraphkt.demo.ui.Game
import org.langgraphkt.demo.ui.GameScreens
import org.langgraphkt.demo.ui.ModelMode
import org.langgraphkt.demo.ui.PizzaTheme
import org.langgraphkt.demo.ui.Screen
import org.langgraphkt.demo.ui.Settings
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Draws every screen without a window and writes it to `build/screenshots`. It fails if a screen
 * cannot be drawn, and the pictures are what the README shows.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScreenshotTest {
    private val scope = TestScope()
    private val game = Game(scriptedModel(delayMillis = 1_000), MemoryStore(), MemoryCheckpointer(), scope, workMillis = 1_000)
    private val delivery = Mail("Ana", "Where is my pizza?")
    private val refund = Mail("Ben", "My pizza arrived cold. I want a refund.")

    private fun shoot(
        name: String,
        screen: Screen,
        width: Int = 1280,
        height: Int = 860,
        dark: Boolean = true,
        settings: Settings = Settings(),
    ) {
        ImageComposeScene(width * 2, height * 2, Density(2f)) {
            PizzaTheme(dark) { GameScreens(game, settings, screen, onNavigate = {}, onSave = {}) }
        }.use { scene ->
            // Text that is typed out letter by letter needs a second of real time to be complete.
            scene.render().close()
            Thread.sleep(1_200)
            scene.render(nanoTime = 1_200_000_000L).close()
            val image = scene.render(nanoTime = 1_216_000_000L)
            val file = File("build/screenshots/$name.png")
            file.parentFile.mkdirs()
            file.writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
            image.close()
            assertTrue(file.length() > 10_000, "$name looks empty")
        }
    }

    @Test
    fun everyScreenCanBeDrawn() {
        shoot("title", Screen.Title)
        shoot("title-light", Screen.Title, dark = false)
        shoot("title-phone", Screen.Title, width = 390, height = 780)

        // Stage 8, halfway: the kitchen and the driver are both at work.
        game.controller(8).play(delivery)
        scope.advanceTimeBy(1_500)
        shoot("stage-8-running", Screen.Play(8), height = 1000)
        scope.advanceUntilIdle()
        shoot("stage-8-clear", Screen.Play(8), height = 1000)
        shoot("stage-8-phone", Screen.Play(8), width = 390, height = 1400)

        game.controller(3).play(Mail("Ana", "My pizza is late!"))
        scope.advanceUntilIdle()
        shoot("stage-3-clear", Screen.Play(3))

        game.controller(5).play(refund)
        scope.advanceUntilIdle()
        shoot("stage-5-save-point", Screen.Play(5))

        // Stage 6, after the tools ran: the model is halfway through writing its answer.
        game.controller(6).play(Mail("Ben", "How much is a margherita and a cola?"))
        scope.advanceTimeBy(2_800)
        shoot("stage-6-writing", Screen.Play(6), height = 1100)
        scope.advanceUntilIdle()
        shoot("stage-6-clear", Screen.Play(6))

        game.controller(7).play(delivery)
        scope.advanceUntilIdle()
        shoot("stage-7-game-over", Screen.Play(7))

        shoot("stage-2-ready", Screen.Play(2), dark = false)

        // A tile was pressed: its code is shown under the board.
        game.controller(2).toggleCode("read")
        shoot("stage-2-code", Screen.Play(2), height = 1500, dark = false)
        game.controller(6).toggleCode("tools")
        shoot("stage-6-code", Screen.Play(6), height = 1900)
        game.controller(4).toggleCode("kitchen")
        shoot("stage-4-code-phone", Screen.Play(4), width = 390, height = 1700)
        shoot("stages", Screen.Stages)
        shoot("stages-phone", Screen.Stages, width = 390, height = 1100)
        shoot("options", Screen.Options)
        shoot("options-claude", Screen.Options, height = 1000, settings = Settings(mode = ModelMode.Claude, apiKey = "sk-ant-example"))
    }
}
