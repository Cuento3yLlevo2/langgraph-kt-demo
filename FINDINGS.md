# Findings for langgraph-kt

What building this app showed about langgraph-kt `0.1.0-SNAPSHOT` (the `develop` branch on
2026-10-01). The app uses the library only through its published artifacts.

## What was checked

- The app builds against the artifacts from the local Maven repository on the JVM and on wasmJs.
  Gradle picked the right variant for each target with no extra configuration.
- The 18 common tests pass on the JVM and in headless Chrome (wasmJs). One more browser-only test
  pauses a run in `localStorage` and resumes it from a second session.
- The production bundle was driven in headless Chrome with the scripted model: the tool loop, the
  approval flow with a change request, a page reload while paused, and the parallel research run
  all behaved as expected.
- With an invalid key, a request from the browser reached the Claude API and its error came back
  as `NodeExecutionException` naming the `assistant` node.

Not checked: a successful Claude call (no API key was available), the desktop window, the Stop
button, and any browser other than Chrome.

## Gaps worth closing before the release

### 1. Writing a checkpointer means re-inventing the checkpoint format

`FileCheckpointer` only works under Node.js on JS and Wasm, so a browser app needs its own
checkpointer. `StorageCheckpointer` here is about 50 lines, and most of them rebuild what
`FileCheckpointer` already has internally: a JSON envelope for `state`, `nextNodes`, `step` and
`interruptedBefore`, and the mapping of read errors to `CheckpointCorruptedException`.

Suggestion: make the envelope public in `langgraph-kt-serialization`, for example a
`CheckpointCodec<State>` with `encode(Checkpoint<State>): String` and `decode(String)`. A
checkpointer for `localStorage`, Room, SQLDelight or Redis is then a few lines, and all of them
share one versioned format.

### 2. `stream()` cannot show which node is running

`GraphEvent.StepCompleted` arrives when a whole step has finished. A UI cannot show that a node has
started, and with parallel branches it cannot show which ones are still working. The app works
around it by wrapping every node action (`RunTracker`).

Suggestion: add `GraphEvent.NodeStarted` and `GraphEvent.NodeCompleted` (or a listener on
`GraphConfig`). This is the gap a Compose user hits first.

### 3. A compiled graph cannot be inspected

To draw the graph, each workflow lists its stages by hand next to the graph definition, and the two
can drift apart. The original plan had a read-only `GraphTopology`; it was not implemented.

Suggestion: expose the nodes, edges and declared conditional targets of a `CompiledGraph`.

## Smaller observations

- **No way to ask where a thread stands.** After a failed `resume`, the app reads the checkpoint
  itself (`checkpointer.load(threadId)`) to decide what to show. A `CompiledGraph.state(config)`
  helper would make that intent clearer.
- **The approval pattern deserves a guide.** "Approve, or go back with feedback" needs a
  pass-through `review` node with `interruptBefore` and a conditional edge after it. It works well,
  but it is not obvious from the README, which only shows the approve-and-continue case.
- **Multi-turn chat restarts the graph each turn.** `invoke` always starts a new run, so the app
  passes the whole conversation in as the input state. That is fine for a chat screen, but there is
  no built-in way to add input to a thread's saved state.
- **`START` and `END` are `__START__` and `__END__`.** The app trims the underscores for display.
- **No model integration outside the JVM.** The Claude client here is about 100 lines of Ktor.
  A multiplatform model module is a candidate for after the first release, not a blocker.

## What worked well

- The three graphs compiled and ran correctly as first written; no library bug turned up.
- The `START then a then b` DSL and `conditionalEdge(from, targets)` read clearly in real graphs.
- Interrupt and resume worked across a page reload on the first attempt, including a second
  interrupt after looping back to `draft`.
- Fan-out with a `Reducer` ran the three branches concurrently in the browser.
- `NodeExecutionException` gave the UI a useful error message with no extra code.
