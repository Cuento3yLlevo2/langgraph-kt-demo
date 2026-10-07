package org.langgraphkt.demo.storage

import dev.deeptelar.telar.Checkpointer
import dev.deeptelar.telar.checkpoint.browser.LocalStorageCheckpointer
import org.langgraphkt.demo.game.Ticket
import dev.deeptelar.telar.serialization.KotlinxStateSerializer

/** The runs are entries of `localStorage`, so they survive a reload of the page. */
actual fun platformCheckpointer(): Checkpointer<Ticket> =
    LocalStorageCheckpointer(KotlinxStateSerializer<Ticket>(), keyPrefix = "pixelpizza.save.")
