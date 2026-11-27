# AndroidWorld: restricted capabilities for Store certification

The package (`desktop/msix/AppxManifest.xml`) declares two restricted capabilities. After the
`.msix` is uploaded, Partner Center asks why each is needed (Submission options → Restricted
capabilities). Paste the text below; it also fits in "Notes for certification".

Other capabilities, which need no justification:
- `internetClient`: the scrcpy version check (GitHub API) and deep link checks
  (`/.well-known/assetlinks.json` on the hosts the user's links name).
- `privateNetworkClientServer`: adb over Wi-Fi (pairing and wireless debugging on the local
  network) and the local MCP server (127.0.0.1 only, off by default).

## Plan

1. **Submit with `unvirtualizedResources`** (this package as built).
2. **If certification rejects it:** reply once in Partner Center with the reason below. If it is
   rejected again, ship a **virtualized** build (see the end of this file).
3. **Keep the MSI** on GitHub Releases as the full-featured download in every case.

## runFullTrust

Paste:

> AndroidWorld is a desktop developer tool (Kotlin/JVM with Compose for Desktop) packaged with the
> Desktop Bridge. runFullTrust is needed for its Windows.FullTrustApplication entry point: it runs
> a Java runtime and starts the Android developer tools the user installed (adb, the Android
> emulator, Google's android command-line tool and scrcpy) to work with the Android phones and
> emulators the user connects, and it can run a local MCP server on 127.0.0.1 for coding agents.

Low risk: runFullTrust is the standard capability for Win32 apps in MSIX and is approved routinely.

## unvirtualizedResources

Paste:

> AndroidWorld turns off file write virtualization (desktop6:FileSystemWriteVirtualization set to
> disabled; registry virtualization stays on). It is a front end for the Android SDK tools the user
> already has: adb, the Android emulator and Google's android command-line tool. Those tools run as
> child processes of the app and must write to the user's shared Android SDK and emulator folders,
> for example %LOCALAPPDATA%\Android\Sdk (system images it installs for new emulators) and the
> emulator's runtime files under %LOCALAPPDATA%\Temp, which Android Studio and the command line use
> as well. With virtualization on, these writes land in the package's private folder: emulators and
> system images created from AndroidWorld would be invisible to Android Studio, and emulators
> started elsewhere could not be found. The app does not change system settings, other apps' data
> or HKLM, and it writes its own settings only to %USERPROFILE%\.androidworld.

## If certification rejects unvirtualizedResources

Microsoft documents this capability as mainly for games, so a rejection is possible.

1. **Reply first.** Restate the shared Android SDK reason; reviewers sometimes approve on a clearer
   explanation.
2. **Ship virtualized:** remove `unvirtualizedResources` and the `desktop6` line from
   `desktop/msix/AppxManifest.xml`, bump `appVersion`, rebuild with `packageMsix` and resubmit.
   What changes for users (not yet tested):
   - Device features (logcat, apps, screenshots, screen share, MCP) keep working: adb writes to
     `%USERPROFILE%\.android`, which is not virtualized.
   - Installing system images and creating emulators through the `android` CLI may write into the
     package's private copy of `%LOCALAPPDATA%`; Android Studio would not see them. Say so in the
     listing, or point users to Android Studio for system images.
   - Settings in `%USERPROFILE%\.androidworld` are unaffected.
