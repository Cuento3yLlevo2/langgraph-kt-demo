# Findings for langgraph-kt

What building this app showed about langgraph-kt `0.1.0-SNAPSHOT` (October 2026). The app uses
the library only through its published artifacts.

The app was built twice. The first version had three separate screens: a tool agent, an email
approval flow and a parallel research run. The second version is the game in this repository,
with the eight help desks of the library's tutorial as stages. Most of this file is about the
first version; [The second version](#the-second-version-the-game) says what the rebuild added.

## What was checked in the first version

- The app builds against the artifacts from the local Maven repository on the JVM and on wasmJs.
  Gradle picked the right variant for each target with no extra configuration.
- The 24 common tests pass on the JVM and in headless Chrome (wasmJs). One more browser-only test
  pauses a run in `localStorage` and resumes it from a second session.
- The production bundle was driven in headless Chrome with the scripted model: the tool loop, the
  approval flow with a change request, a page reload while paused, and the parallel research run
  all behaved as expected.
- With an invalid key, a request from the browser reached the Claude API and its error came back
  as `NodeExecutionException` naming the `assistant` node.
- Stop and Discard are covered by tests that drive the screen controllers. They found a bug in
  this app, not in the library: stopping a redraft left the approval buttons on screen although
  the saved run stood before `draft`. The controller now re-reads the saved run after a stop.
- The desktop app starts and draws its first screen on Linux.

Not checked: a successful Claude call (no API key was available), the workflows in the desktop
window (only the browser build was clicked through), and any browser other than Chrome.

## Gaps found, now closed in the library

The first version of this app needed three workarounds. All three were fixed in langgraph-kt by
[pull request 7](https://github.com/Cuento3yLlevo2/langgraph-kt/pull/7), which is merged, and the
app uses the new APIs instead.

| Gap | Workaround the app had | Library fix |
|---|---|---|
| A checkpointer for a new storage had to rebuild the checkpoint's JSON envelope | A 50-line `StorageCheckpointer` with its own format | `CheckpointCodec`; the checkpointer is now three one-line methods |
| `stream()` only reported finished steps, so the UI could not show running nodes | A `RunTracker` wrapped around every node action | `GraphEvent.NodeStarted` and `NodeCompleted` |
| A compiled graph could not be inspected, so each graph's layout was listed by hand | A `stages` list next to each graph | `CompiledGraph.topology` |

Writing the codec's tests also uncovered a bug: `FileCheckpointer` never wrote its format version
into the file. That is fixed in the same pull request.

## Smaller observations, addressed

- **There was no way to ask where a thread stands.** The app read the checkpoint itself to decide
  what to show after a reload or a failure. `CompiledGraph.lastResult(config)` now returns the
  thread's last `GraphResult`, and the email screen uses it.
- **The approval pattern deserved a guide.** "Approve, or go back with feedback" needs a
  pass-through `review` node with `interruptBefore` and a conditional edge after it. The library
  README now explains it, and `samples/ReviewLoop.kt` runs it.
- **Multi-turn chat restarts the graph each turn.** `invoke` always starts a new run, so a chat
  passes the whole conversation in as the input state. The README now shows this, with
  `lastResult` supplying the previous turn's state.

## Still open

- **`lastResult` cannot tell a pause from an unfinished step.** Both come back as `Interrupted`.
  The app tells them apart by `nextNodes`, which is enough here. `Checkpoint.interruptedBefore`
  has the exact answer for an app that needs it.
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

## The second version: the game

What was checked:

- The 31 common tests pass on the JVM and in headless Chrome (wasmJs), with one more browser-only
  test that pauses a run in `localStorage` and resumes it from a second session. They cover the
  graph of every stage and the game behind the screens: a run that lights its path, parallel
  nodes, a save point answered after a "reload", a failed node retried from its checkpoint, Stop,
  and the cleared stages surviving a new session.
- On the JVM, one test clicks through the screens (title, stage select, run, approve, next stage)
  and another draws every screen in dark, light and at phone width.
- The production browser bundle loads and draws the title screen in headless Chrome.

Not checked: clicking through the game in a real browser or in the desktop window, and, as
before, a successful Claude call.

What the rebuild showed about the library:

- **Eight graphs, no library change.** Every level of the tutorial ran as written inside a
  Compose app on both targets, sharing one `@Serializable` state.
- **`topology` is enough to draw a board, but not to lay one out.** The arrows, and which of them
  are conditional, come from `CompiledGraph.topology`. Where a node sits is still the app's
  decision: each stage places its nodes by hand, and a test checks that the placement and the
  graph agree.
- **Events name nodes, not edges.** To light the arrow a run just followed, the app pairs the
  nodes of the last `StepCompleted` with the next `NodeStarted` and looks the pair up in the
  topology. That is three lines here, but an event that says where a node was reached from would
  make it none.
- **`lastResult` after a failure says where a retry starts.** The game-over screen uses it to tell
  the player whether a retry resumes at the failed node or starts over because nothing was saved
  yet. It needed no extra bookkeeping.
