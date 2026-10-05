package org.langgraphkt.demo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.langgraphkt.demo.game.Mail
import org.langgraphkt.demo.game.scriptedModel
import org.langgraphkt.demo.storage.MemoryStore
import org.langgraphkt.demo.storage.fileCheckpointer
import org.langgraphkt.demo.ui.Game
import org.langgraphkt.demo.ui.Phase
import org.langgraphkt.demo.ui.StageController
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** On the desktop and on Android a paused stage is a file, which the next start of the app finds. */
class FileSavesTest {
    private val directory = Files.createTempDirectory("pixelpizza-saves").toFile()

    /** A game as the app creates it: with a checkpointer on the directory. Each call is a new start. */
    private fun TestScope.game() = Game(scriptedModel(), MemoryStore(), fileCheckpointer(directory), this, workMillis = 0)

    /** Files are written on real threads, so the test waits in real time and not in the time of the test. */
    private suspend fun StageController.awaitSavePoint() = withContext(Dispatchers.Default) {
        withTimeout(5_000) { while (phase != Phase.SavePoint) delay(10) }
    }

    @Test
    fun aStagePausedInOneSessionWaitsInTheNext() = runTest {
        val first = game().controller(5)
        first.play(Mail("Ben", "My pizza arrived cold. I want a refund."))
        first.awaitSavePoint()
        assertTrue(directory.listFiles().orEmpty().any { it.name == "stage-5.json" }, "the run is a file")

        val restarted = game().controller(5)
        restarted.awaitSavePoint()

        assertTrue(restarted.restored)
        assertEquals(12, restarted.ticket?.refund)
        directory.deleteRecursively()
    }
}
