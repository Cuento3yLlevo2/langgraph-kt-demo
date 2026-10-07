package dev.deeptelar.telar.demo.storage

import kotlinx.io.files.Path
import dev.deeptelar.telar.Checkpointer
import dev.deeptelar.telar.checkpoint.file.FileCheckpointer
import dev.deeptelar.telar.demo.game.Ticket
import dev.deeptelar.telar.serialization.KotlinxStateSerializer
import java.io.File

/** Keeps each run as a JSON file in [directory]. */
fun fileCheckpointer(directory: File): Checkpointer<Ticket> = FileCheckpointer(Path(directory.path), KotlinxStateSerializer<Ticket>())
