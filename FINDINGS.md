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
- **No model integration outside the JVM.** Closed in `0.1.0-alpha02` by `langgraph-kt-agent` and
  `langgraph-kt-anthropic`. See [The third pass](#the-third-pass-the-librarys-agent-modules).

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

## The third pass: the library's agent modules

`0.1.0-alpha02` added `langgraph-kt-agent` (a chat model interface, tools, a tool-calling loop) and
`langgraph-kt-anthropic` (a Claude client on Ktor). They grew out of this app's `llm/` package, and
this pass replaced that package with them. It was the first use of those modules from outside the
library's repository.

What changed here:

- The app's own `ChatModel`, its message types and its 110-line Messages API client are gone.
- The agent of stage 6 was two hand-written nodes, a conditional edge and a function that ran the
  tools. It is now one call to `toolLoop`.
- The two tools had JSON schemas written by hand and read their input from a `JsonObject`. Each is
  now a `@Serializable` class with a `@Description`.
- What stayed is what belongs to the game: the scripted model, and the list of Claude models with
  the request fields each one takes.

What was checked:

- The 36 common tests pass on the JVM and in headless Chrome (wasmJs), and the JVM run draws every
  screen. Gradle picked the right variant of the new modules for the browser, the desktop and
  Android with no extra configuration.
- A test runs the agent against a mocked Messages API and checks that Claude's thinking block goes
  back unchanged with the tool result.
- The Android app assembles.

Not checked: a real Claude call from the game. The library's own sample was run against the real
API, but not with the `fallbacks` and `output_config` fields the game adds.

What the pass showed about the library:

- **The model-specific request fields needed no library change.** `AnthropicChatModel` takes extra
  body fields and headers, which covered the effort setting, the fallback beta and the header a
  browser needs.
- **A failed model call did not say which node failed.** The engine wrapped what a node threw in
  `NodeExecutionException`, which names the node, but it passed on an exception of the library
  itself as it was, and `ChatModelException` is one. The game marks the failed node on the board, so
  it fell back to the node that was running. Fixed in `0.1.0-alpha03`: the engine wraps every
  exception of a node, so the failure names its node and the fallback is gone.
- **A conversation that starts from other fields of the state was easy to get wrong.** The ticket
  holds the customer and the message, and the first user message is built from them. `toolLoop`
  reads the conversation with `messages` and adds to it with `append`, and both had to agree on
  that first message: an `append` that added to the stored list dropped it, and nothing failed
  until the model answered a conversation without a question. The app had one function that both
  called. Fixed in
  `0.1.0-alpha03`: `toolLoop` takes a `firstMessage` and stores it with the model's first answer.
  The app's shared function is gone, and `messages` and `append` read and write one list.
- **`toolLoop` has no place for work around the tools.** Every node of the game pretends to take a
  moment. For the tools node, the app wraps each tool. That is three lines, and fine.
- **A text answer that was cut off was not visible to the app.** `ChatResponse.truncated` said so,
  but `append` only receives the messages. The old client failed the run in that case. Fixed in
  `0.1.0-alpha03`: the flag moved to the message (`ChatMessage.Assistant.truncated`), so `append`
  sees it. The game does not use it yet; its answers are two sentences long.
- **Changing the state's shape was handled by the existing checkpoint API.** Old saves of stage 6
  hold the conversation in the old shape. Loading one throws `CheckpointCorruptedException`, and the
  app deletes the save. No migration code was needed.
- **Sealed message types made the run log simpler.** One `when` over `ChatMessage` replaced two
  lookups into raw content blocks.
