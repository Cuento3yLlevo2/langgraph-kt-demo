package org.langgraphkt.demo.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import org.langgraphkt.demo.storage.AndroidStorage
import org.langgraphkt.demo.ui.App

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Settings and saved runs go to the app's private directory.
        AndroidStorage.filesDir = filesDir
        enableEdgeToEdge()
        setContent {
            // Keeps the game clear of the status bar, the navigation bar and the keyboard.
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                App()
            }
        }
    }
}
