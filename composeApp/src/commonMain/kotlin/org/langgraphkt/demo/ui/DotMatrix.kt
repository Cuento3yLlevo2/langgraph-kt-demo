package org.langgraphkt.demo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private const val GLYPH_WIDTH = 5
private const val GLYPH_HEIGHT = 7

/** Every glyph is 5 dots wide and 7 high. Rows run from top to bottom; `#` is a lit dot. */
private val glyphs: Map<Char, List<String>> = mapOf(
    'A' to ".###. #...# #...# ##### #...# #...# #...#",
    'B' to "####. #...# #...# ####. #...# #...# ####.",
    'C' to ".###. #...# #.... #.... #.... #...# .###.",
    'D' to "####. #...# #...# #...# #...# #...# ####.",
    'E' to "##### #.... #.... ####. #.... #.... #####",
    'F' to "##### #.... #.... ####. #.... #.... #....",
    'G' to ".###. #...# #.... #.### #...# #...# .###.",
    'H' to "#...# #...# #...# ##### #...# #...# #...#",
    'I' to ".###. ..#.. ..#.. ..#.. ..#.. ..#.. .###.",
    'J' to "..### ...#. ...#. ...#. ...#. #..#. .##..",
    'K' to "#...# #..#. #.#.. ##... #.#.. #..#. #...#",
    'L' to "#.... #.... #.... #.... #.... #.... #####",
    'M' to "#...# ##.## #.#.# #.#.# #...# #...# #...#",
    'N' to "#...# ##..# #.#.# #..## #...# #...# #...#",
    'O' to ".###. #...# #...# #...# #...# #...# .###.",
    'P' to "####. #...# #...# ####. #.... #.... #....",
    'Q' to ".###. #...# #...# #...# #.#.# #..#. .##.#",
    'R' to "####. #...# #...# ####. #.#.. #..#. #...#",
    'S' to ".#### #.... #.... .###. ....# ....# ####.",
    'T' to "##### ..#.. ..#.. ..#.. ..#.. ..#.. ..#..",
    'U' to "#...# #...# #...# #...# #...# #...# .###.",
    'V' to "#...# #...# #...# #...# #...# .#.#. ..#..",
    'W' to "#...# #...# #...# #.#.# #.#.# ##.## #...#",
    'X' to "#...# #...# .#.#. ..#.. .#.#. #...# #...#",
    'Y' to "#...# #...# .#.#. ..#.. ..#.. ..#.. ..#..",
    'Z' to "##### ....# ...#. ..#.. .#... #.... #####",
    '0' to ".###. #...# #..## #.#.# ##..# #...# .###.",
    '1' to "..#.. .##.. ..#.. ..#.. ..#.. ..#.. .###.",
    '2' to ".###. #...# ....# ...#. ..#.. .#... #####",
    '3' to "##### ...#. ..#.. ...#. ....# #...# .###.",
    '4' to "...#. ..##. .#.#. #..#. ##### ...#. ...#.",
    '5' to "##### #.... ####. ....# ....# #...# .###.",
    '6' to "..##. .#... #.... ####. #...# #...# .###.",
    '7' to "##### ....# ...#. ..#.. .#... .#... .#...",
    '8' to ".###. #...# #...# .###. #...# #...# .###.",
    '9' to ".###. #...# #...# .#### ....# ...#. .##..",
    '.' to "..... ..... ..... ..... ..... .##.. .##..",
    ':' to "..... .##.. .##.. ..... .##.. .##.. .....",
    '!' to "..#.. ..#.. ..#.. ..#.. ..#.. ..... ..#..",
    '?' to ".###. #...# ....# ...#. ..#.. ..... ..#..",
    '-' to "..... ..... ..... ##### ..... ..... .....",
    '/' to "....# ....# ...#. ..#.. .#... #.... #....",
).mapValues { (_, rows) -> rows.split(' ') }

/**
 * Text on a dot-matrix display, one line, upper case only. A character without a glyph is a gap.
 *
 * @param pitch the distance between two dots; a character is five dots wide plus one of spacing.
 * @param unlit the colour of the dots that are off. Leave it unspecified to draw only the lit ones.
 */
@Composable
fun DotMatrix(
    text: String,
    modifier: Modifier = Modifier,
    pitch: Dp = 4.dp,
    color: Color = Theme.colors.ink,
    unlit: Color = Color.Unspecified,
) {
    val upper = text.uppercase()
    val columns = (upper.length * (GLYPH_WIDTH + 1) - 1).coerceAtLeast(0)
    Canvas(
        modifier
            .size(pitch * columns, pitch * GLYPH_HEIGHT)
            .semantics { contentDescription = text },
    ) {
        val step = pitch.toPx()
        val radius = step * 0.38f
        fun dot(column: Int, row: Int, dotColor: Color) =
            drawCircle(dotColor, radius, Offset((column + 0.5f) * step, (row + 0.5f) * step))

        upper.forEachIndexed { index, char ->
            val glyph = glyphs[char]
            val left = index * (GLYPH_WIDTH + 1)
            // The spacing column after a character belongs to the display too, so it gets unlit dots.
            val width = if (index == upper.lastIndex) GLYPH_WIDTH else GLYPH_WIDTH + 1
            for (row in 0 until GLYPH_HEIGHT) {
                for (column in 0 until width) {
                    val lit = glyph != null && column < GLYPH_WIDTH && glyph[row][column] == '#'
                    if (lit) dot(left + column, row, color) else if (unlit != Color.Unspecified) dot(left + column, row, unlit)
                }
            }
        }
    }
}

/** The width [DotMatrix] needs for [text] at [pitch]. */
fun dotMatrixWidth(text: String, pitch: Dp): Dp = pitch * (text.length * (GLYPH_WIDTH + 1) - 1).coerceAtLeast(0)
