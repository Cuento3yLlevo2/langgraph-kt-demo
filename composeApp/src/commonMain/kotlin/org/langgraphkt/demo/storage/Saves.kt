package org.langgraphkt.demo.storage

import org.langgraphkt.Checkpointer
import org.langgraphkt.demo.game.Ticket

/**
 * Where this platform keeps the runs of the stages: the library's `LocalStorageCheckpointer` in the
 * browser, and its `FileCheckpointer` on the desktop and on Android.
 */
expect fun platformCheckpointer(): Checkpointer<Ticket>
