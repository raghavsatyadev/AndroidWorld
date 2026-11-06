# AndroidWorld

A Compose Multiplatform desktop app (Material 3 Expressive) for working with Android devices and
emulators. It also runs an MCP server, so coding agents can see and drive the selected device.

What it does:

- Device list and selection, device facts, current app controls, screenshots
- Logcat with level, search and package filters
- Apps list and app details: shared preferences and SQLite editor, permissions, deep links
- Quick toggles, font scale, density, locale, location, proxy and emulator network throttle
- Screen share and recording (scrcpy)
- Emulators: list, start, stop, create and delete AVDs through the `android` CLI
- Command browser for common shell commands, and a `dumpsys` viewer
- MCP server (screenshots, UI tree, tap/type/swipe, apps, prefs, databases, logcat) with a one-click
  Claude Code connection

See [FEATURES.md](FEATURES.md) for the full feature matrix and what is still open.

## Requirements

- JDK 21
- Android SDK platform tools (`adb`) on PATH
- Optional: Google's [`android` CLI](https://developer.android.com/tools) (emulators, layout,
  screen capture) and [scrcpy](https://github.com/Genymobile/scrcpy) (screen share)

## Build and run

```bash
./gradlew :desktop:run
```

```bash
./gradlew test
```

Modules: `:domain`, `:application`, `:adapters-jvm` and `:adapters-adb` are the device core
(package `io.github.raghavsatyadev.androidworld.core`); `:desktop` is the app (package `io.github.raghavsatyadev.androidworld.desktop`).

## License

MIT, see [LICENSE](LICENSE). Third-party code keeps its own license, listed below.

## Credits

AndroidWorld builds on three projects, used with their authors' permission:

| Project | Used for | License |
| --- | --- | --- |
| [AndroidDeveloperToolkit](https://github.com/dkej123/AndroidDeveloperToolkit) (Device Cockpit) | The device core (`domain`, `application`, `adapters-jvm`, `adapters-adb`): device model, logcat pipeline, screenshots, app data, device controls and the MCP server. Local change: `ProcessOutputKind.Detached` in `JvmProcessExecutor`, for `android emulator start`. | MIT (below) |
| [porpita](https://github.com/rajumark/porpita) | Feature and UI reference; the command browser catalog is a pruned, corrected rewrite of its command list | Used with permission |
| robomori (AvdBuddy) | Screen share: `desktop/.../mirror/ScrcpyMirror.kt` and `ScrcpyControl.kt` are ported from it (HID keyboard left out) | Used with permission |

The device core in turn ports parts of [Oh My Android](https://github.com/ateymoori/oh-my-android)
(layout snapshot, UI tree text, accessibility traversal, input, foreground app, setting toggles,
locale, emulator location and MCP tools), MIT. H.264 decoding for screen share uses
[JCodec](https://github.com/jcodec/jcodec) 0.2.5, BSD 2-Clause.

<details>
<summary>License texts</summary>

AndroidDeveloperToolkit (Device Cockpit):

```
MIT License

Copyright (c) 2026 Damian Kwaśniak

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

Oh My Android (ported at commit `f7419042ef55893831350f17013a3b1ed783a10c`):

```
MIT License

Copyright (c) 2026 Royan AB

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

</details>
