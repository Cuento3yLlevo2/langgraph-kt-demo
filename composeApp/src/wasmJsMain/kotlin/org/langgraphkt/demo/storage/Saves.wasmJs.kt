package org.langgraphkt.demo.storage

import org.langgraphkt.Checkpointer
import org.langgraphkt.checkpoint.browser.LocalStorageCheckpointer
import org.langgraphkt.demo.game.Ticket
import org.langgraphkt.serialization.KotlinxStateSerializer

/** The runs are entries of `localStorage`, so they survive a reload of the page. */
actual fun platformCheckpointer(): Checkpointer<Ticket> =
    LocalStorageCheckpointer(KotlinxStateSerializer<Ticket>(), keyPrefix = "pixelpizza.save.")
