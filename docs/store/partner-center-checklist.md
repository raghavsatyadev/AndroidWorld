# AndroidWorld: first Store submission, your clicks in Partner Center

Texts are in `docs/store/listing.md` and `docs/store/restricted-capabilities.md`. Google Play does
not apply (no Android target).

Before you start:
- [ ] Partner Center → Apps and games → **New product → MSIX or PWA app**, reserve the name
  **AndroidWorld** (fallback in `listing.md`).
- [ ] Product identity page: copy **Package/Identity/Name** into `msixIdentityName` and the reserved
  name into `msixDisplayName` in `gradle.properties` (the current values are placeholders). Check
  that Package/Identity/Publisher matches `msixPublisher`.
- [ ] Rebuild: `.\gradlew.bat :desktop:packageMsix`. Upload file:
  `desktop/build/msix/AndroidWorld-<version>.0.msix` (unsigned; the Store signs it).
- [ ] Privacy URL is live: https://raghavsatyadev.github.io/projects/androidworld/privacy/

In **Start your submission**:

1. **Pricing and availability**: markets all, Public, Free.
2. **Properties**
   - [ ] Category Developer tools; secondary Utilities & tools.
   - [ ] Privacy policy: answer **Yes** to "accesses, collects or transmits personal information"
     (it reads device data and runs a local server), paste the privacy URL.
   - [ ] Website and support contact (GitHub repo and its Issues page).
   - [ ] Product declarations: leave generative AI unticked; rest at defaults.
   - [ ] System requirements: Windows 10 2004 or later, x64; Android SDK platform tools (adb).
3. **Age ratings**: IARC questionnaire, answers in `listing.md`.
4. **Packages**: drag in the `.msix`; it should show your reserved identity, version
   `<appVersion>.0`, x64, Windows 10 Desktop 10.0.19041+. Device family: Desktop only.
5. **Store listings** → English (United States): description, short description, features,
   keywords, copyright from `listing.md`; screenshots `docs/store/screenshots/01…05` with the
   captions in `screenshots/captions.md`; 1:1 app tile icon `docs/store/logos/app-tile-icon-300x300.png`.
6. **Submission options**
   - [ ] Restricted capabilities: paste the runFullTrust and unvirtualizedResources texts.
   - [ ] Notes for certification: "No account or sign-in is needed. AndroidWorld is a developer
     tool for Android phones and emulators and needs the Android SDK platform tools (adb). Without
     adb it opens to an empty state with a 'How to install adb' guide. To see the device features,
     install platform tools (developer.android.com/tools/releases/platform-tools) and either start
     an Android emulator or connect a phone with USB debugging on; it then appears in the device
     list at the top right. Screen share also needs scrcpy (optional). The app has no server of its
     own; its optional MCP server is off by default and listens only on 127.0.0.1 with an access
     key. It does not include generative AI."
7. [ ] **Submit for certification.** If unvirtualizedResources is rejected, follow
   `restricted-capabilities.md`.

Not tested here: installing the MSIX itself (it needs a trusted test certificate or Developer
Mode, which were not changed) and WACK. The release app image inside the package was run: it starts
without adb (install guide shown) and works with an emulator.
