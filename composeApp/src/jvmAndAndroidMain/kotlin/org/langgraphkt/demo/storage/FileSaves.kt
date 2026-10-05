package org.langgraphkt.demo.storage

import kotlinx.io.files.Path
import org.langgraphkt.Checkpointer
import org.langgraphkt.checkpoint.file.FileCheckpointer
import org.langgraphkt.demo.game.Ticket
import org.langgraphkt.serialization.KotlinxStateSerializer
import java.io.File

/** Keeps each run as a JSON file in [directory]. */
fun fileCheckpointer(directory: File): Checkpointer<Ticket> = FileCheckpointer(Path(directory.path), KotlinxStateSerializer<Ticket>())
