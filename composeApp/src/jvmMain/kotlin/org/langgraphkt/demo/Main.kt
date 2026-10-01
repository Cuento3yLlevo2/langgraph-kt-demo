package org.langgraphkt.demo

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import org.langgraphkt.demo.ui.App

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "langgraph-kt demo") {
        App()
    }
}
