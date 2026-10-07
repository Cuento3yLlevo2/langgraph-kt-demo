package dev.deeptelar.telar.demo

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import dev.deeptelar.telar.demo.ui.App

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Pixel Pizza") {
        App()
    }
}
