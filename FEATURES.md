# AndroidWorld — feature matrix

A standalone Compose Multiplatform **desktop** app (Material 3 Expressive) for working with Android
devices and emulators, which also exposes the device to coding agents over **MCP**. Not an IDE plugin.
See the README for credits.

## Rules

- Backend = the core library's `:domain`, `:application`, `:adapters-jvm`, `:adapters-adb`,
  (package `io.github.raghavsatyadev.androidworld.core`). New features follow the same layering.
- UI = `:desktop` (CMP + `MaterialExpressiveTheme`). No business logic in composables.
- **Use the `android` CLI** wherever it already does the job (emulator lifecycle, AVD create/remove,
  SDK/system-image install, `layout`, `screen capture`, `install`/`run`). Don't reimplement those.
- Files ≤ 500 lines (vendored core files exempt unless edited).

## Matrix

Legend: **R** reuse core code · **P** port logic to Kotlin · **CLI** wrap `android` CLI · **N** new.

| Feature | How | Wave |
| --- | --- | --- |
| Device list, selection, Wi-Fi pairing | R | 1 |
| Device facts (model, Android, battery, screen) | R | 1 |
| Current app: restart, kill, clear data, uninstall, permissions | R | 1 |
| Screenshot / full-page screenshot | R | 1 |
| Logcat (levels, search, package filter) | R | 1 |
| MCP server (screenshots w/ marks, UI tree, tap/type/swipe, apps, prefs, DBs, logcat) | R + desktop lifecycle | 1 |
| Claude Code connector + agent skill install | R | 1 |
| Apps list with icons, pins, launch/stop/clear/uninstall | R | 2 |
| App details: shared prefs + SQLite editor, permissions, deep links | R | 2 |
| Deep link launcher with history | R | 2 |
| Quick toggles, font scale, density, locale, location | R | 2 |
| Proxy + emulator network throttle (done) | R | 2 |
| Mirroring + recording (scrcpy) (done) | R | 2 |
| Layout inspector + accessibility audit | R | 3 |
| Emulators: list/start/stop/create/delete (done) | CLI | 2 |
| System images download | CLI (`android sdk`) | 3 |
| Embedded emulator screen (gRPC stream + touch) | P | 3 |
| Device frames, rotation, fold | P | 4 |
| QR Wi-Fi pairing, TV remote, software keyboard | P | 3 |
| Contacts / SMS / call log / calendar viewers | P (content providers) | 3 |
| Command browser (dumpsys, input, pm, settings, setprop…) (done) | P (data + runner) | 2 |
| Built-in adb shell terminal | P | 3 |
| File browser push/pull | P | 3 |
| System properties / services / settings editor | P | 3 |
| Settings: tool paths, capture dir, MCP access level | R + N UI | 1 |

## Wave 1 (vertical slice)

Device picker → Device view (facts, current app, screenshot) → Logcat → MCP server on/off with
Claude Code connect. Must run with `./gradlew :desktop:run`.
