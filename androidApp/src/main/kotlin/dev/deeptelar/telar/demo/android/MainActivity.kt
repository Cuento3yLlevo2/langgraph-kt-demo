package dev.deeptelar.telar.demo.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import dev.deeptelar.telar.demo.storage.AndroidStorage
import dev.deeptelar.telar.demo.ui.App
import dev.deeptelar.telar.demo.ui.PizzaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Settings and saved runs go to the app's private directory.
        AndroidStorage.filesDir = filesDir
        enableEdgeToEdge()
        setContent {
            // The theme paints the game's background on the whole screen, also behind the system
            // bars. The padding keeps the game itself clear of the bars and the keyboard.
            PizzaTheme {
                Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                    App()
                }
            }
        }
    }
}
