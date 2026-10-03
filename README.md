# Pixel Pizza

A small game about graphs. You run the help desk of a pizza shop: customers write in, a
[langgraph-kt](https://github.com/Cuento3yLlevo2/langgraph-kt) graph writes back, and you watch
the ticket cross the board node by node.

![Stage 8: the kitchen and the driver run at the same time](docs/stage.png)

It is the [langgraph-kt tutorial](https://github.com/Cuento3yLlevo2/langgraph-kt/tree/main/docs)
made playable. Eight stages, one new move each, the same Pixel Pizza help desk growing from two
nodes to a full agent workflow. One Compose Multiplatform codebase runs it in the browser
(Kotlin/Wasm) and on the desktop (JVM). No account and no API key needed.

## Stages

| # | Stage | The move | Tutorial |
|---|---|---|---|
| 1 | A line | Two nodes in a row: `node`, `then`, `invoke` | Level 2 |
| 2 | Choices | `conditionalEdge` picks one of three paths | Level 3 |
| 3 | Loops | An edge that goes back until the reply passes a check | Level 4 |
| 4 | Two at once | Fan-out to nodes with a `work` and an `update`, which run at the same time | Level 5 |
| 5 | Save points | `interruptBefore` stops the run for you; `resume` continues it, also after a reload | Level 7 |
| 6 | The agent | A model that calls tools, as a loop of two nodes | Level 8 |
| 7 | Game over | A node fails; `resume` retries from the last save | Level 9 |
| 8 | The full desk | All of it on one board | Level 10 |

Level 6 of the tutorial, watching a run with `stream()`, is not a stage. It is the game: the
board, the ticket panel and the run log are drawn from the events of `stream()` as they arrive.

| | |
|---|---|
| ![A run waiting at a save point](docs/save-point.png) | ![A failed node, with a retry from the last save](docs/game-over.png) |

## Play

langgraph-kt is not on Maven Central yet, so publish it to your local Maven repository first:

```bash
git clone https://github.com/Cuento3yLlevo2/langgraph-kt.git
cd langgraph-kt && ./gradlew publishToMavenLocal
```

Then, in this repository:

```bash
./gradlew :composeApp:wasmJsBrowserDevelopmentRun   # browser, with a dev server
./gradlew :composeApp:run                           # desktop
```

Requires JDK 17 or newer.

Stages 6 and 8 ask a model. By default that is a scripted one with fixed answers. To play them
with Claude, enter your own Anthropic API key under Options and pick a model; they are listed from
the cheapest to the most expensive, with their prices. The key goes straight from the app
to `api.anthropic.com` and is kept in memory unless you tick "Remember the key on this device".
Requests are billed to your account.

## How it works

- **A stage is a graph and a map.** [`game/HelpDesks.kt`](composeApp/src/commonMain/kotlin/org/langgraphkt/demo/game/HelpDesks.kt)
  and [`game/Agent.kt`](composeApp/src/commonMain/kotlin/org/langgraphkt/demo/game/Agent.kt) hold
  the eight graphs. [`game/Stages.kt`](composeApp/src/commonMain/kotlin/org/langgraphkt/demo/game/Stages.kt)
  gives each one its briefing, its inbox, and the cell of every node on the board.
- **The board draws the graph it is given.** Tiles sit where the stage puts them. The arrows are
  read from `CompiledGraph.topology`, and they light up from the `NodeStarted`, `NodeCompleted` and
  `StepCompleted` events of the run.
- **A save point is a checkpoint.** Runs are saved through a `Checkpointer` built on
  `CheckpointCodec`, into `localStorage` in the browser and into files on the desktop. That is why
  a ticket waiting for your decision survives a reload, and why a failed run can be retried from
  the node that failed.
- **The look is drawn, not themed.** Black, white, a ramp of greys and one red, after
  [nothing.tech](https://nothing.tech). The headings are a 5 by 7 dot-matrix alphabet in
  [`ui/DotMatrix.kt`](composeApp/src/commonMain/kotlin/org/langgraphkt/demo/ui/DotMatrix.kt), the
  pizza is text in [`ui/Sprites.kt`](composeApp/src/commonMain/kotlin/org/langgraphkt/demo/ui/Sprites.kt),
  and the rest is Geist Mono. There is no Material in the app.

## Layout

- `game/`: the ticket (the state), the graphs of the stages, the tools of the agent
- `llm/`: `ChatModel`, the Claude Messages API client (Ktor) and the scripted model
- `storage/`: `KeyValueStore` (`localStorage` in the browser, files on the desktop) and the
  `StorageCheckpointer` built on it
- `ui/`: the screens, the board, and `Game.kt`, which runs the graphs for them

## Tests

```bash
./gradlew :composeApp:jvmTest             # everything, on the JVM
./gradlew :composeApp:wasmJsBrowserTest   # the common tests again in headless Chrome (needs Chrome)
```

The JVM run also clicks through the game the way a player would, and draws every screen to
`composeApp/build/screenshots`. The pictures in this README come from there.

Building this app was a test of langgraph-kt before its first release. What that turned up is in
[FINDINGS.md](FINDINGS.md).

## License

[Apache License 2.0](LICENSE). Geist Mono is bundled under the
[SIL Open Font License](licenses/GeistMono-OFL.txt).
