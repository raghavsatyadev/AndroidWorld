# AndroidWorld: Microsoft Store listing (first submission)

Store name: reserve **AndroidWorld** in Partner Center (fallback if taken: "AndroidWorld Device
Toolkit"). Language English (United States). Paste each block into the matching Partner Center
field. Google Play does not apply: AndroidWorld is a Windows desktop app with no Android target.

## Privacy policy URL

https://raghavsatyadev.github.io/projects/androidworld/privacy/

Linked in the app under Settings → About.

## Other Properties / Support fields

- Website: https://github.com/raghavsatyadev/AndroidWorld
- Support contact: https://github.com/raghavsatyadev/AndroidWorld/issues
- Copyright and trademark info: `© 2026 Raghav Satyadev. Android is a trademark of Google LLC.`
- Developed by: `Raghav Satyadev`

## Category

- Category: **Developer tools**. Subcategory: none.
- Secondary category: **Utilities & tools**.

## Product declarations

- Leave **generative AI** unticked: the app makes no AI content. Its MCP server lets coding agents
  the user runs (for example Claude Code) drive a device; the AI belongs to those agents.
- Leave "allows purchases outside the Store commerce system" unticked (the app sells nothing).
- Leave the accessibility declaration unticked unless the Narrator/High Contrast checks are done.
- Leave "install to alternate drives" and "OneDrive backups" at their defaults. Settings live in
  `%USERPROFILE%\.androidworld`, outside the package, so Windows app backup does not include them.

## Short description (≤ 270 chars recommended)

Work with your Android phones and emulators from Windows: device info, logcat, apps and their data, screen share and recording, device settings, emulators, and an MCP server so coding agents can see and drive the device. Uses your own adb; no account.

## Description (plain text, ≤ 10,000 chars, no URLs)

AndroidWorld is a desktop app for Android developers and testers. It puts the device and emulator tasks you usually do with adb commands into one window, and lets coding agents see and drive the device too.

It works with the Android SDK platform tools (adb) you already have. Connect a phone with USB or wireless debugging, or start an emulator, and pick it from the device list.

DEVICES AND LOGS
See device facts at a glance, control the app in front, and take screenshots straight to a folder and the clipboard. Read logcat live with level, search and package filters.

APPS AND THEIR DATA
Browse installed apps, launch, stop, clear or uninstall them, and look inside debuggable apps: edit shared preferences and SQLite databases, check permissions, and test deep links and app links.

DEVICE SETTINGS IN ONE PLACE
Quick toggles, font scale, display density, locale, location, HTTP proxy and emulator network throttling, with one click to put things back.

SCREEN SHARE AND RECORDING
Mirror and control the device screen and record it, using scrcpy.

EMULATORS
List, start, stop, create and delete Android virtual devices through Google's android command-line tool.

FILES, COMMANDS AND SHELL
Browse and pull device files, run common shell commands from a curated catalog, read dumpsys output, and use an adb shell terminal.

FOR CODING AGENTS
Turn on the built-in MCP server and connect Claude Code in one click, or any other MCP client. Agents can take screenshots, read the screen's UI tree, tap, type and swipe, and read apps, preferences, databases and logs. The server listens only on your computer, needs an access key, and is off until you turn it on.

PRIVATE BY DESIGN
No account, no ads, no analytics. Your settings stay on your computer, and device data goes only to the tools and agents you connect.

Requirements: Android SDK platform tools (adb). Optional: Google's android command-line tool for emulators, and scrcpy for screen share. The app shows how to install each.

## Product features (≤ 20, each ≤ 200 chars, no bullets)

1. Device list and facts for phones and emulators connected over USB or Wi-Fi
2. Live logcat with level, search and package filters
3. Screenshots saved to a folder and copied to the clipboard
4. Apps: launch, force stop, clear data, uninstall, permissions
5. Edit shared preferences and SQLite databases of debuggable apps
6. Test deep links and check app link verification
7. Quick toggles, font scale, density, locale, location and proxy, with one-click restore
8. Emulator network throttling
9. Screen share, control and recording with scrcpy
10. Create, start, stop and delete emulators
11. Device file browser, command catalog, dumpsys viewer and adb shell
12. Built-in MCP server for coding agents, with one-click Claude Code setup
13. No account, no telemetry: settings stay on your PC

## Keywords (≤ 7, each ≤ 40 chars, ≤ 21 words in total)

adb · android developer tools · logcat viewer · android emulator · screen mirroring · mcp server · device manager
(7 terms, 12 words)

## What's new in this version

Leave blank for the first submission.

## Screenshots (desktop, PNG, sample device)

Folder `docs/store/screenshots/`, taken from the release app image on a stock Android emulator
(no personal data). Upload in file-name order; captions are in `screenshots/captions.md`.

## Store logos

- 1:1 app tile icon 300 × 300: `docs/store/logos/app-tile-icon-300x300.png`.
- Box art and poster art: games only, skip. 16:9 super hero art: optional, not made.
- The package's own logos are in `desktop/msix/Assets` (StoreLogo 50 × 50, Square150x150,
  Square44x44 with target sizes 16–256), rendered from `desktop/src/main/resources/androidworld-logo.svg`.

## Age rating (IARC questionnaire)

Expected result: IARC 3+ / PEGI 3 / ESRB Everyone. Suggested answers:

- Category: **Utility, Productivity, Communication or Other**.
- Violence, fear, sexual content, nudity, crude humour, bad language, drugs, alcohol, tobacco,
  gambling or simulated gambling: **No** to each.
- Can users interact or exchange content with other users? **No.**
- Does the app share the user's current physical location with other users? **No.** (Setting an
  emulator's location sends coordinates the user types to their own emulator only.)
- Does the app allow purchases of digital goods? **No.**
- Does the app give unrestricted access to the internet (web browser or search engine)? **No.**
- Does the app collect or share personal information with third parties? **No.**
