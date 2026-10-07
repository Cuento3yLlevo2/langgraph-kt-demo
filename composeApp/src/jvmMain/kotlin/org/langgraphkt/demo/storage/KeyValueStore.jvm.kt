package org.langgraphkt.demo.storage

import dev.deeptelar.telar.Checkpointer
import org.langgraphkt.demo.game.Ticket
import java.io.File

private val home = File(System.getProperty("user.home"), ".langgraph-kt-demo")

actual fun platformStore(): KeyValueStore = FileStore(home)

actual fun platformCheckpointer(): Checkpointer<Ticket> = fileCheckpointer(File(home, "saves"))
