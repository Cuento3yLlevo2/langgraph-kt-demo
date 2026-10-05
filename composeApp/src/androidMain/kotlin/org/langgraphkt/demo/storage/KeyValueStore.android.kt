package org.langgraphkt.demo.storage

import org.langgraphkt.Checkpointer
import org.langgraphkt.demo.game.Ticket
import java.io.File

/** The app's private directory. The activity sets it before the first screen is drawn. */
object AndroidStorage {
    lateinit var filesDir: File
}

actual fun platformStore(): KeyValueStore = FileStore(File(AndroidStorage.filesDir, "store"))

actual fun platformCheckpointer(): Checkpointer<Ticket> = fileCheckpointer(File(AndroidStorage.filesDir, "saves"))
