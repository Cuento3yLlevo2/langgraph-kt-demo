package dev.deeptelar.telar.demo.storage

import dev.deeptelar.telar.Checkpointer
import dev.deeptelar.telar.demo.game.Ticket
import java.io.File

private val home = File(System.getProperty("user.home"), ".telar-demo")

actual fun platformStore(): KeyValueStore = FileStore(home)

actual fun platformCheckpointer(): Checkpointer<Ticket> = fileCheckpointer(File(home, "saves"))
