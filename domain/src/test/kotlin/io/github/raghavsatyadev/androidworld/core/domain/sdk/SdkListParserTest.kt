package io.github.raghavsatyadev.androidworld.core.domain.sdk

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class SdkListParserTest {

    companion object {
        private const val ESC = "\u001B"

        val fullListing = listOf(
            "OpenJDK 64-Bit Server VM warning: The UseAllWindowsProcessorGroups flag is not supported on this Windows version and will be ignored.",
            "Installed packages:",
            "$ESC[32m  build-tools/36.0.0                                                                      36.0.0                                        Android SDK Build-Tools 36                      $ESC[39m$ESC[0m",
            "$ESC[33m  emulator                                                                         beta   37.3.9               ->        37.2.13        Android Emulator                                $ESC[39m$ESC[0m",
            "$ESC[32m  system-images/android-37.2/google_apis_playstore_ps16k/x86_64                           7.0.0                                         16 KB Page Size Google Play Intel x86_64 Atom System Image    $ESC[39m$ESC[0m",
            "Available packages:",
            "  system-images/android-34-ext10/google_apis_playstore/x86_64                             2.0.0 (+1)                                    Google Play Intel x86_64 Atom System Image",
            "  system-images/android-37.2/google_apis_playstore_ps16k/x86_64                           7.0.0                                         16 KB Page Size Google Play Intel x86_64 Atom System Image",
            "  system-images/android-CANARY/google_apis_playstore_ps16k/x86_64                         15.0.0                                        16 KB Page Size Google Play Intel x86_64 Atom System Image",
            "  system-images/android-36.1/android-wear-signed/arm64-v8a                                1.0.0                                         Wear OS 6.1 ARM 64 v8a System Image (signed)",
            "  system-images/android-10/default/armeabi-v7a                                            5.0.0                  ARM EABI v7a System Image",
            "  platforms/android-36                                                                    2.0.0                  Android SDK Platform 36",
        ).joinToString("\r\n")

        val emptyInstalledListing = listOf(
            "Installed packages:",
            "  (no installed packages)",
            "Available packages:",
            "  system-images/android-10/default/x86                                                    5.0.0                  Intel x86 Atom System Image",
        ).joinToString("\r\n")
    }

    @Test
    fun `installed and available package counts match expected listing`() {
        val listing = SdkListParser.parse(fullListing)
        listing.installed.size shouldBe 3
        listing.available.size shouldBe 6
    }

    @Test
    fun `emulator row parses channel, version, update, and description`() {
        val listing = SdkListParser.parse(fullListing)
        val emulator = listing.installed.first { it.path == "emulator" }
        emulator.channel shouldBe "beta"
        emulator.version shouldBe "37.3.9"
        emulator.update shouldBe "37.2.13"
        emulator.description shouldBe "Android Emulator"
    }

    @Test
    fun `version with suffix like plus one is kept whole`() {
        val listing = SdkListParser.parse(fullListing)
        val ext10 = listing.available.first { it.path.contains("android-34-ext10") }
        ext10.version shouldBe "2.0.0 (+1)"
    }

    @Test
    fun `description with spaces stays whole`() {
        val listing = SdkListParser.parse(fullListing)
        val ps16k = listing.installed.first { it.path.contains("37.2") }
        ps16k.description shouldBe "16 KB Page Size Google Play Intel x86_64 Atom System Image"
    }

    @Test
    fun `empty-installed fixture yields 0 installed and 1 available`() {
        val listing = SdkListParser.parse(emptyInstalledListing)
        listing.installed.size shouldBe 0
        listing.available.size shouldBe 1
        listing.available.single().path shouldBe "system-images/android-10/default/x86"
    }

    @Test
    fun `failure detects not found message`() {
        val line = "Package system-images/android-99/nope/x86 not found."
        SdkListParser.failure(line) shouldBe line
    }

    @Test
    fun `failure of real install output returns null`() {
        val output =
            "\rhttps://dl.google.com/android/repository/sys-img/android/armeabi-v7a-10_r05.zip...\r\n\rUnzipping system-images/android-10/default/armeabi-v7a......\r\n"
        SdkListParser.failure(output) shouldBe null
    }

    @Test
    fun `failure detects None of message`() {
        val line = "None of the specified packages were found."
        SdkListParser.failure(line) shouldBe line
    }

    @Test
    fun `failure detects Error or Failed messages while ignoring OpenJDK warnings`() {
        val output =
            "OpenJDK 64-Bit Server VM warning: The UseAllWindowsProcessorGroups flag is not supported\r\nError: Could not install\r\n"
        SdkListParser.failure(output) shouldBe "Error: Could not install"
        SdkListParser.failure("Failed to resolve package") shouldBe "Failed to resolve package"
    }
}
