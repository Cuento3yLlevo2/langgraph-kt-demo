package dev.deeptelar.telar.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.deeptelar.telar.demo.game.stages
import dev.deeptelar.telar.demo.ui.Board
import dev.deeptelar.telar.demo.ui.Body
import dev.deeptelar.telar.demo.ui.DotMatrix
import dev.deeptelar.telar.demo.ui.Label
import dev.deeptelar.telar.demo.ui.PixelArt
import dev.deeptelar.telar.demo.ui.Sprites
import dev.deeptelar.telar.demo.ui.StageController
import dev.deeptelar.telar.demo.ui.Theme

/** The size of the card in dp. It is drawn at twice that, 1280 x 640 pixels, the size GitHub asks for. */
const val SOCIAL_WIDTH = 640
const val SOCIAL_HEIGHT = 320

/**
 * The picture a link to the game is shown with, on GitHub and wherever the address of the game is
 * shared: the name, one sentence, and the board of [controller]'s stage as the run left it.
 *
 * It is not a screen of the game, so it lives with the test that draws it.
 */
@Composable
fun SocialCard(controller: StageController) {
    val colors = Theme.colors
    Column(
        Modifier.fillMaxSize().padding(horizontal = 36.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
            PixelArt(Sprites.slice, pixel = 3.dp)
            DotMatrix("Pixel Pizza", pitch = 6.dp, unlit = colors.line.copy(alpha = 0.55f))
        }
        Spacer(Modifier.height(14.dp))
        Body("A game about AI agent workflows in Kotlin")
        Spacer(Modifier.weight(1f))
        Board(controller, Modifier.width(452.dp))
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Label("${stages.size} stages / no api key needed")
            Label("built with Telar", color = colors.red)
        }
    }
}
