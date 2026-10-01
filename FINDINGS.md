# Findings for langgraph-kt

What building this app showed about langgraph-kt `0.1.0-SNAPSHOT` (October 2026). The app uses
the library only through its published artifacts.

## What was checked

- The app builds against the artifacts from the local Maven repository on the JVM and on wasmJs.
  Gradle picked the right variant for each target with no extra configuration.
- The 19 common tests pass on the JVM and in headless Chrome (wasmJs). One more browser-only test
  pauses a run in `localStorage` and resumes it from a second session.
- The production bundle was driven in headless Chrome with the scripted model: the tool loop, the
  approval flow with a change request, a page reload while paused, and the parallel research run
  all behaved as expected.
- With an invalid key, a request from the browser reached the Claude API and its error came back
  as `NodeExecutionException` naming the `assistant` node.

Not checked: a successful Claude call (no API key was available), the desktop window, the Stop
button, and any browser other than Chrome.

## Gaps found, now closed in the library

The first version of this app needed three workarounds. All three are fixed in langgraph-kt by
[pull request 7](https://github.com/Cuento3yLlevo2/langgraph-kt/pull/7), and the app now uses the
new APIs instead.

| Gap | Workaround the app had | Library fix |
|---|---|---|
| A checkpointer for a new storage had to rebuild the checkpoint's JSON envelope | A 50-line `StorageCheckpointer` with its own format | `CheckpointCodec`; the checkpointer is now three one-line methods |
| `stream()` only reported finished steps, so the UI could not show running nodes | A `RunTracker` wrapped around every node action | `GraphEvent.NodeStarted` and `NodeCompleted` |
| A compiled graph could not be inspected, so each graph's layout was listed by hand | A `stages` list next to each graph | `CompiledGraph.topology` |

Writing the codec's tests also uncovered a bug: `FileCheckpointer` never wrote its format version
into the file. That is fixed in the same pull request.

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
